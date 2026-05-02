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

    public HostConnectionMode getConnectionMode() {
        return connectionMode;
    }

    public void setConnectionMode(HostConnectionMode connectionMode) {
        this.connectionMode = connectionMode;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public int getSshPort() {
        return sshPort;
    }

    public void setSshPort(int sshPort) {
        this.sshPort = sshPort;
    }

    public String getSshUser() {
        return sshUser;
    }

    public void setSshUser(String sshUser) {
        this.sshUser = sshUser;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
        this.privateKeyPath = privateKeyPath;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isLocal() {
        return connectionMode == HostConnectionMode.LOCAL;
    }
}
