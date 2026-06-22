package com.vcs.management.usage.dto;

import java.util.List;
import java.util.UUID;

/**
 * Time-series usage data for charts (24h history).
 */
public record UsageHistoryResponse(
        UUID tenantId,
        String tenantName,
        String window,
        List<DataPoint> dataPoints
) {

    /**
     * A single data point in the time series.
     */
    public record DataPoint(
            String timestamp,
            long received,
            long accepted,
            long dropped
    ) {
    }
}
