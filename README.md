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

## Docker Compose stack

This repository now includes a Docker Compose setup for the full SmartDoc stack:

- `smartdoc` on port `8087`
- `AiSmartDoc` on port `8088`
- `smartdoc-ai` on port `8000`
- `postgres` on port `5432`
- `kafka` on port `9092`

### Start everything

```powershell
cd C:\Users\seifa\Documents\smartdoc
Copy-Item .env.example .env
docker compose up --build
```

### Start in detached mode

```powershell
cd C:\Users\seifa\Documents\smartdoc
docker compose up -d --build
```

### Stop everything

```powershell
cd C:\Users\seifa\Documents\smartdoc
docker compose down
```

### Notes

- Copy `.env.example` to `.env` and fill in your own values before starting the stack.
- Uploaded files are persisted through the host `uploads/` folder.
- Kafka is included for the audit stream; `smartdoc` uses `kafka:9092` inside Compose by default.

### End-to-end verification

Use the smoke test script to verify the full stack after a build or a Docker Hub publish:

```powershell
cd C:\Users\seifa\Documents\smartdoc
.\verify-smartdoc-stack.ps1 -SkipRemotePull
```

To also verify that the published Docker Hub images can be pulled successfully:

```powershell
cd C:\Users\seifa\Documents\smartdoc
.\verify-smartdoc-stack.ps1 -DockerHubNamespace <your-dockerhub-namespace>
```

The script checks:

- local images exist or can be rebuilt
- PostgreSQL is healthy and reachable
- Kafka is healthy and responding
- `smartdoc-ai` health endpoint
- `AiSmartDoc` health endpoint
- `smartdoc` OpenAPI endpoint
- optional Docker Hub pulls for the three published images

## Docker Hub publishing

The repository includes a GitHub Actions workflow that publishes the three service images to Docker Hub.
It validates the required secrets first, uses a safe namespace fallback, and publishes only from trusted Git events.

The published images are:

- `smartdoc` → `docker.io/<namespace>/smartdoc-backend-auth`
- `AiSmartDoc` → `docker.io/<namespace>/smartdoc-backend-fast-api`
- `smartdoc-ai` → `docker.io/<namespace>/smartdoc-backend-python`

### Required GitHub secrets

Configure these secrets in the GitHub repository before enabling the workflow:

- `DOCKERHUB_USERNAME`
- `DOCKERHUB_TOKEN`

Optional repository variable:

- `DOCKERHUB_NAMESPACE` — overrides the Docker Hub namespace used for the published images. If omitted, the workflow falls back to `DOCKERHUB_USERNAME`.

Make sure the target repositories already exist in Docker Hub under that namespace.

### Tags that are published

- `latest` on pushes to `main`
- `sha-<git-sha>` on every publication
- `vX.Y.Z` from the Git tag itself, plus `X.Y.Z` and `X.Y` aliases when pushing a Git tag like `v1.2.3`

### How to trigger

- push to `main`
- push a version tag such as `v1.0.0`
- run the workflow manually from the GitHub Actions tab

### Pull the images

```powershell
docker pull <namespace>/smartdoc-backend-auth:latest
docker pull <namespace>/smartdoc-backend-fast-api:latest
docker pull <namespace>/smartdoc-backend-python:latest
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
