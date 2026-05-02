Что делает сервис:

- по расписанию проверяет процессы на хосте;
- при необходимости делает HTTP health-check;
- если процесс не найден или health-check не проходит, выполняет restart-команду;
- ограничивает частоту рестартов через cooldown и restart window.

Структура проекта:

- `pom.xml` — Maven-проект Spring Boot;
- `src/main/java/com/example/guardian` — основной код мониторинга;
- `src/main/resources/application.yml` — пример конфигурации.

Требования:

- Java 17+;
- Maven 3.9+ для сборки проекта.

Как это работает:

1. `MonitoringScheduler` по расписанию вызывает `ServiceMonitor`.
2. `LinuxProcessInspector` ищет процесс через `pgrep -af`.
3. `HttpHealthChecker` опционально проверяет HTTP endpoint.
4. `ServiceMonitor` принимает решение о рестарте и следит за cooldown/window.
5. `CommandExecutor` запускает shell-команды через `bash -lc`.

Пример подходящей restart-команды:

```bash
nohup java -jar /opt/apps/app-one.jar --server.port=8081 >> /var/log/app-one.log 2>&1 &
```

Почему команда должна быть self-contained:

- `nohup` позволяет не зависеть от shell-сессии;
- `>> /var/log/... 2>&1` отправляет вывод в лог;
- `&` запускает процесс в фоне;
- `bash -lc` дает использовать `cd`, redirection и фоновые команды.

Практические замечания из исходного ответа:

- сервис должен запускаться от пользователя, который имеет право видеть процессы и стартовать нужные приложения;
- одного факта существования процесса часто недостаточно, поэтому лучше использовать и process-check, и HTTP health-check;
- cooldown и ограничение числа рестартов защищают от restart storm;
- все restart-команды лучше делать полностью самодостаточными.

Идеи для следующего шага из того же ответа:

- REST API вроде `GET /api/services`, `POST /api/services/{name}/restart`, `GET /api/services/{name}/status`;
- хранение истории рестартов в PostgreSQL;
- уведомления в Telegram, Slack или email;
- удаленное выполнение команд по SSH;
- отдельные режимы проверки: `PROCESS_ONLY`, `HTTP_ONLY`, `PROCESS_AND_HTTP`;
- graceful stop/start и distributed lock.
