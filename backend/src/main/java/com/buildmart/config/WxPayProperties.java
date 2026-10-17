package com.buildmart.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信支付 V3 商户配置。所有敏感项均无默认值，通过环境变量注入：
 * WECHAT_PAY_ENABLED / WECHAT_PAY_MCHID / WECHAT_PAY_SERIAL_NO /
 * WECHAT_PAY_PRIVATE_KEY_PATH / WECHAT_PAY_API_V3_KEY / WECHAT_PAY_NOTIFY_URL。
 * enabled=false 时支付相关接口直接报错，不会用演示数据伪装成功。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat.pay")
public class WxPayProperties {
    private boolean enabled = false;
    /** 商户号 */
    private String mchid;
    /** 商户 API 证书序列号 */
    private String merchantSerialNumber;
    /** 商户 API 私钥文件路径（apiclient_key.pem） */
    private String privateKeyPath;
    /** API V3 密钥 */
    private String apiV3Key;
    /** 支付结果异步通知地址（需公网 HTTPS） */
    private String notifyUrl;

    public boolean configured() {
        return enabled && notBlank(mchid) && notBlank(merchantSerialNumber)
                && notBlank(privateKeyPath) && notBlank(apiV3Key) && notBlank(notifyUrl);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
