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
}
