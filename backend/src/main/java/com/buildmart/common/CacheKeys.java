package com.buildmart.common;

import java.util.Locale;

/** Redis key contract. Keep business prefixes stable so invalidation and monitoring stay predictable. */
public final class CacheKeys {
    public static final String PRODUCT_CATALOG_VERSION = "cache:product:catalog:version";
    public static final String PRODUCT_DETAIL_PREFIX = "cache:product:detail:";
    public static final String PRODUCT_EMPTY_PREFIX = "cache:product:empty:";
    public static final String CART_PREFIX = "cache:cart:";
    public static final String SETTINGS_ALL = "cache:settings:all";
    public static final String AUTH_TOKEN_PREFIX = "auth:token:";
    public static final String IDEMPOTENCY_PREFIX = "idempotency:";
    public static final String LOCK_PREFIX = "lock:";

    private CacheKeys() {
    }

    public static String cart(Long userId) {
        return CART_PREFIX + userId;
    }

    public static String productDetail(Long id) {
        return PRODUCT_DETAIL_PREFIX + id;
    }

    public static String productEmpty(Long id) {
        return PRODUCT_EMPTY_PREFIX + id;
    }

    public static String productPage(long version, long page, long size, String keyword, Integer status) {
        String normalizedKeyword = keyword == null || keyword.isBlank()
                ? "_"
                : keyword.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\-]", "_");
        return "cache:product:page:v" + version + ":" + page + ":" + size + ":" + (status == null ? "_" : status) + ":" + normalizedKeyword;
    }
}
