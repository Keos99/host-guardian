package com.example.guardian.notification;

import com.example.guardian.model.MonitoredService;
import com.example.guardian.repository.MonitoredServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Prepares chat notifications for monitoring and configuration events.
 *
 * <p>The component owns three responsibilities:
 *
 * <ul>
 * <li>gating — a message is sent only when the feature is enabled in
 * {@code application.yml}, the runtime global switch is on, and the affected
 * service has notifications enabled;</li>
 * <li>message texts — every event has one prepared, operator-readable message;</li>
 * <li>safe delivery — messages are handed to a {@link ChatProvider} on a separate
 * thread, and any error (message building or transport) is logged and never
 * propagated into monitoring or REST flows.</li>
 * </ul>
 */
@Component
public class ChatNotifier {

    private static final Logger log = LoggerFactory.getLogger(ChatNotifier.class);

    private final ChatProvider chatProvider;
    private final NotificationProperties properties;
    private final NotificationSettingsService settings;
    private final MonitoredServiceRepository monitoredServiceRepository;
    private final Executor notificationExecutor;

    /**
     * Creates a chat notifier.
     *
     * @param chatProvider transport used to deliver messages
     * @param properties notification feature configuration
     * @param settings runtime global notification switch storage
     * @param monitoredServiceRepository repository used for the startup summary
     * @param notificationExecutor executor that runs deliveries asynchronously
     */
    public ChatNotifier(ChatProvider chatProvider,
                        NotificationProperties properties,
                        NotificationSettingsService settings,
                        MonitoredServiceRepository monitoredServiceRepository,
                        @Qualifier("notificationExecutor") Executor notificationExecutor) {
        this.chatProvider = chatProvider;
        this.properties = properties;
        this.settings = settings;
        this.monitoredServiceRepository = monitoredServiceRepository;
        this.notificationExecutor = notificationExecutor;
    }

    /**
     * Checks whether the notification feature is enabled in {@code application.yml}.
     *
     * <p>When this returns {@code false}, the dashboard hides all notification
     * controls and no message is ever sent.
     *
     * @return {@code true} when the feature is enabled by configuration
     */
    public boolean isFeatureEnabled() {
        return properties.isEnabled();
    }

    /**
     * Checks whether notifications are currently active.
     *
     * @return {@code true} when both the configuration flag and the runtime
     * global switch allow sending
     */
    public boolean isGlobalEnabled() {
        return isFeatureEnabled() && settings.isGlobalEnabled();
    }

    /**
     * Announces that the watcher itself has started.
     *
     * @param event Spring Boot ready event
     */
    @EventListener(ApplicationReadyEvent.class)
    public void watcherStarted(ApplicationReadyEvent event) {
        if (!isGlobalEnabled()) {
            return;
        }
        dispatch(MessageStatus.SUCCESS,
                () -> "Host Guardian запущен. Сервисов под мониторингом: "
                        + monitoredServiceRepository.count());
    }

    /**
     * Announces a newly added monitored service.
     *
     * @param service created service
     */
    public void serviceAdded(MonitoredService service) {
        if (!shouldNotify(service)) {
            return;
        }
        dispatch(MessageStatus.SUCCESS, () -> "Добавлен новый сервис: " + describe(service));
    }

    /**
     * Announces that a monitored service was deleted.
     *
     * @param service removed service
     */
    public void serviceRemoved(MonitoredService service) {
        if (!shouldNotify(service)) {
            return;
        }
        dispatch(MessageStatus.NOT_BUILT, () -> "Сервис удален из мониторинга: " + service.getName());
    }

    /**
     * Announces that a service became unhealthy.
     *
     * @param service unhealthy service
     * @param reason human-readable reason from the latest check
     */
    public void serviceDown(MonitoredService service, String reason) {
        if (!shouldNotify(service)) {
            return;
        }
        dispatch(MessageStatus.FAIL, () -> "Сервис " + describe(service) + " недоступен: " + reason);
    }

    /**
     * Announces that a previously failed service is healthy again.
     *
     * @param service recovered service
     */
    public void serviceRecovered(MonitoredService service) {
        if (!shouldNotify(service)) {
            return;
        }
        dispatch(MessageStatus.OK, () -> "Сервис " + describe(service) + " снова работает");
    }

    /**
     * Announces an automatic or manual recovery attempt.
     *
     * @param service service being started or restarted
     * @param attempt sequential attempt number inside the restart window
     */
    public void restartAttempt(MonitoredService service, int attempt) {
        if (!shouldNotify(service)) {
            return;
        }
        dispatch(MessageStatus.UNSTABLE,
                () -> "Попытка запуска сервиса " + service.getName()
                        + ": " + attempt + " из " + service.getMaxRestartsInWindow()
                        + " в окне " + formatSeconds(service.getRestartWindowSeconds()));
    }

    /**
     * Announces a failed start or restart command.
     *
     * @param service service whose recovery command failed
     * @param reason command error description
     */
    public void restartFailed(MonitoredService service, String reason) {
        if (!shouldNotify(service)) {
            return;
        }
        dispatch(MessageStatus.FAILURE,
                () -> "Не удалось выполнить команду запуска сервиса " + service.getName() + ": " + reason);
    }

    /**
     * Announces that the automatic restart limit is exhausted.
     *
     * @param service service that can no longer be restarted automatically
     */
    public void restartLimitReached(MonitoredService service) {
        if (!shouldNotify(service)) {
            return;
        }
        dispatch(MessageStatus.FAILURE,
                () -> "Не удалось перезапустить сервис " + service.getName()
                        + ": исчерпан лимит " + service.getMaxRestartsInWindow()
                        + " рестартов за " + formatSeconds(service.getRestartWindowSeconds())
                        + ". Требуется ручное вмешательство!");
    }

    /**
     * Announces that monitoring of a service failed with an internal error.
     *
     * @param service service whose check failed
     * @param reason failure description
     */
    public void monitoringError(MonitoredService service, String reason) {
        if (!shouldNotify(service)) {
            return;
        }
        dispatch(MessageStatus.FAILURE,
                () -> "Не удалось проверить сервис " + service.getName() + ": " + reason);
    }

    /**
     * Checks whether a message about the given service may be sent.
     *
     * @param service affected service
     * @return {@code true} when all three switches allow sending
     */
    private boolean shouldNotify(MonitoredService service) {
        return isGlobalEnabled() && service.isNotificationsEnabled();
    }

    /**
     * Builds the message in the calling thread and delivers it asynchronously.
     *
     * <p>Both phases are guarded: a notification must never break the flow that
     * produced it, so any exception is logged and swallowed here. This replaces
     * the per-method {@code try/catch} blocks of the legacy implementation.
     *
     * @param status message severity
     * @param textSupplier lazy message text builder
     */
    private void dispatch(MessageStatus status, Supplier<String> textSupplier) {
        try {
            ChatMessage message = new ChatMessage(
                    status, textSupplier.get(), properties.getPeer(), properties.getServiceUrl());
            notificationExecutor.execute(() -> {
                try {
                    chatProvider.send(message);
                } catch (Exception e) {
                    log.error("Ошибка при отправке сообщения в чат: {}", message.text(), e);
                }
            });
        } catch (Exception e) {
            log.error("Не удалось подготовить сообщение для чата", e);
        }
    }

    /**
     * Builds the standard "name (host)" service reference for message texts.
     *
     * @param service described service
     * @return service name with host name
     */
    private String describe(MonitoredService service) {
        return service.getName() + " (хост " + service.getHost().getName() + ")";
    }

    /**
     * Formats a duration in seconds as a compact human-readable string.
     *
     * @param seconds duration in seconds
     * @return value in minutes when it divides evenly, otherwise in seconds
     */
    private String formatSeconds(long seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            return (seconds / 60) + " мин";
        }
        return seconds + " сек";
    }
}
