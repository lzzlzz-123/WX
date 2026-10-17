package com.buildmart.common;

/**
 * Holds the authenticated identity for the current request thread.
 *
 * <p>Identity may ONLY come from the JWT interceptors. There is deliberately
 * no fallback to client-supplied headers (the old X-User-Id header): trusting
 * a request header would let any caller impersonate any user if a path ever
 * missed the interceptor mapping.</p>
 */
public final class AuthContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> ADMIN_ID = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * Returns the mini-program user id carried by a valid JWT, or throws when
     * the request was not authenticated by the interceptor.
     */
    public static Long requireUserId() {
        Long authenticatedUserId = USER_ID.get();
        if (authenticatedUserId == null) {
            throw new AuthException(AuthException.Reason.NOT_LOGGED_IN);
        }
        return authenticatedUserId;
    }

    public static void setAdminId(Long adminId) {
        ADMIN_ID.set(adminId);
    }

    public static Long getAdminId() {
        return ADMIN_ID.get();
    }

    public static Long requireAdminId() {
        Long authenticatedAdminId = ADMIN_ID.get();
        if (authenticatedAdminId == null) {
            throw new AuthException(AuthException.Reason.NOT_LOGGED_IN);
        }
        return authenticatedAdminId;
    }

    public static void clear() {
        USER_ID.remove();
        ADMIN_ID.remove();
    }
}
