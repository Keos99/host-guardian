package com.example.guardian.model;

/**
 * Последний известный статус monitored-сервиса.
 */
public enum ServiceHealthStatus {
    UNKNOWN,
    UP,
    DOWN,
    PAUSED,
    RESTARTING,
    ERROR
}
