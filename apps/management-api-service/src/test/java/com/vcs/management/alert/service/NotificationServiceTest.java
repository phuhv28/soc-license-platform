package com.vcs.management.alert.service;

import com.vcs.management.tenant.entity.Tenant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private RestTemplate restTemplate;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(mailSender);
        ReflectionTestUtils.setField(notificationService, "fromEmail", "test@test.com");
        ReflectionTestUtils.setField(notificationService, "restTemplate", restTemplate);
    }

    @Test
    @DisplayName("Should send email and webhook when both are configured")
    void testNotifyLicenseExpiring_BothConfigured() {
        Tenant tenant = new Tenant("Test Tenant");
        tenant.setNotificationEmail("admin@test.com");
        tenant.setWebhookUrl("http://webhook.test/api");

        notificationService.notifyLicenseExpiring(tenant, 5);

        // Verify Email
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        
        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertEquals("admin@test.com", sentMessage.getTo()[0]);
        assertEquals("test@test.com", sentMessage.getFrom());
        assertEquals("Action Required: License Expiring Soon", sentMessage.getSubject());
        assertEquals("License for tenant 'Test Tenant' will expire in 5 day(s).", sentMessage.getText());

        // Verify Webhook
        verify(restTemplate).postForEntity(eq("http://webhook.test/api"), any(), eq(String.class));
    }

    @Test
    @DisplayName("Should not throw exception if email fails")
    void testNotifyLicenseExpiring_EmailFails() {
        Tenant tenant = new Tenant("Test Tenant");
        tenant.setNotificationEmail("admin@test.com");

        doThrow(new RuntimeException("SMTP Down")).when(mailSender).send(any(SimpleMailMessage.class));

        // Should not throw exception
        notificationService.notifyLicenseExpiring(tenant, 5);

        verify(mailSender).send(any(SimpleMailMessage.class));
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Should not throw exception if webhook fails")
    void testNotifyLicenseExpiring_WebhookFails() {
        Tenant tenant = new Tenant("Test Tenant");
        tenant.setWebhookUrl("http://webhook.test/api");

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Webhook Down"));

        // Should not throw exception
        notificationService.notifyLicenseExpiring(tenant, 5);

        verify(restTemplate).postForEntity(eq("http://webhook.test/api"), any(), eq(String.class));
        verifyNoInteractions(mailSender);
    }
}
