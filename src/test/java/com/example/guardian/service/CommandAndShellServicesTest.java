package com.example.guardian.service;

import com.example.guardian.TestFixtures;
import com.example.guardian.model.HostConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
        HostShellExecutor shellExecutor = new HostShellExecutor(commandExecutor);
        HostConfig host = TestFixtures.localHost(1);
        Duration timeout = Duration.ofSeconds(5);
        CommandExecutor.CommandResult result = new CommandExecutor.CommandResult(0, "ok", null);
        when(commandExecutor.execute(any(), eq(timeout))).thenReturn(result);

        CommandExecutor.CommandResult actual = shellExecutor.execute(host, "echo ok", timeout);

        assertThat(actual).isSameAs(result);
        verify(commandExecutor).execute(List.of("bash", "-lc", "echo ok"), timeout);
    }

    @Test
    void hostShellExecutorBuildsSshCommandWithKeyAndQuoting() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        HostShellExecutor shellExecutor = new HostShellExecutor(commandExecutor);
        HostConfig host = TestFixtures.sshHost(1);
        Duration timeout = Duration.ofSeconds(5);
        when(commandExecutor.execute(any(), eq(timeout))).thenReturn(new CommandExecutor.CommandResult(0, "ok", null));

        shellExecutor.execute(host, "echo 'ok'", timeout);

        ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandExecutor).execute(commandCaptor.capture(), eq(timeout));
        assertThat(commandCaptor.getValue()).containsExactly(
                "ssh",
                "-o",
                "BatchMode=yes",
                "-o",
                "ConnectTimeout=5",
                "-p",
                "2222",
                "-i",
                "/home/deploy/.ssh/id_ed25519",
                "deploy@10.0.0.5",
                "bash -lc 'echo '\"'\"'ok'\"'\"''"
        );
    }

    @Test
    void hostShellExecutorOmitsPrivateKeyWhenBlank() {
        CommandExecutor commandExecutor = mock(CommandExecutor.class);
        HostShellExecutor shellExecutor = new HostShellExecutor(commandExecutor);
        HostConfig host = TestFixtures.sshHost(1);
        host.setPrivateKeyPath(" ");
        when(commandExecutor.execute(any(), any())).thenReturn(new CommandExecutor.CommandResult(0, "ok", null));

        shellExecutor.execute(host, "uptime", Duration.ofSeconds(5));

        ArgumentCaptor<List<String>> commandCaptor = ArgumentCaptor.forClass(List.class);
        verify(commandExecutor).execute(commandCaptor.capture(), any());
        assertThat(commandCaptor.getValue()).doesNotContain("-i");
        assertThat(commandCaptor.getValue()).contains("deploy@10.0.0.5");
    }

    @Test
    void linuxProcessInspectorReturnsTrueOnlyForSuccessfulNonBlankOutput() {
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        LinuxProcessInspector inspector = new LinuxProcessInspector(hostShellExecutor);
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
    void httpHealthCheckerUsesRemoteCurlOnSshHost() {
        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        HttpHealthChecker checker = new HttpHealthChecker(restTemplateBuilder, hostShellExecutor);
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
        HttpHealthChecker checker = new HttpHealthChecker(restTemplateBuilder, hostShellExecutor);
        HostConfig host = TestFixtures.sshHost(1);
        Duration timeout = Duration.ZERO;
        when(hostShellExecutor.execute(host,
                "curl -fsS --max-time 1 'http://service/health' > /dev/null",
                timeout.plusSeconds(1)))
                .thenReturn(new CommandExecutor.CommandResult(1, "", "failed"));

        assertThat(checker.isHealthy(host, "http://service/health", timeout)).isFalse();
    }

    @Test
    void httpHealthCheckerUsesRestTemplateForLocalHost() {
        RestTemplateBuilder restTemplateBuilder = mock(RestTemplateBuilder.class);
        RestTemplate restTemplate = mock(RestTemplate.class);
        HostShellExecutor hostShellExecutor = mock(HostShellExecutor.class);
        HttpHealthChecker checker = new HttpHealthChecker(restTemplateBuilder, hostShellExecutor);
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
        HttpHealthChecker checker = new HttpHealthChecker(restTemplateBuilder, hostShellExecutor);
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
}
