package com.vcs.management.usage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "billing_metrics", indexes = {
        @Index(name = "idx_billing_tenant_window", columnList = "tenant_id, window_type, window_key", unique = true)
})
@Getter
@Setter
public class BillingMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "window_type", nullable = false, length = 10)
    private String windowType; // "1m", "5m", "15m", "1d"

    @Column(name = "window_key", nullable = false, length = 20)
    private String windowKey; // e.g., "202607011640"

    @Column(name = "received", nullable = false)
    private Long received = 0L;

    @Column(name = "accepted", nullable = false)
    private Long accepted = 0L;

    @Column(name = "dropped", nullable = false)
    private Long dropped = 0L;
}
