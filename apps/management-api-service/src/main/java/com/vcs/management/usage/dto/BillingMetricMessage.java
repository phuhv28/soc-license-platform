package com.vcs.management.usage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.UUID;

@Data
public class BillingMetricMessage {
    @JsonProperty("tenant_id")
    private UUID tenantId;

    @JsonProperty("window_type")
    private String windowType;

    @JsonProperty("window_key")
    private String windowKey;

    private long received;
    private long accepted;
    private long dropped;
}
