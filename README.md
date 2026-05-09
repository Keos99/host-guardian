# Host Guardian

![Java](https://img.shields.io/badge/Java-17+-orange?style=for-the-badge)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-6DB33F?style=for-the-badge)
![Database](https://img.shields.io/badge/DB-H2%20%7C%20PostgreSQL-336791?style=for-the-badge)
![Flyway](https://img.shields.io/badge/Flyway-Migrations-CC0200?style=for-the-badge)
![REST API](https://img.shields.io/badge/API-REST-0A66C2?style=for-the-badge)
![Dashboard](https://img.shields.io/badge/UI-Built--in%20Dashboard-8E44AD?style=for-the-badge)

Host Guardian is a Spring Boot service for monitoring and restarting applications that may live on different Linux hosts.

The project combines:

- background process monitoring;
- optional HTTP health checks;
- automatic start recovery and checked manual restarts;
- host-aware execution through `LOCAL` or `SSH` with selectable SSH providers;
- configuration stored in the database;
- built-in web dashboard for operations.

---

## What It Solves

Many internal services are still started outside of `systemd`, Kubernetes, or mature supervisors.  
They run as plain `java -jar ...`, shell scripts, or long-lived background processes on one or more Linux servers.

That creates a few practical problems:

- there is no central place to see whether the process is alive;
- health endpoints may fail even if the process still exists;
- restart commands are often manual and host-specific;
- teams need a way to temporarily pause monitoring during maintenance;
- operational configuration gets scattered across YAML files and shell history.

Host Guardian addresses that by moving service definitions into the database, attaching each service to a target host, and exposing a simple operational UI and REST API over the top.

---

## Current Feature Set

- Monitoring configuration stored in the database instead of static YAML service lists.
- Host registry with `LOCAL` and `SSH` connection modes.
- Optional service grouping for dashboard filtering.
- Flag-based pause/resume of monitoring through `monitoringEnabled`.
- Periodic monitoring via Spring scheduling.
- Linux process lookup through `pgrep -af`, PID persistence, and PID liveness checks.
- Optional HTTP health checks.
- Optional service execution path used before start and manual restart commands.
- Required start command per service.
- Start-only recovery for missing services and checked manual restart flow for failed health checks.
- Cooldown and restart-window protection for restart attempts.
- Manual `restart` and manual `check` actions through REST API and dashboard.
- Built-in dashboard for creating, editing, deleting, filtering, and operating monitored services.
- SSH provider switch: system `ssh` client or JSch library.
- Flyway-managed schema migrations.
- H2 support for local start and PostgreSQL support through a dedicated profile.

---

## Tech Stack

| Area | Technology |
|---|---|
| Language | Java 17+ |
| Framework | Spring Boot 3.2.5 |
| Web | Spring Web MVC |
| Persistence | Spring Data JPA, Hibernate |
| Migrations | Flyway |
| Databases | H2, PostgreSQL |
| Monitoring execution | `bash`, `pgrep`, `curl`, system `ssh`, JSch |
| UI | Built-in static dashboard (`HTML`, `CSS`, `JavaScript`) |

---

## Architecture

The project is organized as a modular Spring Boot backend with a built-in admin UI.
Configuration is persisted in relational tables, while runtime check status is kept in memory for fast dashboard access.

```mermaid
flowchart LR
    UI["Dashboard / API Client"] --> REST["REST Controllers"]
    REST --> CFG["ConfigurationService"]
    REST --> MON["ServiceMonitor"]

    CFG --> DB[(H2 / PostgreSQL)]
    MON --> DB
    MON --> PROC["LinuxProcessInspector"]
    MON --> HTTP["HttpHealthChecker"]
    MON --> SHELL["HostShellExecutor"]
    SHELL --> CMD["CommandExecutor"]
    SHELL --> SSH["SshCommandProvider"]

    SHELL --> HOST1["Local Host"]
    SSH --> HOST2["Remote Host via system ssh or JSch"]
```

### Core Flow

1. Scheduler starts a monitoring cycle.
2. `ServiceMonitor` loads all services from the database.
3. For each enabled service it:
   finds a matching process and persists the latest PID,
   verifies process liveness with the PID,
   optionally runs HTTP health check,
   evaluates recovery policy,
   executes only the configured start command when the service is missing,
   leaves failed health checks visible until an operator triggers restart.
4. The latest runtime state is exposed through the dashboard API.

---

## Domain Model

The current persistent model is intentionally compact and operations-focused.

```mermaid
erDiagram
    HOST_CONFIG ||--o{ MONITORED_SERVICE : runs
    SERVICE_GROUP ||--o{ MONITORED_SERVICE : groups

    HOST_CONFIG {
        bigint id
        string name
        string connection_mode
        string address
        int ssh_port
        string ssh_user
        string private_key_path
    }

    SERVICE_GROUP {
        bigint id
        string name
        string description
    }

    MONITORED_SERVICE {
        bigint id
        string name
        bigint host_id
        bigint group_id
        string process_match
        string execution_path
        string restart_command
        string start_command
        boolean manual_restart_enabled
        bigint last_known_pid
        string health_url
        bigint health_timeout_seconds
        bigint restart_cooldown_seconds
        bigint restart_window_seconds
        int max_restarts_in_window
        boolean monitoring_enabled
    }
```

### Persistent tables

- `host_config` stores target hosts and how to connect to them.
- `service_group` stores optional logical groups for filtering.
- `monitored_service` stores process matching, command execution settings, restart logic, last known PID, and health-check settings for each service.

Command columns use `varchar(4096)` for PostgreSQL compatibility.

### Runtime-only state

`ServiceState` is not stored in the database.
It keeps transient operational data such as:

- last known status;
- last check time;
- last restart time;
- last process and health-check result;
- restart history used for cooldown and restart window enforcement;
- last message shown in the dashboard.

---

## Project Structure

```text
src/main/java/com/example/guardian
+-- api          # request/response DTOs and mapping
+-- config       # application-level configuration properties
+-- controller   # REST endpoints
+-- model        # JPA entities and runtime status models
+-- repository   # Spring Data repositories
+-- scheduler    # background scheduling entrypoint
+-- service      # monitoring, host execution, configuration logic

src/main/resources
+-- db/migration # Flyway migrations
+-- static       # built-in dashboard UI
+-- application.yml
+-- application-postgres.yml
```

---

## Quick Start

## Local Start with H2

Default startup uses file-based H2 and is the fastest way to run the project locally.

```bash
mvn spring-boot:run
```

Default H2 settings:

- JDBC URL: `jdbc:h2:file:./data/host-guardian;MODE=PostgreSQL;AUTO_SERVER=TRUE`
- H2 console: [http://localhost:8099/h2-console](http://localhost:8099/h2-console)
- App URL: [http://localhost:8099](http://localhost:8099)

On first start the app creates a default local host entry automatically.

---

## Start with PostgreSQL

The project supports PostgreSQL through the `postgres` Spring profile.

### Option 1: Start local PostgreSQL via Docker Compose

```bash
docker compose -f docker-compose.postgres.yml up -d
```

Then start the app with:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

Or if you run a packaged jar:

```bash
java -jar app.jar --spring.profiles.active=postgres
```

### Option 2: Connect to an existing PostgreSQL instance

Set environment variables:

```bash
POSTGRES_URL=jdbc:postgresql://localhost:5432/host_guardian
POSTGRES_USER=host_guardian
POSTGRES_PASSWORD=host_guardian
```

And run with profile:

```bash
SPRING_PROFILES_ACTIVE=postgres
```

Detailed PostgreSQL notes are also available in [POSTGRES.md](D:\hi-lt-atm\hi-lt-watcher\POSTGRES.md).

---

## Configuration Profiles

### Default profile

Defined in [application.yml](D:\hi-lt-atm\hi-lt-watcher\src\main\resources\application.yml):

- H2 datasource;
- Flyway validation path through standard startup;
- H2 console enabled;
- monitor interval configured through `monitor.interval`;
- SSH provider configured through `monitor.ssh.provider`;
- command timeouts configured through `monitor.command.*`.

Relevant default settings:

```yaml
monitor:
  interval: 30s
  ssh:
    provider: system # system | jsch
    strict-host-key-checking: false
  command:
    process-lookup-timeout: 5s
    pid-check-timeout: 3s
    stop-timeout: 10s
    stop-command-extra-timeout: 2s
    restart-timeout: 20s
    start-timeout: 20s
    health-command-extra-timeout: 1s
    ssh-connect-timeout: 5s
```

### PostgreSQL profile

Defined in [application-postgres.yml](D:\hi-lt-atm\hi-lt-watcher\src\main\resources\application-postgres.yml):

- PostgreSQL datasource;
- H2 console disabled;
- externalized credentials via environment variables.

---

## Remote Hosts and SSH

Host Guardian can execute checks and restart commands in two modes:

- `LOCAL` — commands are executed directly on the machine where Host Guardian runs;
- `SSH` — commands are executed on a remote Linux host using the configured SSH provider.

### SSH providers

- `system` uses the operating system `ssh` client and preserves the original behavior.
- `jsch` uses the JSch Java library and does not require the `ssh` executable on the Host Guardian machine.

Provider selection:

```yaml
monitor:
  ssh:
    provider: jsch
```

### SSH assumptions in the current version

- Target hosts are expected to be Linux hosts.
- `bash`, `pgrep`, and `curl` must be available on target hosts.
- The `system` provider additionally requires `ssh` in `PATH` on the Host Guardian machine.
- SSH uses key-based access.
- The host entry stores:
  host address,
  SSH port,
  SSH user,
  path to private key.
- For remote hosts, health checks are executed on the target host itself through `curl`.
- `strict-host-key-checking` is currently applied by the JSch provider. The system provider follows the host machine SSH client configuration.

The remote health-check behavior matters because many internal health endpoints are only available as `127.0.0.1` on the remote machine.

---

## Web Dashboard

The built-in dashboard lives at `/` and is backed entirely by the project itself.

### What the dashboard supports

- create, edit, and delete hosts;
- create, edit, and delete groups;
- create, edit, and delete monitored services;
- filter service view by one or more groups;
- manually trigger service check;
- manually trigger service restart;
- pause or resume monitoring per service;
- inspect latest status, process result, PID, Health check state, and last message;
- show busy animation while saving or executing service operations.

### Dashboard data shown per service

- service name;
- host and connection mode;
- group;
- monitoring enabled or paused state;
- calculated health status;
- process lookup result;
- last known PID;
- separate Health check state: `yes`, `no`, or `off` when no URL is configured;
- last check time;
- last restart time;
- latest operational message.

---

## REST API Overview

Base UI and API are served from the same application.

### Hosts

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/hosts` | List configured hosts |
| `POST` | `/api/hosts` | Create host |
| `PUT` | `/api/hosts/{id}` | Update host |
| `DELETE` | `/api/hosts/{id}` | Delete host |

### Groups

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/groups` | List groups |
| `POST` | `/api/groups` | Create group |
| `PUT` | `/api/groups/{id}` | Update group |
| `DELETE` | `/api/groups/{id}` | Delete group |

### Monitored services

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/services` | List services |
| `GET` | `/api/services/{id}` | Get one service |
| `POST` | `/api/services` | Create service |
| `PUT` | `/api/services/{id}` | Update service |
| `DELETE` | `/api/services/{id}` | Delete service |
| `PATCH` | `/api/services/{id}/monitoring?enabled=true|false` | Pause/resume monitoring |
| `POST` | `/api/services/{id}/restart` | Trigger checked restart/start recovery |
| `POST` | `/api/services/{id}/check` | Trigger immediate check |

### Dashboard

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/dashboard` | Dashboard summary and service state |

Group filtering is supported through repeated query params:

```text
/api/dashboard?groupId=1&groupId=2
```

---

## Example Use Cases

### Example 1: Local app running as `java -jar`

- Host mode: `LOCAL`
- Process match: `billing-service.jar`
- Execution path: `/opt/apps/billing-service`
- Start command:

```bash
nohup java -jar billing-service.jar >> /var/log/billing-service.log 2>&1 &
```

- Manual restart command enabled: no. If the service is missing, Host Guardian runs only the start command. If the operator clicks restart and the configured health-check fails, Host Guardian finds and stops the matched process, escalates to `kill -9` after timeout, then runs the start command.
- Health URL: `http://127.0.0.1:8085/actuator/health`

### Example 2: Remote app on another server

- Host mode: `SSH`
- Host address: `10.10.20.15`
- Process match: `order-worker.jar`
- Execution path: `/opt/order-worker`
- Start command:

```bash
nohup java -jar order-worker.jar >> /var/log/order-worker.log 2>&1 &
```

- Manual restart command enabled: yes
- Restart command:

```bash
./stop.sh
```

- Health URL: `http://127.0.0.1:8092/actuator/health`

---

## Why Restart Protection Matters

Blind restart loops can make incidents worse.

Host Guardian protects against that using:

- `restartCooldownSeconds` — minimum time between restarts;
- `restartWindowSeconds` — time window for counting restart attempts;
- `maxRestartsInWindow` — maximum restart count allowed inside that window.

This prevents simple restart storms when a service has a persistent startup failure.

---

## Database Migrations

Schema is managed through Flyway.

Current migrations:

- [V1__init.sql](D:\hi-lt-atm\hi-lt-watcher\src\main\resources\db\migration\V1__init.sql)
- [V2__service_start_and_pid.sql](D:\hi-lt-atm\hi-lt-watcher\src\main\resources\db\migration\V2__service_start_and_pid.sql)

Flyway runs automatically on startup for both H2 and PostgreSQL.

---

## Operational Notes

- The service is designed for Linux-oriented operational environments.
- Shell commands should be self-contained and safe to run non-interactively.
- If a monitored app requires environment variables or stdout redirection, include that in the start or manual restart command.
- If a service has an execution path, Host Guardian runs start and manual restart commands as `cd '<executionPath>' && <command>`.
- For SSH hosts, filesystem paths and local loopback health URLs are interpreted on the remote machine, not on the Host Guardian host.

---

## Current Limitations

- SSH secrets are not encrypted in the database.
- Runtime status is in memory and is not persisted across restarts.
- There is no full audit trail for configuration changes yet.
- There is no notification channel yet.
- There is no role-based access control for the dashboard or API yet.
- SSH auth currently assumes private key usage rather than password auth.
- The JSch provider supports the configured private key path; passphrase management is not modeled separately yet.
- The frontend is an embedded operational dashboard, not a separate SPA.

---

## Roadmap Ideas

- encrypt SSH-related secrets or move them into a secret manager;
- persist check history and restart history in dedicated tables;
- add alerts for Telegram, Slack, and email;
- add optimistic locking and audit metadata for configuration changes;
- add authentication and authorization for operators;
- support richer host execution strategies;
- add service templates for recurring patterns;
- add maintenance windows and scheduled pause rules.

---

## Status

This project already works as a practical operations console for host-based service monitoring, but it is still at the stage where the platform shape is emerging.

It is especially useful when:

- you have several internal services on one or more Linux hosts;
- not everything is under Kubernetes or `systemd`;
- you want a central place to observe state and trigger recovery actions;
- you need something lighter than a full infrastructure monitoring stack.

If you want, the next good README upgrade after this would be adding:

- screenshots or GIFs of the dashboard;
- sample JSON payloads for the REST API;
- a ready-to-run demo dataset;
- a small sequence diagram for `LOCAL` vs `SSH` execution.
