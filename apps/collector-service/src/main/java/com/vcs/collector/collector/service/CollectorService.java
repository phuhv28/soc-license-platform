package com.vcs.collector.collector.service;

import com.vcs.collector.collector.dto.CollectorBatchResponseDTO;
import com.vcs.collector.collector.dto.CollectorEventBatchRequestDTO;

/**
 * Service interface for event collection and processing
 */
public interface CollectorService {

    /**
     * Process a batch of events
     *
     * @param request the batch request containing events
     * @return the batch processing result
     */
    CollectorBatchResponseDTO processBatch(CollectorEventBatchRequestDTO request);
}
