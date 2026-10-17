package com.buildmart.common;

/**
 * Errors raised while authenticating a mini-program user.
 *
 * <p>The API deliberately exposes a small, stable error-code vocabulary.  The
 * upstream WeChat error text is not returned to clients because it is both
 * unstable and occasionally contains implementation details.  The original
 * exception is retained as the cause for server-side diagnostics.</p>
 */
public class AuthException extends RuntimeException {
    private final Reason reason;

    public AuthException(Reason reason) {
        this(reason, null);
    }

    public AuthException(Reason reason, Throwable cause) {
        super(reason.message(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public int apiCode() {
        return reason.apiCode();
    }

    public enum Reason {
        CODE_REQUIRED(-1001, "微信登录 code 不能为空"),
        CODE_REUSED(-1002, "微信登录 code 已使用，请重新获取"),
        CODE_INVALID(-1003, "微信登录 code 无效或已过期，请重新获取"),
        OPENID_NOT_ALLOWED(-1004, "生产环境不接受 openid，请使用微信登录 code"),
        PHONE_CODE_REQUIRED(-1005, "请使用手机号一键登录"),
        PHONE_CODE_INVALID(-1006, "手机号授权已失效，请重新点击一键登录"),
        CONFIGURATION(-1101, "微信登录服务配置错误，请联系管理员"),
        PROVIDER_UNAVAILABLE(-1102, "微信登录服务暂时不可用，请稍后重试"),
        RESPONSE_INVALID(-1103, "微信登录服务响应格式无效，请稍后重试"),
        OPENID_MISSING(-1104, "微信登录服务未返回用户标识，请稍后重试"),
        USER_DISABLED(-1201, "用户已被禁用，请联系管理员"),
        USER_PERSISTENCE(-1202, "用户登录信息保存失败，请稍后重试"),
        NOT_LOGGED_IN(-1203, "用户未登录");

        private final int apiCode;
        private final String message;

        Reason(int apiCode, String message) {
            this.apiCode = apiCode;
            this.message = message;
        }

        public int apiCode() {
            return apiCode;
        }

        public String message() {
            return message;
        }
    }
}
