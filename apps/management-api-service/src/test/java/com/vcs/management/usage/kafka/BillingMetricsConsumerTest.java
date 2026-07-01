package com.vcs.management.usage.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vcs.management.usage.dto.BillingMetricMessage;
import com.vcs.management.usage.entity.BillingMetric;
import com.vcs.management.usage.repository.BillingMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BillingMetricsConsumerTest {

    @Mock
    private BillingMetricRepository billingMetricRepository;

    @Mock
    private ObjectMapper objectMapper;

    private BillingMetricsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new BillingMetricsConsumer(billingMetricRepository, objectMapper);
    }

    @Test
    void consume_NewMetric_SavesSuccessfully() throws JsonProcessingException {
        String message = "{}";
        UUID tenantId = UUID.randomUUID();
        BillingMetricMessage dto = new BillingMetricMessage();
        dto.setTenantId(tenantId);
        dto.setWindowType("1m");
        dto.setWindowKey("2024-01-01T00:00:00Z");
        dto.setReceived(100L);
        dto.setAccepted(80L);
        dto.setDropped(20L);

        when(objectMapper.readValue(message, BillingMetricMessage.class)).thenReturn(dto);
        when(billingMetricRepository.findByTenantIdAndWindowTypeAndWindowKey(tenantId, "1m", "2024-01-01T00:00:00Z"))
                .thenReturn(Optional.empty());

        consumer.consume(message);

        ArgumentCaptor<BillingMetric> captor = ArgumentCaptor.forClass(BillingMetric.class);
        verify(billingMetricRepository).save(captor.capture());

        BillingMetric saved = captor.getValue();
        assertEquals(tenantId, saved.getTenantId());
        assertEquals(100L, saved.getReceived());
        assertEquals(80L, saved.getAccepted());
        assertEquals(20L, saved.getDropped());
    }

    @Test
    void consume_ExistingMetric_UpdatesSuccessfully() throws JsonProcessingException {
        String message = "{}";
        UUID tenantId = UUID.randomUUID();
        BillingMetricMessage dto = new BillingMetricMessage();
        dto.setTenantId(tenantId);
        dto.setWindowType("1m");
        dto.setWindowKey("2024-01-01T00:00:00Z");
        dto.setReceived(50L);
        dto.setAccepted(40L);
        dto.setDropped(10L);

        BillingMetric existing = new BillingMetric();
        existing.setTenantId(tenantId);
        existing.setReceived(100L);
        existing.setAccepted(80L);
        existing.setDropped(20L);

        when(objectMapper.readValue(message, BillingMetricMessage.class)).thenReturn(dto);
        when(billingMetricRepository.findByTenantIdAndWindowTypeAndWindowKey(tenantId, "1m", "2024-01-01T00:00:00Z"))
                .thenReturn(Optional.of(existing));

        consumer.consume(message);

        ArgumentCaptor<BillingMetric> captor = ArgumentCaptor.forClass(BillingMetric.class);
        verify(billingMetricRepository).save(captor.capture());

        BillingMetric saved = captor.getValue();
        assertEquals(150L, saved.getReceived());
        assertEquals(120L, saved.getAccepted());
        assertEquals(30L, saved.getDropped());
    }

    @Test
    void consume_InvalidJson_LogsErrorAndDoesNotSave() throws JsonProcessingException {
        String message = "invalid json";
        when(objectMapper.readValue(message, BillingMetricMessage.class)).thenThrow(JsonProcessingException.class);

        consumer.consume(message);

        verify(billingMetricRepository, never()).save(any());
    }
}
