package com.vcs.management.usage.dto;

public record UsageDimensionResponse(
        String name,
        long receivedCount,
        long acceptedCount,
        long droppedCount,
        double receivedEps,
        double acceptedEps,
        double droppedEps
) {
}
