package com.example.guardian.notification;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationConfigTest {

    private final NotificationConfig config = new NotificationConfig();

    @Test
    void chatRestTemplateUsesConfiguredTimeouts() {
        NotificationProperties properties = new NotificationProperties();
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setReadTimeout(Duration.ofSeconds(4));

        RestTemplate restTemplate = config.chatRestTemplate(properties);

        assertThat(restTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);
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
