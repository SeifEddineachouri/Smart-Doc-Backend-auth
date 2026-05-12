# SmartDoc CI/CD Pipeline Setup Guide

Complete CI/CD pipeline setup for SmartDoc Backend with Jenkins, SonarQube, Docker, and Kubernetes.

## Architecture Overview

```
GitHub Push → GitHub Webhook → Jenkins Pipeline
    ↓
Maven Build & Unit Tests
    ↓
SonarQube Analysis
    ↓
Docker Image Build & Push to Docker Hub
    ↓
Deploy to Kubernetes (K8s)
    ↓
Health Checks & Verification
```

## Prerequisites

### Local Development
- Docker & Docker Compose installed
- kubectl configured
- Git installed
- JDK 17 or higher
- Maven 3.9+

### Production/Cloud
- Kubernetes cluster (v1.24+)
- Container registry (Docker Hub account)
- Git repository (GitHub)
- SonarQube Cloud account (optional) or SonarQube Server

## Quick Start - Local Setup

### 1. Start CI/CD Infrastructure

```bash
# Navigate to project root
cd Smart-Doc-Backend-auth

# Start all services (Jenkins, SonarQube, PostgreSQL, Kafka, etc.)
docker-compose -f docker-compose.cicd.yaml up -d

# Verify services are running
docker-compose -f docker-compose.cicd.yaml ps
```

### 2. Access Services

- **Jenkins**: http://localhost:8080
- **SonarQube**: http://localhost:9000 (admin/admin)
- **PostgreSQL**: localhost:5432
- **Kafka**: localhost:9092

### 3. Configure Jenkins

1. Open Jenkins at http://localhost:8080
2. Get initial admin password:
   ```bash
   docker exec smartdoc-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
   ```
3. Complete setup wizard
4. Install suggested plugins

### 4. Add GitHub Credentials to Jenkins

1. Manage Jenkins → Manage Credentials → System → Global credentials
2. Add credentials:
   - **Docker Hub**: 
     - Kind: Username with password
     - ID: `docker-hub-credentials`
     - Username: Your Docker Hub username
     - Password: Docker Hub access token
   
   - **SonarQube Token**:
     - Kind: Secret text
     - ID: `sonar-token`
     - Secret: Your SonarQube token
   
   - **GitHub Token**:
     - Kind: Secret text
     - ID: `github-token`
     - Secret: GitHub Personal Access Token

### 5. Create Pipeline Job in Jenkins

```bash
# Pipeline will be auto-created from Jenkinsfile if using Pipeline job from SCM
# Or manually create a new Pipeline job:
# - Name: smartdoc-backend-pipeline
# - Pipeline script from SCM
# - SCM: Git
# - Repository URL: https://github.com/YOUR_USERNAME/Smart-Doc-Backend-auth.git
# - Branch: */main, */develop
# - Script Path: Jenkinsfile
```

## GitHub Actions Setup (Alternative to Jenkins)

### 1. Add GitHub Secrets

Go to: Settings → Secrets and variables → Actions

Add the following secrets:
```
DOCKER_USERNAME      = Your Docker Hub username
DOCKER_PASSWORD      = Docker Hub access token
SONAR_TOKEN          = SonarQube Cloud token
SLACK_WEBHOOK_URL    = Slack webhook (optional)
KUBE_CONFIG          = Base64 encoded kubeconfig
```

### 2. Configure Kubeconfig Secret

```bash
# Encode your kubeconfig file
cat ~/.kube/config | base64 -w 0 | xclip -selection clipboard

# Paste in GitHub secret: KUBE_CONFIG
```

### 3. Trigger Pipeline

Push to `main` or `develop` branch to trigger the pipeline automatically.

## Kubernetes Deployment

### 1. Create Kubernetes Namespace

```bash
kubectl create namespace smartdoc-dev
```

### 2. Apply Manifests

```bash
kubectl apply -f k8s/ -n smartdoc-dev
```

### 3. Verify Deployment

```bash
# Check deployment status
kubectl get deployment smartdoc-backend -n smartdoc-dev

# Check pods
kubectl get pods -n smartdoc-dev

# Check service
kubectl get svc smartdoc-backend -n smartdoc-dev

# View pod logs
kubectl logs -n smartdoc-dev -l app=smartdoc-backend --tail=100 -f

# Port forward for testing
kubectl port-forward -n smartdoc-dev svc/smartdoc-backend 8080:80
```

### 4. Configure Ingress (Optional)

For external access, create an Ingress resource:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: smartdoc-backend-ingress
  namespace: smartdoc-dev
spec:
  rules:
  - host: smartdoc.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: smartdoc-backend
            port:
              number: 80
```

## Environment Configuration

### Development Environment

`application-dev.properties`:
```properties
spring.datasource.url=jdbc:postgresql://postgres:5432/smartdoc
spring.datasource.username=smartdoc
spring.datasource.password=smartdoc123
spring.kafka.bootstrap-servers=kafka:9092
logging.level.root=INFO
```

### Production Environment

`application-prod.properties`:
```properties
spring.datasource.url=jdbc:postgresql://<RDS_ENDPOINT>:5432/smartdoc
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
spring.kafka.bootstrap-servers=${KAFKA_BROKERS}
logging.level.root=WARN
```

## CI/CD Pipeline Stages

### Build Stage
- Checks out code
- Compiles with Maven
- Runs unit tests
- Generates build artifacts

### SonarQube Analysis Stage
- Performs static code analysis
- Checks code quality gate
- Reports on bugs, vulnerabilities, and technical debt

### Docker Build Stage
- Builds Docker image from Dockerfile
- Tags image with build number and commit hash
- Pushes to Docker Registry

### Kubernetes Deployment Stage
- Creates namespace and secrets
- Updates Kubernetes deployment
- Performs rolling update
- Verifies pod health

## Monitoring & Logs

### View Jenkins Logs

```bash
docker logs smartdoc-jenkins
```

### View SonarQube Logs

```bash
docker logs smartdoc-sonarqube
```

### View Application Logs (Kubernetes)

```bash
kubectl logs -n smartdoc-dev -l app=smartdoc-backend -f
```

### View Pipeline Execution

Jenkins Dashboard → smartdoc-backend-pipeline → Build History

## Troubleshooting

### Jenkins Connection Issues

```bash
# Check if Jenkins is running
docker ps | grep jenkins

# Restart Jenkins
docker restart smartdoc-jenkins

# Check Jenkins logs
docker logs smartdoc-jenkins --tail=50
```

### SonarQube Connection Issues

```bash
# Check SonarQube health
curl http://localhost:9000/api/system/health

# Restart SonarQube
docker restart smartdoc-sonarqube
```

### Kubernetes Deployment Issues

```bash
# Describe deployment for events
kubectl describe deployment smartdoc-backend -n smartdoc-dev

# Check pod events
kubectl describe pod <pod-name> -n smartdoc-dev

# Check image pull secrets
kubectl get secrets -n smartdoc-dev
```

### Docker Image Push Failures

```bash
# Verify Docker credentials
docker login docker.io

# Check image tags
docker images | grep smartdoc

# Manually push image
docker push docker.io/smartdoc-backend-auth:latest
```

## Security Best Practices

### 1. Credentials Management
- Use Jenkins credentials store (not hardcoded)
- Rotate tokens regularly
- Use GitHub Personal Access Tokens with minimal permissions

### 2. Image Security
- Scan Docker images for vulnerabilities
- Use minimal base images (alpine, distroless)
- Keep dependencies updated

### 3. Kubernetes Security
- Use RBAC for service accounts
- Apply network policies
- Use Pod Security Policies
- Run containers as non-root

### 4. Code Security
- Enable SonarQube security hotspots
- Use dependency check in build pipeline
- Scan for secrets in code

## Scaling & Performance

### Horizontal Pod Autoscaling

The deployment includes HPA configuration:
- Min replicas: 2
- Max replicas: 5
- CPU threshold: 70%
- Memory threshold: 80%

### Jenkins Agent Scaling

Jenkins uses Kubernetes agents that auto-scale:
- Pods spawn on-demand
- Scale down when idle
- Cost-effective resource usage

## Cleanup

### Stop Local Services

```bash
# Stop all services
docker-compose -f docker-compose.cicd.yaml down

# Remove volumes (optional)
docker-compose -f docker-compose.cicd.yaml down -v
```

### Delete Kubernetes Resources

```bash
# Delete all resources in namespace
kubectl delete namespace smartdoc-dev

# Or delete specific resources
kubectl delete deployment smartdoc-backend -n smartdoc-dev
```

## Next Steps

1. **Update GitHub webhook**: Configure automatic triggers
2. **Set up monitoring**: Add Prometheus & Grafana
3. **Add pre-deployment tests**: Implement smoke tests
4. **Configure SSL/TLS**: Set up HTTPS
5. **Add database backups**: Implement backup strategy
6. **Set up logging**: Integrate ELK stack or Loki
7. **Add alerting**: Configure alerts for failures

## Support & Documentation

- Jenkins Documentation: https://www.jenkins.io/doc/
- SonarQube Documentation: https://docs.sonarqube.org/
- Kubernetes Documentation: https://kubernetes.io/docs/
- Docker Documentation: https://docs.docker.com/

## Contact & Issues

For issues or questions, refer to the project repository or contact the DevOps team.
