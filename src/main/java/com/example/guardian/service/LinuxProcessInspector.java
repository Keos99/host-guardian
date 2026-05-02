package com.example.guardian.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

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

    private final CommandExecutor commandExecutor;

    /**
     * Создает инспектор процессов.
     *
     * @param commandExecutor низкоуровневый исполнитель shell-команд
     */
    public LinuxProcessInspector(CommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    /**
     * Проверяет, существует ли в системе процесс, соответствующий заданному шаблону.
     *
     * @param processMatch строка поиска для команды {@code pgrep -af}
     * @return {@code true}, если найден хотя бы один подходящий процесс; иначе {@code false}
     */
    public boolean isRunning(String processMatch) {
        CommandExecutor.CommandResult result = commandExecutor.execute(
                List.of("bash", "-lc", "pgrep -af \"" + escape(processMatch) + "\""),
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
