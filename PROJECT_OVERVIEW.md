# SmartDoc Backend - Projet Analyse

## 1. Résumé du projet

Ce projet est un backend Java Spring Boot appelé `smartdoc`. Il fournit une API REST sécurisée pour :

- l'authentification et la gestion des tokens JWT
- la gestion de l'utilisateur courant
- le téléchargement, la liste et la suppression de documents
- une API AI pour poser des questions et consulter l'historique
- la publication d'événements d'audit vers Kafka

Le backend est conçu pour fonctionner avec PostgreSQL en production et H2 en développement local.

## 2. Architecture générale

### 2.1 Structure principale

- `src/main/java/com/example/demo`
  - `config/` : configuration Spring, sécurité, propriétés, Kafka
  - `controller/` : points d'entrée HTTP
  - `service/` : logique métier
  - `repository/` : accès aux données via Spring Data JPA
  - `model/` : entités, DTOs, événements, énumérations
  - `exception/` : gestion des erreurs applicatives
  - `util/` : JWT, sécurité et principal utilisateur

- `src/main/resources`
  - `application.properties` : configuration de l'application
  - `db/migration/` : scripts Flyway pour initialiser la base

### 2.2 Principaux composants

- `SmartdocApplication.java` : point d'entrée Spring Boot
- `SecurityConfig.java` : sécurité stateless, JWT, CORS, accès public Swagger
- `AuthController.java` : endpoints d'inscription, connexion, refresh et logout
- `UserController.java` : endpoints pour le profil utilisateur
- `DocumentController.java` : upload, liste et suppression de documents
- `AiController.java` : API AI (questions + historique)

## 3. Endpoints disponibles

### Authentification

- `POST /api/v1/auth/signup` ou `/api/v1/auth/register`
- `POST /api/v1/auth/signin`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/signout`

### Utilisateur

- `GET /api/v1/users/me`
- `PATCH /api/v1/users/me/language`

### Documents

- `POST /api/v1/documents/upload`
- `GET /api/v1/documents`
- `DELETE /api/v1/documents/{id}`

### AI

- `POST /api/v1/ai/questions`
- `GET /api/v1/ai/history`

## 4. Sécurité et JWT

- `SecurityConfig` autorise les endpoints d'authentification et les docs OpenAPI.
- Toutes les autres requêtes doivent avoir le rôle `USER`.
- Le token d'accès JWT est validé par `JwtAuthenticationFilter`.
- Le refresh token est stocké en cookie HTTP-only nommé `refreshToken`.
- Le mot de passe est chiffré avec `BCryptPasswordEncoder`.

## 5. Base de données et migration

- `application.properties` configure PostgreSQL par défaut :
  - `spring.datasource.url=jdbc:postgresql://localhost:5432/SmartDoc`
  - `spring.datasource.username=${DB_USERNAME:postgres}`
  - `spring.datasource.password=${DB_PASSWORD:admin}`

- Flyway charge les scripts dans `classpath:db/migration`
- Les entités principales sont :
  - `UserEntity`
  - `RoleEntity`
  - `DocumentEntity`
  - `RefreshTokenEntity`
  - `ChatSessionEntity`
  - `ChatMessageEntity`

## 6. Configuration importante

- `app.jwt.secret` : clé JWT
- `app.jwt.access-token-seconds` : durée du token d'accès
- `app.auth.refresh-short-days` / `app.auth.refresh-long-days` : durée du refresh token
- `app.auth.refresh-cookie-name` : nom du cookie de refresh token
- `app.cors.allowed-origins` : origines autorisées
- `app.documents.storage-dir` : dossier de stockage des fichiers uploadés
- `app.kafka.bootstrap-servers` : broker Kafka pour les événements d'audit
- `app.ai-gateway.base-url` : base URL du service AI externe

## 7. Tests et couverture

- Le projet utilise `spring-boot-starter-test` et `spring-security-test`.
- `pom.xml` contient le plugin JaCoCo : génération de rapport de couverture pendant les tests.
- La commande à exécuter :

```bash
mvn clean test
```

- Le rapport de couverture JaCoCo est généré dans `target/site/jacoco`.

## 8. SonarCloud

- Le projet possède une configuration Sonar Cloud dans `pom.xml`.
- Un workflow GitHub Actions existe dans `.github/workflows/sonarcloud.yml`.
- Il doit être exécuté via un secret GitHub `SONAR_TOKEN` pour pousser l'analyse sur SonarCloud.

## 9. Exécution locale

### Lancer l'application

```powershell
cd "c:\Users\Mega Pc\OneDrive\Desktop\Back Auth\Smart-Doc-Backend-auth"
./mvnw.cmd spring-boot:run
```

### Points d'accès utiles

- API : `http://localhost:8087`
- Swagger UI : `http://localhost:8087/swagger-ui/index.html`

## 10. Notes importantes

- `app.security.docs-public-enabled=true` rend Swagger et la console H2 publics en local.
- En production, sécuriser les variables d'environnement : JWT secret, DB credentials, Kafka, AI gateway.
- Retirer les tokens ou secrets codés en dur du code source et passer par des secrets GitHub ou des variables d'environnement.

---

### Conseils rapides

- Vérifiez la configuration de `application.properties` avant de lancer en local.
- Si vous testez sans PostgreSQL, adaptez le profil pour H2 ou installez PostgreSQL.
- Le endpoint AI `/api/v1/ai/questions` est conçu pour être délégué à un service AI externe.
