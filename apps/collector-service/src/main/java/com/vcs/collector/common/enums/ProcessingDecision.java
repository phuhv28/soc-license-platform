package com.vcs.collector.common.enums;

/**
 * Decision enum for batch processing results
 * Explains why events were accepted/dropped
 */
public enum ProcessingDecision {
    /**
     * Tenant has no active license
     * All events dropped due to missing quota
     */
    NO_ACTIVE_LICENSE,

    /**
     * Token bucket limit reached
     * Some or all events dropped due to rate limit
     */
    OVER_QUOTA,

    /**
     * Some events are valid, some are invalid
     * Mix of accepted and dropped
     */
    PARTIAL_VALIDATION,

    /**
     * All events passed validation and token bucket
     * All accepted
     */
    ALL_ACCEPTED,

    /**
     * All events failed validation
     * All dropped due to invalid format
     */
    ALL_INVALID
}
