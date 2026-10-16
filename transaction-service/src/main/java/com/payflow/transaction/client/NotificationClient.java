package com.payflow.transaction.client;

import com.payflow.common.security.InternalKeyFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends notifications to the notification-service.
 *
 * Best effort on purpose: if notifications are down, the money has still moved
 * correctly, so a failure here is logged and never fails the transfer.
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);

    public record NotificationCommand(Long userId, String message) {
    }

    private final RestClient restClient;

    public NotificationClient(RestClient.Builder builder,
                              @Value("${payflow.services.notification-url}") String baseUrl,
                              @Value("${payflow.internal-key}") String internalKey) {
        this.restClient = builder.baseUrl(baseUrl).defaultHeader(InternalKeyFilter.HEADER, internalKey).build();
    }

    public void send(Long userId, String message) {
        try {
            restClient.post().uri("/internal/notifications")
                    .body(new NotificationCommand(userId, message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Could not notify user {}: {}", userId, e.getMessage());
        }
    }
}
