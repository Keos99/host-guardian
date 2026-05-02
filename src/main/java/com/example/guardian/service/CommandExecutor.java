package com.example.guardian.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Универсальный компонент для запуска внешних команд операционной системы.
 *
 * <p>Используется остальными сервисами как низкоуровневая абстракция над
 * {@link ProcessBuilder}. Компонент умеет:
 *
 * <ul>
 * <li>запускать команды с ограничением по времени;</li>
 * <li>объединять стандартный вывод и поток ошибок в один поток;</li>
 * <li>возвращать структурированный результат выполнения без выбрасывания
 * checked-исключений наружу.</li>
 * </ul>
 *
 * <p>Такой подход позволяет бизнес-логике мониторинга принимать решение по
 * результату команды, не смешивая его с деталями управления процессами JVM.
 */
@Component
public class CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(CommandExecutor.class);

    /**
     * Выполняет внешнюю команду и ожидает ее завершения не дольше указанного таймаута.
     *
     * <p>При превышении таймаута процесс принудительно завершается, а в результате
     * возвращается специальный код ошибки {@code -1}. Если во время запуска или
     * чтения вывода возникает исключение, оно логируется, а информация об ошибке
     * также возвращается через объект результата.
     *
     * @param command команда в виде списка аргументов для {@link ProcessBuilder}
     * @param timeout максимальное время ожидания завершения процесса
     * @return результат выполнения команды, включая код завершения, вывод и текст ошибки
     */
    public CommandResult execute(List<String> command, Duration timeout) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);

            Process process = pb.start();

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(-1, "", "Command timeout");
            }

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            return new CommandResult(process.exitValue(), output, null);
        } catch (Exception e) {
            log.error("Command execution failed: {}", command, e);
            return new CommandResult(-1, "", e.getMessage());
        }
    }

    /**
     * Структурированное представление результата выполнения внешней команды.
     *
     * @param exitCode код завершения процесса; значение {@code 0} трактуется как успех
     * @param output объединенный стандартный вывод и поток ошибок процесса
     * @param error текст ошибки инфраструктурного уровня, если команду не удалось
     *              корректно запустить или дождаться ее завершения
     */
    public record CommandResult(int exitCode, String output, String error) {
        /**
         * Проверяет, завершилась ли команда успешно.
         *
         * @return {@code true}, если код завершения равен {@code 0}, иначе {@code false}
         */
        public boolean success() {
            return exitCode == 0;
        }
    }
}
