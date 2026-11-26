package com.digitalwill.notification.service;

import com.digitalwill.notification.config.NotificationProperties;
import com.digitalwill.notification.model.NotificationDeliveryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.UUID;

/**
 * Service managing delivery of verification request links to trusted contacts
 * and scoped estate disclosure links to beneficiaries (Workstream 3).
 *
 * Invariants:
 * 1. Safe Link Construction: Links are built exclusively from configured public base URL, never untrusted Host headers.
 * 2. Token Secrecy: Raw tokens are used strictly in memory for link generation; never logged or persisted.
 * 3. Transaction-Aware: If inside an active transaction, delivery dispatches post-commit via TransactionSynchronization.
 *    Rolled-back transactions never send links.
 * 4. Honest Status: Unconfigured or disabled states return SKIPPED_UNCONFIGURED; never falsely claims "delivered".
 * 5. Fail-Safe: Delivery failure is logged observably but does not crash or corrupt domain state transitions.
 */
@Service
public class NotificationDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final NotificationProperties properties;

    private final RestClient mailClient;

    @Autowired
    public NotificationDeliveryService(NotificationProperties properties) {
        this(properties, createMailClient());
    }

    private static RestClient createMailClient() {
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(10));
        return RestClient.builder().baseUrl("https://api.resend.com").requestFactory(factory).build();
    }

    public NotificationDeliveryService(NotificationProperties properties, RestClient mailClient) {
        this.mailClient = mailClient;
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    /**
     * Dispatches verification request notification to a trusted contact.
     */
    public NotificationDeliveryResult sendVerificationNotification(String recipientEmail, String recipientName, UUID willId, String rawToken) {
        if (recipientEmail == null || recipientEmail.isBlank() || rawToken == null || rawToken.isBlank()) {
            log.warn("Cannot send verification notification: recipient or token is missing for will [{}]", willId);
            return NotificationDeliveryResult.failed("***", "Recipient email or token is blank");
        }

        String maskedEmail = maskEmail(recipientEmail);
        String verificationUrl = buildVerificationUrl(rawToken);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        doSendVerificationEmail(recipientEmail, recipientName, willId, maskedEmail, verificationUrl);
                    } catch (Exception e) {
                        log.error("Failed post-commit verification notification to [{}]: {}", maskedEmail, e.getClass().getSimpleName());
                    }
                }
            });
            return new NotificationDeliveryResult(false, "PENDING_COMMIT", maskedEmail, null);
        } else {
            return doSendVerificationEmail(recipientEmail, recipientName, willId, maskedEmail, verificationUrl);
        }
    }

    /**
     * Dispatches controlled disclosure package notification to a beneficiary.
     */
    public NotificationDeliveryResult sendDisclosureNotification(String recipientEmail, String recipientName, UUID willId, String rawToken) {
        if (recipientEmail == null || recipientEmail.isBlank() || rawToken == null || rawToken.isBlank()) {
            log.warn("Cannot send disclosure notification: recipient or token is missing for will [{}]", willId);
            return NotificationDeliveryResult.failed("***", "Recipient email or token is blank");
        }

        String maskedEmail = maskEmail(recipientEmail);
        String disclosureUrl = buildDisclosureUrl(rawToken);

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        doSendDisclosureEmail(recipientEmail, recipientName, willId, maskedEmail, disclosureUrl);
                    } catch (Exception e) {
                        log.error("Failed post-commit disclosure notification to [{}]: {}", maskedEmail, e.getClass().getSimpleName());
                    }
                }
            });
            return new NotificationDeliveryResult(false, "PENDING_COMMIT", maskedEmail, null);
        } else {
            return doSendDisclosureEmail(recipientEmail, recipientName, willId, maskedEmail, disclosureUrl);
        }
    }

    public NotificationDeliveryResult doSendVerificationEmail(String recipientEmail, String recipientName, UUID willId, String maskedEmail, String verificationUrl) {
        return sendLink(recipientEmail, maskedEmail, willId, "Digital Will verification request", verificationUrl);
    }

    public NotificationDeliveryResult doSendDisclosureEmail(String recipientEmail, String recipientName, UUID willId, String maskedEmail, String disclosureUrl) {
        return sendLink(recipientEmail, maskedEmail, willId, "Your Digital Will disclosure package", disclosureUrl);
    }

    private NotificationDeliveryResult sendLink(String email, String maskedEmail, UUID willId, String subject, String link) {
        if (!properties.isEnabled() || "DISABLED".equalsIgnoreCase(properties.getProvider())) {
            return NotificationDeliveryResult.skipped(maskedEmail, "Notification provider is disabled or unconfigured");
        }
        if ("CONSOLE".equalsIgnoreCase(properties.getProvider())) {
            log.info("Previewed notification for will [{}]", willId);
            return new NotificationDeliveryResult(false, "PREVIEWED", maskedEmail, null);
        }
        if (!"RESEND".equalsIgnoreCase(properties.getProvider())) {
            return NotificationDeliveryResult.failed(maskedEmail, "Unsupported notification provider");
        }
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return NotificationDeliveryResult.failed(maskedEmail, "Missing provider API key");
        }
        if (!"https".equals(URI.create(link).getScheme())) {
            return NotificationDeliveryResult.failed(maskedEmail, "Public notification links require HTTPS");
        }
        try {
            var result = mailClient.post().uri("/emails")
                    .headers(headers -> headers.setBearerAuth(properties.getApiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("from", properties.getFromEmail(), "to", List.of(email),
                            "subject", subject, "text", subject + "\n\n" + link))
                    .retrieve().body(ProviderReceipt.class);
            if (result != null && result.id() != null && !result.id().isBlank()) {
                return new NotificationDeliveryResult(false, "ACCEPTED_BY_PROVIDER", maskedEmail, null);
            }
        } catch (RestClientException unavailable) {
            // A provider response can contain recipient data. Keep its body out of application logs.
            log.warn("Email provider did not accept a notification for will [{}]", willId);
        }
        return NotificationDeliveryResult.failed(maskedEmail, "No provider receipt");
    }

    private record ProviderReceipt(String id) {}

    public String buildVerificationUrl(String rawToken) {
        String base = properties.getFrontendBaseUrl().replaceAll("/+$", "");
        return base + "/verify?token=" + rawToken;
    }

    public String buildDisclosureUrl(String rawToken) {
        String base = properties.getFrontendBaseUrl().replaceAll("/+$", "");
        return base + "/disclosure/" + rawToken;
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIdx = email.indexOf('@');
        String name = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        if (name.length() <= 1) {
            return name + "***" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }
}
