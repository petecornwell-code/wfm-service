package com.wfm.config;

/**
 * Thread-local holder for the current tenant ID.
 * Set by TenantFilter on each request; must be cleared in a finally block.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static Long getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
