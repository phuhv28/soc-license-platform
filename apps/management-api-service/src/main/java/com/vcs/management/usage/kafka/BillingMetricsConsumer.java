package com.vcs.management.usage.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vcs.management.usage.dto.BillingMetricMessage;
import com.vcs.management.usage.entity.BillingMetric;
import com.vcs.management.usage.repository.BillingMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class BillingMetricsConsumer {

    private static final Logger log = LoggerFactory.getLogger(BillingMetricsConsumer.class);

    private final BillingMetricRepository billingMetricRepository;
    private final ObjectMapper objectMapper;

    public BillingMetricsConsumer(BillingMetricRepository billingMetricRepository, ObjectMapper objectMapper) {
        this.billingMetricRepository = billingMetricRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "billing-metrics", groupId = "management-api-group")
    @Transactional
    public void consume(String message) {
        try {
            BillingMetricMessage dto = objectMapper.readValue(message, BillingMetricMessage.class);

            Optional<BillingMetric> existingOpt = billingMetricRepository.findByTenantIdAndWindowTypeAndWindowKey(
                    dto.getTenantId(), dto.getWindowType(), dto.getWindowKey()
            );

            BillingMetric metric;
            if (existingOpt.isPresent()) {
                metric = existingOpt.get();
                metric.setReceived(metric.getReceived() + dto.getReceived());
                metric.setAccepted(metric.getAccepted() + dto.getAccepted());
                metric.setDropped(metric.getDropped() + dto.getDropped());
            } else {
                metric = new BillingMetric();
                metric.setTenantId(dto.getTenantId());
                metric.setWindowType(dto.getWindowType());
                metric.setWindowKey(dto.getWindowKey());
                metric.setReceived(dto.getReceived());
                metric.setAccepted(dto.getAccepted());
                metric.setDropped(dto.getDropped());
            }

            billingMetricRepository.save(metric);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse billing metric message: {}", message, e);
        } catch (Exception e) {
            log.error("Error processing billing metric message", e);
        }
    }
}
