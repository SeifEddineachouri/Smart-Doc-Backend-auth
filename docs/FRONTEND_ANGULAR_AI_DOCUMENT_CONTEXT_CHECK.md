# SmartDoc Frontend - Verification Guide (AI Document Context)

## Objectif
Ce document sert a un agent frontend Angular pour verifier que:
- l'implementation frontend est correcte,
- les documents uploades sont bien pris en consideration lors des questions AI,
- le fallback `I cannot answer from the available context.` n'apparait que dans les vrais cas sans contexte.

Ce guide est aligne avec le backend `smartdoc` (port `8087`) et la chaine microservice:
- `smartdoc` -> `AiSmartDoc` -> `smartdoc-ai`.

---

## Checklist rapide (bloquants)
- [ ] Le frontend appelle `POST /api/v1/documents/upload` avant de poser une question.
- [ ] Le frontend envoie a `POST /api/v1/ai/questions` un `documents[]` non vide avec des IDs BD reels.
- [ ] Les appels AI passent via `smartdoc` (`8087`), pas directement vers `smartdoc-ai`.
- [ ] `Authorization: Bearer <token>` est present sur upload/list/questions/history.
- [ ] Le PDF est re-uploade apres le correctif ingestion PDF (anciens uploads non re-ingestes).

---

## Contrats API a respecter
Base URL frontend en dev:
- `http://localhost:8087/api/v1`

### 1) Upload document
- Endpoint: `POST /documents/upload`
- Type: `multipart/form-data`
- Champ: `file`
- Reponse:

```json
{
  "id": 12,
  "name": "COURS_ARCHI-DP-CH5-Creation.pdf",
  "size": "1.2 MB",
  "mimeType": "application/pdf"
}
```

### 2) Lister documents user
- Endpoint: `GET /documents`
- Utiliser les elements retournes ici pour alimenter la selection de documents.

### 3) Poser une question AI
- Endpoint: `POST /ai/questions`
- Body attendu:

```json
{
  "question": "c'est quoi design pattern creation ?",
  "documents": [
    {
      "id": 12,
      "name": "COURS_ARCHI-DP-CH5-Creation.pdf",
      "size": "1.2 MB",
      "mimeType": "application/pdf"
    }
  ]
}
```

- Contraintes backend:
  - `question` non vide
  - `documents` non vide
  - chaque doc: `id`, `name`, `size`, `mimeType` obligatoires

### 4) Historique
- Endpoint: `GET /ai/history?page=0&size=20`
- Le backend utilise le user du token (ne pas envoyer `userId` dans la requete).

---

## Ce qui prouve que le document est pris en compte
Comme le frontend ne recoit pas encore les citations detaillees, la verification se fait par faisceau d'indices:

1. **Network frontend**
   - upload reussi -> `200` avec `id` numerique reel.
   - question AI -> body contient cet `id` reel dans `documents[].id`.

2. **Comportement fonctionnel**
   - la reponse n'est plus le fallback generique sur une question explicitement couverte par le document.

3. **Logs backend (optionnel mais recommande)**
   - absence du log `AI ingestion skipped: unsupported mimeType=application/pdf...`.
   - presence d'appels `POST /query` cote `smartdoc-ai`.

---

## Erreurs frequentes cote frontend
1. IDs de documents locaux UI (`1,2,3`) au lieu des IDs BD renvoyes par upload/list.
2. Appel direct mauvais host (`4200` ou `8000`) au lieu de `8087`.
3. Envoi de `userId` dans les payloads AI (inutile et source de confusion).
4. Question envoyee avant la fin de l'upload.
5. Re-utilisation d'un PDF upload avant le correctif ingestion PDF.

---

## Procedure de test E2E (agent frontend)
1. Se connecter dans l'app Angular.
2. Uploader un PDF (nouvel upload).
3. Verifier dans Network la reponse upload et conserver `id`.
4. Poser une question liee au contenu du PDF.
5. Verifier le payload `POST /api/v1/ai/questions`:
   - `documents.length > 0`
   - `documents[0].id == id` retourne par upload
6. Verifier la reponse:
   - pas de fallback generique si le sujet est dans le PDF.
7. Ouvrir l'historique:
   - `GET /api/v1/ai/history?page=0&size=20`
   - la question/reponse doit apparaitre.

---

## Criteres d'acceptation
- [ ] Upload + question fonctionnent avec le meme document reel (ID BD).
- [ ] Les questions pertinentes sur le PDF ne retournent pas systematiquement le fallback.
- [ ] Historique AI affiche la question/reponse de la session.
- [ ] Aucun appel frontend ne cible directement `smartdoc-ai`.

---

## Snippet TypeScript de reference
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
```

---

## Notes techniques utiles
- Le backend `smartdoc` extrait maintenant le texte des PDF pour l'ingestion AI.
- Les documents uploades avant ce correctif peuvent rester sans contexte ingere: re-upload recommande.
- En dev, garder un seul point d'entree API frontend: `http://localhost:8087` (direct ou proxy Angular).

