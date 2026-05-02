package com.example.guardian.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Duration;

/**
 * Конфигурация одного monitored-сервиса, хранящаяся в базе данных.
 */
@Entity
@Table(name = "monitored_service")
public class MonitoredService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private HostConfig host;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ServiceGroup group;

    @Column(nullable = false)
    private String processMatch;

    @Lob
    @Column(nullable = false)
    private String restartCommand;

    private String healthUrl;

    @Column(nullable = false)
    private long healthTimeoutSeconds = 3;

    @Column(nullable = false)
    private long restartCooldownSeconds = 60;

    @Column(nullable = false)
    private long restartWindowSeconds = 600;

    @Column(nullable = false)
    private int maxRestartsInWindow = 3;

    @Column(nullable = false)
    private boolean monitoringEnabled = true;

    @Column(length = 2000)
    private String description;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public HostConfig getHost() {
        return host;
    }

    public void setHost(HostConfig host) {
        this.host = host;
    }

    public ServiceGroup getGroup() {
        return group;
    }

    public void setGroup(ServiceGroup group) {
        this.group = group;
    }

    public String getProcessMatch() {
        return processMatch;
    }

    public void setProcessMatch(String processMatch) {
        this.processMatch = processMatch;
    }

    public String getRestartCommand() {
        return restartCommand;
    }

    public void setRestartCommand(String restartCommand) {
        this.restartCommand = restartCommand;
    }

    public String getHealthUrl() {
        return healthUrl;
    }

    public void setHealthUrl(String healthUrl) {
        this.healthUrl = healthUrl;
    }

    public long getHealthTimeoutSeconds() {
        return healthTimeoutSeconds;
    }

    public void setHealthTimeoutSeconds(long healthTimeoutSeconds) {
        this.healthTimeoutSeconds = healthTimeoutSeconds;
    }

    public long getRestartCooldownSeconds() {
        return restartCooldownSeconds;
    }

    public void setRestartCooldownSeconds(long restartCooldownSeconds) {
        this.restartCooldownSeconds = restartCooldownSeconds;
    }

    public long getRestartWindowSeconds() {
        return restartWindowSeconds;
    }

    public void setRestartWindowSeconds(long restartWindowSeconds) {
        this.restartWindowSeconds = restartWindowSeconds;
    }

    public int getMaxRestartsInWindow() {
        return maxRestartsInWindow;
    }

    public void setMaxRestartsInWindow(int maxRestartsInWindow) {
        this.maxRestartsInWindow = maxRestartsInWindow;
    }

    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    public void setMonitoringEnabled(boolean monitoringEnabled) {
        this.monitoringEnabled = monitoringEnabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Duration getHealthTimeout() {
        return Duration.ofSeconds(healthTimeoutSeconds);
    }

    public Duration getRestartCooldown() {
        return Duration.ofSeconds(restartCooldownSeconds);
    }

    public Duration getRestartWindow() {
        return Duration.ofSeconds(restartWindowSeconds);
    }
}
