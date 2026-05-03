package com.example.guardian.model;

/**
 * Represents the latest known health state of a monitored service.
 */
public enum ServiceHealthStatus {
    /**
     * No runtime information is available yet.
     */
    UNKNOWN,

    /**
     * The process is running and the optional health-check passed.
     */
    UP,

    /**
     * The service is unhealthy and may require recovery.
     */
    DOWN,

    /**
     * Automatic monitoring is intentionally disabled.
     */
    PAUSED,

    /**
     * A restart command has just been triggered.
     */
    RESTARTING,

    /**
     * Monitoring failed because of an internal or infrastructure error.
     */
    ERROR
}
