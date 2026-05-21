# Описание архитектуры решения Host Guardian

## 0. Информационная карточка

| Поле | Значение |
|---|---|
| Название сервиса | Host Guardian |
| Репозиторий | `hi-lt-watcher` |
| Тип решения | Операционная консоль мониторинга и восстановления сервисов на Linux-хостах |
| Технологический стек | Java 17, Spring Boot 3.2.5, Spring Web MVC, Spring Data JPA, Flyway, H2/PostgreSQL, HTML/CSS/JavaScript |
| Основной пользователь | Оператор, разработчик или администратор, отвечающий за состояние внутренних сервисов |
| Ключевая ценность | Единая панель для проверки, запуска и перезапуска сервисов, которые работают вне полноценного supervisor-окружения |

## 1. Введение и цели

Host Guardian представляет собой Spring Boot приложение для мониторинга и восстановления внутренних сервисов, запущенных на одном или нескольких Linux-хостах. Решение ориентировано на ситуации, когда приложения работают как `java -jar`, shell-скрипты или фоновые процессы и еще не переведены под `systemd`, Kubernetes или другой зрелый механизм управления жизненным циклом.

Сервис хранит конфигурацию мониторинга в базе данных, выполняет регулярные проверки процессов, опционально проверяет HTTP health endpoint, поддерживает ручные действия оператора и предоставляет встроенный веб-dashboard. Выполнение команд возможно на локальном хосте или удаленно по SSH.

### Бизнес-цели

| Цель | Описание |
|---|---|
| Сокращение времени реакции на сбои | Оператор видит текущее состояние сервисов в единой панели и может запустить проверку или recovery-действие без ручного входа на сервер. |
| Повышение доступности внутренних сервисов | Автоматический start recovery поднимает отсутствующие процессы в рамках заданных ограничений. |
| Централизация операционной конфигурации | Хосты, группы и мониторируемые сервисы хранятся в БД и управляются через REST API/dashboard. |
| Снижение риска ручных ошибок | Команды старта и рестарта фиксируются в конфигурации, а частота recovery-действий ограничивается cooldown/window политиками. |
| Подготовка к дальнейшему развитию | Архитектура допускает подключение PostgreSQL, удаленных SSH-хостов, новых каналов уведомлений, аудита и RBAC. |

### Обзор требований

| Сценарий | Краткое описание |
|---|---|
| Регистрация хостов | Пользователь создает локальные или SSH-хосты, на которых будут выполняться проверки и команды. |
| Регистрация групп сервисов | Пользователь создает логические группы для фильтрации и организации dashboard. |
| Регистрация мониторируемого сервиса | Пользователь задает имя, хост, группу, шаблон поиска процесса, команды, health URL и restart-политику. |
| Периодический мониторинг | Система по расписанию проверяет все включенные сервисы. |
| Проверка процесса | Система ищет процесс на целевом хосте через `pgrep -af`, сохраняет PID и проверяет его живость. |
| HTTP health-check | Если задан `healthUrl`, система проверяет прикладную готовность сервиса через HTTP. |
| Автоматический start recovery | Если процесс отсутствует и policy разрешает действие, система выполняет только `startCommand`. |
| Ручная проверка | Оператор запускает немедленную проверку одного сервиса. |
| Ручной restart | Оператор запускает контролируемый recovery-flow: старт отсутствующего процесса или рестарт при упавшем health-check. |
| Dashboard | Пользователь просматривает сводку, статусы, PID, состояние health-check, последние сообщения и управляет конфигурацией. |

### Цели по качеству решения

| Название цели | Краткое описание |
|---|---|
| Надежность | Ошибка проверки одного сервиса не должна останавливать мониторинг остальных сервисов. |
| Доступность | Dashboard и API должны оставаться доступными для операционных действий, пока работает приложение Host Guardian. |
| Безопасность | Доступ к удаленным хостам должен быть контролируемым; чувствительные SSH-данные не должны попадать в логи. |
| Поддерживаемость | Код должен быть разделен на понятные слои: REST, конфигурационная логика, мониторинг, выполнение команд, persistence. |
| Расширяемость | Должна сохраняться возможность добавить уведомления, audit trail, RBAC, новые SSH/command providers и историю проверок без полной переработки. |
| Наблюдаемость | Логи и runtime-состояние должны позволять понять, почему сервис отмечен как `UP`, `DOWN`, `PAUSED`, `RESTARTING` или `ERROR`. |
| Удобство эксплуатации | Dashboard должен закрывать базовые задачи оператора без прямого выполнения shell-команд вручную. |

### Ключевые участники

| Роль | Ожидания |
|---|---|
| Оператор/дежурный инженер | Быстро увидеть проблему, запустить проверку или восстановление, не ошибиться в командах. |
| Разработчик сервиса | Получить простой способ наблюдать состояние приложения вне Kubernetes/systemd. |
| Администратор инфраструктуры | Контролировать список хостов, SSH-доступы, команды и политики восстановления. |
| Владелец продукта/системы | Снизить простой внутренних сервисов и повысить прозрачность эксплуатации. |
| Специалист по безопасности | Минимизировать риски несанкционированного доступа к хостам и утечки секретов. |

## 2. Архитектурные ограничения

| Категория | Ограничение | Описание |
|---|---|---|
| Технические | Linux target hosts | Мониторинг процессов опирается на `bash`, `pgrep`, `curl` и shell-команды, поэтому целевые хосты предполагаются Linux-окружениями. |
| Технические | Java/Spring Boot | Основной backend реализован на Java 17 и Spring Boot 3.2.5. |
| Инфраструктурные | H2/PostgreSQL | Локально используется file-based H2, для более устойчивого окружения предусмотрен PostgreSQL profile. |
| Инфраструктурные | LOCAL/SSH execution | Команды выполняются либо локально на хосте Host Guardian, либо удаленно через SSH. |
| Безопасность | Key-based SSH | SSH-доступ сейчас моделируется через пользователя и путь к private key. Password/passphrase management не выделен отдельно. |
| Организационные | Встроенный UI | Dashboard поставляется как статические ресурсы внутри Spring Boot приложения, без отдельного frontend-сервиса. |
| Функциональные | Нет полноценного supervisor | Host Guardian не заменяет Kubernetes/systemd, а закрывает промежуточный сценарий host-based мониторинга и recovery. |

## 3. Границы и окружение системы

### Бизнес-контекст

Host Guardian находится между операционной командой и набором контролируемых внутренних сервисов. Система не является бизнес-сервисом конечного пользователя; это внутренний инструмент эксплуатации, который помогает поддерживать работоспособность прикладных процессов.

Основные бизнес-взаимодействия:

- операторы управляют хостами, группами и мониторируемыми сервисами;
- dashboard показывает состояние сервисов и дает ручные действия;
- scheduler автоматически проверяет сервисы и при необходимости запускает start recovery;
- SSH/LOCAL execution слой выполняет команды на целевых хостах;
- БД хранит конфигурацию, а runtime-состояние последних проверок живет в памяти приложения.

### Перечень партнеров

| Партнер | Описание взаимодействия |
|---|---|
| Оператор / разработчик | Использует dashboard или REST API для настройки хостов, групп, сервисов и запуска ручных операций. |
| Целевой Linux-хост | На нем выполняются команды поиска процесса, PID-check, health-check и start/restart. |
| Мониторируемый сервис | Процесс, состояние которого определяется по `processMatch`, PID и опциональному HTTP health endpoint. |
| SSH-провайдер | Выполняет команды на удаленном хосте через системный `ssh` client или JSch. |
| База данных | Хранит `host_config`, `service_group`, `monitored_service` и мигрируется Flyway. |
| API-клиент | Может использовать REST API для интеграции с внешними консолями или скриптами. |
| Браузер пользователя | Загружает встроенный dashboard и обращается к REST API приложения. |

### Технический контекст

| Внешний контур | Интерфейс | Вход | Выход |
|---|---|---|---|
| Web dashboard | HTTP/REST | Действия пользователя, формы конфигурации, фильтры | Сводка статусов, списки хостов/групп/сервисов, результат действий |
| REST API | JSON over HTTP | CRUD-запросы, manual check/restart, фильтрация | DTO ответов, HTTP status, `ProblemDetail` при ошибках |
| Target host LOCAL | Shell command | `pgrep`, PID-check, `curl`, start/restart command | Exit code, stdout, stderr |
| Target host SSH | SSH command | Те же команды, но на удаленной машине | Exit code, stdout, stderr, ошибки подключения |
| H2/PostgreSQL | JDBC/JPA | Чтение и запись конфигурации | Persistent state конфигурации |
| OpenAPI/Swagger UI | HTTP | Запрос документации | OpenAPI JSON и интерактивный Swagger UI |

## 4. Стратегия решения

| Категория | Описание принятого решения | Рассмотренные альтернативы | Обоснование |
|---|---|---|---|
| Технологии | Java 17 + Spring Boot 3.2.5, Spring MVC, JPA, Flyway. | Go, Node.js, shell-only решение. | Spring Boot хорошо подходит для REST API, scheduler, валидации, JPA и быстрой поддержки небольшой командой. |
| Декомпозиция | Модульный монолит с разделением на controllers, services, repositories, model, api, scheduler. | Микросервисы, один большой controller/service. | Модульный монолит снижает операционную сложность, но сохраняет понятные границы для развития. |
| Хранение данных | Конфигурация хранится в БД, runtime snapshots хранятся в памяти. | Только YAML, хранить все runtime-состояние в БД. | БД удобна для dashboard/API и PostgreSQL profile; runtime в памяти дешевле и достаточен для текущего состояния. |
| Выполнение команд | Единый `HostShellExecutor` маршрутизирует выполнение в LOCAL или SSH. | Только локальные команды, прямые SSH-вызовы из мониторинга. | Отдельный слой изолирует детали выполнения и позволяет менять provider. |
| Recovery logic | Автоматически стартуется только отсутствующий процесс; полный restart выполняется вручную и только при проверенных условиях. | Автоматический полный restart при любом сбое. | Выбранный подход снижает риск restart storm и разрушительных действий при ложных health-check отказах. |
| UI | Встроенный статический dashboard. | Отдельное SPA-приложение. | Встроенный UI проще поставлять и сопровождать для операционного инструмента. |

## 5. Компонентный состав

### Верхнеуровневая компонентная схема

```mermaid
flowchart LR
    Browser["Web browser"] --> StaticUI["Static dashboard"]
    StaticUI --> Api["REST Controllers"]
    ApiClient["External API client"] --> Api

    Api --> Config["ConfigurationService"]
    Api --> Monitor["ServiceMonitor"]
    Scheduler["MonitoringScheduler"] --> Monitor

    Config --> Repos["Spring Data repositories"]
    Monitor --> Repos
    Repos --> Db[("H2 / PostgreSQL")]

    Monitor --> Process["LinuxProcessInspector"]
    Monitor --> Health["HttpHealthChecker"]
    Monitor --> Shell["HostShellExecutor"]

    Process --> Shell
    Health --> Shell
    Shell --> Local["LOCAL shell"]
    Shell --> SshProvider["SshCommandProvider"]
    SshProvider --> Remote["Remote Linux host"]

    Api --> OpenApi["OpenAPI / Swagger UI"]
```

### Основные части системы

| Компонент | Ответственность | Входящие коммуникации | Исходящие коммуникации |
|---|---|---|---|
| Web dashboard | Операционный UI для просмотра статусов и управления конфигурацией. | HTTP responses, статические ресурсы. | REST-запросы к API. |
| REST Controllers | HTTP-контур для dashboard и внешних клиентов. | REST-запросы по `/api/*`. | Вызовы `ConfigurationService`, `ServiceMonitor`, маппинг DTO. |
| ConfigurationService | Правила создания, обновления, удаления и чтения хостов, групп и сервисов. | REST controllers. | Spring Data repositories. |
| ServiceMonitor | Центральная логика проверки сервисов, вычисления статуса и запуска recovery. | Scheduler, REST manual actions. | Repositories, process inspector, health checker, shell executor. |
| MonitoringScheduler | Периодический запуск `ServiceMonitor.checkAll()` по `monitor.interval`. | Spring scheduling. | ServiceMonitor. |
| LinuxProcessInspector | Поиск процесса по `pgrep -af`, сохранение/проверка PID, остановка процесса при restart-flow. | ServiceMonitor. | HostShellExecutor. |
| HttpHealthChecker | Проверка HTTP health endpoint с учетом target host. | ServiceMonitor. | HostShellExecutor / `curl`. |
| HostShellExecutor | Единая точка выполнения команд на LOCAL или SSH-хосте. | Process inspector, health checker, monitor. | CommandExecutor, SshCommandProvider. |
| CommandExecutor | Низкоуровневое выполнение локальных shell-команд с timeout. | HostShellExecutor. | ОС хоста Host Guardian. |
| SshCommandProvider | Выполнение удаленных команд через `system` SSH или JSch. | HostShellExecutor. | Удаленный Linux-хост. |
| Repositories/DB | Хранение persistent-конфигурации. | Services. | H2/PostgreSQL. |
| OpenAPI config | Публикация описания API и Swagger UI. | HTTP-запросы к `/swagger-ui.html`, `/v3/api-docs`. | OpenAPI JSON/UI. |

### Основные REST API

| API | Описание | MVP |
|---|---|---|
| `GET /api/dashboard` | Сводка статусов, список групп и сервисов с runtime snapshots. | Да |
| `GET /api/hosts` | Получение списка хостов. | Да |
| `POST /api/hosts` | Создание хоста `LOCAL` или `SSH`. | Да |
| `PUT /api/hosts/{id}` | Обновление хоста. | Да |
| `DELETE /api/hosts/{id}` | Удаление хоста, если на него не ссылаются сервисы. | Да |
| `GET /api/groups` | Получение списка групп. | Да |
| `POST /api/groups` | Создание группы. | Да |
| `PUT /api/groups/{id}` | Обновление группы. | Да |
| `DELETE /api/groups/{id}` | Удаление группы, если она не используется сервисами. | Да |
| `GET /api/services` | Получение списка сервисов с фильтром по `groupId`. | Да |
| `GET /api/services/{id}` | Получение одного сервиса. | Да |
| `POST /api/services` | Создание мониторируемого сервиса. | Да |
| `PUT /api/services/{id}` | Обновление мониторируемого сервиса. | Да |
| `PATCH /api/services/{id}/monitoring` | Включение/отключение мониторинга. | Да |
| `POST /api/services/{id}/check` | Немедленная проверка сервиса. | Да |
| `POST /api/services/{id}/restart` | Контролируемый recovery-flow. | Да |
| `DELETE /api/services/{id}` | Удаление сервиса. | Да |

### Доменная модель и агрегаты

| Название | Описание |
|---|---|
| `HostConfig` | Целевой хост: имя, режим подключения, адрес, SSH port, SSH user, private key path, описание. |
| `ServiceGroup` | Логическая группа сервисов для фильтрации dashboard и API. |
| `MonitoredService` | Основной persistent-агрегат мониторинга: имя, хост, группа, process match, execution path, start/restart команды, health URL, restart policy, monitoring flag, last known PID. |
| `ServiceState` | In-memory runtime state: статус, время последней проверки, время последнего restart/start, PID, состояние health-check, сообщение, история recovery-действий. |
| `ServiceRuntimeSnapshot` | Immutable DTO runtime-состояния для API/dashboard. |
| `ServiceHealthStatus` | Статусы `UNKNOWN`, `UP`, `DOWN`, `PAUSED`, `RESTARTING`, `ERROR`. |

## 6. Динамическое представление

### Перечень сценариев

| Сценарий | Описание | Список компонентов |
|---|---|---|
| Периодический мониторинг | Scheduler запускает проверку всех сервисов; отключенные сервисы помечаются как `PAUSED`, остальные проходят process/health checks. | `MonitoringScheduler`, `ServiceMonitor`, repositories, `LinuxProcessInspector`, `HttpHealthChecker`, `HostShellExecutor`. |
| Автоматический start recovery | Если процесс отсутствует, а cooldown/window policy разрешает действие, выполняется `startCommand`. | `ServiceMonitor`, `HostShellExecutor`, `CommandExecutor` или `SshCommandProvider`, target host. |
| Ручная проверка | Оператор нажимает check; система сразу проверяет один сервис и обновляет runtime state. | Dashboard, `MonitoredServiceController`, `ServiceMonitor`. |
| Ручной restart | Оператор нажимает restart; если процесс отсутствует, выполняется start; если health-check отсутствует или успешен, restart не выполняется; если health-check падает, выполняется restart/stop + start. | Dashboard, `MonitoredServiceController`, `ServiceMonitor`, `LinuxProcessInspector`, `HostShellExecutor`. |
| Управление конфигурацией | Пользователь создает/редактирует/удаляет хосты, группы и сервисы; изменения сохраняются в БД. | Dashboard/API client, REST controllers, `ConfigurationService`, repositories, DB. |
| Просмотр dashboard | UI запрашивает `/api/dashboard`, получает конфигурацию и runtime snapshots в одном агрегированном ответе. | Browser, static UI, `DashboardController`, `ConfigurationService`, `ServiceMonitor`. |

### Сиквенс: периодическая проверка сервиса

```mermaid
sequenceDiagram
    participant S as MonitoringScheduler
    participant M as ServiceMonitor
    participant R as MonitoredServiceRepository
    participant P as LinuxProcessInspector
    participant H as HttpHealthChecker
    participant E as HostShellExecutor
    participant T as Target host

    S->>M: checkAll()
    M->>R: findAllByOrderByNameAsc()
    R-->>M: services
    loop each enabled service
        M->>P: findFirst(host, processMatch)
        P->>E: execute pgrep command
        E->>T: shell/ssh command
        T-->>E: pid/output
        E-->>P: CommandResult
        P-->>M: ProcessInfo
        M->>P: isPidRunning(host, pid)
        alt healthUrl configured
            M->>H: isHealthy(host, healthUrl, timeout)
            H->>E: execute curl command
            E->>T: shell/ssh command
            T-->>E: HTTP result
            E-->>H: CommandResult
            H-->>M: true/false
        end
        M->>M: update ServiceState
        alt process missing and policy allows
            M->>E: execute startCommand
            E->>T: shell/ssh command
            T-->>E: CommandResult
            E-->>M: result
            M->>M: record recovery action
        end
    end
```

### Сиквенс: загрузка dashboard

```mermaid
sequenceDiagram
    participant B as Browser
    participant D as DashboardController
    participant C as ConfigurationService
    participant M as ServiceMonitor

    B->>D: GET /api/dashboard?groupId=...
    D->>M: getRuntimeSnapshots()
    M-->>D: map serviceId -> snapshot
    D->>C: getServices(groupId)
    C-->>D: services
    D->>C: getGroups()
    C-->>D: groups
    D->>D: build summary counters
    D-->>B: DashboardResponse
```

## 7. Схема развертывания

| Особенность | Описание |
|---|---|
| Тип поставки | Spring Boot jar или запуск через Maven в dev-окружении. |
| Контейнеризация | Возможна через Docker, но в репозитории явно задан только `docker-compose.postgres.yml` для локального PostgreSQL. |
| Контуры | Dev/local: H2 file DB. Test/prod-like: PostgreSQL profile. |
| Внешний доступ | HTTP на порту `8099`; dashboard по `/`, Swagger UI по `/swagger-ui.html`, API по `/api/*`. |
| База данных | H2 по умолчанию: `jdbc:h2:file:./data/host-guardian`; PostgreSQL через `SPRING_PROFILES_ACTIVE=postgres`. |
| SSH | Для удаленного выполнения требуется доступ с хоста Host Guardian к target host по private key. |
| Миграции | Flyway запускает миграции при старте приложения. |
| Конфигурация | `application.yml`, `application-postgres.yml`, переменные окружения PostgreSQL, `monitor.*` настройки. |

```mermaid
flowchart TB
    User["Operator browser"] --> App["Host Guardian Spring Boot app :8099"]
    ApiUser["API client"] --> App
    App --> Db[("H2 file DB / PostgreSQL")]
    App --> Local["Local Linux host"]
    App -->|SSH| Remote1["Remote Linux host"]
    App -->|SSH| Remote2["Remote Linux host"]
    Local --> S1["Monitored service"]
    Remote1 --> S2["Monitored service"]
    Remote2 --> S3["Monitored service"]
```

## 8. Архитектурные и дизайн-решения (ADR)

| ADR | Аргументация |
|---|---|
| ADR-001. Модульный монолит на Spring Boot | Контекст: сервис небольшой, но включает API, UI, scheduler, БД и выполнение команд. Альтернативы: микросервисы или shell-only утилита. Решение: один Spring Boot сервис с четкими пакетными границами. Почему так: проще разворачивать, тестировать и сопровождать; при росте можно выделить компоненты по уже существующим границам. |
| ADR-002. Конфигурация в БД, runtime-состояние в памяти | Контекст: операторам нужна управляемая конфигурация через UI/API, но история всех проверок пока не является обязательной. Альтернативы: YAML-only или хранение каждого runtime события в БД. Решение: persistent-конфигурация в JPA entities, последние статусы в `ConcurrentHashMap`. Почему так: быстрый dashboard, простая модель и меньше записи в БД. Ограничение: runtime-состояние не переживает restart приложения. |
| ADR-003. LOCAL/SSH выполнение через отдельный executor | Контекст: сервисы могут жить на разных хостах. Альтернативы: только локальное выполнение или прямые SSH-вызовы из бизнес-логики. Решение: `HostShellExecutor` выбирает LOCAL или SSH, а SSH provider может быть `system` или JSch. Почему так: детали транспорта изолированы, можно менять provider без переписывания мониторинга. |
| ADR-004. Осторожная recovery-политика | Контекст: автоматический restart может усилить инцидент. Альтернативы: полный автоматический restart при любом DOWN. Решение: автоматический режим выполняет только start отсутствующего процесса; ручной restart проверяет состояние и не перезапускает healthy сервис. Почему так: меньше разрушительных действий и ниже риск restart storm. |
| ADR-005. Встроенный dashboard вместо отдельного frontend | Контекст: UI нужен для операционных задач, а не как публичный продукт. Альтернативы: SPA с отдельной сборкой и деплоем. Решение: статические HTML/CSS/JS ресурсы внутри Spring Boot. Почему так: проще поставка, меньше инфраструктуры, достаточно для MVP. |

## 9. Требования к атрибутам качества

### 9.1 Дерево атрибутов качества

| Атрибут | Описание |
|---|---|
| Доступность (Availability) | Dashboard и API должны быть доступны для просмотра состояния и ручных действий, пока работает Host Guardian. Отказ одного проверяемого сервиса не должен блокировать остальные проверки. |
| Надежность (Reliability) | Проверки должны быть изолированы по сервисам; ошибки shell/SSH/HTTP должны переводиться в понятные статусы и сообщения. |
| Производительность (Performance) | Сводка dashboard должна возвращаться быстро для ожидаемого числа хостов и сервисов; timeouts команд должны предотвращать зависание scheduler. |
| Масштабируемость (Scalability) | Система должна поддерживать рост числа сервисов и хостов через БД и группировку, без изменения базовой модели. |
| Безопасность (Security) | SSH-доступ должен быть ограничен, команды должны выполняться только из сохраненной конфигурации, секреты не должны выводиться в ответы API и логи. |
| Поддерживаемость (Maintainability) | Код разделен по слоям, покрыт unit-тестами, миграции схемы управляются Flyway, API документируется OpenAPI. |
| Расширяемость (Extensibility / Evolvability) | Возможность добавления уведомлений, аудита, RBAC, истории проверок, новых providers и maintenance windows без полной смены архитектуры. |
| Наблюдаемость (Observability) | Для каждого сервиса сохраняется последний runtime snapshot, статус, сообщение, PID, время проверки и время recovery-действия; системные ошибки пишутся в логи. |
| Удобство использования (Usability / UX) | Dashboard должен быть понятным для оператора: статусы, фильтры, кнопки check/restart, пауза мониторинга, формы конфигурации. |
| Совместимость/интегрируемость (Interoperability) | REST API и OpenAPI должны позволять подключать внешние клиенты, скрипты и будущие интеграции. |

### 9.2 Сценарии проверки атрибутов качества

| Риск | Описание |
|---|---|
| Доступность | Остановить один мониторируемый сервис и убедиться, что dashboard/API Host Guardian продолжают отвечать, остальные сервисы проверяются, а проблемный сервис получает статус `DOWN` или `RESTARTING`. |
| Надежность | Сымитировать ошибку SSH-подключения к одному хосту и проверить, что ошибка отражается в runtime-сообщении, логируется и не останавливает проверки других хостов. |
| Производительность | Создать набор из десятков/сотен сервисов с разными health-check настройками и измерить время ответа `/api/dashboard`, а также отсутствие зависания scheduler при timeout команд. |
| Масштабируемость | Добавить несколько хостов и групп, распределить сервисы по группам и проверить фильтрацию `/api/dashboard?groupId=...` и `/api/services?groupId=...`. |
| Безопасность | Проверить, что API-ответы не раскрывают содержимое private key, а логи команд не содержат секретов. Для SSH-хоста проверить отказ при отсутствии `sshUser` или `privateKeyPath`. |
| Поддерживаемость | Выполнить изменение модели через новую Flyway-миграцию и проверить, что тесты и запуск с H2/PostgreSQL проходят без ручной правки схемы. |
| Наблюдаемость | Сымитировать падение health-check и убедиться, что dashboard показывает health-check state, last message, last check time и статус сервиса. |
| Удобство использования | Через UI создать хост, группу и сервис, выполнить check/restart и поставить мониторинг на паузу без обращения к shell. |
| Интегрируемость | Открыть `/v3/api-docs` и Swagger UI, проверить наличие основных endpoints и корректные `ProblemDetail` ответы для 400/404/409 сценариев. |

## 10. Риски и технический долг

| Риск | Описание |
|---|---|
| Хранение SSH-секретов | Private key path хранится в конфигурации, но нет отдельного secret manager, шифрования секретов и управления passphrase. |
| Отсутствие RBAC | Dashboard и API пока не имеют полноценной аутентификации и авторизации, что критично для production-доступа. |
| Неперсистентное runtime-состояние | Последние статусы и история recovery-действий находятся в памяти и сбрасываются при restart Host Guardian. |
| Нет audit trail | Изменения конфигурации хостов, групп и сервисов не фиксируются как отдельная история действий оператора. |
| Риск опасных shell-команд | Команды старта/рестарта выполняются из конфигурации; при ошибочной настройке возможны нежелательные действия на хосте. |
| Ограниченность health model | HTTP health-check бинарный; нет расширенного анализа degraded-состояний, зависимостей и бизнес-метрик. |
| Нет notification channel | Система показывает статус в dashboard, но не отправляет alert в Telegram/Slack/email. |
| Масштаб scheduler | При большом числе сервисов последовательные проверки и shell/SSH timeouts могут увеличить длительность одного цикла. |
| Нет production deployment spec | В репозитории нет полноценного Dockerfile/Kubernetes/systemd unit для самого Host Guardian. |

## 11. Словарь

| Термин | Определение |
|---|---|
| Host Guardian | Сервис мониторинга и восстановления процессов на Linux-хостах. |
| Хост | Машина, на которой выполняются проверки и команды: локальная (`LOCAL`) или удаленная (`SSH`). |
| Мониторируемый сервис | Прикладной процесс, состояние которого отслеживает Host Guardian. |
| `processMatch` | Строка/шаблон для поиска процесса через `pgrep -af`. |
| `healthUrl` | Опциональный HTTP endpoint, подтверждающий прикладную готовность сервиса. |
| Start recovery | Автоматическое выполнение `startCommand`, когда процесс не найден и политика разрешает действие. |
| Manual restart | Ручное действие оператора, которое выполняет проверенный restart-flow. |
| Cooldown | Минимальный интервал между recovery-действиями для одного сервиса. |
| Restart window | Временное окно, внутри которого ограничивается число recovery-действий. |
| Runtime snapshot | Последнее in-memory состояние сервиса, возвращаемое dashboard API. |
| Dashboard | Встроенный веб-интерфейс для просмотра статусов и управления конфигурацией. |
| SSH provider | Реализация удаленного выполнения команд: системный `ssh` client или JSch. |
| Flyway | Механизм миграции схемы БД при старте приложения. |
| `ProblemDetail` | Стандартный формат HTTP-ошибок, возвращаемый REST API. |
