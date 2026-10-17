package com.buildmart.service;

import com.buildmart.common.AuthException;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;

/**
 * Exchanges a mini-program login code with WeChat.
 *
 * <p>The exchange is intentionally a single upstream request. A WeChat code
 * is one-time by contract. The in-flight guard closes the common race where
 * two app-launch requests submit the same code at once, while allowing a
 * caller to retry after a transport failure. WeChat remains the authority for
 * codes used by another instance.</p>
 */
@Service
public class WechatAuthService {
    private static final Logger log = LoggerFactory.getLogger(WechatAuthService.class);
    private static final int MAX_CODE_LENGTH = 512;

    private final RestClient restClient;
    private final String appId;
    private final String appSecret;
    private final String apiBaseUrl;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, CompletableFuture<WechatIdentity>> inFlightCodes =
            new ConcurrentHashMap<>();
    // Access tokens are app-scoped and valid for 7200s; sharing one across
    // requests avoids hitting WeChat's daily token quota.
    private volatile String cachedAccessToken;
    private volatile long accessTokenExpiresAtMillis;

    /** Production constructor. All values can be supplied by deployment env vars. */
    @Autowired
    public WechatAuthService(
            @Value("${wechat.app-id:}") String appId,
            @Value("${wechat.secret:}") String appSecret,
            @Value("${wechat.api-base-url:https://api.weixin.qq.com}") String apiBaseUrl,
            @Value("${wechat.connect-timeout:3s}") Duration connectTimeout,
            @Value("${wechat.read-timeout:5s}") Duration readTimeout,
            @Value("${wechat.proxy-host:}") String proxyHost,
            @Value("${wechat.proxy-port:0}") int proxyPort) {
        this(appId, appSecret, apiBaseUrl, connectTimeout, readTimeout,
                proxyHost, proxyPort, null);
    }

    /** Kept for callers that construct this service directly in tests/tools. */
    public WechatAuthService(String appId, String appSecret) {
        this(appId, appSecret, "https://api.weixin.qq.com", Duration.ofSeconds(3),
                Duration.ofSeconds(5), null, 0, null);
    }

    /** Package-private test constructor retained for existing tests. */
    WechatAuthService(String appId, String appSecret, String apiBaseUrl,
                      Duration connectTimeout, Duration readTimeout, RestClient restClient) {
        this(appId, appSecret, apiBaseUrl, connectTimeout, readTimeout,
                null, 0, restClient);
    }

    WechatAuthService(String appId, String appSecret, String apiBaseUrl,
                      Duration connectTimeout, Duration readTimeout,
                      String proxyHost, int proxyPort,
                      RestClient restClient) {
        if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
            throw new IllegalArgumentException("微信登录 API 地址不能为空");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("微信登录超时时间必须大于 0");
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        if (proxyHost != null && !proxyHost.isBlank()) {
            if (proxyPort <= 0 || proxyPort > 65535) {
                throw new IllegalArgumentException("微信登录代理端口必须在 1-65535 范围内");
            }
            requestFactory.setProxy(new Proxy(Proxy.Type.HTTP,
                    new InetSocketAddress(proxyHost.trim(), proxyPort)));
        } else if (proxyPort != 0) {
            throw new IllegalArgumentException("配置微信登录代理端口时必须同时配置代理主机");
        }

        this.apiBaseUrl = apiBaseUrl.trim().replaceAll("/+$", "");
        this.restClient = restClient == null
                ? RestClient.builder().baseUrl(this.apiBaseUrl).requestFactory(requestFactory).build()
                : restClient;
        this.appId = appId;
        this.appSecret = appSecret;
        this.objectMapper = new ObjectMapper();
    }

    /** Resolve the WeChat identity for a one-time wx.login code. */
    public WechatIdentity resolveIdentity(String code) {
        String normalizedCode = normalizeCode(code);
        validateConfiguration();

        CompletableFuture<WechatIdentity> pending = new CompletableFuture<>();
        CompletableFuture<WechatIdentity> running = inFlightCodes.putIfAbsent(normalizedCode, pending);
        if (running != null) {
            return awaitInFlight(running);
        }
        try {
            WechatIdentity identity = exchangeOnce(normalizedCode);
            pending.complete(identity);
            return identity;
        } catch (RuntimeException ex) {
            pending.completeExceptionally(ex);
            throw ex;
        } finally {
            inFlightCodes.remove(normalizedCode, pending);
        }
    }

    private WechatIdentity exchangeOnce(String normalizedCode) {
        RawWechatResponse response;
        try {
            // exchange() lets us read the body ourselves. WeChat and some local
            // proxies have returned JSON with text/plain or no content type;
            // relying on a message converter here causes UnknownContentTypeException.
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sns/jscode2session")
                            .queryParam("appid", appId)
                            .queryParam("secret", appSecret)
                            .queryParam("js_code", normalizedCode)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .exchange((request, clientResponse) -> readRawResponse(clientResponse));
        } catch (ResourceAccessException ex) {
            log.warn("微信 code2Session 网络请求失败，endpoint={}，异常类型={}",
                    apiBaseUrl, ex.getClass().getSimpleName());
            throw new AuthException(AuthException.Reason.PROVIDER_UNAVAILABLE, ex);
        } catch (RestClientException ex) {
            log.warn("微信 code2Session 响应读取失败，endpoint={}，异常类型={}",
                    apiBaseUrl, ex.getClass().getSimpleName());
            throw new AuthException(AuthException.Reason.PROVIDER_UNAVAILABLE, ex);
        }

        log.debug("微信 code2Session 响应 status={} contentType={} bodyLength={}",
                response.status(), response.contentType(), response.body().length());
        if (response.status() < 200 || response.status() >= 300) {
            throw new AuthException(AuthException.Reason.PROVIDER_UNAVAILABLE);
        }
        if (response.body().isBlank()) {
            throw new AuthException(AuthException.Reason.RESPONSE_INVALID);
        }

        WechatSession session;
        try {
            session = objectMapper.readValue(response.body(), WechatSession.class);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("微信 code2Session 返回内容不是有效 JSON，endpoint={}，异常类型={}",
                    apiBaseUrl, ex.getClass().getSimpleName());
            throw new AuthException(AuthException.Reason.RESPONSE_INVALID, ex);
        }
        if (session == null) {
            throw new AuthException(AuthException.Reason.RESPONSE_INVALID);
        }
        if (session.errcode() != null && session.errcode() != 0) {
            throw mapWechatError(session.errcode());
        }
        if (session.openid() == null || session.openid().isBlank()) {
            throw new AuthException(AuthException.Reason.OPENID_MISSING);
        }
        return new WechatIdentity(session.openid().trim(), normalizeOptional(session.unionid()));
    }

    private WechatIdentity awaitInFlight(CompletableFuture<WechatIdentity> running) {
        try {
            return running.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AuthException(AuthException.Reason.PROVIDER_UNAVAILABLE, cause);
        }
    }

    /** Backwards-compatible helper for existing service callers. */
    public String resolveOpenid(String code) {
        return resolveIdentity(code).openid();
    }

    /**
     * Exchange the one-time code from the getPhoneNumber button for the
     * user's phone number. Requires an app access token, which is cached
     * here because it is valid app-wide for 7200 seconds.
     */
    public String resolvePhoneNumber(String phoneCode) {
        String normalizedCode = normalizeCode(phoneCode);
        validateConfiguration();
        String accessToken = getAccessToken();

        RawWechatResponse response;
        try {
            response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/wxa/business/getuserphonenumber")
                            .queryParam("access_token", accessToken)
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("code", normalizedCode))
                    .exchange((request, clientResponse) -> readRawResponse(clientResponse));
        } catch (ResourceAccessException ex) {
            log.warn("微信手机号获取请求失败，endpoint={}，异常类型={}",
                    apiBaseUrl, ex.getClass().getSimpleName());
            throw new AuthException(AuthException.Reason.PROVIDER_UNAVAILABLE, ex);
        } catch (RestClientException ex) {
            log.warn("微信手机号响应读取失败，endpoint={}，异常类型={}",
                    apiBaseUrl, ex.getClass().getSimpleName());
            throw new AuthException(AuthException.Reason.PROVIDER_UNAVAILABLE, ex);
        }

        if (response.status() < 200 || response.status() >= 300 || response.body().isBlank()) {
            throw new AuthException(AuthException.Reason.PROVIDER_UNAVAILABLE);
        }
        PhoneResponse phoneResponse;
        try {
            phoneResponse = objectMapper.readValue(response.body(), PhoneResponse.class);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("微信手机号返回内容不是有效 JSON，endpoint={}，异常类型={}",
                    apiBaseUrl, ex.getClass().getSimpleName());
            throw new AuthException(AuthException.Reason.RESPONSE_INVALID, ex);
        }
        if (phoneResponse == null) {
            throw new AuthException(AuthException.Reason.RESPONSE_INVALID);
        }
        if (phoneResponse.errcode() != null && phoneResponse.errcode() != 0) {
            log.warn("微信手机号获取失败，errcode={}", phoneResponse.errcode());
            // 40029/40163 = phone code invalid or already used.
            if (phoneResponse.errcode() == 40029 || phoneResponse.errcode() == 40163) {
                throw new AuthException(AuthException.Reason.PHONE_CODE_INVALID);
            }
            if (phoneResponse.errcode() == 40013 || phoneResponse.errcode() == 40125) {
                throw new AuthException(AuthException.Reason.CONFIGURATION);
            }
            throw new AuthException(AuthException.Reason.PROVIDER_UNAVAILABLE);
        }
        PhoneInfo info = phoneResponse.phoneInfo();
        String phone = info == null ? null : normalizeOptional(info.purePhoneNumber());
        if (phone == null) {
            phone = info == null ? null : normalizeOptional(info.phoneNumber());
        }
        if (phone == null) {
            throw new AuthException(AuthException.Reason.RESPONSE_INVALID);
        }
        return phone;
    }

    private synchronized String getAccessToken() {
        long now = System.currentTimeMillis();
        if (cachedAccessToken != null && now < accessTokenExpiresAtMillis) {
            return cachedAccessToken;
        }
        RawWechatResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cgi-bin/token")
                            .queryParam("grant_type", "client_credential")
                            .queryParam("appid", appId)
                            .queryParam("secret", appSecret)
                            .build())
                    .exchange((request, clientResponse) -> readRawResponse(clientResponse));
        } catch (RestClientException ex) {
            log.warn("微信 access_token 请求失败，endpoint={}，异常类型={}",
                    apiBaseUrl, ex.getClass().getSimpleName());
            throw new AuthException(AuthException.Reason.PROVIDER_UNAVAILABLE, ex);
        }
        AccessTokenResponse tokenResponse;
        try {
            tokenResponse = objectMapper.readValue(response.body(), AccessTokenResponse.class);
        } catch (JsonProcessingException | RuntimeException ex) {
            throw new AuthException(AuthException.Reason.RESPONSE_INVALID, ex);
        }
        if (tokenResponse == null || tokenResponse.accessToken() == null
                || tokenResponse.accessToken().isBlank()) {
            log.warn("微信 access_token 获取失败，errcode={}", tokenResponse == null ? null : tokenResponse.errcode());
            throw new AuthException(AuthException.Reason.CONFIGURATION);
        }
        // Refresh 5 minutes early so an expiring token is never handed out.
        long expiresIn = tokenResponse.expiresIn() == null ? 7200L : tokenResponse.expiresIn();
        cachedAccessToken = tokenResponse.accessToken();
        accessTokenExpiresAtMillis = now + Math.max(60, expiresIn - 300) * 1000L;
        return cachedAccessToken;
    }

    private RawWechatResponse readRawResponse(ClientHttpResponse clientResponse) {
        // Read the body ourselves. WeChat and some local proxies have
        // returned JSON with text/plain or no content type; relying on a
        // message converter here causes UnknownContentTypeException.
        String body;
        try (InputStream input = clientResponse.getBody()) {
            body = input == null ? "" : StreamUtils.copyToString(input, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RestClientException("读取微信响应失败", ex);
        }
        String contentType = clientResponse.getHeaders().getFirst("Content-Type");
        final int status;
        try {
            status = clientResponse.getStatusCode().value();
        } catch (IOException ex) {
            throw new RestClientException("读取微信响应状态失败", ex);
        }
        return new RawWechatResponse(status, contentType, body);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new AuthException(AuthException.Reason.CODE_REQUIRED);
        }
        String normalized = code.trim();
        if (normalized.length() > MAX_CODE_LENGTH) {
            throw new AuthException(AuthException.Reason.CODE_INVALID);
        }
        return normalized;
    }

    private void validateConfiguration() {
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new AuthException(AuthException.Reason.CONFIGURATION);
        }
    }

    private AuthException mapWechatError(int errcode) {
        // 40029 = invalid/expired code, 40163 = code already used.
        if (errcode == 40163) {
            return new AuthException(AuthException.Reason.CODE_REUSED);
        }
        if (errcode == 40029) {
            return new AuthException(AuthException.Reason.CODE_INVALID);
        }
        // 40013 = invalid appid, 40125 = invalid appsecret.
        if (errcode == 40013 || errcode == 40125) {
            return new AuthException(AuthException.Reason.CONFIGURATION);
        }
        return new AuthException(AuthException.Reason.PROVIDER_UNAVAILABLE);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record WechatIdentity(String openid, String unionid) {
    }

    public record WechatSession(@JsonProperty("openid") String openid,
                                @JsonProperty("session_key") String sessionKey,
                                @JsonProperty("unionid") String unionid,
                                @JsonProperty("errcode") Integer errcode,
                                @JsonProperty("errmsg") String errmsg) {
    }

    public record AccessTokenResponse(@JsonProperty("access_token") String accessToken,
                                      @JsonProperty("expires_in") Long expiresIn,
                                      @JsonProperty("errcode") Integer errcode,
                                      @JsonProperty("errmsg") String errmsg) {
    }

    public record PhoneResponse(@JsonProperty("errcode") Integer errcode,
                                @JsonProperty("errmsg") String errmsg,
                                @JsonProperty("phone_info") PhoneInfo phoneInfo) {
    }

    public record PhoneInfo(@JsonProperty("phoneNumber") String phoneNumber,
                            @JsonProperty("purePhoneNumber") String purePhoneNumber,
                            @JsonProperty("countryCode") String countryCode) {
    }

    private record RawWechatResponse(int status, String contentType, String body) {
    }
}
