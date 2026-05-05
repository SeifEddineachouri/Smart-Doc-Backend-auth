# SmartDoc Frontend - Chat History Integration (Angular)

## 1) Objectif
Ce document decrit ce qu'il faut ajouter cote Angular pour integrer les nouveaux changements backend de chat:
- Persistance de chaque question/reponse AI
- Recuperation de l'historique pagine par utilisateur connecte

Le backend reste en mode placeholder pour la reponse AI (microservice FastAPI non branche pour le moment), mais le contrat frontend est stable.

---

## 2) Endpoints a integrer
Base API locale:
- `http://localhost:8087/api/v1`

Nouveaux endpoints AI:
- `POST /ai/questions` (auth requis)
- `GET /ai/history?page=0&size=20` (auth requis)

Important:
- Ces routes necessitent `Authorization: Bearer <accessToken>`.
- Ne pas utiliser `withCredentials` ici si vous passez seulement le bearer token.
- Le refresh token reste gere via cookie HttpOnly sur les endpoints auth.

---

## 3) Contrats backend (TypeScript)
Ajouter/mettre a jour vos models dans `core/models/ai.models.ts` (ou equivalent).

```ts
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

export interface ChatHistoryItem {
  id: number;
  question: string;
  answer: string;
  createdAt: string; // ISO date string
}

export interface ChatHistoryResponse {
  items: ChatHistoryItem[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}
```

---

## 4) Service Angular a ajouter
Dans `core/api/ai.service.ts`, ajouter une methode d'historique.

```ts
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AiService {
  private readonly baseUrl = 'http://localhost:8087/api/v1/ai';

  constructor(private readonly http: HttpClient) {}

  askQuestion(payload: AskQuestionRequest): Observable<AskQuestionResponse> {
    return this.http.post<AskQuestionResponse>(`${this.baseUrl}/questions`, payload);
  }

  getHistory(page = 0, size = 20): Observable<ChatHistoryResponse> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<ChatHistoryResponse>(`${this.baseUrl}/history`, { params });
  }
}
```

Si vous utilisez deja `environment.apiBaseUrl`, gardez ce pattern (recommande).

---

## 5) Interceptor / Auth
Verifier que votre interceptor token ajoute bien le bearer sur:
- `POST /api/v1/ai/questions`
- `GET /api/v1/ai/history`

Comportement attendu:
1. Requete AI avec bearer
2. Si `401`, lancer refresh via endpoint auth
3. Mettre a jour `accessToken`
4. Rejouer la requete initiale
5. Si refresh echoue, logout + redirection signin

---

## 6) UI a ajouter dans la feature AI
### 6.1 Chargement initial
- Au chargement de la page AI, appeler `getHistory(0, 20)`
- Afficher la liste (plus recent en premier)

### 6.2 Envoi d'une question
- Envoyer via `askQuestion(payload)`
- Afficher la reponse immediate dans l'UI
- Rafraichir ensuite l'historique (ou insertion optimiste locale)

### 6.3 Pagination
- Bouton "Load more" ou pagination classique
- Appeler `getHistory(page + 1, size)`
- Concatener `items` existants + nouveaux

---

## 7) Gestion erreurs UI
Le backend retourne un format `ApiErrorResponse` standard.
Mapper au minimum:
- `400` `VALIDATION_ERROR`: message formulaire + champs invalides
- `401` `UNAUTHORIZED`: session expiree, refresh ou login
- `403` `FORBIDDEN`: acces non autorise
- `500` `INTERNAL_ERROR`: toast global "Unexpected error"

---

## 8) Exemple de flux UX
1. User ouvre page AI
2. Front appelle `GET /ai/history?page=0&size=20`
3. User pose une question + selection docs
4. Front appelle `POST /ai/questions`
5. Front affiche `answers[]`
6. Front recharge ou met a jour l'historique local

---

## 9) Checklist implementation frontend
### Blocant
- [ ] Models `ChatHistoryItem` et `ChatHistoryResponse` ajoutes
- [ ] `AiService.getHistory(page, size)` implemente
- [ ] Interceptor bearer actif sur endpoints AI
- [ ] Gestion 401/refresh validee

### Recommande
- [ ] UI historique avec pagination
- [ ] Date `createdAt` formatee dans locale utilisateur
- [ ] Etats UX: loading, empty state, retry
- [ ] Tests unitaires service + composant AI

---

## 10) Notes importantes
- L'historique est isole par utilisateur authentifie (pas de melange entre comptes).
- Le backend AI renvoie encore une reponse placeholder tant que FastAPI n'est pas branche.
- Pour eviter les erreurs CORS en dev Angular, utiliser soit:
  - base URL backend directe `http://localhost:8087`, ou
  - proxy Angular vers `http://localhost:8087`.

