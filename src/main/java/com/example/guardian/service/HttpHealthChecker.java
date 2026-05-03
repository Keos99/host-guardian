package com.example.guardian.service;

import com.example.guardian.model.HostConfig;
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
    private final HostShellExecutor hostShellExecutor;

    /**
     * Создает компонент health-check на основе {@link RestTemplateBuilder}.
     *
     * @param restTemplateBuilder фабрика для построения {@link RestTemplate}
     *                            с нужными сетевыми таймаутами
     * @param hostShellExecutor исполнитель shell-команд на локальном или удаленном хосте
     */
    public HttpHealthChecker(RestTemplateBuilder restTemplateBuilder,
                             HostShellExecutor hostShellExecutor) {
        this.restTemplateBuilder = restTemplateBuilder;
        this.hostShellExecutor = hostShellExecutor;
    }

    /**
     * Выполняет HTTP GET запрос к health endpoint и определяет, считается ли сервис здоровым.
     *
     * <p>Для локального хоста используется обычный HTTP-клиент JVM. Для удаленных
     * хостов health-check выполняется на самом целевом хосте через {@code curl},
     * чтобы можно было проверять endpoints, доступные только локально на удаленной
     * машине, например {@code http://127.0.0.1:8081/actuator/health}.
     *
     * @param host хост, на котором расположен сервис
     * @param url адрес health endpoint
     * @param timeout единый таймаут на установление соединения и чтение ответа
     * @return {@code true}, если endpoint ответил кодом {@code 2xx}; иначе {@code false}
     */
    public boolean isHealthy(HostConfig host, String url, Duration timeout) {
        if (!host.isLocal()) {
            String command = "curl -fsS --max-time " + Math.max(1, timeout.toSeconds())
                    + " " + shellQuote(url) + " > /dev/null";
            CommandExecutor.CommandResult result = hostShellExecutor.execute(host, command, timeout.plusSeconds(1));
            return result.success();
        }

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

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
