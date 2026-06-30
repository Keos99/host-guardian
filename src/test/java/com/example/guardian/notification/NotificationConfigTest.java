package com.example.guardian.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationConfigTest {

    private final NotificationConfig config = new NotificationConfig();

    @Test
    void chatRestTemplateUsesApacheHttpClientFactoryByDefault() throws Exception {
        NotificationProperties properties = new NotificationProperties();
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(4));

        RestTemplate restTemplate = config.chatRestTemplate(properties);

        assertThat(restTemplate.getRequestFactory()).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
    }

    @Test
    void chatRestTemplateLoadsConfiguredTrustStore(@TempDir Path tempDir) throws Exception {
        Path trustStorePath = writeEmptyStore(tempDir.resolve("trust.p12"), "changeit");

        NotificationProperties properties = new NotificationProperties();
        properties.getSsl().setTrustStorePath(trustStorePath.toString());
        properties.getSsl().setTrustStorePassword("changeit");
        properties.getSsl().setTrustStoreType("PKCS12");

        RestTemplate restTemplate = config.chatRestTemplate(properties);

        assertThat(restTemplate.getRequestFactory()).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
    }

    @Test
    void chatRestTemplateLoadsConfiguredKeyStoreForMutualTls(@TempDir Path tempDir) throws Exception {
        Path keyStorePath = writeEmptyStore(tempDir.resolve("client.p12"), "secret");

        NotificationProperties properties = new NotificationProperties();
        properties.getSsl().setKeyStorePath(keyStorePath.toString());
        properties.getSsl().setKeyStorePassword("secret");
        properties.getSsl().setKeyStoreType("PKCS12");

        RestTemplate restTemplate = config.chatRestTemplate(properties);

        assertThat(restTemplate.getRequestFactory()).isInstanceOf(HttpComponentsClientHttpRequestFactory.class);
    }

    @Test
    void chatRestTemplateFailsFastWhenTrustStoreIsMissing(@TempDir Path tempDir) {
        NotificationProperties properties = new NotificationProperties();
        properties.getSsl().setTrustStorePath(tempDir.resolve("absent.p12").toString());

        assertThatThrownBy(() -> config.chatRestTemplate(properties))
                .isInstanceOf(Exception.class);
    }

    @Test
    void chatRestTemplateFailsFastWhenKeyStoreIsMissing(@TempDir Path tempDir) {
        NotificationProperties properties = new NotificationProperties();
        properties.getSsl().setKeyStorePath(tempDir.resolve("absent.p12").toString());

        assertThatThrownBy(() -> config.chatRestTemplate(properties))
                .isInstanceOf(Exception.class);
    }

    private Path writeEmptyStore(Path path, String password) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, password.toCharArray());
        try (OutputStream out = Files.newOutputStream(path)) {
            store.store(out, password.toCharArray());
        }
        return path;
    }

    @Test
    void notificationExecutorRunsTasksOnSingleDaemonThread() throws Exception {
        ExecutorService executor = config.notificationExecutor();
        try {
            CompletableFuture<Thread> executionThread = new CompletableFuture<>();
            executor.execute(() -> executionThread.complete(Thread.currentThread()));

            Thread thread = executionThread.get(5, TimeUnit.SECONDS);
            assertThat(thread.getName()).isEqualTo("chat-notifier");
            assertThat(thread.isDaemon()).isTrue();
        } finally {
            executor.shutdown();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
