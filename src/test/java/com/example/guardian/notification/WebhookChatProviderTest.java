package com.example.guardian.notification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WebhookChatProviderTest {

    private static final String WEBHOOK_URL = "http://chat.example/api/send";

    private NotificationProperties properties;
    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private WebhookChatProvider provider;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        provider = new WebhookChatProvider(properties, restTemplate);
    }

    @Test
    void blankUrlSwitchesToLogOnlyModeWithoutHttpCalls() {
        properties.setUrl(" ");

        assertThatCode(() -> provider.send(new ChatMessage(MessageStatus.SUCCESS, "hello", "peer-1", "http://guardian")))
                .doesNotThrowAnyException();

        server.verify();
    }

    @Test
    void postsLegacyCompatiblePayloadWithAuthHeader() {
        properties.setUrl(WEBHOOK_URL);
        properties.setAuthToken("secret-token");

        server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "secret-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.peer").value("ops-channel-7"))
                .andExpect(jsonPath("$.status").value("FAIL"))
                .andExpect(jsonPath("$.message").value("Сервис недоступен"))
                .andExpect(jsonPath("$.url").value("http://guardian.local:8099"))
                .andRespond(withSuccess());

        provider.send(new ChatMessage(MessageStatus.FAIL, "Сервис недоступен", "ops-channel-7", "http://guardian.local:8099"));

        server.verify();
    }

    @Test
    void mapsProviderStatusCodesFromGetValue() {
        properties.setUrl(WEBHOOK_URL);

        server.expect(requestTo(WEBHOOK_URL))
                .andExpect(jsonPath("$.status").value("UNSTABLE"))
                .andRespond(withSuccess());

        provider.send(new ChatMessage(MessageStatus.UNSTABLE, "restarting", "peer-1", "http://guardian"));

        server.verify();
    }

    @Test
    void omitsAuthHeaderWhenTokenIsBlank() {
        properties.setUrl(WEBHOOK_URL);

        server.expect(requestTo(WEBHOOK_URL))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(jsonPath("$.peer").value(""))
                .andRespond(withSuccess());

        provider.send(new ChatMessage(MessageStatus.OK, "ok", "", ""));

        server.verify();
    }

    @Test
    void transportFailureIsWrappedIntoChatSendException() {
        properties.setUrl(WEBHOOK_URL);

        server.expect(requestTo(WEBHOOK_URL)).andRespond(withServerError());

        assertThatThrownBy(() -> provider.send(new ChatMessage(MessageStatus.OK, "ok", "peer-1", "http://guardian")))
                .isInstanceOf(ChatSendException.class)
                .hasMessageContaining("webhook");
    }
}
