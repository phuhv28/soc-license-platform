package com.vcs.management.usage.dto;

public record UsageDimensionResponse(
        String name,
        long count,
        double eps
) {
}
