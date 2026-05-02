package com.example.guardian.service;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Компонент для HTTP-проверки доступности и базового здоровья сервиса.
 *
 * <p>Проверка считается успешной, если указанный endpoint отвечает кодом
 * семейства {@code 2xx}. Компонент намеренно не анализирует тело ответа,
 * а только подтверждает сетевую доступность и успешный HTTP-статус, чего
 * достаточно для простого мониторинга приложений с endpoint вроде
 * {@code /actuator/health}.
 */
@Component
public class HttpHealthChecker {

    private final RestTemplateBuilder restTemplateBuilder;

    /**
     * Создает компонент health-check на основе {@link RestTemplateBuilder}.
     *
     * @param restTemplateBuilder фабрика для построения {@link RestTemplate}
     *                            с нужными сетевыми таймаутами
     */
    public HttpHealthChecker(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplateBuilder = restTemplateBuilder;
    }

    /**
     * Выполняет HTTP GET запрос к health endpoint и определяет, считается ли сервис здоровым.
     *
     * @param url адрес health endpoint
     * @param timeout единый таймаут на установление соединения и чтение ответа
     * @return {@code true}, если endpoint ответил кодом {@code 2xx}; иначе {@code false}
     */
    public boolean isHealthy(String url, Duration timeout) {
        try {
            RestTemplate restTemplate = restTemplateBuilder
                    .setConnectTimeout(timeout)
                    .setReadTimeout(timeout)
                    .build();

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
