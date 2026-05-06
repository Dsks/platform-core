# Platform Core

Repositorio monorepo de con arquitectura de microservicios.

---

## Componentes

### Backends (Spring Boot / Java)

- **api-users**  
  Gestión de usuarios y autenticación.

- **api-core**  
  Core de la aplicación.

- **email-sender**  
  Envío de emails (consumer de Kafka).

---

### Frontends (Angular + Nx, SSR)

- **frontend-client**  
  Web para usuarios.

- **frontend-admin**  
  Panel de administración.

---

### Infraestructura local

- **PostgreSQL**  
  1 instancia con 3 bases de datos (una por microservicio).

- **Kafka**  
  Mensajería entre microservicios.

- **Mailpit**  
  SMTP y UI para pruebas de envío de emails.

---

## Requisitos

- Docker
- Docker Compose v2
- Archivo `.env` en la raíz del repositorio

---

## Docker Compose por perfiles

El archivo principal de Docker Compose se encuentra en:

    docker/compose/docker-compose.local.yml

Para evitar problemas con rutas y variables de entorno, **siempre se usa `--env-file .env`** al ejecutar los comandos.

---

## Comandos

### 1) Solo infraestructura (DB + Kafka + Mailpit)

    docker compose --env-file .\.env -f .\docker\compose\docker-compose.local.yml --profile infra up -d

---

### 2) Infra + users + email + gateway  
(para probar registro y envío de mails end-to-end)

    docker compose --env-file .\.env -f .\docker\compose\docker-compose.local.yml --profile infra --profile users --profile email --profile gateway up -d --build

---

### 3) Infra + core + gateway  
(si estás trabajando solo recetas / menús)

    docker compose --env-file .\.env -f .\docker\compose\docker-compose.local.yml --profile infra --profile core --profile gateway up -d --build

---

### 4) Todo (stack completo)

    docker compose --env-file .\.env -f .\docker\compose\docker-compose.local.yml --profile full up -d --build

---

### 5) Reset total  
(si cambiaste el init SQL de Postgres)

    docker compose --env-file .\.env -f .\docker\compose\docker-compose.local.yml down -v

⚠️ Este comando elimina los volúmenes. Se perderán los datos locales.

---

## URLs útiles en local

- API Gateway:  
  http://localhost

- API Users:  
  http://localhost/v1/users

- API Core:  
  http://localhost/v1/core

- Frontend client:  
  http://localhost

- Frontend admin:  
  http://localhost/admin

- Mailpit UI:  
  http://localhost:8025

---

## OpenAPI / Swagger via gateway

- En local/dev, Swagger se expone solo con prefijo por servicio para evitar colisiones:
      http://localhost/api-users/v3/api-docs
      http://localhost/api-users/swagger-ui/index.html

- Las rutas globales como `/v3/api-docs` y `/swagger-ui/` deben devolver `404`.
- `api-core` no publica Swagger actualmente; sus rutas namespaced quedan bloqueadas hasta que el servicio incorpore OpenAPI.
- `email-sender` no tiene exposicion Swagger publica.
- En pre/prod compartido, el gateway debe bloquear Swagger/OpenAPI por defecto, incluso si un backend lo habilita por error.

---

## Notas

- Los scripts de inicialización de Postgres se encuentran en:  
      docker/postgres/init

- Los scripts de init **solo se ejecutan la primera vez** que el volumen está vacío.
- Si modificas esos scripts, debes ejecutar el **reset total**.

---
