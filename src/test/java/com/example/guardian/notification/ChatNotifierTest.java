package com.example.guardian.notification;

import com.example.guardian.TestFixtures;
import com.example.guardian.model.MonitoredService;
import com.example.guardian.repository.MonitoredServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatNotifierTest {

    @Mock
    private ChatProvider chatProvider;

    @Mock
    private NotificationSettingsService settings;

    @Mock
    private MonitoredServiceRepository monitoredServiceRepository;

    @Captor
    private ArgumentCaptor<ChatMessage> messageCaptor;

    private NotificationProperties properties;
    private ChatNotifier notifier;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        notifier = new ChatNotifier(chatProvider, properties, settings,
                monitoredServiceRepository, Runnable::run);
    }

    @Test
    void serviceDownSendsAlarmWithServiceHostAndReason() {
        when(settings.isGlobalEnabled()).thenReturn(true);
        MonitoredService service = TestFixtures.service(1, TestFixtures.sshHost(1), null);

        notifier.serviceDown(service, "Process is missing");

        verify(chatProvider).send(messageCaptor.capture());
        ChatMessage message = messageCaptor.getValue();
        assertThat(message.status()).isEqualTo(MessageStatus.ALARM);
        assertThat(message.text())
                .contains("billing-api")
                .contains("Remote host")
                .contains("Process is missing");
    }

    @Test
    void serviceRecoveredSendsOkMessage() {
        when(settings.isGlobalEnabled()).thenReturn(true);
        MonitoredService service = TestFixtures.service(1, TestFixtures.sshHost(1), null);

        notifier.serviceRecovered(service);

        verify(chatProvider).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().status()).isEqualTo(MessageStatus.OK);
        assertThat(messageCaptor.getValue().text()).contains("billing-api").contains("снова работает");
    }

    @Test
    void serviceAddedAndRemovedSendInfoMessages() {
        when(settings.isGlobalEnabled()).thenReturn(true);
        MonitoredService service = TestFixtures.service(1, TestFixtures.sshHost(1), null);

        notifier.serviceAdded(service);
        notifier.serviceRemoved(service);

        verify(chatProvider, times(2)).send(messageCaptor.capture());
        List<ChatMessage> messages = messageCaptor.getAllValues();
        assertThat(messages.get(0).status()).isEqualTo(MessageStatus.INFO);
        assertThat(messages.get(0).text()).contains("Добавлен новый сервис").contains("billing-api");
        assertThat(messages.get(1).status()).isEqualTo(MessageStatus.INFO);
        assertThat(messages.get(1).text()).contains("удален из мониторинга").contains("billing-api");
    }

    @Test
    void restartAttemptReportsCounterAndWindowInMinutes() {
        when(settings.isGlobalEnabled()).thenReturn(true);
        MonitoredService service = TestFixtures.service(1, TestFixtures.sshHost(1), null);

        notifier.restartAttempt(service, 2);

        verify(chatProvider).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().status()).isEqualTo(MessageStatus.WARNING);
        assertThat(messageCaptor.getValue().text())
                .contains("Попытка запуска")
                .contains("2 из 3")
                .contains("10 мин");
    }

    @Test
    void restartAttemptReportsOddWindowInSeconds() {
        when(settings.isGlobalEnabled()).thenReturn(true);
        MonitoredService service = TestFixtures.service(1, TestFixtures.sshHost(1), null);
        service.setRestartWindowSeconds(90);

        notifier.restartAttempt(service, 1);

        verify(chatProvider).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().text()).contains("90 сек");
    }

    @Test
    void restartFailedAndLimitAndMonitoringErrorSendAlarms() {
        when(settings.isGlobalEnabled()).thenReturn(true);
        MonitoredService service = TestFixtures.service(1, TestFixtures.sshHost(1), null);

        notifier.restartFailed(service, "exit code 1");
        notifier.restartLimitReached(service);
        notifier.monitoringError(service, "ssh unreachable");

        verify(chatProvider, times(3)).send(messageCaptor.capture());
        List<ChatMessage> messages = messageCaptor.getAllValues();
        assertThat(messages).allSatisfy(message ->
                assertThat(message.status()).isEqualTo(MessageStatus.ALARM));
        assertThat(messages.get(0).text()).contains("команду запуска").contains("exit code 1");
        assertThat(messages.get(1).text())
                .contains("исчерпан лимит 3 рестартов")
                .contains("Требуется ручное вмешательство");
        assertThat(messages.get(2).text()).contains("Не удалось проверить").contains("ssh unreachable");
    }

    @Test
    void watcherStartedReportsMonitoredServiceCount() {
        when(settings.isGlobalEnabled()).thenReturn(true);
        when(monitoredServiceRepository.count()).thenReturn(7L);

        notifier.watcherStarted(null);

        verify(chatProvider).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().status()).isEqualTo(MessageStatus.INFO);
        assertThat(messageCaptor.getValue().text()).contains("Host Guardian запущен").contains("7");
    }

    @Test
    void nothingIsSentWhenFeatureIsDisabledByProperties() {
        properties.setEnabled(false);
        MonitoredService service = TestFixtures.service(1, TestFixtures.sshHost(1), null);

        notifier.watcherStarted(null);
        notifier.serviceAdded(service);
        notifier.serviceRemoved(service);
        notifier.serviceDown(service, "down");
        notifier.serviceRecovered(service);
        notifier.restartAttempt(service, 1);
        notifier.restartFailed(service, "boom");
        notifier.restartLimitReached(service);
        notifier.monitoringError(service, "error");

        verifyNoInteractions(chatProvider, settings, monitoredServiceRepository);
        assertThat(notifier.isFeatureEnabled()).isFalse();
        assertThat(notifier.isGlobalEnabled()).isFalse();
    }

    @Test
    void nothingIsSentWhenGlobalRuntimeSwitchIsOff() {
        when(settings.isGlobalEnabled()).thenReturn(false);
        MonitoredService service = TestFixtures.service(1, TestFixtures.sshHost(1), null);

        notifier.serviceDown(service, "down");

        verifyNoInteractions(chatProvider);
        assertThat(notifier.isFeatureEnabled()).isTrue();
        assertThat(notifier.isGlobalEnabled()).isFalse();
    }

    @Test
    void nothingIsSentWhenServiceNotificationsAreDisabled() {
        when(settings.isGlobalEnabled()).thenReturn(true);
        MonitoredService service = TestFixtures.service(1, TestFixtures.sshHost(1), null);
        service.setNotificationsEnabled(false);

        notifier.serviceDown(service, "down");

        verifyNoInteractions(chatProvider);
    }

    @Test
    void providerFailureNeverPropagatesToCaller() {
        when(settings.isGlobalEnabled()).thenReturn(true);
        doThrow(new ChatSendException("chat is down")).when(chatProvider).send(any());
        MonitoredService service = TestFixtures.service(1, TestFixtures.sshHost(1), null);

        assertThatCode(() -> notifier.serviceDown(service, "down")).doesNotThrowAnyException();
        verify(chatProvider).send(any());
    }

    @Test
    void messageBuildingFailureNeverPropagatesToCaller() {
        when(settings.isGlobalEnabled()).thenReturn(true);
        MonitoredService service = TestFixtures.service(1, null, null);

        assertThatCode(() -> notifier.serviceDown(service, "down")).doesNotThrowAnyException();
        verifyNoInteractions(chatProvider);
    }
}
