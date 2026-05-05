# SmartDoc - Microservice Boundaries and De-duplication

## Goal
Define a clean split into 3 services and avoid duplicate implementation across folders.

## Services

1. `smartdoc-ai` (FastAPI, Python)
- Role: Gemini/RAG engine.
- Owns:
  - `POST /ingest`
  - `POST /query`
  - `GET /health`
- Owns document chunking, retrieval, LLM calls.
- Must not implement auth or user session logic.

2. `AiSmartDoc` (Spring, Java)
- Role: AI gateway/backend-for-backend to FastAPI.
- Owns:
  - `/api/v1/ai/questions`
  - `/api/v1/ai/ingest`
  - `/api/v1/ai/history` (if gateway keeps AI conversation projection)
  - `/api/v1/ai/health`
- Should call `smartdoc-ai` only for AI operations.
- Must not implement provider logic directly.

3. Auth service (Spring, Java) - current app: `smartdoc`
- Role: sign-up/sign-in/refresh/sign-out and JWT validation.
- Owns auth endpoints and user identity lifecycle.
- Must not duplicate AI ingestion/query logic.
- Also owns the Kafka audit producer for document and AI request events.

## Current duplicate work found and fixed

- Duplicate setup handoff document existed in both:
  - `docs/AGENT_SETUP_ACTIONS.md`
  - `AiSmartDoc/AGENT_SETUP_ACTIONS.md`
- Fix applied:
  - Keep full canonical handoff in `docs/AGENT_SETUP_ACTIONS.md`.
  - Keep only service-local actions in `AiSmartDoc/AGENT_SETUP_ACTIONS.md`.

## Integration contract

- Frontend calls Spring APIs only.
- `smartdoc` keeps auth endpoints and user identity lifecycle.
- `AiSmartDoc` calls `smartdoc-ai` with internal service token.
- Auth token/JWT validation remains in Spring auth service.
- Kafka is used as a best-effort audit stream from the Spring backend to support future analytics and traceability.

## Environment variables

### AiSmartDoc
- `APP_AI_BASE_URL`
- `APP_AI_SERVICE_TOKEN`
- `APP_AI_CONNECT_TIMEOUT_MS`
- `APP_AI_READ_TIMEOUT_MS`

### smartdoc-ai
- `GEMINI_API_KEY`
- `SERVICE_TOKEN`
- `MODEL_NAME`
- `CHUNK_SIZE_WORDS`
- `CHUNK_OVERLAP_WORDS`
- `RETRIEVAL_TOP_K`

## Next migration step (recommended)

1. Move all auth endpoints and JWT classes into dedicated auth service module.
2. Keep AI endpoints in `AiSmartDoc` only.
3. Keep Gemini and vector logic in `smartdoc-ai` only.
4. Add a shared API contract doc (OpenAPI or markdown) and version it.


