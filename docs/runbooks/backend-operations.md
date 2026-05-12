# Backend Operations Runbook

## A. Purpose

Practical first-line runbook for operating and diagnosing the PlatformCore backend email-verification path: `api-users`, `email-sender`, Nginx gateway, PostgreSQL, and Kafka/Redpanda.

This does not cover frontend operation, `api-core` business workflows, production incident command, schema design, or manual data repair.

## B. Services

| Service | Local container | What to check first |
| --- | --- | --- |
| `api-users` | `platformcore_api_users` | Auth/user logs, `auth_outbox_events`, Kafka producer config. |
| `email-sender` | `platformcore_email_sender` | Consumer logs, `email_jobs`, SMTP config, payload key. |
| gateway/Nginx | `platformcore_gateway` | `/health`, upstream errors, Swagger routing/blocks. |
| PostgreSQL | `platformcore_postgres` | `platformcore_api_users_db`, `platformcore_email_sender_db`, Flyway history. |
| Kafka/Redpanda | `platformcore_kafka` locally; Redpanda in E2E | Topic, broker availability, consumer group lag. |

Local DBs are created by `docker/postgres/init/01-create-databases.sql`: `platformcore_api_users_db`, `platformcore_api_core_db`, `platformcore_email_sender_db`.

## C. Local Startup

Run from repo root; always pass `.env`.

```powershell
# Infra only: PostgreSQL + Kafka + Mailpit
docker compose --env-file .\.env -f .\docker\compose\docker-compose.local.yml --profile infra up -d

# Backend email path, no gateway
docker compose --env-file .\.env -f .\docker\compose\docker-compose.local.yml --profile infra --profile users --profile email up -d --build

# Full stack with gateway
docker compose --env-file .\.env -f .\docker\compose\docker-compose.local.yml --profile full up -d --build

# Status and logs
docker compose --env-file .\.env -f .\docker\compose\docker-compose.local.yml --profile full ps
docker logs platformcore_api_users --tail 100
docker logs platformcore_email_sender --tail 100
docker logs platformcore_gateway --tail 100
```

Health and routing:

```powershell
curl.exe http://localhost/health
curl.exe http://localhost/v1/auth/csrf
```

Expected gateway `/health` response is `ok`. Local Swagger for `api-users` via gateway:

```text
http://localhost/api-users/swagger-ui/index.html
http://localhost/api-users/v3/api-docs
```

Current Compose gap: `--profile gateway` with only `infra`, `users`, and `email` is invalid because `gateway` depends on `api-core`, `frontend-client`, and `frontend-admin`. Use `--profile full` or also include `--profile core --profile front --profile gateway`.

## D. Test Commands

```powershell
# api-users unit/integration tests and cross-service E2E
cd backend\api-users
.\gradlew.bat spotlessCheck
.\gradlew.bat test
.\gradlew.bat crossServiceE2eTest

# email-sender unit/integration tests
cd ..\email-sender
.\gradlew.bat spotlessCheck
.\gradlew.bat test
```

CI uses the Linux equivalents with `./gradlew`. `api-users` and `email-sender` integration tests use PostgreSQL Testcontainers. `crossServiceE2eTest` uses PostgreSQL containers plus Redpanda `docker.redpanda.com/redpandadata/redpanda:v24.2.5` and topic `platformcore.email.commands.e2e`. If Docker/Testcontainers is unavailable, expect container-backed tests to fail or be skipped depending on annotation; Docker is required for CI-parity.

## E. `api-users` Outbox Troubleshooting

Table: `auth_outbox_events` in `platformcore_api_users_db`.

```powershell
docker exec -it platformcore_postgres psql -U platformcore_api_users_user -d platformcore_api_users_db
```

```sql
SELECT id, aggregate_type, aggregate_id, event_type, topic, key, status,
       attempts, left(last_error, 300) AS last_error, created_at, updated_at, sent_at
FROM auth_outbox_events
ORDER BY created_at DESC
LIMIT 20;

SELECT status, count(*)
FROM auth_outbox_events
GROUP BY status
ORDER BY status;
```

Real states:

| Status | Meaning |
| --- | --- |
| `PENDING` | Inserted by `api-users`; eligible after `platformcore.outbox.publisher.min-age-ms`. |
| `IN_PROGRESS` | Claimed by `OutboxPublisherJob`; should be short-lived. |
| `SENT` | Kafka send acknowledged. |
| `FAILED` | Publish failed; retryable while attempts remain. |
| `DEAD` | Terminal publish failure after `platformcore.outbox.publisher.max-attempts`. |

Logs to search in `platformcore_api_users`: `email_verification_issued`, `email_verification_resend_rate_limited`, `outbox_event_published` (debug), `outbox_event_failed`, `outbox_event_dead`.

Do not manually edit `payload_json`, reset `attempts`, or move statuses without an approved repair plan. Prefer natural scheduled retry or controlled replay.

## F. `email-sender` Job Troubleshooting

Table: `email_jobs` in `platformcore_email_sender_db`.

```powershell
docker exec -it platformcore_postgres psql -U platformcore_email_sender_user -d platformcore_email_sender_db
```

```sql
SELECT event_id, correlation_id, type, template, to_email_fp, status,
       attempts, left(last_error, 300) AS last_error, created_at, updated_at, sent_at
FROM email_jobs
ORDER BY created_at DESC
LIMIT 20;

SELECT status, count(*)
FROM email_jobs
GROUP BY status
ORDER BY status;
```

Real states:

| Status | Meaning |
| --- | --- |
| `PENDING` | Durable job created before first send attempt completed. |
| `SENT` | SMTP delivery call completed and job was marked sent. |
| `FAILED` | Send/render/decrypt/deserialization failure persisted for retry. |
| `DEAD` | Terminal retry failure after `platformcore.email.retry.max-attempts`. |

There is no `IN_PROGRESS` state in `email_jobs`; retry claiming refreshes `updated_at` while status remains `PENDING` or `FAILED`.

Logs to search in `platformcore_email_sender`: `email_command_sent`, `email_command_send_failed`, `email_command_invalid`, `email_command_processing_failed`, `duplicate_event`, `email_command_ignored`, `retry_batch_size`, `retry_attempt`, `retry_failed`.

SMTP diagnosis: check `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_SMTP_AUTH`, `SPRING_MAIL_SMTP_STARTTLS`, `PLATFORMCORE_MAIL_FROM`, `PLATFORMCORE_SUBJECT_EMAIL_VERIFICATION`. Local Mailpit UI is usually `http://localhost:8025`.

Payload/key/config diagnosis: check `PLATFORMCORE_EMAIL_PAYLOAD_KEY_B64`; invalid Kafka payloads are logged as `email_command_invalid reason=<code> sha=<digest> size=<bytes>` and acknowledged/discarded. Supported command pair is `EMAIL_VERIFICATION_REQUESTED` with template `EMAIL_VERIFICATION`.

Do not decrypt `payload_enc`, write raw email addresses into notes, or update job status by hand without approval.

## G. Kafka/Redpanda Troubleshooting

Local Compose uses service `kafka` / container `platformcore_kafka`. E2E uses Redpanda Testcontainers.

```powershell
docker compose --env-file .\.env -f .\docker\compose\docker-compose.local.yml --profile infra ps kafka
docker logs platformcore_kafka --tail 100
docker exec -it platformcore_kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker exec -it platformcore_kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group platformcore-email-sender
```

Email command topic defaults to `platformcore.email.commands`. Property: `platformcore.kafka.topics.email-commands`; env var: `PLATFORMCORE_TOPIC_EMAIL_COMMANDS`.

Differentiate failure modes:

- Publish failure: `auth_outbox_events.status` is `FAILED` or `DEAD`; no matching `email_jobs.event_id`; `api-users` logs outbox failure.
- Consume/processing failure: outbox is `SENT`; `email_jobs` is missing, `FAILED`, or `DEAD`; check `email-sender` invalid/processing/SMTP logs and consumer-group lag.

## H. Database Troubleshooting

Run in each service DB:

```sql
SELECT installed_rank, version, description, type, script, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 20;
```

DB separation:

- `platformcore_api_users_db`: `auth_users`, `auth_roles`, `auth_users_roles`, `auth_verification_tokens`, `auth_outbox_events`.
- `platformcore_email_sender_db`: `email_jobs`.

Do not edit migrations already applied to persistent environments. Add a new versioned migration instead.

## I. Swagger/OpenAPI Troubleshooting

Local gateway routes for `api-users`:

```text
http://localhost/api-users/v3/api-docs
http://localhost/api-users/v3/api-docs.yaml
http://localhost/api-users/swagger-ui/index.html
```

Global routes intentionally return `404` locally: `/v3/api-docs`, `/v3/api-docs/`, `/v3/api-docs.yaml`, `/swagger-ui`, `/swagger-ui/`, `/swagger-ui.html`. Shared preprod Nginx blocks both global and namespaced Swagger/OpenAPI routes.

Backend properties:

```properties
springdoc.api-docs.enabled=${PLATFORMCORE_OPENAPI_ENABLED:false}
springdoc.swagger-ui.enabled=${PLATFORMCORE_SWAGGER_UI_ENABLED:false}
```

`application-local.properties` enables both. If UI loads but cannot find `/v3/api-docs`, check active profile/env vars, use the namespaced URL, verify `docker/nginx/default.conf` is mounted, and inspect `platformcore_gateway` upstream errors to `api-users:8080`.

## J. Logging and Sensitive Data

Never log raw email, OTP, JWT, CSRF token, verification-session cookie, full Kafka payload, encrypted payload material, passwords, or secrets.

Safe correlation data: `eventId`, `outboxId`, `aggregateId`, `userId`, `correlationId`, `emailFingerprint`, `to_email_fp`, payload `sha`, status, attempts, timestamps, topic names, type/template, and bounded sanitized `last_error`.

## K. Safe Manual Operations

Safe inspections: `docker compose ps`, `docker logs ... --tail N`, read-only SQL `SELECT`, Kafka topic list, consumer-group describe, and Mailpit UI in local development.

Changes requiring approval: status updates, Kafka replay, row deletion, Flyway history edits, applied migration edits, and rotation of `JWT_SECRET`, `PLATFORMCORE_EMAIL_PAYLOAD_KEY_B64`, SMTP credentials, or DB credentials.

Prefer natural retries when rows are `FAILED` and attempts remain. Use controlled replay only when durable state cannot progress by itself.

## L. Escalation Checklist

1. Health/routing: `curl.exe http://localhost/health`, `docker compose ... ps`.
2. Logs: `platformcore_api_users`, `platformcore_email_sender`, `platformcore_gateway`, `platformcore_kafka`.
3. DB counts: `auth_outbox_events` and `email_jobs` grouped by status.
4. Kafka: broker availability, topic list, `platformcore-email-sender` consumer group.
5. Config/env: DB URLs/users, `KAFKA_BOOTSTRAP_SERVERS`, `PLATFORMCORE_TOPIC_EMAIL_COMMANDS`, SMTP vars, `PLATFORMCORE_EMAIL_PAYLOAD_KEY_B64`, OpenAPI vars, retry tuning vars.
6. CI/E2E: especially `backend/api-users` `crossServiceE2eTest`.

## Operational Gaps / Follow-ups

- `api-users` permits `GET /health` in security config, but no health controller or actuator health endpoint was found; gateway `/health` only proves Nginx is responding.
- `email-sender` has `/health`, but local Compose does not publish its port.
- Current `gateway` profile cannot be used with only `infra`, `users`, and `email` because Compose `depends_on` also references `api-core` and both frontends.
- Compose diagnostics on this workstation emitted `~/.docker/config.json: Access denied`; fix local Docker CLI permissions if it blocks operations.

## Kafka topic initialization

Kafka topics are not auto-created in pre-production/production environments.

This is intentional: disabling topic auto-creation avoids silently creating wrong topics due to typos or misconfigured service properties.

The email command flow requires this topic:

```text
platformcore.email.commands
```

Create it manually when provisioning a new Kafka environment:

```bash
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create \
  --if-not-exists \
  --topic platformcore.email.commands \
  --partitions 1 \
  --replication-factor 1
```

Verify that the topic exists:

```bash
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

Describe the topic:

```bash
docker exec -it kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic platformcore.email.commands
```

Expected usage:

- `api-users` publishes email command events to `platformcore.email.commands`.
- `email-sender` consumes from `platformcore.email.commands`.

If `api-users` logs this error:

```text
UNKNOWN_TOPIC_OR_PARTITION
Topic platformcore.email.commands not present in metadata after 60000 ms
```

then Kafka is reachable, but the topic does not exist or the configured topic name does not match.

Check that both services use the same topic value:

```text
PLATFORMCORE_TOPIC_EMAIL_COMMANDS=platformcore.email.commands
```

Do not enable Kafka topic auto-creation in pre-production/production unless there is a deliberate operational reason.