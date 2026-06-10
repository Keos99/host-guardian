package com.example.guardian.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

    @Column(length = 1024)
    private String executionPath;

    @Column(nullable = false, length = 4096)
    private String restartCommand = "";

    @Column(nullable = false, length = 4096)
    private String startCommand;

    @Column(nullable = false)
    private boolean manualRestartEnabled;

    private Long lastKnownPid;

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

    @Column(nullable = false)
    private boolean notificationsEnabled = true;

    @Column(length = 2000)
    private String description;

    /**
     * Returns the persistent service identifier.
     *
     * @return database identifier of the monitored service
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the persistent service identifier.
     *
     * @param id database identifier of the monitored service
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the unique service display name.
     *
     * @return service name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the unique service display name.
     *
     * @param name service name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the host that owns this monitored service.
     *
     * @return configured host
     */
    public HostConfig getHost() {
        return host;
    }

    /**
     * Sets the host that owns this monitored service.
     *
     * @param host configured host
     */
    public void setHost(HostConfig host) {
        this.host = host;
    }

    /**
     * Returns the optional logical group for dashboard filtering.
     *
     * @return service group, or {@code null} when ungrouped
     */
    public ServiceGroup getGroup() {
        return group;
    }

    /**
     * Sets the optional logical group for dashboard filtering.
     *
     * @param group service group, or {@code null} when ungrouped
     */
    public void setGroup(ServiceGroup group) {
        this.group = group;
    }

    /**
     * Returns the process lookup pattern.
     *
     * @return pattern passed to {@code pgrep -af}
     */
    public String getProcessMatch() {
        return processMatch;
    }

    /**
     * Sets the process lookup pattern.
     *
     * @param processMatch pattern passed to {@code pgrep -af}
     */
    public void setProcessMatch(String processMatch) {
        this.processMatch = processMatch;
    }

    public String getExecutionPath() {
        return executionPath;
    }

    public void setExecutionPath(String executionPath) {
        this.executionPath = executionPath;
    }

    /**
     * Returns the shell command used to restart the service.
     *
     * @return restart command
     */
    public String getRestartCommand() {
        return restartCommand;
    }

    /**
     * Sets the shell command used to restart the service.
     *
     * @param restartCommand restart command
     */
    public void setRestartCommand(String restartCommand) {
        this.restartCommand = restartCommand;
    }

    public String getStartCommand() {
        return startCommand;
    }

    public void setStartCommand(String startCommand) {
        this.startCommand = startCommand;
    }

    public boolean isManualRestartEnabled() {
        return manualRestartEnabled;
    }

    public void setManualRestartEnabled(boolean manualRestartEnabled) {
        this.manualRestartEnabled = manualRestartEnabled;
    }

    public Long getLastKnownPid() {
        return lastKnownPid;
    }

    public void setLastKnownPid(Long lastKnownPid) {
        this.lastKnownPid = lastKnownPid;
    }

    /**
     * Returns the optional HTTP health-check URL.
     *
     * @return health-check URL, or {@code null} when disabled
     */
    public String getHealthUrl() {
        return healthUrl;
    }

    /**
     * Sets the optional HTTP health-check URL.
     *
     * @param healthUrl health-check URL
     */
    public void setHealthUrl(String healthUrl) {
        this.healthUrl = healthUrl;
    }

    /**
     * Returns the configured health-check timeout in seconds.
     *
     * @return timeout in seconds
     */
    public long getHealthTimeoutSeconds() {
        return healthTimeoutSeconds;
    }

    /**
     * Sets the configured health-check timeout in seconds.
     *
     * @param healthTimeoutSeconds timeout in seconds
     */
    public void setHealthTimeoutSeconds(long healthTimeoutSeconds) {
        this.healthTimeoutSeconds = healthTimeoutSeconds;
    }

    /**
     * Returns the cooldown between restart attempts in seconds.
     *
     * @return restart cooldown in seconds
     */
    public long getRestartCooldownSeconds() {
        return restartCooldownSeconds;
    }

    /**
     * Sets the cooldown between restart attempts in seconds.
     *
     * @param restartCooldownSeconds restart cooldown in seconds
     */
    public void setRestartCooldownSeconds(long restartCooldownSeconds) {
        this.restartCooldownSeconds = restartCooldownSeconds;
    }

    /**
     * Returns the rolling restart window size in seconds.
     *
     * @return restart window in seconds
     */
    public long getRestartWindowSeconds() {
        return restartWindowSeconds;
    }

    /**
     * Sets the rolling restart window size in seconds.
     *
     * @param restartWindowSeconds restart window in seconds
     */
    public void setRestartWindowSeconds(long restartWindowSeconds) {
        this.restartWindowSeconds = restartWindowSeconds;
    }

    /**
     * Returns the maximum number of restarts allowed in the rolling window.
     *
     * @return restart limit for the configured window
     */
    public int getMaxRestartsInWindow() {
        return maxRestartsInWindow;
    }

    /**
     * Sets the maximum number of restarts allowed in the rolling window.
     *
     * @param maxRestartsInWindow restart limit for the configured window
     */
    public void setMaxRestartsInWindow(int maxRestartsInWindow) {
        this.maxRestartsInWindow = maxRestartsInWindow;
    }

    /**
     * Checks whether automatic monitoring is enabled.
     *
     * @return {@code true} when scheduled checks should include the service
     */
    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }

    /**
     * Sets whether automatic monitoring is enabled.
     *
     * @param monitoringEnabled {@code true} to include the service in scheduled checks
     */
    public void setMonitoringEnabled(boolean monitoringEnabled) {
        this.monitoringEnabled = monitoringEnabled;
    }

    /**
     * Checks whether chat notifications are enabled for this service.
     *
     * @return {@code true} when events of this service may be sent to the chat
     */
    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    /**
     * Sets whether chat notifications are enabled for this service.
     *
     * @param notificationsEnabled {@code true} to send events of this service to the chat
     */
    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    /**
     * Returns the optional service description.
     *
     * @return service description, or {@code null} when absent
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the optional service description.
     *
     * @param description service description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the configured health-check timeout as a {@link Duration}.
     *
     * @return health-check timeout duration
     */
    public Duration getHealthTimeout() {
        return Duration.ofSeconds(healthTimeoutSeconds);
    }

    /**
     * Returns the restart cooldown as a {@link Duration}.
     *
     * @return restart cooldown duration
     */
    public Duration getRestartCooldown() {
        return Duration.ofSeconds(restartCooldownSeconds);
    }

    /**
     * Returns the rolling restart window as a {@link Duration}.
     *
     * @return restart window duration
     */
    public Duration getRestartWindow() {
        return Duration.ofSeconds(restartWindowSeconds);
    }
}
