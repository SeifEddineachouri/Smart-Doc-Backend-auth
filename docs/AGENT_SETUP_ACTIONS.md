# SmartDoc - Agent Setup Actions Handoff

## Purpose
This document is a full handoff for another agent to complete the remaining setup work for SmartDoc AI integration.

Scope includes:
- Current Spring Boot status
- FastAPI microservice setup for GPT-based document analysis (RAG)
- Spring <-> FastAPI integration
- Angular integration requirements
- Security and environment configuration
- Verification and acceptance criteria

---

## 1) Current Project State (Verified)

### Backend stack
- Main backend: Spring Boot `3.3.5`
- Java: Corretto 23
- DB: PostgreSQL (default app config now points to Postgres)
- Migrations: Flyway enabled

### Existing migrations
- `src/main/resources/db/migration/V1__auth_core.sql`
- `src/main/resources/db/migration/V2__documents.sql`
- `src/main/resources/db/migration/V3__chat_history.sql`

### Existing API capabilities
- Auth + refresh cookie flow
- Users profile endpoints
- Document upload/list/delete
- AI endpoints:
  - `POST /api/v1/ai/questions`
  - `GET /api/v1/ai/history?page=0&size=20`

### AI current behavior
- AI answer is still placeholder in Spring service
- Chat history is persisted (question + answer per authenticated user)

### Important current config notes
- App runs on `server.port=8087`
- CORS includes Angular dev origins (`4200` included)
- Security hardening was started (env placeholders, prod toggles, cookie secure flag)
- Kafka audit events are now published from the Spring backend as a best-effort side channel.

---

## 2) Target Architecture

- Keep existing Spring app as the main API and auth gateway.
- Add a separate FastAPI microservice for AI/RAG.
- Spring calls FastAPI internally for AI question answering and document ingestion.
- Angular continues to call Spring only.

Do **not** create a second Spring application unless explicitly requested.

---

## 3) FastAPI Service - Required Deliverables

## 3.1 Create service skeleton
Create a new folder outside or beside Spring repo (recommended):
- `smartdoc-ai/`

Minimum files:
- `app/main.py`
- `app/api/routes.py`
- `app/core/config.py`
- `app/services/ingest_service.py`
- `app/services/query_service.py`
- `app/services/gemini_client.py`
- `app/services/vector_store.py`
- `app/models/schemas.py`
- `requirements.txt` (or `pyproject.toml`)
- `README.md`

## 3.2 Implement endpoints
- `GET /health`
- `POST /ingest`
  - Input: `userId`, `documentId`, and document content (file or text)
  - Output: ingestion status, chunks created
- `POST /query`
  - Input: `userId`, `question`, optional `documentIds[]`
  - Output: answer + citations

Optional but recommended:
- `GET /ingest/{documentId}/status`
- `POST /reindex/{documentId}`

## 3.3 RAG pipeline requirements
- Text extraction:
  - PDF (`pypdf`)
  - DOCX (`python-docx`)
  - TXT
- Chunking:
  - target size around 800-1200 tokens
  - overlap around 100-150 tokens
- Embeddings:
  - use Gemini embeddings (`text-embedding-004` initially)
- Retrieval:
  - top-k nearest chunks (start with k=5)
- Generation:
  - Gemini model for grounded answer (start with `gemini-3.1`)
  - enforce answer from retrieved context only
  - return citations with `documentId` and chunk/page metadata

---

## 4) Data Layer for FastAPI

Preferred option:
- PostgreSQL + `pgvector`

Required tables (example logical model):
- `ai_chunks`
  - `id`
  - `user_id`
  - `document_id`
  - `chunk_index`
  - `content`
  - `embedding` (vector)
  - `metadata_json`
  - `created_at`

Optional table:
- `ai_ingestion_jobs` for async tracking

Rules:
- Always filter retrieval by `user_id`
- If `documentIds` provided, filter by that list too

---

## 5) Spring Integration Tasks

## 5.1 Replace placeholder in AI service
Update Spring `AiQaService` to:
- call FastAPI `/query`
- map FastAPI response -> existing `AskQuestionResponseDto`
- keep saving chat history in Spring DB

## 5.2 Trigger ingestion flow
When documents are uploaded:
- trigger FastAPI `/ingest` with document metadata/content
- choose sync or async mode (async preferred for large docs)

## 5.3 Add config properties in Spring
Add environment-driven properties, e.g.:
- `app.ai.base-url`
- `app.ai.service-token`
- `app.ai.connect-timeout-ms`
- `app.ai.read-timeout-ms`

## 5.4 Resilience
Implement:
- retries with backoff
- request timeout
- clear fallback error messages
- idempotency key for ingestion

## 5.5 Kafka audit stream
- Publish document and AI lifecycle events to Kafka from the Spring backend.
- Keep Kafka non-blocking for user-facing requests.
- Use a local default broker at `localhost:9092` unless overridden with `KAFKA_BOOTSTRAP_SERVERS`.
- Keep payloads redacted: no full document text, no raw question text, no secrets.

---

## 6) Security Requirements

## 6.1 Secrets and env vars
Spring and FastAPI must use env vars only for sensitive values.

Spring critical env:
- `DB_USERNAME`
- `DB_PASSWORD`
- `APP_JWT_SECRET`
- `APP_AI_BASE_URL`
- `APP_AI_SERVICE_TOKEN`

FastAPI critical env:
- `GEMINI_API_KEY`
- `DATABASE_URL`
- `SERVICE_TOKEN` (if service-to-service auth)

## 6.2 Service-to-service auth
Recommended:
- Spring sends internal bearer token to FastAPI
- FastAPI validates token for protected endpoints

## 6.3 Data isolation and safety
- Enforce strict per-user filtering in retrieval
- Never return chunks from other users
- Redact sensitive logs (document text and secrets)

---

## 7) Frontend Angular Tasks (for reference)

Use existing handoff doc:
- `docs/FRONTEND_ANGULAR_CHAT_HISTORY_HANDOFF.md`

Additional AI integration expectations once FastAPI is connected:
- No frontend call directly to FastAPI
- Frontend still calls Spring `/api/v1/ai/*`
- UI should show citations if backend adds them
- Keep existing auth interceptor and 401 refresh flow

---

## 8) Setup and Run Checklist

## 8.1 Spring run prerequisites
- Java 23 configured
- PostgreSQL running
- Required env vars set (`DB_*`, `APP_JWT_SECRET`)

## 8.2 FastAPI run prerequisites
- Python 3.11+
- dependencies installed
- Gemini key set
- vector DB extension available

## 8.3 Suggested local run order
1. Start PostgreSQL
2. Start FastAPI service
3. Start Spring Boot app
4. Start Angular app

---

## 9) Validation Checklist

## Backend integration validation
- [ ] `POST /api/v1/ai/questions` returns non-placeholder AI response
- [ ] Chat history still saved in Spring (`/api/v1/ai/history`)
- [ ] Responses are user-isolated
- [ ] If FastAPI unavailable, Spring returns controlled error

## RAG quality validation
- [ ] Ask question with uploaded document context
- [ ] Answer references relevant content from uploaded docs
- [ ] Citations include source metadata

## Security validation
- [ ] No plaintext secrets committed
- [ ] FastAPI rejects unauthorized service calls
- [ ] Logs do not expose full private document content
- [ ] Kafka audit events are emitted without blocking uploads/questions
- [ ] Kafka payloads only contain redacted metadata

---

## 10) Acceptance Criteria (Done Definition)

This handoff is considered complete when:
1. FastAPI microservice is up with `/health`, `/ingest`, `/query`
2. Spring AI endpoint is connected to FastAPI and no longer placeholder
3. Uploaded docs are ingested and retrievable for RAG
4. Angular AI flow works without direct FastAPI calls
5. All secrets are env-based and basic security controls are active
6. End-to-end test: upload doc -> ask question -> grounded answer -> history visible

---

## 11) Optional Enhancements (After MVP)

- Async ingestion workers (Celery/RQ)
- Hybrid retrieval (keyword + vector)
- Re-ranking for better context precision
- Streaming answer tokens to frontend
- Cost/latency dashboards and per-user quota
- OCR support for scanned PDFs

