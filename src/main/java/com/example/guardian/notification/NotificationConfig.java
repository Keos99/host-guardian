package com.example.guardian.notification;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustSelfSignedStrategy;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Spring beans that back the chat notification feature.
 */
@Configuration
public class NotificationConfig {

    /**
     * Creates the HTTP client used for webhook calls.
     *
     * <p>Timeouts come from {@link NotificationProperties}, so a slow chat backend
     * cannot hold a notification thread forever. The client is backed by Apache
     * HttpClient with a TLS trust configuration built from
     * {@link NotificationProperties.Ssl}: self-signed certificates are accepted via
     * {@code TrustSelfSignedStrategy}, and an optional custom trust store path
     * supplies the trusted certificates when configured.
     *
     * @param properties notification feature configuration
     * @return HTTP client for the webhook provider
     * @throws Exception when the configured trust store cannot be loaded
     */
    @Bean
    public RestTemplate chatRestTemplate(NotificationProperties properties) throws Exception {
        SSLContext sslContext = buildSslContext(properties.getSsl());
        SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                .setSslContext(sslContext)
                .build();

        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .setDefaultSocketConfig(SocketConfig.custom()
                        .setSoTimeout(Timeout.ofMilliseconds(properties.getReadTimeout().toMillis()))
                        .build())
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        return new RestTemplate(requestFactory);
    }

    /**
     * Builds the TLS context used by the webhook client.
     *
     * <p>{@code TrustSelfSignedStrategy} is always applied, so endpoints protected
     * by self-signed certificates are accepted. When a trust store path is
     * configured, that store is loaded and supplies the trusted certificates;
     * otherwise the JVM default trust store is used together with the self-signed
     * strategy. When a key store path is configured, its client certificate and
     * private key are loaded for mutual TLS authentication.
     *
     * @param ssl TLS configuration
     * @return configured SSL context
     * @throws Exception when a store cannot be loaded or the context built
     */
    private SSLContext buildSslContext(NotificationProperties.Ssl ssl) throws Exception {
        SSLContextBuilder builder = SSLContexts.custom();

        if (isSet(ssl.getKeyStorePath())) {
            KeyStore keyStore = loadStore(ssl.getKeyStorePath(), ssl.getKeyStoreType(), ssl.getKeyStorePassword());
            char[] keyPassword = isSet(ssl.getKeyPassword())
                    ? ssl.getKeyPassword().toCharArray()
                    : passwordChars(ssl.getKeyStorePassword());
            builder.loadKeyMaterial(keyStore, keyPassword);
        }

        if (isSet(ssl.getTrustStorePath())) {
            KeyStore trustStore = loadStore(ssl.getTrustStorePath(), ssl.getTrustStoreType(), ssl.getTrustStorePassword());
            builder.loadTrustMaterial(trustStore, new TrustSelfSignedStrategy());
        } else {
            builder.loadTrustMaterial(new TrustSelfSignedStrategy());
        }

        return builder.build();
    }

    /**
     * Loads a key or trust store from the file system.
     *
     * @param path store file path
     * @param type store type, for example {@code PKCS12} or {@code JKS}
     * @param password store password, may be blank
     * @return loaded key store
     * @throws Exception when the store cannot be read or parsed
     */
    private KeyStore loadStore(String path, String type, String password) throws Exception {
        KeyStore store = KeyStore.getInstance(type);
        try (InputStream storeStream = Files.newInputStream(Path.of(path))) {
            store.load(storeStream, passwordChars(password));
        }
        return store;
    }

    /**
     * Converts a password string into characters, treating blank as no password.
     *
     * @param password password value
     * @return password characters, or {@code null} when blank
     */
    private char[] passwordChars(String password) {
        return isSet(password) ? password.toCharArray() : null;
    }

    /**
     * Checks whether a configuration value is present.
     *
     * @param value value to test
     * @return {@code true} when the value is non-null and not blank
     */
    private boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Creates the executor that delivers chat messages asynchronously.
     *
     * <p>A single daemon thread keeps message order stable and guarantees that
     * slow webhook calls never block the monitoring loop or REST requests.
     *
     * @return single-threaded notification executor
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService notificationExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chat-notifier");
            thread.setDaemon(true);
            return thread;
        });
    }
}
