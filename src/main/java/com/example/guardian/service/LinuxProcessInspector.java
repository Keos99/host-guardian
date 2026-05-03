package com.example.guardian.service;

import com.example.guardian.model.HostConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Компонент для проверки наличия процесса на Linux-хосте.
 *
 * <p>Поиск выполняется с помощью команды {@code pgrep -af}, что позволяет
 * искать процесс по командной строке запуска. Такой подход хорошо подходит
 * для приложений, стартующих через {@code java -jar ...}, когда имя jar-файла
 * или другая часть аргументов может служить стабильным идентификатором.
 */
@Component
public class LinuxProcessInspector {

    private final HostShellExecutor hostShellExecutor;

    /**
     * Создает инспектор процессов.
     *
     * @param hostShellExecutor исполнитель shell-команд на целевых хостах
     */
    public LinuxProcessInspector(HostShellExecutor hostShellExecutor) {
        this.hostShellExecutor = hostShellExecutor;
    }

    /**
     * Проверяет, существует ли в системе процесс, соответствующий заданному шаблону.
     *
     * @param host хост, на котором нужно искать процесс
     * @param processMatch строка поиска для команды {@code pgrep -af}
     * @return {@code true}, если найден хотя бы один подходящий процесс; иначе {@code false}
     */
    public boolean isRunning(HostConfig host, String processMatch) {
        CommandExecutor.CommandResult result = hostShellExecutor.execute(
                host,
                "pgrep -af \"" + escape(processMatch) + "\"",
                Duration.ofSeconds(5)
        );

        if (!result.success()) {
            return false;
        }

        String output = result.output();
        return output != null && !output.isBlank();
    }

    /**
     * Экранирует двойные кавычки перед подстановкой значения в shell-команду.
     *
     * @param value исходная строка поиска
     * @return строка, безопасная для помещения внутрь двойных кавычек
     */
    private String escape(String value) {
        return value.replace("\"", "\\\"");
    }
}
