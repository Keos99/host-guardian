package com.example.guardian.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

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
     * <p>Timeouts come from {@link NotificationProperties}, so a slow chat
     * backend cannot hold a notification thread forever.
     *
     * @param properties notification feature configuration
     * @return HTTP client for the webhook provider
     */
    @Bean
    public RestTemplate chatRestTemplate(NotificationProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getReadTimeout().toMillis());
        return new RestTemplate(requestFactory);
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
