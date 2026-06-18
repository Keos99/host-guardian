package com.example.guardian.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuration of the chat notification feature, bound from the
 * {@code notification.chat} section of {@code application.yml}.
 *
 * <p>The {@code enabled} flag is the build-time kill switch: when it is
 * {@code false}, no message is ever sent and the dashboard hides every
 * notification control. The runtime on/off toggle that operators flip from
 * the dashboard is stored separately in the database.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "notification.chat")
public class NotificationProperties {

    private boolean enabled = true;
    private String url = "";
    private String peer = "";
    private String authHeaderName = "Authorization";
    private String authToken = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(10);

    /**
     * Checks whether the notification feature is enabled at all.
     *
     * @return {@code true} when chat notifications may be sent and shown in the UI
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the notification feature is enabled at all.
     *
     * @param enabled {@code true} to enable the feature
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the chat webhook endpoint URL.
     *
     * @return webhook URL, or a blank string for log-only mode
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the chat webhook endpoint URL.
     *
     * @param url webhook URL; blank value switches the provider to log-only mode
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Returns the chat recipient identifier passed as the {@code peer} payload field.
     *
     * @return recipient identifier, or a blank string when the chat backend needs none
     */
    public String getPeer() {
        return peer;
    }

    /**
     * Sets the chat recipient identifier passed as the {@code peer} payload field.
     *
     * @param peer recipient identifier, for example a channel or dialog id
     */
    public void setPeer(String peer) {
        this.peer = peer;
    }

    /**
     * Returns the HTTP header name used to pass the auth token.
     *
     * @return auth header name
     */
    public String getAuthHeaderName() {
        return authHeaderName;
    }

    /**
     * Sets the HTTP header name used to pass the auth token.
     *
     * @param authHeaderName auth header name
     */
    public void setAuthHeaderName(String authHeaderName) {
        this.authHeaderName = authHeaderName;
    }

    /**
     * Returns the auth token added to webhook requests.
     *
     * @return auth token, or a blank string when the webhook needs no auth
     */
    public String getAuthToken() {
        return authToken;
    }

    /**
     * Sets the auth token added to webhook requests.
     *
     * @param authToken auth token value
     */
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    /**
     * Returns the webhook connect timeout.
     *
     * @return connect timeout
     */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * Sets the webhook connect timeout.
     *
     * @param connectTimeout connect timeout
     */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Returns the webhook response read timeout.
     *
     * @return read timeout
     */
    public Duration getReadTimeout() {
        return readTimeout;
    }

    /**
     * Sets the webhook response read timeout.
     *
     * @param readTimeout read timeout
     */
    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
