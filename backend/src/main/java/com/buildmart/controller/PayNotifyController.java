package com.buildmart.controller;

import com.buildmart.service.WxPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信支付/退款结果异步通知。路径不走 /miniapp 与 /admin，无需登录态，
 * 安全性由微信支付 V3 回调验签（SDK NotificationParser）保证。
 */
@Tag(name = "微信支付回调", description = "微信支付与退款结果异步通知")
@RestController
@RequestMapping("/pay/wechat")
public class PayNotifyController {
    private static final Logger log = LoggerFactory.getLogger(PayNotifyController.class);

    private final WxPayService wxPayService;

    public PayNotifyController(WxPayService wxPayService) {
        this.wxPayService = wxPayService;
    }

    @PostMapping("/notify")
    @Operation(summary = "支付结果通知")
    public NotifyResponse payNotify(@RequestHeader("Wechatpay-Signature") String signature,
                                    @RequestHeader("Wechatpay-Nonce") String nonce,
                                    @RequestHeader("Wechatpay-Timestamp") String timestamp,
                                    @RequestHeader("Wechatpay-Serial") String serial,
                                    @RequestBody String body) {
        try {
            wxPayService.handlePayNotify(signature, nonce, timestamp, serial, body);
            return NotifyResponse.success();
        } catch (Exception e) {
            log.error("支付回调处理失败", e);
            return NotifyResponse.fail("支付回调处理失败");
        }
    }

    @PostMapping("/refund-notify")
    @Operation(summary = "退款结果通知")
    public NotifyResponse refundNotify(@RequestHeader("Wechatpay-Signature") String signature,
                                       @RequestHeader("Wechatpay-Nonce") String nonce,
                                       @RequestHeader("Wechatpay-Timestamp") String timestamp,
                                       @RequestHeader("Wechatpay-Serial") String serial,
                                       @RequestBody String body) {
        try {
            wxPayService.handleRefundNotify(signature, nonce, timestamp, serial, body);
            return NotifyResponse.success();
        } catch (Exception e) {
            log.error("退款回调处理失败", e);
            return NotifyResponse.fail("退款回调处理失败");
        }
    }

    /** 微信支付要求的应答格式：code=SUCCESS/FAIL。 */
    public record NotifyResponse(String code, String message) {
        static NotifyResponse success() {
            return new NotifyResponse("SUCCESS", "成功");
        }

        static NotifyResponse fail(String message) {
            return new NotifyResponse("FAIL", message);
        }
    }
}
