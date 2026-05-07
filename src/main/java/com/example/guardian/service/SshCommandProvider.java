package com.example.guardian.service;

import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;

import java.time.Duration;

public interface SshCommandProvider {

    MonitorProperties.Ssh.Provider provider();

    CommandExecutor.CommandResult execute(HostConfig host, String shellCommand, Duration timeout);
}
