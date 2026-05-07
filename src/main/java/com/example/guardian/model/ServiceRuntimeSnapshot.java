package com.example.guardian.model;

import java.time.Instant;

/**
 * Неизменяемый снимок runtime-состояния сервиса для REST API и UI.
 *
 * @param status расчетный статус сервиса
 * @param processRunning найден ли процесс
 * @param healthCheckPassed прошел ли health-check
 * @param lastMessage поясняющее сообщение
 * @param lastCheckAt время последней проверки
 * @param lastRestartAt время последнего успешного рестарта
 */
public record ServiceRuntimeSnapshot(
        ServiceHealthStatus status,
        boolean processRunning,
        boolean healthCheckPassed,
        String lastMessage,
        Instant lastCheckAt,
        Instant lastRestartAt
) {
}
