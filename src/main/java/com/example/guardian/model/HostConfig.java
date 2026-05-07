package com.example.guardian.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Конфигурация хоста, на котором могут находиться monitored-сервисы.
 */
@Entity
@Table(name = "host_config")
public class HostConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HostConnectionMode connectionMode = HostConnectionMode.LOCAL;

    @Column(nullable = false)
    private String address = "127.0.0.1";

    @Column(nullable = false)
    private int sshPort = 22;

    private String sshUser;

    @Column(length = 1024)
    private String privateKeyPath;

    @Column(length = 2000)
    private String description;

    /**
     * Returns the persistent host identifier.
     *
     * @return database identifier of the host
     */
    public Long getId() {
        return id;
    }

    /**
     * Sets the persistent host identifier.
     *
     * @param id database identifier of the host
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Returns the unique host display name.
     *
     * @return host name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the unique host display name.
     *
     * @param name host name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the command execution mode for this host.
     *
     * @return local or SSH connection mode
     */
    public HostConnectionMode getConnectionMode() {
        return connectionMode;
    }

    /**
     * Sets the command execution mode for this host.
     *
     * @param connectionMode local or SSH connection mode
     */
    public void setConnectionMode(HostConnectionMode connectionMode) {
        this.connectionMode = connectionMode;
    }

    /**
     * Returns the host network address.
     *
     * @return address used for local or remote access
     */
    public String getAddress() {
        return address;
    }

    /**
     * Sets the host network address.
     *
     * @param address address used for local or remote access
     */
    public void setAddress(String address) {
        this.address = address;
    }

    /**
     * Returns the SSH port configured for remote access.
     *
     * @return SSH port number
     */
    public int getSshPort() {
        return sshPort;
    }

    /**
     * Sets the SSH port configured for remote access.
     *
     * @param sshPort SSH port number
     */
    public void setSshPort(int sshPort) {
        this.sshPort = sshPort;
    }

    /**
     * Returns the SSH username for remote hosts.
     *
     * @return SSH username, or {@code null} for local hosts
     */
    public String getSshUser() {
        return sshUser;
    }

    /**
     * Sets the SSH username for remote hosts.
     *
     * @param sshUser SSH username
     */
    public void setSshUser(String sshUser) {
        this.sshUser = sshUser;
    }

    /**
     * Returns the private key path used for SSH authentication.
     *
     * @return private key path, or {@code null} for local hosts
     */
    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    /**
     * Sets the private key path used for SSH authentication.
     *
     * @param privateKeyPath private key path
     */
    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    /**
     * Returns the optional host description.
     *
     * @return host description, or {@code null} when absent
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the optional host description.
     *
     * @param description host description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Checks whether commands should run on the watcher machine.
     *
     * @return {@code true} when this host uses local command execution
     */
    public boolean isLocal() {
        return connectionMode == HostConnectionMode.LOCAL;
    }
}
