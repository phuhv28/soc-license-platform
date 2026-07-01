package com.vcs.management.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.List;
import java.util.Map;

@Component
public class TenantAuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Only intercept /api/v1/... requests that contain tenantId, skip internal endpoints
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/") || path.startsWith("/api/v1/internal/")) {
            return true;
        }

        // Extract tenantId from path variables
        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (pathVariables == null || !pathVariables.containsKey("tenantId")) {
            return true;
        }

        String requestTenantId = pathVariables.get("tenantId");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return false;
        }

        // Admin can access any tenant
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return true;
        }

        if (auth instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            
            // Support both String and List<String> depending on Mapper config
            Object tenantIdClaim = jwt.getClaim("tenantId");
            if (tenantIdClaim != null) {
                if (tenantIdClaim instanceof String) {
                    if (tenantIdClaim.equals(requestTenantId)) return true;
                } else if (tenantIdClaim instanceof List<?> list) {
                    if (list.contains(requestTenantId)) return true;
                }
            }
        }

        // Forbidden
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied: You do not own this tenant resource.");
        return false;
    }
}
