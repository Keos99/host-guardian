package com.example.guardian.service;

import com.example.guardian.model.HostConfig;
import com.example.guardian.model.MonitoredService;
import com.example.guardian.config.MonitorProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Компонент для HTTP-проверки доступности и базового здоровья сервиса.
 *
 * <p>Проверка считается успешной, если указанный endpoint отвечает кодом
 * семейства {@code 2xx}. Компонент намеренно не анализирует тело ответа,
 * а только подтверждает сетевую доступность и успешный HTTP-статус, чего
 * достаточно для простого мониторинга приложений с endpoint вроде
 * {@code /actuator/health}.
 *
 * <p>Для удаленных хостов проверки всех сервисов хоста выполняются одним
 * скриптом с параллельными вызовами {@code curl}: это одно SSH-соединение на
 * хост вместо отдельного вызова на каждый сервис, а параллельный запуск
 * ограничивает общее время самым медленным endpoint, а не их суммой.
 */
@Component
public class HttpHealthChecker {

    private final RestTemplateBuilder restTemplateBuilder;
    private final HostShellExecutor hostShellExecutor;
    private final MonitorProperties monitorProperties;

    /**
     * Создает компонент health-check на основе {@link RestTemplateBuilder}.
     *
     * @param restTemplateBuilder фабрика для построения {@link RestTemplate}
     *                            с нужными сетевыми таймаутами
     * @param hostShellExecutor исполнитель shell-команд на локальном или удаленном хосте
     * @param monitorProperties глобальные настройки таймаутов мониторинга
     */
    public HttpHealthChecker(RestTemplateBuilder restTemplateBuilder,
                             HostShellExecutor hostShellExecutor,
                             MonitorProperties monitorProperties) {
        this.restTemplateBuilder = restTemplateBuilder;
        this.hostShellExecutor = hostShellExecutor;
        this.monitorProperties = monitorProperties;
    }

    /**
     * Проверяет здоровье сразу всех сервисов одного хоста.
     *
     * <p>Для локального хоста используется обычный HTTP-клиент JVM по каждому
     * сервису. Для удаленного хоста собирается единый скрипт, который параллельно
     * опрашивает все endpoints через {@code curl} на самом целевом хосте и
     * возвращает по строке {@code "<id> <0|1>"} на сервис.
     *
     * @param host хост, на котором расположены сервисы
     * @param services сервисы этого хоста, у которых задан health URL
     * @return карта «идентификатор сервиса → результат health-check»
     */
    public Map<Long, Boolean> batchHealthy(HostConfig host, List<MonitoredService> services) {
        Map<Long, Boolean> results = new HashMap<>();
        if (services.isEmpty()) {
            return results;
        }

        if (host.isLocal()) {
            for (MonitoredService service : services) {
                results.put(service.getId(), localHealthy(service.getHealthUrl(), service.getHealthTimeout()));
            }
            return results;
        }

        return remoteHealthy(host, services);
    }

    /**
     * Runs all remote health checks for a host in one parallel {@code curl} script.
     *
     * @param host remote host
     * @param services services with a configured health URL
     * @return map of service identifier to health-check result
     */
    private Map<Long, Boolean> remoteHealthy(HostConfig host, List<MonitoredService> services) {
        Map<Long, Boolean> results = new HashMap<>();
        services.forEach(service -> results.put(service.getId(), false));

        long maxTimeoutSeconds = 1;
        StringBuilder script = new StringBuilder();
        script.append("__hg_check() { if curl -fsS --max-time \"$2\" \"$3\" >/dev/null 2>&1; "
                + "then echo \"$1 1\"; else echo \"$1 0\"; fi; }\n");
        for (MonitoredService service : services) {
            long timeoutSeconds = Math.max(1, service.getHealthTimeout().toSeconds());
            maxTimeoutSeconds = Math.max(maxTimeoutSeconds, timeoutSeconds);
            script.append("__hg_check ")
                    .append(shellQuote(String.valueOf(service.getId()))).append(' ')
                    .append(shellQuote(String.valueOf(timeoutSeconds))).append(' ')
                    .append(shellQuote(service.getHealthUrl())).append(" &\n");
        }
        script.append("wait\n");

        Duration commandTimeout = Duration.ofSeconds(maxTimeoutSeconds)
                .plus(monitorProperties.getCommand().getHealthCommandExtraTimeout());
        CommandExecutor.CommandResult result = hostShellExecutor.execute(host, script.toString(), commandTimeout);
        if (!result.success() || result.output() == null) {
            return results;
        }

        result.output().lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .forEach(line -> parseHealthLine(line, results));
        return results;
    }

    /**
     * Parses one {@code "<id> <0|1>"} result line into the results map.
     *
     * @param line raw output line from the health script
     * @param results map updated in place with the parsed result
     */
    private void parseHealthLine(String line, Map<Long, Boolean> results) {
        String[] parts = line.split("\\s+");
        if (parts.length < 2) {
            return;
        }
        try {
            results.put(Long.parseLong(parts[0]), "1".equals(parts[1]));
        } catch (NumberFormatException e) {
            // Ignore unexpected lines without failing the whole batch.
        }
    }

    /**
     * Performs a local HTTP health check from the watcher JVM.
     *
     * @param url health endpoint URL
     * @param timeout connect and read timeout
     * @return {@code true} when the endpoint answered with a 2xx status
     */
    private boolean localHealthy(String url, Duration timeout) {
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

    /**
     * Quotes a value so it can be passed as a single shell argument.
     *
     * @param value raw shell argument value
     * @return safely single-quoted shell argument
     */
    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
