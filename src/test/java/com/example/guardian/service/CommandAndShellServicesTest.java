package com.example.guardian.service;

import com.example.guardian.TestFixtures;
import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandAndShellServicesTest {

    @Test
    void commandResultSuccessReflectsExitCode() {
        assertThat(new CommandExecutor.CommandResult(0, "ok", null).success()).isTrue();
        assertThat(new CommandExecutor.CommandResult(1, "", "failed").success()).isFalse();
    }

    @Test
    void commandExecutorRunsSuccessfulProcessAndCapturesOutput() {
        String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                isWindows() ? "java.exe" : "java"
        ).toString();
        CommandExecutor executor = new CommandExecutor();

        CommandExecutor.CommandResult result = executor.execute(List.of(javaExecutable, "-version"), Duration.ofSeconds(10));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).containsIgnoringCase("version");
        assertThat(result.error()).isNull();
        assertThat(result.success()).isTrue();
    }

    @Test
    void commandExecutorReturnsErrorWhenProcessCannotStart() {
        CommandExecutor executor = new CommandExecutor();

        CommandExecutor.CommandResult result = executor.execute(List.of("definitely-not-a-real-command"), Duration.ofSeconds(1));

        assertThat(result.exitCode()).isEqualTo(-1);
        assertThat(result.output()).isEmpty();
        assertThat(result.error()).isNotBlank();
        assertThat(result.success()).isFalse();
    }

    @Test
    void hostShellExecutorBuildsLocalCommand() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        SshCommandProvider sshProvider = mock(SshCommandProvider.class);
        HostShellExecutor shellExecutor = new HostShellExecutor(commandExecutor, List.of(sshProvider), monitorProperties());
        HostConfig host = TestFixtures.localHost(1);
        Duration timeout = Duration.ofSeconds(5);
        CommandExecutor.CommandResult result = new CommandExecutor.CommandResult(0, "ok", null);
        when(commandExecutor.execute(any(), eq(timeout))).thenReturn(result);

        CommandExecutor.CommandResult actual = shellExecutor.execute(host, "echo ok", timeout);

        assertThat(actual).isSameAs(result);
        verify(commandExecutor).execute(List.of("bash", "-lc", "echo ok"), timeout);
        verify(sshProvider, never()).execute(any(), any(), any());
    }

    @Test
    void hostShellExecutorDelegatesSshHostToConfiguredProvider() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        SshCommandProvider systemProvider = mock(SshCommandProvider.class);
        SshCommandProvider jschProvider = mock(SshCommandProvider.class);
        MonitorProperties properties = monitorProperties();
        properties.getSsh().setProvider(MonitorProperties.Ssh.Provider.JSCH);
        HostShellExecutor shellExecutor = new HostShellExecutor(commandExecutor, List.of(systemProvider, jschProvider), properties);
        HostConfig host = TestFixtures.sshHost(1);
        Duration timeout = Duration.ofSeconds(5);
        CommandExecutor.CommandResult result = new CommandExecutor.CommandResult(0, "ok", null);
        when(systemProvider.provider()).thenReturn(MonitorProperties.Ssh.Provider.SYSTEM);
        when(jschProvider.provider()).thenReturn(MonitorProperties.Ssh.Provider.JSCH);
        when(jschProvider.execute(host, "uptime", timeout)).thenReturn(result);

        CommandExecutor.CommandResult actual = shellExecutor.execute(host, "uptime", timeout);

        assertThat(actual).isSameAs(result);
        verify(commandExecutor, never()).execute(any(), any());
        verify(systemProvider, never()).execute(any(), any(), any());
        verify(jschProvider).execute(host, "uptime", timeout);
    }

    @Test
    void systemSshCommandProviderBuildsSshCommandWithKeyAndQuoting() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        MonitorProperties properties = monitorProperties();
        properties.getCommand().setSshConnectTimeout(Duration.ofSeconds(9));
        SystemSshCommandProvider sshProvider = new SystemSshCommandProvider(commandExecutor, properties);
        HostConfig host = TestFixtures.sshHost(1);
        Duration timeout = Duration.ofSeconds(5);
        when(commandExecutor.execute(any(), eq(timeout))).thenReturn(new CommandExecutor.CommandResult(0, "ok", null));

        sshProvider.execute(host, "echo 'ok'", timeout);

        ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandExecutor).execute(commandCaptor.capture(), eq(timeout));
        assertThat(commandCaptor.getValue()).containsExactly(
                "ssh",
                "-o",
                "BatchMode=yes",
                "-o",
                "ConnectTimeout=9",
                "-p",
                "2222",
                "-i",
                "/home/deploy/.ssh/id_ed25519",
                "deploy@10.0.0.5",
                "bash -lc 'echo '\"'\"'ok'\"'\"''"
        );
    }

    @Test
    void systemSshCommandProviderOmitsPrivateKeyWhenBlank() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        SystemSshCommandProvider sshProvider = new SystemSshCommandProvider(commandExecutor, monitorProperties());
        HostConfig host = TestFixtures.sshHost(1);
        host.setPrivateKeyPath(" ");
        when(commandExecutor.execute(any(), any())).thenReturn(new CommandExecutor.CommandResult(0, "ok", null));

        sshProvider.execute(host, "uptime", Duration.ofSeconds(5));

        ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandExecutor).execute(commandCaptor.capture(), any());
        assertThat(commandCaptor.getValue()).doesNotContain("-i");
        assertThat(commandCaptor.getValue()).contains("deploy@10.0.0.5");
    }

    @Test
    void jschSshCommandProviderExecutesCommandThroughJschSession() throws Exception {
        JschFacade jschFacade = mock(JschFacade.class);
        MonitorProperties properties = monitorProperties();
        properties.getCommand().setSshConnectTimeout(Duration.ofSeconds(9));
        JschSshCommandProvider provider = new JschSshCommandProvider(jschFacade, properties);
        HostConfig host = TestFixtures.sshHost(1);
        Session session = mock(Session.class);
        ChannelExec channel = mock(ChannelExec.class);
        ByteArrayInputStream stdout = new ByteArrayInputStream("ok\n".getBytes(StandardCharsets.UTF_8));

        when(jschFacade.openSession(host, properties.getSsh(), Duration.ofSeconds(9))).thenReturn(session);
        when(session.openChannel("exec")).thenReturn(channel);
        when(channel.getInputStream()).thenReturn(stdout);
        when(channel.isClosed()).thenReturn(true);
        when(channel.getExitStatus()).thenReturn(0);
        doAnswer(invocation -> {
            OutputStream stderr = invocation.getArgument(0);
            stderr.write("warn\n".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(channel).setErrStream(any());

        CommandExecutor.CommandResult result = provider.execute(host, "echo 'ok'", Duration.ofSeconds(5));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).isEqualTo("ok\n");
        assertThat(result.error()).isEqualTo("warn\n");
        verify(channel).setCommand("bash -lc 'echo '\"'\"'ok'\"'\"''");
        verify(channel).connect(9_000);
        verify(channel).disconnect();
        verify(session).disconnect();
    }

    @Test
    void jschFacadeConfiguresSessionAuthenticationAndHostChecking() throws Exception {
        JSch jsch = mock(JSch.class);
        Session session = mock(Session.class);
        MonitorProperties properties = monitorProperties();
        properties.getSsh().setStrictHostKeyChecking(true);
        JschFacade facade = new JschFacade(() -> jsch);
        HostConfig host = TestFixtures.sshHost(1);
        when(jsch.getSession("deploy", "10.0.0.5", 2222)).thenReturn(session);

        Session actual = facade.openSession(host, properties.getSsh(), Duration.ofSeconds(6));

        assertThat(actual).isSameAs(session);
        verify(jsch).addIdentity("/home/deploy/.ssh/id_ed25519");
        verify(session).setConfig("StrictHostKeyChecking", "yes");
        verify(session).connect(6_000);
    }

    @Test
    void linuxProcessInspectorReturnsTrueOnlyForSuccessfulNonBlankOutput() {
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        LinuxProcessInspector inspector = new LinuxProcessInspector(hostShellExecutor, monitorProperties());
        HostConfig host = TestFixtures.localHost(1);

        when(hostShellExecutor.execute(host, "pgrep -af \"api\\\"service\"", Duration.ofSeconds(5)))
                .thenReturn(new CommandExecutor.CommandResult(0, "123 api", null))
                .thenReturn(new CommandExecutor.CommandResult(0, " ", null))
                .thenReturn(new CommandExecutor.CommandResult(1, "", "missing"));

        assertThat(inspector.isRunning(host, "api\"service")).isTrue();
        assertThat(inspector.isRunning(host, "api\"service")).isFalse();
        assertThat(inspector.isRunning(host, "api\"service")).isFalse();
    }

    @Test
    void linuxProcessInspectorFindsFirstProcessAndChecksPidWithConfiguredTimeouts() {
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        MonitorProperties properties = monitorProperties();
        properties.getCommand().setProcessLookupTimeout(Duration.ofSeconds(7));
        properties.getCommand().setPidCheckTimeout(Duration.ofSeconds(2));
        LinuxProcessInspector inspector = new LinuxProcessInspector(hostShellExecutor, properties);
        HostConfig host = TestFixtures.localHost(1);

        when(hostShellExecutor.execute(host, "pgrep -af \"api.jar\"", Duration.ofSeconds(7)))
                .thenReturn(new CommandExecutor.CommandResult(0, "1234 java -jar api.jar\n5678 grep api.jar", null));
        when(hostShellExecutor.execute(host, "kill -0 1234", Duration.ofSeconds(2)))
                .thenReturn(new CommandExecutor.CommandResult(0, "", null));

        Optional<LinuxProcessInspector.ProcessInfo> process = inspector.findFirst(host, "api.jar");

        assertThat(process).isPresent();
        assertThat(process.orElseThrow().pid()).isEqualTo(1234L);
        assertThat(process.orElseThrow().commandLine()).isEqualTo("java -jar api.jar");
        assertThat(inspector.isPidRunning(host, 1234L)).isTrue();
    }

    @Test
    void linuxProcessInspectorStopsKnownPidThenFallsBackToKillNineAfterTimeout() {
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        MonitorProperties properties = monitorProperties();
        properties.getCommand().setStopCommandExtraTimeout(Duration.ofSeconds(3));
        LinuxProcessInspector inspector = new LinuxProcessInspector(hostShellExecutor, properties);
        HostConfig host = TestFixtures.localHost(1);
        Duration timeout = Duration.ofSeconds(4);
        Duration commandTimeout = Duration.ofSeconds(7);

        when(hostShellExecutor.execute(eq(host), any(), eq(commandTimeout)))
                .thenReturn(new CommandExecutor.CommandResult(0, "stopped", null));

        CommandExecutor.CommandResult result = inspector.stop(host, "api.jar", 1234L, timeout);

        assertThat(result.success()).isTrue();
        ArgumentCaptor<String> commandCaptor = ArgumentCaptor.forClass(String.class);
        verify(hostShellExecutor).execute(eq(host), commandCaptor.capture(), eq(commandTimeout));
        assertThat(commandCaptor.getValue()).contains("pids=$(pgrep -f 'api.jar' || true)");
        assertThat(commandCaptor.getValue()).contains("known_pid='1234'");
        assertThat(commandCaptor.getValue()).contains("ps -p \"$known_pid\" -o args=");
        assertThat(commandCaptor.getValue()).contains("kill $pids");
        assertThat(commandCaptor.getValue()).contains("kill -9 $alive");
    }

    @Test
    void httpHealthCheckerUsesRemoteCurlOnSshHost() {
        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        HttpHealthChecker checker = new HttpHealthChecker(restTemplateBuilder, hostShellExecutor, monitorProperties());
        HostConfig host = TestFixtures.sshHost(1);
        Duration timeout = Duration.ofSeconds(3);
        when(hostShellExecutor.execute(host,
                "curl -fsS --max-time 3 'http://127.0.0.1:8080/health' > /dev/null",
                timeout.plusSeconds(1)))
                .thenReturn(new CommandExecutor.CommandResult(0, "", null));

        assertThat(checker.isHealthy(host, "http://127.0.0.1:8080/health", timeout)).isTrue();
    }

    @Test
    void httpHealthCheckerUsesAtLeastOneSecondForRemoteCurl() {
        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        MonitorProperties properties = monitorProperties();
        properties.getCommand().setHealthCommandExtraTimeout(Duration.ofSeconds(3));
        HttpHealthChecker checker = new HttpHealthChecker(restTemplateBuilder, hostShellExecutor, properties);
        HostConfig host = TestFixtures.sshHost(1);
        Duration timeout = Duration.ZERO;
        when(hostShellExecutor.execute(host,
                "curl -fsS --max-time 1 'http://service/health' > /dev/null",
                timeout.plusSeconds(3)))
                .thenReturn(new CommandExecutor.CommandResult(1, "", "failed"));

        assertThat(checker.isHealthy(host, "http://service/health", timeout)).isFalse();
    }

    @Test
    void httpHealthCheckerUsesRestTemplateForLocalHost() {
        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        HttpHealthChecker checker = new HttpHealthChecker(restTemplateBuilder, hostShellExecutor, monitorProperties());
        HostConfig host = TestFixtures.localHost(1);
        Duration timeout = Duration.ofSeconds(2);

        when(restTemplateBuilder.setConnectTimeout(timeout)).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(timeout)).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        when(restTemplate.getForEntity("http://localhost/health", String.class)).thenReturn(ResponseEntity.ok("ok"));

        assertThat(checker.isHealthy(host, "http://localhost/health", timeout)).isTrue();
    }

    @Test
    void httpHealthCheckerReturnsFalseWhenLocalRequestFails() {
        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        HttpHealthChecker checker = new HttpHealthChecker(restTemplateBuilder, hostShellExecutor, monitorProperties());
        HostConfig host = TestFixtures.localHost(1);
        Duration timeout = Duration.ofSeconds(2);

        when(restTemplateBuilder.setConnectTimeout(timeout)).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(timeout)).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        when(restTemplate.getForEntity("http://localhost/health", String.class)).thenThrow(new IllegalStateException("down"));

        assertThat(checker.isHealthy(host, "http://localhost/health", timeout)).isFalse();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private MonitorProperties monitorProperties() {
        return new MonitorProperties();
    }
}
