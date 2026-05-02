package com.example.guardian.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Корневой бин конфигурации мониторинга, заполняемый из секции {@code monitor}
 * в {@code application.yml}.
 *
 * <p>Класс хранит глобальные параметры мониторинга, например интервал выполнения
 * проверок, а также список описаний сервисов, за которыми нужно следить.
 * Используется как единый источник настроек для планировщика и бизнес-логики
 * мониторинга.
 *
 * <p>Для конфигурации включена валидация, поэтому некорректные или неполные
 * значения будут обнаружены на этапе старта приложения, а не в момент первой
 * проверки сервисов.
 */
@Component
@Validated
@ConfigurationProperties(prefix = "monitor")
public class MonitorProperties {

    private Duration interval = Duration.ofSeconds(30);

    @Valid
    @NotEmpty
    private List<MonitoredService> services = new ArrayList<>();

    /**
     * Возвращает интервал между последовательными циклами мониторинга.
     *
     * @return длительность паузы между проверками всех сервисов
     */
    public Duration getInterval() {
        return interval;
    }

    /**
     * Устанавливает интервал между циклами мониторинга.
     *
     * @param interval новый интервал проверки
     */
    public void setInterval(Duration interval) {
        this.interval = interval;
    }

    /**
     * Возвращает список сервисов, описанных в конфигурации.
     *
     * @return список сервисов для мониторинга
     */
    public List<MonitoredService> getServices() {
        return services;
    }

    /**
     * Заменяет полный список сервисов, загруженных из конфигурации.
     *
     * @param services новый список сервисов
     */
    public void setServices(List<MonitoredService> services) {
        this.services = services;
    }

    /**
     * Описание одного отслеживаемого сервиса.
     *
     * <p>Экземпляр этого класса задает все данные, необходимые для контроля
     * конкретного приложения: как его найти в списке процессов, как проверить
     * здоровье по HTTP, и как безопасно запускать повторный старт при сбое.
     * Объекты создаются автоматически из YAML-конфигурации.
     */
    public static class MonitoredService {
        @NotBlank
        private String name;

        @NotBlank
        private String processMatch;

        @NotBlank
        private String restartCommand;

        private String healthUrl;

        private Duration healthTimeout = Duration.ofSeconds(3);

        private Duration restartCooldown = Duration.ofMinutes(1);

        private int maxRestartsInWindow = 3;

        private Duration restartWindow = Duration.ofMinutes(10);

        /**
         * Возвращает логическое имя сервиса.
         *
         * <p>Это имя используется в логах и как ключ состояния в памяти.
         *
         * @return имя сервиса
         */
        public String getName() {
            return name;
        }

        /**
         * Устанавливает логическое имя сервиса.
         *
         * @param name имя сервиса
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Возвращает шаблон поиска процесса в командной строке.
         *
         * @return строка, по которой сервис ищется через {@code pgrep -af}
         */
        public String getProcessMatch() {
            return processMatch;
        }

        /**
         * Устанавливает шаблон поиска процесса.
         *
         * @param processMatch строка или подстрока для поиска процесса
         */
        public void setProcessMatch(String processMatch) {
            this.processMatch = processMatch;
        }

        /**
         * Возвращает shell-команду, выполняемую для старта или рестарта сервиса.
         *
         * @return команда рестарта
         */
        public String getRestartCommand() {
            return restartCommand;
        }

        /**
         * Устанавливает shell-команду рестарта.
         *
         * @param restartCommand команда, которая должна поднять сервис
         */
        public void setRestartCommand(String restartCommand) {
            this.restartCommand = restartCommand;
        }

        /**
         * Возвращает URL health-check endpoint, если он настроен.
         *
         * @return URL HTTP-проверки или {@code null}, если проверка не нужна
         */
        public String getHealthUrl() {
            return healthUrl;
        }

        /**
         * Устанавливает URL health endpoint.
         *
         * @param healthUrl адрес HTTP-проверки
         */
        public void setHealthUrl(String healthUrl) {
            this.healthUrl = healthUrl;
        }

        /**
         * Возвращает таймаут HTTP health-check.
         *
         * @return максимальная длительность ожидания ответа
         */
        public Duration getHealthTimeout() {
            return healthTimeout;
        }

        /**
         * Устанавливает таймаут HTTP health-check.
         *
         * @param healthTimeout длительность ожидания ответа
         */
        public void setHealthTimeout(Duration healthTimeout) {
            this.healthTimeout = healthTimeout;
        }

        /**
         * Возвращает минимальную паузу между двумя рестартами одного сервиса.
         *
         * @return значение cooldown
         */
        public Duration getRestartCooldown() {
            return restartCooldown;
        }

        /**
         * Устанавливает минимальную паузу между рестартами.
         *
         * @param restartCooldown значение cooldown
         */
        public void setRestartCooldown(Duration restartCooldown) {
            this.restartCooldown = restartCooldown;
        }

        /**
         * Возвращает максимально допустимое число рестартов в одном окне.
         *
         * @return лимит рестартов
         */
        public int getMaxRestartsInWindow() {
            return maxRestartsInWindow;
        }

        /**
         * Устанавливает максимально допустимое число рестартов в окне.
         *
         * @param maxRestartsInWindow лимит рестартов
         */
        public void setMaxRestartsInWindow(int maxRestartsInWindow) {
            this.maxRestartsInWindow = maxRestartsInWindow;
        }

        /**
         * Возвращает размер временного окна для ограничения частоты рестартов.
         *
         * @return длительность окна
         */
        public Duration getRestartWindow() {
            return restartWindow;
        }

        /**
         * Устанавливает размер временного окна, внутри которого считается число рестартов.
         *
         * @param restartWindow длительность окна
         */
        public void setRestartWindow(Duration restartWindow) {
            this.restartWindow = restartWindow;
        }
    }
}
