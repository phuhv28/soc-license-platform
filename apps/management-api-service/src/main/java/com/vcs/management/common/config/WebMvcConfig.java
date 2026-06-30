package com.vcs.management.common.config;

import com.vcs.management.common.security.TenantAuthorizationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantAuthorizationInterceptor tenantAuthorizationInterceptor;

    public WebMvcConfig(TenantAuthorizationInterceptor tenantAuthorizationInterceptor) {
        this.tenantAuthorizationInterceptor = tenantAuthorizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantAuthorizationInterceptor)
                .addPathPatterns("/api/v1/**");
    }
}
