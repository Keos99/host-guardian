package com.example.guardian.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Корневой бин конфигурации мониторинга, заполняемый из секции {@code monitor}
 * в {@code application.yml}.
 *
 * <p>После переноса конфигурации сервисов в базу данных этот класс хранит только
 * глобальные параметры приложения, например интервал между циклами фонового
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
    private Command command = new Command();

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

    public Command getCommand() {
        return command;
    }

    public void setCommand(Command command) {
        this.command = command;
    }

    public static class Command {

        private Duration processLookupTimeout = Duration.ofSeconds(5);
        private Duration pidCheckTimeout = Duration.ofSeconds(3);
        private Duration stopTimeout = Duration.ofSeconds(10);
        private Duration stopCommandExtraTimeout = Duration.ofSeconds(2);
        private Duration restartTimeout = Duration.ofSeconds(20);
        private Duration startTimeout = Duration.ofSeconds(20);
        private Duration healthCommandExtraTimeout = Duration.ofSeconds(1);
        private Duration sshConnectTimeout = Duration.ofSeconds(5);

        public Duration getProcessLookupTimeout() {
            return processLookupTimeout;
        }

        public void setProcessLookupTimeout(Duration processLookupTimeout) {
            this.processLookupTimeout = processLookupTimeout;
        }

        public Duration getPidCheckTimeout() {
            return pidCheckTimeout;
        }

        public void setPidCheckTimeout(Duration pidCheckTimeout) {
            this.pidCheckTimeout = pidCheckTimeout;
        }

        public Duration getStopTimeout() {
            return stopTimeout;
        }

        public void setStopTimeout(Duration stopTimeout) {
            this.stopTimeout = stopTimeout;
        }

        public Duration getStopCommandExtraTimeout() {
            return stopCommandExtraTimeout;
        }

        public void setStopCommandExtraTimeout(Duration stopCommandExtraTimeout) {
            this.stopCommandExtraTimeout = stopCommandExtraTimeout;
        }

        public Duration getRestartTimeout() {
            return restartTimeout;
        }

        public void setRestartTimeout(Duration restartTimeout) {
            this.restartTimeout = restartTimeout;
        }

        public Duration getStartTimeout() {
            return startTimeout;
        }

        public void setStartTimeout(Duration startTimeout) {
            this.startTimeout = startTimeout;
        }

        public Duration getHealthCommandExtraTimeout() {
            return healthCommandExtraTimeout;
        }

        public void setHealthCommandExtraTimeout(Duration healthCommandExtraTimeout) {
            this.healthCommandExtraTimeout = healthCommandExtraTimeout;
        }

        public Duration getSshConnectTimeout() {
            return sshConnectTimeout;
        }

        public void setSshConnectTimeout(Duration sshConnectTimeout) {
            this.sshConnectTimeout = sshConnectTimeout;
        }
    }
}
