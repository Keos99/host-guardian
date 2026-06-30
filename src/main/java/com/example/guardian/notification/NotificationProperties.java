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
    private String serviceUrl = "";
    private String authHeaderName = "Authorization";
    private String authToken = "";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(10);
    private Ssl ssl = new Ssl();

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
     * Returns the public link to this Host Guardian instance.
     *
     * <p>The value is sent as the {@code url} payload field so chat recipients can
     * open the dashboard from the notification.
     *
     * @return Host Guardian service URL, or a blank string when not configured
     */
    public String getServiceUrl() {
        return serviceUrl;
    }

    /**
     * Sets the public link to this Host Guardian instance.
     *
     * @param serviceUrl Host Guardian service URL passed as the {@code url} payload field
     */
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
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

    /**
     * Returns the TLS settings for the webhook HTTP client.
     *
     * @return TLS configuration
     */
    public Ssl getSsl() {
        return ssl;
    }

    /**
     * Sets the TLS settings for the webhook HTTP client.
     *
     * @param ssl TLS configuration
     */
    public void setSsl(Ssl ssl) {
        this.ssl = ssl;
    }

    /**
     * TLS settings for the webhook HTTP client.
     *
     * <p>The client always trusts self-signed certificates via
     * {@code TrustSelfSignedStrategy}. When a trust store path is provided, that
     * store supplies the trusted certificates (for example a corporate CA or a
     * pinned self-signed certificate); otherwise the JVM default trust store is
     * used, extended with the self-signed trust strategy.
     *
     * <p>An optional key store provides the client certificate and private key
     * for mutual TLS, so the webhook can require client authentication. When no
     * key store path is set, the client presents no certificate.
     */
    public static class Ssl {

        private String trustStorePath = "";
        private String trustStorePassword = "";
        private String trustStoreType = "PKCS12";
        private String keyStorePath = "";
        private String keyStorePassword = "";
        private String keyStoreType = "PKCS12";
        private String keyPassword = "";

        /**
         * Returns the path to a custom trust store / certificate store.
         *
         * @return trust store path, or a blank string to use the JVM default store
         */
        public String getTrustStorePath() {
            return trustStorePath;
        }

        /**
         * Sets the path to a custom trust store / certificate store.
         *
         * @param trustStorePath trust store path; blank uses the JVM default store
         */
        public void setTrustStorePath(String trustStorePath) {
            this.trustStorePath = trustStorePath;
        }

        /**
         * Returns the password used to open the trust store.
         *
         * @return trust store password, or a blank string when none is required
         */
        public String getTrustStorePassword() {
            return trustStorePassword;
        }

        /**
         * Sets the password used to open the trust store.
         *
         * @param trustStorePassword trust store password
         */
        public void setTrustStorePassword(String trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
        }

        /**
         * Returns the trust store type.
         *
         * @return key store type, for example {@code PKCS12} or {@code JKS}
         */
        public String getTrustStoreType() {
            return trustStoreType;
        }

        /**
         * Sets the trust store type.
         *
         * @param trustStoreType key store type, for example {@code PKCS12} or {@code JKS}
         */
        public void setTrustStoreType(String trustStoreType) {
            this.trustStoreType = trustStoreType;
        }

        /**
         * Returns the path to the client key store used for mutual TLS.
         *
         * @return key store path, or a blank string when client authentication is not used
         */
        public String getKeyStorePath() {
            return keyStorePath;
        }

        /**
         * Sets the path to the client key store used for mutual TLS.
         *
         * @param keyStorePath key store path; blank disables client authentication
         */
        public void setKeyStorePath(String keyStorePath) {
            this.keyStorePath = keyStorePath;
        }

        /**
         * Returns the password used to open the key store.
         *
         * @return key store password, or a blank string when none is required
         */
        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        /**
         * Sets the password used to open the key store.
         *
         * @param keyStorePassword key store password
         */
        public void setKeyStorePassword(String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
        }

        /**
         * Returns the key store type.
         *
         * @return key store type, for example {@code PKCS12} or {@code JKS}
         */
        public String getKeyStoreType() {
            return keyStoreType;
        }

        /**
         * Sets the key store type.
         *
         * @param keyStoreType key store type, for example {@code PKCS12} or {@code JKS}
         */
        public void setKeyStoreType(String keyStoreType) {
            this.keyStoreType = keyStoreType;
        }

        /**
         * Returns the password protecting the private key entry.
         *
         * @return key password, or a blank string to reuse the key store password
         */
        public String getKeyPassword() {
            return keyPassword;
        }

        /**
         * Sets the password protecting the private key entry.
         *
         * @param keyPassword key password; blank reuses the key store password
         */
        public void setKeyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
        }
    }
}
