package com.vcs.management.alert.service;

import com.vcs.management.tenant.entity.Tenant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate;

    @Value("${spring.mail.username:no-reply@soc-license.com}")
    private String fromEmail;

    public NotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        this.restTemplate = new RestTemplate();
    }

    public void notifyLicenseExpiring(Tenant tenant, long daysRemaining) {
        String message = String.format("License for tenant '%s' will expire in %d day(s).", tenant.getName(), daysRemaining);
        String subject = "Action Required: License Expiring Soon";

        // Send Email if configured
        if (tenant.getNotificationEmail() != null && !tenant.getNotificationEmail().isBlank()) {
            try {
                sendEmail(tenant.getNotificationEmail(), subject, message);
                log.info("Sent expiration email to {}", tenant.getNotificationEmail());
            } catch (Exception e) {
                log.error("Failed to send email to {}: {}", tenant.getNotificationEmail(), e.getMessage());
            }
        }

        // Send Webhook if configured
        if (tenant.getWebhookUrl() != null && !tenant.getWebhookUrl().isBlank()) {
            try {
                sendWebhook(tenant.getWebhookUrl(), tenant.getName(), message);
                log.info("Sent webhook to {}", tenant.getWebhookUrl());
            } catch (Exception e) {
                log.error("Failed to send webhook to {}: {}", tenant.getWebhookUrl(), e.getMessage());
            }
        }
    }

    private void sendEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private void sendWebhook(String url, String tenantName, String messageText) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenant", tenantName);
        payload.put("message", messageText);
        payload.put("type", "LICENSE_EXPIRING_SOON");

        restTemplate.postForEntity(url, payload, String.class);
    }
}
