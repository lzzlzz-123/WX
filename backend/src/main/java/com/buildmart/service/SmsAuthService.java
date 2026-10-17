package com.buildmart.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Test SMS service for mini-program phone-code login.
 *
 * <p>No real SMS is sent: the generated code is logged and echoed back in the
 * API response (while {@code app.sms.test-echo} is enabled) so the flow can be
 * exercised end to end without an SMS provider. Codes are stored in Redis
 * (primary) with an in-memory fallback so the test login keeps working when
 * Redis is unavailable locally.</p>
 *
 * <p>Before going to production, plug a real SMS gateway into {@link #deliver}
 * and set {@code app.sms.test-echo=false}.</p>
 */
@Service
public class SmsAuthService {
    private static final Logger log = LoggerFactory.getLogger(SmsAuthService.class);
    private static final Pattern CN_PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedisCacheService cacheService;
    private final long codeTtlSeconds;
    private final long resendSeconds;
    private final boolean testEcho;

    /** Fallback store: phone -> (code, expireAt). Also mirrors Redis for resilience. */
    private final Map<String, CodeEntry> localStore = new ConcurrentHashMap<>();
    private final Map<String, Instant> localResendLimit = new ConcurrentHashMap<>();

    public SmsAuthService(RedisCacheService cacheService,
                          @Value("${app.sms.code-ttl-seconds:300}") long codeTtlSeconds,
                          @Value("${app.sms.resend-seconds:60}") long resendSeconds,
                          @Value("${app.sms.test-echo:true}") boolean testEcho) {
        this.cacheService = cacheService;
        this.codeTtlSeconds = codeTtlSeconds;
        this.resendSeconds = resendSeconds;
        this.testEcho = testEcho;
    }

    public record SendResult(long ttlSeconds, long resendSeconds, String testCode) {
    }

    /** Generate and "deliver" a code. Throws on invalid phone or resend too soon. */
    public SendResult sendCode(String phone) {
        String normalized = requirePhone(phone);
        enforceResendLimit(normalized);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant expireAt = Instant.now().plusSeconds(codeTtlSeconds);
        cacheService.putRaw(codeKey(normalized), code, Duration.ofSeconds(codeTtlSeconds));
        cacheService.putRaw(limitKey(normalized), "1", Duration.ofSeconds(resendSeconds));
        localStore.put(normalized, new CodeEntry(code, expireAt));
        localResendLimit.put(normalized, Instant.now().plusSeconds(resendSeconds));

        deliver(normalized, code);
        return new SendResult(codeTtlSeconds, resendSeconds, testEcho ? code : null);
    }

    /** Verify a code and consume it (one-time use). */
    public void verifyAndConsume(String phone, String code) {
        String normalized = requirePhone(phone);
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("验证码不能为空");
        }
        String expected = cacheService.getRaw(codeKey(normalized));
        if (expected == null) {
            CodeEntry entry = localStore.get(normalized);
            if (entry != null && entry.expireAt().isAfter(Instant.now())) {
                expected = entry.code();
            }
        }
        if (expected == null) {
            throw new IllegalArgumentException("验证码已过期，请重新获取");
        }
        if (!expected.equals(code.trim())) {
            throw new IllegalArgumentException("验证码不正确");
        }
        cacheService.delete(codeKey(normalized));
        localStore.remove(normalized);
    }

    /** Test delivery channel: server log only. Swap for a real SMS gateway later. */
    private void deliver(String phone, String code) {
        log.info("[测试短信服务] 向 {} 发送登录验证码：{}（{} 秒内有效）", phone, code, codeTtlSeconds);
    }

    private void enforceResendLimit(String phone) {
        if (cacheService.hasKey(limitKey(phone))) {
            throw new IllegalArgumentException("发送过于频繁，请 " + resendSeconds + " 秒后再试");
        }
        Instant limited = localResendLimit.get(phone);
        if (limited != null && limited.isAfter(Instant.now())) {
            throw new IllegalArgumentException("发送过于频繁，请 " + resendSeconds + " 秒后再试");
        }
    }

    private String requirePhone(String phone) {
        String normalized = phone == null ? "" : phone.trim();
        if (!CN_PHONE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        return normalized;
    }

    private String codeKey(String phone) {
        return "sms:code:" + phone;
    }

    private String limitKey(String phone) {
        return "sms:limit:" + phone;
    }

    private record CodeEntry(String code, Instant expireAt) {
    }
}
