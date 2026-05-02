package com.example.guardian;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка входа Spring Boot приложения {@code host-guardian}.
 *
 * <p>Этот класс поднимает Spring-контекст, регистрирует все компоненты мониторинга
 * и включает поддержку планировщика Spring через {@link EnableScheduling}.
 * После старта приложения периодический мониторинг сервисов выполняется автоматически
 * согласно настройкам из {@code application.yml}.
 */
@SpringBootApplication
@EnableScheduling
public class HostGuardianApplication {

    /**
     * Запускает приложение и инициализирует Spring Boot runtime.
     *
     * @param args аргументы командной строки, переданные JVM при старте приложения
     */
    public static void main(String[] args) {
        SpringApplication.run(HostGuardianApplication.class, args);
    }
}
