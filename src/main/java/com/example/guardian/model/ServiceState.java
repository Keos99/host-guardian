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
 * по которой рассчитываются cooldown и лимиты в пределах временного окна.
 */
public class ServiceState {

    private Instant lastRestartAt;
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
