package com.vcs.collector.collector.controller;

import com.vcs.collector.collector.dto.CollectorBatchResponseDTO;
import com.vcs.collector.collector.dto.CollectorEventBatchRequestDTO;
import com.vcs.collector.collector.service.CollectorService;
import com.vcs.collector.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for event collection
 * Entrypoint of Data Plane
 */
@RestController
@RequestMapping("/api/v1/collector")
@RequiredArgsConstructor
public class CollectorController {

    private final CollectorService collectorService;

    /**
     * Receive and process a batch of events
     *
     * @param request the batch request containing events
     * @return the batch processing result with statistics
     */
    @PostMapping("/events/batch")
    public ResponseEntity<ApiResponse<CollectorBatchResponseDTO>> processBatch(
            @Valid @RequestBody CollectorEventBatchRequestDTO request
    ) {
        CollectorBatchResponseDTO response = collectorService.processBatch(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
