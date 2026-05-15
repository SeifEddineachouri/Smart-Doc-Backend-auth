# Deploying SmartDoc to Kubernetes

This guide explains how to run the SmartDoc stack on Kubernetes. It is written for the current repository layout, where the application is split into these services:

- `smartdoc` — Spring Boot backend
- `AiSmartDoc` — Java AI gateway/service
- `smartdoc-ai` — Python FastAPI service
- `frontend` — Angular UI
- `postgres` — database
- `kafka` — event broker

## What you need

- A Kubernetes cluster, such as Docker Desktop Kubernetes, Minikube, kind, or a managed cluster
- `kubectl`
- A container registry for the SmartDoc images
- Optional: `helm`, `kustomize`, or plain YAML manifests

## Recommended deployment approach

For this project, the simplest path is:

1. Build and publish each image to a registry
2. Create Kubernetes `Deployment` and `Service` objects for every app component
3. Use `ConfigMap` and `Secret` for configuration
4. Add `Ingress` for public access to the frontend and backend if needed
5. Persist PostgreSQL with a `PersistentVolumeClaim`
6. Persist Kafka only if you want durable broker storage in your cluster setup

## 1) Container images

The stack in `compose.yaml` currently uses local image names such as:

- `smartdoc:local`
- `aismartdoc:local`
- `smartdoc-ai:local`
- `smartdoc-frontend:local`

For Kubernetes, push them to a registry instead, for example:

- `docker.io/<namespace>/smartdoc-backend-auth:latest`
- `docker.io/<namespace>/smartdoc-backend-fast-api:latest`
- `docker.io/<namespace>/smartdoc-backend-python:latest`
- `docker.io/<namespace>/smartdoc-frontend:latest`

If you already publish images through GitHub Actions, reuse those tags in the manifests.

## 2) Suggested namespace

Create a dedicated namespace for the app:

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: smartdoc
```

## 3) Configuration and secrets

Use a `ConfigMap` for non-sensitive settings and a `Secret` for credentials.

### Example `ConfigMap`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: smartdoc-config
  namespace: smartdoc
data:
  POSTGRES_DB: SmartDoc
  POSTGRES_USER: postgres
  APP_AI_BASE_URL: http://smartdoc-ai:8000
  APP_AI_GATEWAY_BASE_URL: http://aismartdoc:8088/api/v1/ai
  KAFKA_BOOTSTRAP_SERVERS: kafka:9092
  APP_KAFKA_AUDIT_TOPIC: smartdoc.audit.events
  APP_KAFKA_CLIENT_ID: smartdoc
```

### Example `Secret`

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: smartdoc-secrets
  namespace: smartdoc
type: Opaque
stringData:
  POSTGRES_PASSWORD: admin
  APP_JWT_SECRET: change-me-to-a-long-random-secret
  SERVICE_TOKEN: smartdoc-dev-token
  GEMINI_API_KEY: ""
```

Do not keep production secrets in Git. Use external secret management if your cluster supports it.

## 4) PostgreSQL deployment

You can run PostgreSQL with a `Deployment` and a `PersistentVolumeClaim`, or use a managed database in production.

### Example Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: smartdoc
spec:
  selector:
    app: postgres
  ports:
    - port: 5432
      targetPort: 5432
```

### Example Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
  namespace: smartdoc
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
        - name: postgres
          image: postgres:16-alpine
          ports:
            - containerPort: 5432
          env:
            - name: POSTGRES_DB
              valueFrom:
                configMapKeyRef:
                  name: smartdoc-config
                  key: POSTGRES_DB
            - name: POSTGRES_USER
              valueFrom:
                configMapKeyRef:
                  name: smartdoc-config
                  key: POSTGRES_USER
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: smartdoc-secrets
                  key: POSTGRES_PASSWORD
          volumeMounts:
            - name: postgres-data
              mountPath: /var/lib/postgresql/data
      volumes:
        - name: postgres-data
          persistentVolumeClaim:
            claimName: postgres-pvc
```

## 5) Kafka deployment

Kafka is more involved on Kubernetes than in Docker Compose. For a production cluster, use a Kafka operator or a managed Kafka service.

For a minimal test environment, you can deploy a single-broker Kafka setup, but that is usually only suitable for development.

## 6) Backend deployment

Deploy the Spring Boot backend with its environment variables and a Service.

### Example Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: smartdoc
  namespace: smartdoc
spec:
  selector:
    app: smartdoc
  ports:
    - port: 8087
      targetPort: 8087
```

### Example Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: smartdoc
  namespace: smartdoc
spec:
  replicas: 1
  selector:
    matchLabels:
      app: smartdoc
  template:
    metadata:
      labels:
        app: smartdoc
    spec:
      containers:
        - name: smartdoc
          image: docker.io/<namespace>/smartdoc-backend-auth:latest
          ports:
            - containerPort: 8087
          env:
            - name: SPRING_DATASOURCE_URL
              value: jdbc:postgresql://postgres:5432/SmartDoc?binaryTransfer=true&stringtype=unspecified
            - name: DB_USERNAME
              valueFrom:
                configMapKeyRef:
                  name: smartdoc-config
                  key: POSTGRES_USER
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: smartdoc-secrets
                  key: POSTGRES_PASSWORD
            - name: APP_JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: smartdoc-secrets
                  key: APP_JWT_SECRET
            - name: APP_AI_GATEWAY_BASE_URL
              valueFrom:
                configMapKeyRef:
                  name: smartdoc-config
                  key: APP_AI_GATEWAY_BASE_URL
            - name: APP_AI_GATEWAY_SERVICE_TOKEN
              valueFrom:
                secretKeyRef:
                  name: smartdoc-secrets
                  key: SERVICE_TOKEN
            - name: KAFKA_BOOTSTRAP_SERVERS
              valueFrom:
                configMapKeyRef:
                  name: smartdoc-config
                  key: KAFKA_BOOTSTRAP_SERVERS
            - name: APP_KAFKA_AUDIT_TOPIC
              valueFrom:
                configMapKeyRef:
                  name: smartdoc-config
                  key: APP_KAFKA_AUDIT_TOPIC
          readinessProbe:
            httpGet:
              path: /v3/api-docs
              port: 8087
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /v3/api-docs
              port: 8087
            initialDelaySeconds: 60
            periodSeconds: 20
```

## 7) AI gateway deployment

Deploy `AiSmartDoc` the same way, pointing it to `smartdoc-ai`.

## 8) Python AI service deployment

Deploy `smartdoc-ai` with its FastAPI port exposed on `8000`.

### Example Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: smartdoc-ai
  namespace: smartdoc
spec:
  selector:
    app: smartdoc-ai
  ports:
    - port: 8000
      targetPort: 8000
```

## 9) Frontend deployment

The frontend can be deployed as a simple NGINX container or an Angular build served by NGINX.

### Example Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: frontend
  namespace: smartdoc
spec:
  selector:
    app: frontend
  ports:
    - port: 80
      targetPort: 80
```

### Example Ingress

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: smartdoc-ingress
  namespace: smartdoc
spec:
  rules:
    - host: smartdoc.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: frontend
                port:
                  number: 80
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: smartdoc
                port:
                  number: 8087
```

If you use a different routing strategy, you can point the frontend to the API via NGINX reverse proxy or a Kubernetes Ingress controller.

## 10) Persistence

For development, a `PersistentVolumeClaim` is enough for PostgreSQL and optional file uploads.

For the current repository, uploaded files are stored in `uploads/` in Docker Compose. In Kubernetes, consider one of these:

- shared persistent volume
- object storage such as S3 or MinIO
- direct upload to a managed storage service

## 11) Health checks

Use health and readiness probes for every service.

Recommended endpoints:

- `smartdoc`: `/v3/api-docs` or actuator endpoints if enabled
- `AiSmartDoc`: `/api/v1/ai/health`
- `smartdoc-ai`: `/health`
- `frontend`: `/`
- `postgres`: TCP probe on `5432`

## 12) Monitoring on Kubernetes

If you also add Prometheus and Grafana, they can run as separate Kubernetes workloads:

- Prometheus scrapes `/actuator/prometheus`
- Grafana reads from Prometheus as a datasource
- The backend must include `spring-boot-starter-actuator` and `micrometer-registry-prometheus`

## 13) Suggested rollout order

1. Create the namespace, secrets, and config map
2. Deploy PostgreSQL or connect to managed PostgreSQL
3. Deploy Kafka or connect to managed Kafka
4. Deploy `smartdoc-ai`
5. Deploy `AiSmartDoc`
6. Deploy `smartdoc`
7. Deploy the frontend
8. Add ingress and monitoring

## 14) Local testing commands

### Apply manifests

```powershell
kubectl apply -f k8s\namespace.yaml
kubectl apply -f k8s\configmap.yaml
kubectl apply -f k8s\secret.yaml
kubectl apply -f k8s\postgres.yaml
kubectl apply -f k8s\smartdoc-ai.yaml
kubectl apply -f k8s\aismartdoc.yaml
kubectl apply -f k8s\smartdoc.yaml
kubectl apply -f k8s\frontend.yaml
kubectl apply -f k8s\ingress.yaml
```

### Check status

```powershell
kubectl get pods -n smartdoc
kubectl get svc -n smartdoc
kubectl get ingress -n smartdoc
```

## 15) Notes for this repository

The current repository already includes a Docker Compose stack for local development. Kubernetes is best used when you want:

- multi-environment deployment
- horizontal scaling
- rolling updates
- service discovery
- stronger separation between infrastructure and application code

If you want, the next step is to create a `k8s/` directory with starter manifests for this project.

