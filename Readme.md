# Platform Core

Platform Core es un proyecto full stack en desarrollo activo centrado en una arquitectura modular con backend en Spring Boot, frontend en Angular y comunicación asíncrona entre servicios.

Actualmente el proyecto está centrado principalmente en la parte backend de usuarios, autenticación, verificación de email y procesamiento asíncrono de comandos de email. El frontend y algunos módulos funcionales todavía están en una fase inicial.

El repositorio refleja una base técnica en evolución, con mayor madurez actualmente en la parte backend que en el frontend.

---

## Objetivo del proyecto

El objetivo general es construir una plataforma compuesta por:

- servicios backend independientes;
- aplicaciones frontend separadas para cliente y administración;
- base de datos PostgreSQL;
- comunicación asíncrona mediante Kafka;
- envío de emails desacoplado del flujo principal de usuarios;
- entorno local reproducible mediante Docker Compose.

El flujo más avanzado actualmente es el de registro de usuarios, verificación de email y envío asíncrono de emails.

---

## Stack tecnológico

### Backend

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Security
- Spring JDBC
- Spring Kafka
- Flyway
- PostgreSQL
- Lombok
- Gradle Kotlin DSL
- JUnit
- Testcontainers
- Jacoco
- Spotless con Google Java Format

### Frontend

- Angular 21
- Nx 22
- TypeScript
- SCSS
- Vitest
- Playwright
- ESLint
- Prettier

### Infraestructura local

- Docker Compose
- PostgreSQL 16
- Apache Kafka 4
- Mailpit
- Nginx

---

## Estructura general del repositorio

```text
.
├── backend
│   ├── api-users
│   ├── api-core
│   └── email-sender
│
├── frontend
│   ├── apps
│   │   ├── qomo-web
│   │   └── qomo-admin
│   ├── package.json
│   └── nx.json
│
└── docker
    ├── compose
    │   └── docker-compose.local.yml
    ├── nginx
    └── postgres
```

---

## Módulos principales

### `backend/api-users`

Servicio responsable de la parte de usuarios, autenticación y verificación de email.

Actualmente incluye:

- registro de usuarios;
- login;
- JWT en cookie HTTP-only;
- CSRF mediante cookie/token;
- roles de usuario;
- endpoints protegidos por rol;
- verificación de email mediante código;
- cookie separada para sesión de verificación;
- respuestas genéricas en flujos sensibles para reducir enumeración de usuarios;
- publicación de eventos mediante patrón outbox;
- migraciones Flyway para usuarios, roles, tokens de verificación y outbox.

Endpoints principales actualmente:

```text
POST /v1/auth/register
POST /v1/auth/login
GET  /v1/auth/csrf
POST /v1/users/verify-email
POST /v1/users/verification/resend
POST /v1/users
```

El endpoint `POST /v1/users` está restringido a roles `ADMIN` o `SUPERADMIN`.

---

### `backend/email-sender`

Servicio responsable de consumir comandos de email desde Kafka y procesar el envío.

Actualmente incluye:

- consumidor Kafka para comandos de email;
- validación básica del payload recibido;
- decisión explícita de cuándo hacer `ack` y cuándo no;
- descarte con `ack` de mensajes inválidos;
- persistencia del estado del procesamiento;
- worker periódico para reintentar emails fallidos;
- configuración SMTP pensada para usar Mailpit en local.

El caso principal implementado actualmente es el envío de emails de verificación.

---

### `backend/api-core`

Servicio backend separado para la parte core de la plataforma.

Actualmente está configurado como servicio Spring Boot independiente. En el estado actual del proyecto esta parte no esta implementada.

---

### `frontend/qomo-web`

Aplicación Angular principal para cliente.

Actualmente existe dentro del workspace Nx y tiene configuración de build, serve, lint y test.

En el estado actual, las rutas de la aplicación todavía están vacías, por lo que debe considerarse una base inicial de frontend.

---

### `frontend/qomo-admin`

Aplicación Angular para administración.

Actualmente existe dentro del workspace Nx y tiene configuración de build, serve, lint y test. También define configuración específica para publicarse bajo `/admin/`.

Igual que `qomo-web`, todavía está en fase inicial.

---

## Arquitectura general

La arquitectura actual separa responsabilidades por servicio:

```text
Frontend Angular
   |
   v
Nginx Gateway
   |
   ├── api-users
   │     |
   │     ├── PostgreSQL
   │     └── Outbox table
   │             |
   │             v
   │          Kafka
   │             |
   │             v
   ├── email-sender
   │     |
   │     ├── PostgreSQL
   │     └── SMTP / Mailpit
   │
   └── api-core
         |
         └── PostgreSQL
```

El flujo de email está desacoplado del flujo principal de usuarios:

1. `api-users` registra o gestiona una acción que requiere email.
2. `api-users` guarda un evento pendiente en la tabla outbox.
3. Un job interno reclama eventos publicables.
4. El evento se publica en Kafka.
5. `email-sender` consume el comando.
6. `email-sender` valida, procesa y persiste el resultado.
7. Si el envío falla de forma recuperable, un worker periódico lo reintenta.

---

## Comunicación asíncrona y outbox

El servicio `api-users` usa un patrón outbox para publicar eventos de forma desacoplada.

La tabla `auth_outbox_events` contempla estos estados:

```text
PENDING
IN_PROGRESS
SENT
FAILED
DEAD
```

El job de publicación:

- reclama eventos en lote;
- usa una edad mínima antes de publicar;
- marca eventos como `IN_PROGRESS`;
- publica el evento en Kafka;
- marca como `SENT` si la publicación fue correcta;
- marca como `FAILED` si hay error recuperable;
- marca como `DEAD` cuando se superan los intentos máximos.

El claim de eventos usa `FOR UPDATE SKIP LOCKED`, lo que permite evitar que varios workers reclamen el mismo evento de forma concurrente.

---

## Seguridad

El módulo `api-users` incluye una configuración de seguridad stateless basada en cookies.

Características actuales:

- sesión stateless;
- JWT guardado en cookie HTTP-only;
- CSRF mediante cookie/token;
- endpoints públicos delimitados;
- endpoints protegidos mediante autenticación;
- alta de usuarios restringida a roles `ADMIN` y `SUPERADMIN`;
- cookie separada para sesiones de verificación de email;
- respuestas genéricas en verificación/reenvío para evitar filtrar información innecesaria.

Variables relevantes:

```text
JWT_SECRET
JWT_EXPIRATION_MS
QOMO_COOKIE_SECURE
QOMO_COOKIE_SAMESITE
QOMO_CSRF_COOKIE_SECURE
QOMO_CSRF_COOKIE_SAMESITE
QOMO_VERIFICATION_COOKIE_NAME
QOMO_VERIFICATION_COOKIE_SECURE
QOMO_VERIFICATION_COOKIE_SAMESITE
```

---

## Requisitos previos

Para ejecutar el proyecto en local se necesita:

- Docker y Docker Compose;
- Java 21;
- Node.js compatible con Angular 21;
- npm;
- acceso a los puertos usados por los servicios locales:
  - PostgreSQL;
  - Kafka;
  - Mailpit;
  - frontend web;
  - frontend admin;
  - gateway Nginx.

---

## Configuración local

El entorno local se define principalmente en:

```text
docker/compose/docker-compose.local.yml
```

El compose usa perfiles para levantar partes concretas del sistema:

```text
infra
users
core
email
front
gateway
full
```

Antes de ejecutar el entorno local hay que definir las variables de entorno requeridas.

Ejemplo orientativo:

```env
# General
IMAGE_TAG=local
SPRING_PROFILES_ACTIVE=local
HOST=localhost

# PostgreSQL
POSTGRES_ADMIN_USER=postgres
POSTGRES_ADMIN_PASSWORD=postgres
POSTGRES_DB=qomo
POSTGRES_PORT=5432

# api-users DB
USERS_DB_URL=jdbc:postgresql://postgres:5432/qomo_users
USERS_DB_USER=qomo_users
USERS_DB_PASSWORD=qomo_users_password

# api-core DB
CORE_DB_URL=jdbc:postgresql://postgres:5432/qomo_core
CORE_DB_USER=qomo_core
CORE_DB_PASSWORD=qomo_core_password

# email-sender DB
EMAIL_DB_URL=jdbc:postgresql://postgres:5432/qomo_email
EMAIL_DB_USER=qomo_email
EMAIL_DB_PASSWORD=qomo_email_password

# Security
JWT_SECRET=change-me-to-a-long-random-secret
JWT_EXPIRATION_MS=86400000

# Bootstrap
SUPERADMIN_EMAIL=superadmin@qomo.app
SUPERADMIN_INITIAL_PASSWORD=change-me

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
QOMO_TOPIC_EMAIL_COMMANDS=qomo.email.commands

# Mailpit / SMTP
MAIL_WEB_PORT=8025
SPRING_MAIL_HOST=mailpit
SPRING_MAIL_PORT=1025
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
SPRING_MAIL_SMTP_AUTH=false
SPRING_MAIL_SMTP_STARTTLS=false

# Email payload key
QOMO_EMAIL_PAYLOAD_KEY_B64=change-me
```

> Nota: este bloque es una guía inicial. Conviene mantener un `.env.example` actualizado con los valores reales esperados por el entorno local.

---

## Ejecución con Docker Compose

Desde la raíz del repositorio:

```bash
docker compose \
  -f docker/compose/docker-compose.local.yml \
  --profile infra \
  up -d
```

Esto levanta la infraestructura base:

- PostgreSQL;
- Kafka;
- Mailpit.

Para levantar todo el entorno definido en compose:

```bash
docker compose \
  -f docker/compose/docker-compose.local.yml \
  --profile full \
  up --build
```

También se pueden levantar perfiles concretos:

```bash
# api-users
docker compose \
  -f docker/compose/docker-compose.local.yml \
  --profile users \
  up --build

# email-sender
docker compose \
  -f docker/compose/docker-compose.local.yml \
  --profile email \
  up --build

# frontends
docker compose \
  -f docker/compose/docker-compose.local.yml \
  --profile front \
  up --build
```

Mailpit debería quedar accesible en el puerto configurado por `MAIL_WEB_PORT`.

---

## Flujo principal implementado

El flujo funcional más completo actualmente es el de registro y verificación de email:

1. Un usuario solicita registro mediante `POST /v1/auth/register`.
2. `api-users` procesa la solicitud sin exponer detalles innecesarios al cliente.
3. Se crea una sesión/código de verificación.
4. Se registra un evento en la tabla outbox.
5. El job de outbox publica el evento en Kafka.
6. `email-sender` consume el comando desde Kafka.
7. `email-sender` valida el payload y procesa el envío.
8. En local, el email puede revisarse mediante Mailpit.
9. El usuario confirma el código mediante `POST /v1/users/verify-email`.
10. Si la verificación es correcta, se limpia la cookie de verificación.

---

## Estado actual del proyecto

### Implementado o avanzado

- Estructura de monorepo con backend, frontend e infraestructura local.
- Tres servicios backend independientes:
  - `api-users`;
  - `api-core`;
  - `email-sender`.
- Configuración Docker Compose local con PostgreSQL, Kafka, Mailpit, Nginx, backends y frontends.
- Registro, login y verificación de email en `api-users`.
- Seguridad stateless con JWT en cookie HTTP-only.
- CSRF con cookie/token.
- Roles `SUPERADMIN`, `ADMIN` y `USER`.
- Migraciones Flyway en `api-users`.
- Outbox para publicación de eventos desde `api-users`.
- Publicación de comandos de email hacia Kafka.
- Consumidor Kafka en `email-sender`.
- Worker de reintentos para emails fallidos.
- Tests unitarios/integración en backend.
- Configuración de cobertura con Jacoco.
- Formateo Java con Spotless.
- Workspace Nx con dos aplicaciones Angular:
  - `qomo-web`;
  - `qomo-admin`.

### En desarrollo o pendiente

- Completar funcionalidad real de `api-core`.
- Desarrollar las pantallas y rutas principales de `qomo-web`.
- Desarrollar las pantallas y rutas principales de `qomo-admin`.
- Añadir o mantener un `.env.example` con todas las variables necesarias.
- Documentar mejor los endpoints disponibles.
- Añadir documentación de flujos técnicos y decisiones de arquitectura.
- Confirmar o añadir pipeline CI con GitHub Actions.
- Revisar configuración de producción frente a configuración local.
- Endurecer observabilidad, logging y métricas.
- Añadir más pruebas end-to-end de sistema completo.

---

## Notas importantes

- El proyecto está en desarrollo activo.
- La parte backend de usuarios y email está más avanzada que la parte frontend.
- El frontend existe y está configurado, pero actualmente no representa todavía una aplicación funcional completa.
- La comunicación asíncrona existe principalmente en el flujo `api-users -> outbox -> Kafka -> email-sender`.
- La configuración local depende de variables de entorno que deben mantenerse documentadas.
