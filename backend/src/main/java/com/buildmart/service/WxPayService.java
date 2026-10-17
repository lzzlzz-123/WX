package com.buildmart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.buildmart.common.CacheKeys;
import com.buildmart.config.WxPayProperties;
import com.buildmart.entity.PaymentRecord;
import com.buildmart.entity.PurchaseOrder;
import com.buildmart.mapper.PaymentRecordMapper;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationConfig;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.refund.RefundService;
import com.wechat.pay.java.service.refund.model.AmountReq;
import com.wechat.pay.java.service.refund.model.CreateRequest;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.RefundNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 微信支付 V3 闭环：JSAPI 下单、支付结果通知（按微信交易号幂等）、退款与退款通知。
 * 未配置商户凭证（wechat.pay.enabled=false）时接口明确报错，不伪装成功。
 */
@Service
public class WxPayService {
    private static final Logger log = LoggerFactory.getLogger(WxPayService.class);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final WxPayProperties properties;
    private final MiniappOrderService orderService;
    private final UserService userService;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RedisCacheService cache;

    private volatile Config config;
    private volatile JsapiServiceExtension jsapiService;
    private volatile RefundService refundService;
    private volatile com.wechat.pay.java.core.notification.NotificationParser parser;

    public WxPayService(WxPayProperties properties, MiniappOrderService orderService, UserService userService,
                        PaymentRecordMapper paymentRecordMapper, RedisCacheService cache) {
        this.properties = properties;
        this.orderService = orderService;
        this.userService = userService;
        this.paymentRecordMapper = paymentRecordMapper;
        this.cache = cache;
    }

    /**
     * 小程序发起支付：JSAPI 下单并返回 wx.requestPayment 所需参数。
     */
    public Map<String, String> createJsapiPayment(Long userId, Long orderId) {
        PurchaseOrder order = orderService.detail(userId, orderId).order();
        if (!MiniappOrderService.ST_PENDING_PAYMENT.equals(order.getStatus())) {
            throw new IllegalStateException("订单当前状态不可支付");
        }
        String openid = userService.requireById(userId).getOpenid();
        if (openid == null || openid.isBlank()) {
            throw new IllegalStateException("用户未完成微信授权，无法发起支付");
        }
        PrepayRequest request = new PrepayRequest();
        request.setAppid(appId);
        request.setMchid(properties.getMchid());
        request.setDescription("建材集采订单-" + order.getOrderNo());
        request.setOutTradeNo(order.getOrderNo());
        request.setNotifyUrl(properties.getNotifyUrl());
        Amount amount = new Amount();
        amount.setTotal(Math.toIntExact(toFen(order.getPayableAmount())));
        amount.setCurrency("CNY");
        request.setAmount(amount);
        Payer payer = new Payer();
        payer.setOpenid(openid);
        request.setPayer(payer);

        PrepayWithRequestPaymentResponse response = jsapiService().prepayWithRequestPayment(request);
        Map<String, String> params = new HashMap<>();
        params.put("timeStamp", response.getTimeStamp());
        params.put("nonceStr", response.getNonceStr());
        params.put("package", response.getPackageVal());
        params.put("signType", response.getSignType());
        params.put("paySign", response.getPaySign());
        return params;
    }

    /**
     * 支付结果通知处理。按微信交易号幂等：重复通知直接返回成功。
     * @return true 表示本次通知实际完成了入账
     */
    @Transactional
    public boolean handlePayNotify(String signature, String nonce, String timestamp, String serial, String body) {
        Transaction transaction = payParser().parse(buildParam(signature, nonce, timestamp, serial, body), Transaction.class);
        if (transaction.getTradeState() != Transaction.TradeStateEnum.SUCCESS) {
            log.info("支付通知非成功状态 outTradeNo={} state={}", transaction.getOutTradeNo(), transaction.getTradeState());
            return false;
        }
        String idemKey = CacheKeys.IDEMPOTENCY_PREFIX + "pay:" + transaction.getTransactionId();
        if (!cache.setIfAbsent(idemKey, "1", IDEMPOTENCY_TTL)) {
            // Redis 占位失败：可能重复通知或 Redis 故障，查库兜底幂等
            Long dup = paymentRecordMapper.selectCount(new LambdaQueryWrapper<PaymentRecord>()
                    .eq(PaymentRecord::getType, PaymentRecord.TYPE_PAY)
                    .eq(PaymentRecord::getTransactionId, transaction.getTransactionId()));
            if (dup != null && dup > 0) {
                log.info("支付通知重复，已忽略 transactionId={}", transaction.getTransactionId());
                return false;
            }
        }
        PurchaseOrder order = orderService.getByOrderNo(transaction.getOutTradeNo());
        if (order == null) {
            log.error("支付通知找不到订单 outTradeNo={}", transaction.getOutTradeNo());
            throw new IllegalStateException("订单不存在，无法入账");
        }
        if (transaction.getAmount() == null || transaction.getAmount().getTotal() == null
                || transaction.getAmount().getTotal().longValue() != toFen(order.getPayableAmount())) {
            log.error("支付金额与订单不一致 orderNo={} expected={} actual={}", order.getOrderNo(),
                    order.getPayableAmount(), transaction.getAmount());
            throw new IllegalStateException("支付金额与订单金额不一致");
        }
        boolean transitioned = orderService.markPaid(order.getId());
        if (transitioned) {
            PaymentRecord record = new PaymentRecord();
            record.setOrderId(order.getId());
            record.setOrderNo(order.getOrderNo());
            record.setType(PaymentRecord.TYPE_PAY);
            record.setTransactionId(transaction.getTransactionId());
            record.setAmount(order.getPayableAmount());
            record.setStatus("SUCCESS");
            record.setRawNotify(truncate(body));
            paymentRecordMapper.insert(record);
            log.info("订单支付入账成功 orderNo={} transactionId={}", order.getOrderNo(), transaction.getTransactionId());
        }
        return transitioned;
    }

    /**
     * 管理端发起退款（仅限待发货订单，货未发出可原路退回）。
     */
    public Refund refund(PurchaseOrder order, BigDecimal refundAmount, String reason) {
        if (!MiniappOrderService.ST_PENDING_SHIPMENT.equals(order.getStatus())) {
            throw new IllegalStateException("仅待发货订单可在线退款，其他状态请线下处理");
        }
        PaymentRecord payRecord = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, order.getId())
                .eq(PaymentRecord::getType, PaymentRecord.TYPE_PAY)
                .eq(PaymentRecord::getStatus, "SUCCESS"));
        if (payRecord == null) throw new IllegalStateException("未找到该订单的支付记录，无法退款");

        String outRefundNo = "RF" + order.getOrderNo();
        CreateRequest request = new CreateRequest();
        request.setOutTradeNo(order.getOrderNo());
        request.setOutRefundNo(outRefundNo);
        request.setReason(reason == null || reason.isBlank() ? "订单取消退款" : reason);
        request.setNotifyUrl(properties.getNotifyUrl().replace("/notify", "/refund-notify"));
        AmountReq amountReq = new AmountReq();
        amountReq.setRefund(toFen(refundAmount));
        amountReq.setTotal(toFen(order.getPayableAmount()));
        amountReq.setCurrency("CNY");
        request.setAmount(amountReq);

        Refund refund = refundService().create(request);

        PaymentRecord record = new PaymentRecord();
        record.setOrderId(order.getId());
        record.setOrderNo(order.getOrderNo());
        record.setType(PaymentRecord.TYPE_REFUND);
        record.setTransactionId(refund.getRefundId());
        record.setOutRefundNo(outRefundNo);
        record.setAmount(refundAmount);
        record.setStatus(refund.getStatus() == null ? "PROCESSING" : refund.getStatus().name());
        paymentRecordMapper.insert(record);
        log.info("退款申请已提交 orderNo={} outRefundNo={} amount={}", order.getOrderNo(), outRefundNo, refundAmount);
        return refund;
    }

    /**
     * 退款结果通知：退款成功后订单置为已退款；待发货订单同步回补库存。
     */
    @Transactional
    public boolean handleRefundNotify(String signature, String nonce, String timestamp, String serial, String body) {
        RefundNotification notification = payParser().parse(
                buildParam(signature, nonce, timestamp, serial, body), RefundNotification.class);
        if (notification.getRefundStatus() != com.wechat.pay.java.service.refund.model.Status.SUCCESS) {
            log.info("退款通知非成功状态 outRefundNo={} status={}",
                    notification.getOutRefundNo(), notification.getRefundStatus());
            return false;
        }
        String idemKey = CacheKeys.IDEMPOTENCY_PREFIX + "refund:" + notification.getRefundId();
        if (!cache.setIfAbsent(idemKey, "1", IDEMPOTENCY_TTL)) {
            Long dup = paymentRecordMapper.selectCount(new LambdaQueryWrapper<PaymentRecord>()
                    .eq(PaymentRecord::getType, PaymentRecord.TYPE_REFUND)
                    .eq(PaymentRecord::getTransactionId, notification.getRefundId())
                    .eq(PaymentRecord::getStatus, "SUCCESS"));
            if (dup != null && dup > 0) return false;
        }
        PurchaseOrder order = orderService.getByOrderNo(notification.getOutTradeNo());
        if (order == null) {
            log.error("退款通知找不到订单 outTradeNo={}", notification.getOutTradeNo());
            throw new IllegalStateException("订单不存在");
        }
        boolean changed = orderService.markRefunded(order.getId());
        PaymentRecord record = paymentRecordMapper.selectOne(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOutRefundNo, notification.getOutRefundNo()));
        if (record != null) {
            record.setStatus("SUCCESS");
            record.setRawNotify(truncate(body));
            paymentRecordMapper.updateById(record);
        }
        log.info("退款入账完成 orderNo={} refundId={} stateChanged={}",
                notification.getOutTradeNo(), notification.getRefundId(), changed);
        return changed;
    }

    public boolean configured() {
        return properties.configured();
    }

    private Config config() {
        if (config == null) {
            synchronized (this) {
                if (config == null) {
                    if (!properties.configured()) {
                        throw new IllegalStateException("微信支付未配置：请设置 WECHAT_PAY_* 环境变量并开启 wechat.pay.enabled");
                    }
                    config = new RSAAutoCertificateConfig.Builder()
                            .merchantId(properties.getMchid())
                            .privateKeyFromPath(properties.getPrivateKeyPath())
                            .merchantSerialNumber(properties.getMerchantSerialNumber())
                            .apiV3Key(properties.getApiV3Key())
                            .build();
                }
            }
        }
        return config;
    }

    private JsapiServiceExtension jsapiService() {
        if (jsapiService == null) {
            synchronized (this) {
                if (jsapiService == null) {
                    jsapiService = new JsapiServiceExtension.Builder().config(config()).build();
                }
            }
        }
        return jsapiService;
    }

    private RefundService refundService() {
        if (refundService == null) {
            synchronized (this) {
                if (refundService == null) {
                    refundService = new RefundService.Builder().config(config()).build();
                }
            }
        }
        return refundService;
    }

    private com.wechat.pay.java.core.notification.NotificationParser payParser() {
        if (parser == null) {
            synchronized (this) {
                if (parser == null) {
                    parser = new com.wechat.pay.java.core.notification.NotificationParser(
                            (NotificationConfig) config());
                }
            }
        }
        return parser;
    }

    private RequestParam buildParam(String signature, String nonce, String timestamp, String serial, String body) {
        return new RequestParam.Builder()
                .serialNumber(serial)
                .nonce(nonce)
                .signature(signature)
                .timestamp(timestamp)
                .body(body)
                .build();
    }

    /** 微信支付金额单位为分。 */
    private static Long toFen(BigDecimal yuan) {
        return yuan.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 小程序 AppID 复用登录配置，避免两处不一致。 */
    @org.springframework.beans.factory.annotation.Value("${wechat.app-id}")
    private String appId;

    private String truncate(String body) {
        if (body == null) return null;
        return body.length() > 4000 ? body.substring(0, 4000) : body;
    }
}
