# PostgreSQL profile

В проект добавлен отдельный Spring profile `postgres`.

## Что изменено

- добавлен JDBC driver PostgreSQL в `pom.xml`;
- добавлен профиль [application-postgres.yml](D:\hi-lt-atm\hi-lt-watcher\src\main\resources\application-postgres.yml);
- добавлен пример локального запуска PostgreSQL через [docker-compose.postgres.yml](D:\hi-lt-atm\hi-lt-watcher\docker-compose.postgres.yml).

## Как запустить с PostgreSQL

1. Поднять PostgreSQL:

```bash
docker compose -f docker-compose.postgres.yml up -d
```

2. Запустить приложение с профилем `postgres`:

```bash
java -jar app.jar --spring.profiles.active=postgres
```

или через переменную окружения:

```bash
SPRING_PROFILES_ACTIVE=postgres
```

## Переменные окружения

Профиль поддерживает такие переменные:

- `POSTGRES_URL`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`

Значения по умолчанию:

- `jdbc:postgresql://localhost:5432/host_guardian`
- `host_guardian`
- `host_guardian`

## Поведение

- без профиля `postgres` приложение по-прежнему использует H2 из `application.yml`;
- с профилем `postgres` datasource переключается на PostgreSQL;
- Flyway продолжает применять ту же схему миграций.
