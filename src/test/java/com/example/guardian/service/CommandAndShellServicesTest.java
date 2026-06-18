package com.example.guardian.service;

import com.example.guardian.TestFixtures;
import com.example.guardian.config.MonitorProperties;
import com.example.guardian.model.HostConfig;
import com.example.guardian.model.MonitoredService;
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
import java.util.Map;
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
    void jschSshCommandProviderRunsCommandOnPooledSessionWithoutClosingIt() throws Exception {
        JschSessionPool sessionPool = mock(JschSessionPool.class);
        MonitorProperties properties = monitorProperties();
        properties.getCommand().setSshConnectTimeout(Duration.ofSeconds(9));
        JschSshCommandProvider provider = new JschSshCommandProvider(sessionPool, properties);
        HostConfig host = TestFixtures.sshHost(1);
        Session session = mock(Session.class);
        ChannelExec channel = mock(ChannelExec.class);
        ByteArrayInputStream stdout = new ByteArrayInputStream("ok\n".getBytes(StandardCharsets.UTF_8));

        when(sessionPool.withSession(eq(host), any())).thenAnswer(invocation -> {
            JschSessionPool.SessionWork<?> work = invocation.getArgument(1);
            return work.apply(session);
        });
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
        verify(session, never()).disconnect();
    }

    @Test
    void jschFacadeConfiguresSessionAuthenticationKeepAliveAndHostChecking() throws Exception {
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
        verify(session).setServerAliveInterval(15_000);
        verify(session).connect(6_000);
    }

    @Test
    void linuxProcessInspectorSnapshotIsUnavailableForFailedOrBlankOutput() {
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        LinuxProcessInspector inspector = new LinuxProcessInspector(hostShellExecutor, monitorProperties());
        HostConfig host = TestFixtures.localHost(1);

        when(hostShellExecutor.execute(host, "ps -ww -eo pid=,args=", Duration.ofSeconds(5)))
                .thenReturn(new CommandExecutor.CommandResult(0, "123 api", null))
                .thenReturn(new CommandExecutor.CommandResult(0, " ", null))
                .thenReturn(new CommandExecutor.CommandResult(1, "", "missing"));

        assertThat(inspector.snapshot(host).findFirst("api")).isPresent();
        assertThat(inspector.snapshot(host).isAvailable()).isFalse();
        assertThat(inspector.snapshot(host).isAvailable()).isFalse();
    }

    @Test
    void linuxProcessInspectorSnapshotParsesProcessesWithConfiguredTimeout() {
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        MonitorProperties properties = monitorProperties();
        properties.getCommand().setProcessLookupTimeout(Duration.ofSeconds(7));
        LinuxProcessInspector inspector = new LinuxProcessInspector(hostShellExecutor, properties);
        HostConfig host = TestFixtures.localHost(1);

        when(hostShellExecutor.execute(host, "ps -ww -eo pid=,args=", Duration.ofSeconds(7)))
                .thenReturn(new CommandExecutor.CommandResult(0, "1234 java -jar api.jar\n5678 java -jar other.jar", null));

        ProcessSnapshot snapshot = inspector.snapshot(host);
        Optional<LinuxProcessInspector.ProcessInfo> process = snapshot.findFirst("api\\.jar");

        assertThat(snapshot.isAvailable()).isTrue();
        assertThat(process).isPresent();
        assertThat(process.orElseThrow().pid()).isEqualTo(1234L);
        assertThat(process.orElseThrow().commandLine()).isEqualTo("java -jar api.jar");
        assertThat(snapshot.findFirst("nonexistent")).isEmpty();
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
    void httpHealthCheckerBatchesRemoteChecksIntoOneScript() {
        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        HttpHealthChecker checker = new HttpHealthChecker(restTemplateBuilder, hostShellExecutor, monitorProperties());
        HostConfig host = TestFixtures.sshHost(1);
        MonitoredService first = healthService(101, "http://127.0.0.1:8080/health", 3);
        MonitoredService second = healthService(102, "http://127.0.0.1:9090/health", 5);

        ArgumentCaptor<String> scriptCaptor = ArgumentCaptor.forClass(String.class);
        when(hostShellExecutor.execute(eq(host), scriptCaptor.capture(), eq(Duration.ofSeconds(6))))
                .thenReturn(new CommandExecutor.CommandResult(0, "101 1\n102 0", null));

        Map<Long, Boolean> result = checker.batchHealthy(host, List.of(first, second));

        assertThat(result).containsEntry(101L, true).containsEntry(102L, false);
        assertThat(scriptCaptor.getValue())
                .contains("curl -fsS --max-time")
                .contains("__hg_check '101' '3' 'http://127.0.0.1:8080/health' &")
                .contains("__hg_check '102' '5' 'http://127.0.0.1:9090/health' &")
                .contains("wait");
    }

    @Test
    void httpHealthCheckerReturnsAllFalseWhenRemoteScriptFails() {
        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        HttpHealthChecker checker = new HttpHealthChecker(restTemplateBuilder, hostShellExecutor, monitorProperties());
        HostConfig host = TestFixtures.sshHost(1);
        when(hostShellExecutor.execute(eq(host), any(), any()))
                .thenReturn(new CommandExecutor.CommandResult(1, "", "failed"));

        Map<Long, Boolean> result = checker.batchHealthy(host, List.of(healthService(101, "http://service/health", 3)));

        assertThat(result).containsEntry(101L, false);
    }

    @Test
    void httpHealthCheckerBatchesLocalChecksWithRestTemplate() {
        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        HttpHealthChecker checker = new HttpHealthChecker(restTemplateBuilder, hostShellExecutor, monitorProperties());
        HostConfig host = TestFixtures.localHost(1);

        when(restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(2))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(Duration.ofSeconds(2))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        when(restTemplate.getForEntity("http://localhost/health", String.class)).thenReturn(ResponseEntity.ok("ok"));

        Map<Long, Boolean> result = checker.batchHealthy(host, List.of(healthService(7, "http://localhost/health", 2)));

        assertThat(result).containsEntry(7L, true);
    }

    @Test
    void httpHealthCheckerReturnsFalseWhenLocalRequestFails() {
        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        HttpHealthChecker checker = new HttpHealthChecker(restTemplateBuilder, hostShellExecutor, monitorProperties());
        HostConfig host = TestFixtures.localHost(1);

        when(restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(2))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.setReadTimeout(Duration.ofSeconds(2))).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        when(restTemplate.getForEntity("http://localhost/health", String.class)).thenThrow(new IllegalStateException("down"));

        Map<Long, Boolean> result = checker.batchHealthy(host, List.of(healthService(7, "http://localhost/health", 2)));

        assertThat(result).containsEntry(7L, false);
    }

    @Test
    void httpHealthCheckerReturnsEmptyMapWhenNoServicesHaveHealthUrl() {
        HttpHealthChecker checker = new HttpHealthChecker(
                mock(RestTemplateBuilder.class), mock(HostShellExecutor.class), monitorProperties());

        assertThat(checker.batchHealthy(TestFixtures.sshHost(1), List.of())).isEmpty();
    }

    private MonitoredService healthService(long id, String url, long timeoutSeconds) {
        MonitoredService service = TestFixtures.service(id, TestFixtures.localHost(1), null);
        service.setHealthUrl(url);
        service.setHealthTimeoutSeconds(timeoutSeconds);
        return service;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private MonitorProperties monitorProperties() {
        return new MonitorProperties();
    }
}
