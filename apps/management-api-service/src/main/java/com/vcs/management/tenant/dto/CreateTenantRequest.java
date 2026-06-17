package com.vcs.management.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank(message = "Tenant name is required")
        @Size(max = 255, message = "Tenant name must not exceed 255 characters")
        String name
) {
}
