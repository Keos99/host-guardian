package com.example.guardian.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ChatProvider} that posts messages to a configurable HTTP webhook.
 *
 * <p>The payload is a small JSON object
 * {@code {"peer": "...", "status": "...", "message": "...", "url": ""}}
 * whose field set matches the legacy SberChat sender, so an existing chat
 * endpoint can be plugged in by configuring {@code notification.chat.url}
 * and {@code notification.chat.peer} only. The {@code status} field carries the
 * provider status code from {@link MessageStatus#getValue()}; the chat backend
 * accepts only those codes. Generic webhook receivers simply ignore the fields
 * they do not need. An optional auth token is attached as a configurable HTTP header.
 *
 * <p>When no webhook URL is configured, the provider degrades to log-only mode:
 * messages are written to the application log instead of being dropped, which
 * keeps the feature observable on fresh installations.
 */
@Component
public class WebhookChatProvider implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(WebhookChatProvider.class);

    private final NotificationProperties properties;
    private final RestTemplate restTemplate;

    /**
     * Creates a webhook chat provider.
     *
     * @param properties notification feature configuration
     * @param chatRestTemplate HTTP client preconfigured with webhook timeouts
     */
    public WebhookChatProvider(NotificationProperties properties,
                               @Qualifier("chatRestTemplate") RestTemplate chatRestTemplate) {
        this.properties = properties;
        this.restTemplate = chatRestTemplate;
    }

    /**
     * Posts the message to the configured webhook or logs it when no URL is set.
     *
     * @param message message to deliver
     * @throws ChatSendException when the webhook call fails
     */
    @Override
    public void send(ChatMessage message) {
        if (properties.getUrl() == null || properties.getUrl().isBlank()) {
            log.info("Chat webhook url is not configured, message logged only: [{}] {}",
                    message.status().getValue(), message.text());
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getAuthToken() != null && !properties.getAuthToken().isBlank()) {
            headers.set(properties.getAuthHeaderName(), properties.getAuthToken());
        }

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("peer", message.peer() != null ? message.peer() : "");
        payload.put("status", message.status().getValue());
        payload.put("message", message.text());
        payload.put("url", message.url() != null ? message.url() : "");

        try {
            restTemplate.postForEntity(properties.getUrl(), new HttpEntity<>(payload, headers), String.class);
        } catch (RestClientException e) {
            throw new ChatSendException("Failed to deliver chat message to webhook", e);
        }
    }
}
