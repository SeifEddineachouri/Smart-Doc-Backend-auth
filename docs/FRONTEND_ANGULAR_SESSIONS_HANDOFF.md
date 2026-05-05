# SmartDoc Frontend - Session-Based Chats Handoff

## Objective
Enable true per-session chats in Angular: each session has its own uploads, AI answers, and history. Sessions appear in the sidebar and can be created/selected/deleted.

---

## Quick checklist (frontend)
- [ ] Create a new session before starting a chat: `POST /api/v1/sessions`
- [ ] Store `sessionId` in the client state
- [ ] Pass `sessionId` on upload, list docs, ask question, and history calls
- [ ] Use `GET /api/v1/sessions` to populate the sidebar
- [ ] Ensure `Authorization: Bearer <token>` is sent for all calls

---

## New endpoints

### 1) Create session
**POST** `/api/v1/sessions`
- Body (optional):
```json
{ "name": "New chat" }
```
- Response:
```json
{
  "id": "9d7c2e3e-0a9a-4ec6-b4f6-4ef7f5b4d9f5",
  "name": "New chat",
  "createdAt": "2026-04-28T23:50:00Z",
  "updatedAt": "2026-04-28T23:50:00Z",
  "lastQuestion": null
}
```

### 2) List sessions (sidebar)
**GET** `/api/v1/sessions`
- Response (list):
```json
[
  {
    "id": "9d7c2e3e-0a9a-4ec6-b4f6-4ef7f5b4d9f5",
    "name": "New chat",
    "createdAt": "2026-04-28T23:50:00Z",
    "updatedAt": "2026-04-28T23:55:00Z",
    "lastQuestion": "c'est quoi le design pattern creation?"
  }
]
```

### 3) Delete session
**DELETE** `/api/v1/sessions/{id}`
- Deletes the session and its documents/history.

---

## Updated existing endpoints (session-aware)

### Upload document
**POST** `/api/v1/documents/upload?sessionId=...`
- `multipart/form-data` with `file`
- Response:
```json
{
  "id": 12,
  "name": "COURS_ARCHI-DP-CH5-Creation.pdf",
  "size": "1.3 MB",
  "mimeType": "application/pdf",
  "sessionId": "9d7c2e3e-0a9a-4ec6-b4f6-4ef7f5b4d9f5"
}
```

### List documents
**GET** `/api/v1/documents?sessionId=...`
- Response items include `sessionId`.

### Ask question
**POST** `/api/v1/ai/questions?sessionId=...`
- Body:
```json
{
  "question": "c'est quoi le design pattern creation?",
  "documents": [
    { "id": 12, "name": "COURS_ARCHI-DP-CH5-Creation.pdf", "size": "1.3 MB", "mimeType": "application/pdf", "sessionId": "9d7c2e3e-0a9a-4ec6-b4f6-4ef7f5b4d9f5" }
  ]
}
```
- Response:
```json
{
  "answers": [ { "title": "AI Answer", "type": "Summary", "summary": "..." } ],
  "sessionId": "9d7c2e3e-0a9a-4ec6-b4f6-4ef7f5b4d9f5"
}
```

### History
**GET** `/api/v1/ai/history?page=0&size=20&sessionId=...`
- Response:
```json
{
  "items": [
    { "id": 1, "question": "...", "answer": "...", "createdAt": "2026-04-28T23:55:10Z" }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1,
  "sessionId": "9d7c2e3e-0a9a-4ec6-b4f6-4ef7f5b4d9f5"
}
```

---

## Default session behavior
If `sessionId` is not sent:
- Backend auto-creates or reuses a per-user default session ("Default chat").
- Existing documents/messages without session are attached to it.

---

## Security/validation rules
- `sessionId` must belong to the authenticated user.
- Documents used in `/ai/questions` must belong to the same session.
- Invalid session => 404, unauthorized access => 403.

---

## Recommended frontend flow
1. User clicks **New chat** -> `POST /sessions` -> store `sessionId`.
2. Upload docs to that session (`upload?sessionId=...`).
3. Ask question with `sessionId` and document IDs returned by upload/list.
4. Display history using `/ai/history?sessionId=...`.
5. Sidebar lists sessions using `GET /sessions`.

---

## Notes
- Keep all calls through `smartdoc` base URL (port 8087).
- The backend is backward-compatible if `sessionId` is omitted.

