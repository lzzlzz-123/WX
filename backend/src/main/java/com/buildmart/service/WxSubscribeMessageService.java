package com.buildmart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序订阅消息推送。模板 ID 通过系统设置配置：
 * - `wechat.template.quote_replied`：询价报价回复通知
 * - `wechat.template.order_shipped`：订单发货通知
 * 模板未配置时静默跳过；推送失败只记日志，不影响主流程。
 */
@Service
public class WxSubscribeMessageService {
    private static final Logger log = LoggerFactory.getLogger(WxSubscribeMessageService.class);
    private static final String ACCESS_TOKEN_KEY = "wechat:access_token";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofSeconds(7000);

    private final String appId;
    private final String appSecret;
    private final RestClient restClient;
    private final RedisCacheService cache;
    private final SettingService settingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WxSubscribeMessageService(@Value("${wechat.app-id}") String appId,
                                     @Value("${wechat.secret:}") String appSecret,
                                     @Value("${wechat.api-base-url:https://api.weixin.qq.com}") String apiBaseUrl,
                                     RedisCacheService cache, SettingService settingService) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl.trim().replaceAll("/+$", "")).build();
        this.cache = cache;
        this.settingService = settingService;
    }

    /** 报价回复通知。data 键名需与订阅模板字段一致，如 thing1（商品）、amount2（报价）。 */
    public void notifyQuoteReplied(String openid, String productName, String quotePrice) {
        String templateId = settingService.get("wechat.template.quote_replied", "");
        if (templateId.isBlank()) return;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thing1", Map.of("value", truncate(productName, 20)));
        data.put("amount2", Map.of("value", quotePrice));
        data.put("thing3", Map.of("value", "报价已出，请前往小程序查看"));
        send(openid, templateId, "pages/inquiry/index", data);
    }

    /** 订单发货通知。自提订单传核销码，配送订单传物流单号。 */
    public void notifyOrderShipped(String openid, String orderNo, String deliveryInfo) {
        String templateId = settingService.get("wechat.template.order_shipped", "");
        if (templateId.isBlank()) return;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("character_string1", Map.of("value", orderNo));
        data.put("thing2", Map.of("value", truncate(deliveryInfo, 20)));
        data.put("thing3", Map.of("value", "订单已发货，请留意收货"));
        send(openid, templateId, "pages/order/list", data);
    }

    private void send(String openid, String templateId, String page, Map<String, Object> data) {
        if (openid == null || openid.isBlank()) return;
        try {
            String token = accessToken();
            if (token == null) return;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("touser", openid);
            payload.put("template_id", templateId);
            payload.put("page", page);
            payload.put("miniprogram_state", "formal");
            payload.put("lang", "zh_CN");
            payload.put("data", data);
            String response = restClient.post()
                    .uri("/cgi-bin/message/subscribe/send?access_token=" + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payload))
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(response);
            int errcode = json.path("errcode").asInt(-1);
            if (errcode != 0) {
                // 43101 用户未订阅等属于正常业务情况，仅提示不告警
                log.info("订阅消息未送达 errcode={} errmsg={} openid={}", errcode, json.path("errmsg").asText(), openid);
            }
        } catch (Exception ex) {
            log.warn("订阅消息发送失败 openid={} template={}：{}", openid, templateId, ex.getMessage());
        }
    }

    /** access_token 缓存于 Redis（7000 秒），Redis 不可用时每次实时获取。 */
    private String accessToken() {
        if (appSecret == null || appSecret.isBlank()) {
            log.info("未配置微信 AppSecret，跳过订阅消息推送");
            return null;
        }
        String cached = cache.getRaw(ACCESS_TOKEN_KEY);
        if (cached != null && !cached.isBlank()) return cached;
        try {
            String response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/cgi-bin/token")
                            .queryParam("grant_type", "client_credential")
                            .queryParam("appid", appId)
                            .queryParam("secret", appSecret)
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(response);
            String token = json.path("access_token").asText(null);
            if (token == null || token.isBlank()) {
                log.warn("获取微信 access_token 失败：{}", response);
                return null;
            }
            cache.putRaw(ACCESS_TOKEN_KEY, token, ACCESS_TOKEN_TTL);
            return token;
        } catch (Exception ex) {
            log.warn("获取微信 access_token 异常：{}", ex.getMessage());
            return null;
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) : text;
    }
}
