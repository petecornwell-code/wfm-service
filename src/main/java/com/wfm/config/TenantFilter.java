package com.wfm.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Extracts X-Tenant-ID header from every request and stores it in TenantContext.
 * Returns 400 if the header is missing or not a valid long.
 * Actuator endpoints are excluded.
 */
@Component
@Order(1)
public class TenantFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-ID";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String tenantHeader = request.getHeader(TENANT_HEADER);
        if (tenantHeader == null || tenantHeader.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing X-Tenant-ID header");
            return;
        }

        try {
            long tenantId = Long.parseLong(tenantHeader.trim());
            TenantContext.setTenantId(tenantId);
            filterChain.doFilter(request, response);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid X-Tenant-ID header");
        } finally {
            TenantContext.clear();
        }
    }
}
