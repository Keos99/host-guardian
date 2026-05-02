package com.example.guardian.scheduler;

import com.example.guardian.service.ServiceMonitor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Компонент, который регулярно инициирует цикл мониторинга всех сервисов.
 *
 * <p>Фактическая бизнес-логика проверки и рестарта находится в
 * {@link ServiceMonitor}, а этот класс только связывает ее с механизмом
 * планировщика Spring. Интервал между вызовами настраивается через бин
 * {@code monitorProperties}.
 */
@Component
public class MonitoringScheduler {

    private final ServiceMonitor serviceMonitor;

    /**
     * Создает планировщик мониторинга.
     *
     * @param serviceMonitor сервис, выполняющий фактические проверки и рестарты
     */
    public MonitoringScheduler(ServiceMonitor serviceMonitor) {
        this.serviceMonitor = serviceMonitor;
    }

    /**
     * Запускает один полный проход мониторинга по всем сконфигурированным сервисам.
     *
     * <p>Метод вызывается Spring по fixed delay, вычисляемому из настройки
     * {@code monitor.interval}. Следующий запуск начинается только после завершения
     * предыдущего.
     */
    @Scheduled(fixedDelayString = "#{@monitorProperties.interval.toMillis()}")
    public void monitor() {
        serviceMonitor.checkAll();
    }
}
