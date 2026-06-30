package com.vcs.management.usage.dto;

import java.util.List;

public record PagedDimensionResponse(
        List<UsageDimensionResponse> items,
        long total,
        int page,
        int pageSize,
        int totalPages
) {
}
