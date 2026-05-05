# SmartDoc Frontend Angular Handoff

## 1) Objectif
Ce document explique quoi implementer cote Angular pour s'integrer proprement avec le backend SmartDoc:
- Authentification JWT + refresh token (cookie HttpOnly)
- Guards de routes
- Interceptors HTTP
- Services API (auth, user, documents, ai)
- Gestion des erreurs API unifiee
- Flux UI attendus (signin/signup/profile/language/upload/ask question)

---

## 2) Hypotheses Backend
Base API (dev):
- `http://localhost:8080/api/v1`

Endpoints exposes:
- `POST /auth/signup`
- `POST /auth/signin`
- `POST /auth/refresh`
- `POST /auth/signout`
- `GET /users/me`
- `PATCH /users/me/language`
- `POST /documents/upload`
- `GET /documents`
- `DELETE /documents/{id}`
- `POST /ai/questions` (placeholder pour l'instant)

Swagger:
- `http://localhost:8080/swagger-ui/index.html`

---

## 3) Architecture Angular recommandee
Arborescence cible (suggestion):

```text
src/app
  core/
    api/
      auth.service.ts
      user.service.ts
      document.service.ts
      ai.service.ts
    guards/
      auth.guard.ts
      guest.guard.ts
    interceptors/
      auth-token.interceptor.ts
      auth-refresh.interceptor.ts
      api-error.interceptor.ts
    models/
      auth.models.ts
      user.models.ts
      document.models.ts
      api-error.models.ts
    state/
      auth.store.ts
      session.store.ts
  features/
    auth/
      pages/
      components/
    dashboard/
    documents/
    ai/
    profile/
  shared/
    components/
    utils/
```

---

## 4) Models TypeScript a implementer

```ts
export type LanguageCode = 'en' | 'fr';

export interface SignInRequest {
  email: string;
  password: string;
  rememberMe: boolean;
}

export interface SignUpRequest {
  fullName: string;
  workEmail: string;
  password: string;
  acceptedTerms: boolean;
}

export interface AuthUserProfile {
  id: string;
  fullName: string;
  email: string;
  language: LanguageCode;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: AuthUserProfile;
}

export interface RefreshTokenRequest {
  refreshToken?: string; // fallback uniquement
}

export interface RefreshTokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
}

export interface UploadedDocument {
  id: number;
  name: string;
  size: string;
  mimeType: string;
}

export interface AskQuestionRequest {
  question: string;
  documents: UploadedDocument[];
}

export interface AiAnswerCard {
  title: string;
  type: string;
  summary: string;
}

export interface AskQuestionResponse {
  answers: AiAnswerCard[];
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  fieldErrors: { field: string; message: string }[];
}
```

---

## 5) Services Angular a creer

### 5.1 AuthService
Responsabilites:
- `signup(payload: SignUpRequest): Observable<AuthResponse>`
- `signin(payload: SignInRequest): Observable<AuthResponse>`
- `refresh(payload?: RefreshTokenRequest): Observable<RefreshTokenResponse>`
- `signout(): Observable<void>`

Regles importantes:
- `withCredentials: true` sur `signup`, `signin`, `refresh`, `signout` pour cookie refresh.
- Sauvegarder seulement `accessToken` cote frontend (pas le refresh token cookie).
- Nettoyer la session locale au `signout` et sur refresh impossible.

### 5.2 UserService
- `getMe(): Observable<AuthUserProfile>` -> `GET /users/me`
- `updateLanguage(language: LanguageCode): Observable<AuthUserProfile>` -> `PATCH /users/me/language`

### 5.3 DocumentService
- `upload(file: File): Observable<UploadedDocument>` -> multipart `file`
- `list(): Observable<UploadedDocument[]>`
- `delete(id: number): Observable<void>`

### 5.4 AiService
- `askQuestion(payload: AskQuestionRequest): Observable<AskQuestionResponse>`
- Note: la reponse actuelle est un placeholder backend en attendant microservice FastAPI.

---

## 6) Interceptors a implementer

### 6.1 `auth-token.interceptor`
- Ajouter `Authorization: Bearer <accessToken>` sur toutes les routes API protegees.
- Exclure endpoints publics:
  - `/auth/signup`
  - `/auth/signin`
  - `/auth/refresh`

### 6.2 `auth-refresh.interceptor`
- Si 401 sur requete protegee:
  1. lancer `POST /auth/refresh` (`withCredentials: true`)
  2. mettre a jour `accessToken`
  3. rejouer la requete initiale
- Si refresh echoue:
  - vider session locale
  - rediriger `/auth/signin`
- Prevoir anti-boucle:
  - un seul refresh en cours
  - queue des requetes en attente

### 6.3 `api-error.interceptor`
- Normaliser les erreurs backend `ApiErrorResponse` vers un format UI.
- Mapper rapidement:
  - `400` validation -> afficher `fieldErrors`
  - `401` -> session expiree/login
  - `403` -> acces interdit
  - `404` -> ressource absente
  - `409` -> conflit (email deja pris)
  - `500` -> message global

---

## 7) Guards de routes

### 7.1 `AuthGuard`
- Autoriser acces si token present et session valide.
- Sinon tenter refresh silencieux.
- Si echec -> redirect `/auth/signin`.

### 7.2 `GuestGuard`
- Pour routes login/register.
- Si utilisateur deja connecte -> redirect `/dashboard`.

### 7.3 (Optionnel) `RoleGuard`
- Preparer le support role-based si besoin futur (`ROLE_ADMIN`).

---

## 8) Gestion d'etat session
Minimum attendu:
- `accessToken` (memory + persistance conditionnelle selon UX)
- `currentUser`
- `isAuthenticated`
- `isRefreshingToken`

Au boot app:
1. tenter `refresh()` (silent)
2. si succes -> `getMe()`
3. sinon -> utilisateur non connecte

---

## 9) Flux fonctionnels a brancher

### 9.1 Signup
- Form validation locale
- `POST /auth/signup`
- Stocker token + user
- Redirect dashboard

### 9.2 Signin
- `POST /auth/signin`
- Stocker token + user
- Redirect dashboard

### 9.3 Profile
- `GET /users/me` a l'entree page
- `PATCH /users/me/language` sur changement langue

### 9.4 Documents
- Upload fichier (`multipart/form-data`)
- Rafraichir liste documents
- Suppression avec confirmation UI

### 9.5 AI Question
- Construire `AskQuestionRequest` avec `question` + `documents[]`
- Afficher `answers[]` en cards
- Mentionner en UI si reponse placeholder

---

## 10) Routing minimal
Suggestion:
- `/auth/signin` (GuestGuard)
- `/auth/signup` (GuestGuard)
- `/dashboard` (AuthGuard)
- `/documents` (AuthGuard)
- `/ai` (AuthGuard)
- `/profile` (AuthGuard)

---

## 11) Checklist implementation frontend

### Priorite 1 (blocante)
- [ ] Models TS aligns backend
- [ ] AuthService complet (`withCredentials`)
- [ ] Token interceptor
- [ ] Refresh interceptor avec anti-boucle
- [ ] AuthGuard + GuestGuard

### Priorite 2
- [ ] UserService + ecran profile/language
- [ ] DocumentService + ecran upload/list/delete
- [ ] AiService + ecran ask question

### Priorite 3
- [ ] Normalisation des erreurs UI
- [ ] UX session expiree
- [ ] Tests unitaires services/interceptors/guards

---

## 12) Notes integration importantes
- Le refresh token est en cookie HttpOnly: il n'est pas lisible en JS, c'est normal.
- Toujours passer `withCredentials: true` pour les appels auth.
- Le backend accepte un `refreshToken` en body comme fallback, mais preferer le cookie.
- La partie AI est contractuelle (placeholder). Le frontend peut deja integrer sans attendre FastAPI.

