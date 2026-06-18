package com.example.guardian.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Внутреннее runtime-состояние отслеживаемого сервиса.
 *
 * <p>Экземпляры этого класса не загружаются из конфигурации и не сохраняются
 * в базу данных. Они используются только в памяти для принятия решений о том,
 * можно ли выполнять очередной рестарт сервиса в текущий момент времени.
 *
 * <p>Состояние включает время последнего рестарта и историю недавних рестартов,
 * по которой рассчитываются cooldown и лимиты в пределах временного окна, а также
 * последний известный health-статус, пригодный для дашборда.
 */
public class ServiceState {

    private Instant lastRestartAt;
    private Instant lastCheckAt;
    private ServiceHealthStatus status = ServiceHealthStatus.UNKNOWN;
    private boolean processRunning;
    private Long lastKnownPid;
    private boolean healthCheckEnabled;
    private boolean healthCheckPassed;
    private String lastMessage = "Service has not been checked yet";
    private boolean downAlertSent;
    private boolean startFailureAlertSent;
    private boolean restartLimitAlertSent;
    private final Deque<Instant> restartHistory = new ArrayDeque<>();

    /**
     * Возвращает момент последнего успешного рестарта сервиса.
     *
     * @return время последнего рестарта или {@code null}, если рестартов еще не было
     */
    public Instant getLastRestartAt() {
        return lastRestartAt;
    }

    /**
     * Сохраняет время последнего успешного рестарта.
     *
     * @param lastRestartAt момент времени, когда сервис был перезапущен
     */
    public void setLastRestartAt(Instant lastRestartAt) {
        this.lastRestartAt = lastRestartAt;
    }

    /**
     * Возвращает время последней проверки сервиса.
     *
     * @return момент последнего check-run или {@code null}, если проверок еще не было
     */
    public Instant getLastCheckAt() {
        return lastCheckAt;
    }

    /**
     * Сохраняет время последней проверки сервиса.
     *
     * @param lastCheckAt момент последней проверки
     */
    public void setLastCheckAt(Instant lastCheckAt) {
        this.lastCheckAt = lastCheckAt;
    }

    /**
     * Возвращает последний рассчитанный health-статус.
     *
     * @return статус сервиса для отображения в UI и API
     */
    public ServiceHealthStatus getStatus() {
        return status;
    }

    /**
     * Устанавливает последний рассчитанный health-статус.
     *
     * @param status новый статус сервиса
     */
    public void setStatus(ServiceHealthStatus status) {
        this.status = status;
    }

    /**
     * Возвращает, был ли найден процесс сервиса при последней проверке.
     *
     * @return {@code true}, если процесс найден
     */
    public boolean isProcessRunning() {
        return processRunning;
    }

    /**
     * Сохраняет признак наличия процесса по итогам последней проверки.
     *
     * @param processRunning {@code true}, если процесс найден
     */
    public void setProcessRunning(boolean processRunning) {
        this.processRunning = processRunning;
    }

    public Long getLastKnownPid() {
        return lastKnownPid;
    }

    public void setLastKnownPid(Long lastKnownPid) {
        this.lastKnownPid = lastKnownPid;
    }

    public boolean isHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    public void setHealthCheckEnabled(boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
    }

    /**
     * Возвращает результат последнего health-check.
     *
     * @return {@code true}, если health-check прошел успешно
     */
    public boolean isHealthCheckPassed() {
        return healthCheckPassed;
    }

    /**
     * Сохраняет результат последнего health-check.
     *
     * @param healthCheckPassed {@code true}, если health-check успешен
     */
    public void setHealthCheckPassed(boolean healthCheckPassed) {
        this.healthCheckPassed = healthCheckPassed;
    }

    /**
     * Возвращает текстовое пояснение к последнему статусу.
     *
     * @return сообщение для логов, API и UI
     */
    public String getLastMessage() {
        return lastMessage;
    }

    /**
     * Устанавливает текстовое пояснение к последнему статусу.
     *
     * @param lastMessage человекочитаемое сообщение о состоянии сервиса
     */
    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    /**
     * Возвращает, отправлен ли уже алерт о недоступности сервиса.
     *
     * <p>Флаг защищает чат от спама: пока сервис не восстановится, повторные
     * циклы мониторинга не должны слать одинаковые сообщения.
     *
     * @return {@code true}, если алерт о текущем эпизоде недоступности уже отправлен
     */
    public boolean isDownAlertSent() {
        return downAlertSent;
    }

    /**
     * Сохраняет признак отправленного алерта о недоступности.
     *
     * @param downAlertSent {@code true}, если алерт отправлен
     */
    public void setDownAlertSent(boolean downAlertSent) {
        this.downAlertSent = downAlertSent;
    }

    /**
     * Возвращает, отправлен ли алерт о неудачной команде запуска.
     *
     * @return {@code true}, если алерт о текущей серии неудачных запусков уже отправлен
     */
    public boolean isStartFailureAlertSent() {
        return startFailureAlertSent;
    }

    /**
     * Сохраняет признак отправленного алерта о неудачной команде запуска.
     *
     * @param startFailureAlertSent {@code true}, если алерт отправлен
     */
    public void setStartFailureAlertSent(boolean startFailureAlertSent) {
        this.startFailureAlertSent = startFailureAlertSent;
    }

    /**
     * Возвращает, отправлен ли алерт об исчерпании лимита рестартов.
     *
     * @return {@code true}, если алерт об исчерпании лимита уже отправлен
     */
    public boolean isRestartLimitAlertSent() {
        return restartLimitAlertSent;
    }

    /**
     * Сохраняет признак отправленного алерта об исчерпании лимита рестартов.
     *
     * @param restartLimitAlertSent {@code true}, если алерт отправлен
     */
    public void setRestartLimitAlertSent(boolean restartLimitAlertSent) {
        this.restartLimitAlertSent = restartLimitAlertSent;
    }

    /**
     * Возвращает очередь истории рестартов.
     *
     * <p>Очередь хранит только те события, которые еще попадают в активное
     * окно ограничения частоты рестартов.
     *
     * @return двусторонняя очередь с временными метками рестартов
     */
    public Deque<Instant> getRestartHistory() {
        return restartHistory;
    }
}
