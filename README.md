# SmartDoc Backend (Spring Boot)

Backend API for SmartDoc with JWT auth, user profile/language, document metadata upload, and AI question contract endpoint (placeholder).

## What is implemented

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/signin`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/signout`
- `GET /api/v1/users/me`
- `PATCH /api/v1/users/me/language`
- `POST /api/v1/documents/upload`
- `GET /api/v1/documents`
- `DELETE /api/v1/documents/{id}`
- `POST /api/v1/ai/questions` (placeholder until FastAPI microservice)
- Kafka audit stream for document and AI lifecycle events

## Tech

- Java 17
- Spring Boot 3.3.x
- Spring Security + JWT (`jjwt`)
- Spring Data JPA
- Flyway migrations
- H2 (default local profile), PostgreSQL-ready
- SpringDoc OpenAPI (`/swagger-ui/index.html`)

## Local run

```powershell
cd C:\Users\seifa\Documents\smartdoc
.\mvnw.cmd spring-boot:run
```

## One-click relaunch and verification

To stop any existing listeners, relaunch the three-app stack, and run the AI integration checks:

```powershell
cd C:\Users\seifa\Documents\smartdoc
powershell -ExecutionPolicy Bypass -File .\relaunch-smartdoc-stack.ps1
```

This wrapper:

- clears ports `8000`, `8087`, and `8088` before startup
- relaunches `smartdoc-ai`, `AiSmartDoc`, and `smartdoc`
- reuses the shared service token stored in `logs\service-token.txt`
- runs the FastAPI + Spring AI integration checks
- verifies the root OpenAPI document at `/v3/api-docs`

## Run tests

```powershell
cd C:\Users\seifa\Documents\smartdoc
.\mvnw.cmd test
```

## Configuration notes

Main configuration is in `src/main/resources/application.properties`.

- JWT secret: `app.jwt.secret`
- Access token TTL: `app.jwt.access-token-seconds`
- Refresh token policy: `app.auth.refresh-short-days`, `app.auth.refresh-long-days`
- CORS: `app.cors.allowed-origins`
- Document storage path: `app.documents.storage-dir`
- Kafka audit stream: `app.kafka.bootstrap-servers`, `app.kafka.audit-topic`, `app.kafka.client-id`

For production, set environment variables or externalized properties for DB credentials and JWT secret.

## AI microservice integration

`POST /api/v1/ai/questions` currently returns a placeholder response while preserving the final request/response contract expected by the frontend. Replace `AiQaService` implementation with an HTTP client call to FastAPI microservice when ready.

## Kafka audit stream

The backend now publishes best-effort audit events for document uploads/deletes and AI question flows. The default broker is `localhost:9092`; override it with `KAFKA_BOOTSTRAP_SERVERS` when running against a real cluster.

