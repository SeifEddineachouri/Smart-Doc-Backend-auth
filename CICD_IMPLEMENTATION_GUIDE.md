# SmartDoc Backend CI/CD Pipeline - Implementation Guide

## 📋 Overview

This comprehensive CI/CD pipeline automates the entire deployment workflow for SmartDoc Backend:

```
GitHub Push → Webhook Trigger → Jenkins Pipeline
    ↓
SonarQube Code Analysis
    ↓
Maven Build & Tests
    ↓
Docker Image Build
    ↓
Push to Docker Hub
    ↓
Deploy to Kubernetes
    ↓
Health Verification
```

## 🎯 Pipeline Features

- ✅ Automated build on every push
- ✅ SonarQube code quality analysis
- ✅ Maven build with unit tests
- ✅ Docker containerization
- ✅ Automatic Docker Hub push
- ✅ Kubernetes deployment with rolling updates
- ✅ Health checks and auto-scaling
- ✅ GitHub Actions alternative pipeline
- ✅ Jenkins Configuration as Code (JCasC)
- ✅ Security scanning and monitoring
- ✅ Comprehensive logging and monitoring

## 📁 Generated Files

### Pipeline Configuration
- **`Jenkinsfile`** - Jenkins declarative pipeline configuration
- **`.github/workflows/cicd-pipeline.yml`** - GitHub Actions workflow
- **`jenkins-config/jenkins.yaml`** - Jenkins Configuration as Code (JCasC)

### Kubernetes Configuration
- **`k8s/deployment.yaml`** - K8s deployment, service, HPA, RBAC
- **`k8s/advanced-resources.yaml`** - Network policies, backups, monitoring

### Docker & Infrastructure
- **`docker-compose.cicd.yaml`** - Local development infrastructure
- **`.env.cicd`** - Environment variables

### Scripts & Setup
- **`setup-cicd.sh`** - Linux/Mac setup automation
- **`setup-cicd.bat`** - Windows setup automation
- **`jenkins-requirements.txt`** - Required Jenkins plugins

### Documentation
- **`docs/CICD_SETUP_GUIDE.md`** - Detailed setup instructions
- **`sonar-project.properties`** - SonarQube configuration

## 🚀 Quick Start

### Option 1: Linux/Mac

```bash
# Make setup script executable
chmod +x setup-cicd.sh

# Run setup
./setup-cicd.sh
```

### Option 2: Windows

```batch
REM Run setup script
setup-cicd.bat
```

### Option 3: Manual Setup

```bash
# Start services
docker-compose -f docker-compose.cicd.yaml up -d

# Wait for services to be ready
# Then access Jenkins at http://localhost:8080
```

## 🔧 Manual Configuration

### 1. Jenkins Initial Setup

```bash
# Get Jenkins admin password
docker exec smartdoc-jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

1. Open http://localhost:8080
2. Enter the admin password
3. Install suggested plugins
4. Create admin user

### 2. Add Credentials

**Manage Jenkins** → **Manage Credentials** → **System** → **Global credentials**

Add these credentials:

#### Docker Hub
```
Kind: Username with password
ID: docker-hub-credentials
Username: <your-docker-username>
Password: <docker-hub-token>
```

#### SonarQube
```
Kind: Secret text
ID: sonar-token
Secret: <your-sonarqube-token>
```

#### GitHub
```
Kind: Secret text
ID: github-token
Secret: <github-personal-access-token>
```

### 3. Configure SonarQube Token

1. Access SonarQube: http://localhost:9000
2. Login: admin/admin
3. **My Account** → **Security** → **Generate Tokens**
4. Copy token to Jenkins credentials

### 4. Create Pipeline Job

**New Item** → **Pipeline**

Configuration:
```
Pipeline name: smartdoc-backend-pipeline
Definition: Pipeline script from SCM
SCM: Git
Repository: https://github.com/YOUR_USERNAME/Smart-Doc-Backend-auth.git
Credentials: github-token
Branch: */main, */develop
Script path: Jenkinsfile
```

### 5. Configure GitHub Webhook

GitHub Repository → **Settings** → **Webhooks** → **Add webhook**

```
Payload URL: http://your-jenkins-url/github-webhook/
Content type: application/json
Events: Push events, Pull requests
Active: ✓
```

## 📊 Pipeline Stages Explained

### Build Stage
- Checks out code from Git
- Compiles Java source with Maven
- Generates compiled artifacts
- Duration: ~2-3 minutes

### SonarQube Analysis
- Performs static code analysis
- Checks code quality gates
- Reports bugs and vulnerabilities
- Duration: ~1-2 minutes

### Unit Tests
- Runs all JUnit tests
- Generates test reports
- Fails if tests don't pass
- Duration: ~1-2 minutes

### Docker Build
- Creates Docker image
- Tags with build number and commit hash
- Caches layers for faster builds
- Duration: ~1-2 minutes

### Push to Registry
- Authenticates with Docker Hub
- Pushes image with tags
- Pushes latest tag
- Only on main branch
- Duration: ~1 minute

### Kubernetes Deployment
- Creates namespace if needed
- Creates image pull secrets
- Updates deployment with new image
- Performs rolling update
- Only on main branch
- Duration: ~2-3 minutes

### Verification
- Checks deployment status
- Verifies pod health
- Reports service endpoints
- Duration: ~1 minute

## 📈 Kubernetes Deployment Details

### Deployment Configuration
```yaml
- Replicas: 2 (minimum for HA)
- Strategy: RollingUpdate (0 downtime)
- Auto-scaling: 2-5 replicas based on CPU/Memory
- Resources: 256Mi request, 512Mi limit
```

### Health Checks
```yaml
- Liveness: Checks if pod is alive
- Readiness: Checks if pod is ready to receive traffic
- Restart policy: Automatically restart failed pods
```

### Security
```yaml
- Non-root user: Runs as UID 1000
- Read-only filesystem: Prevents modifications
- No privilege escalation
- Dropped capabilities
```

### Networking
```yaml
- Service type: ClusterIP (internal)
- Pod anti-affinity: Spreads across nodes
- Network policies: Restricts traffic
```

## 🔒 Security Best Practices Implemented

1. **Credentials Management**
   - Secrets stored in Jenkins credentials
   - Environment variables for sensitive data
   - No hardcoded passwords

2. **Image Security**
   - Minimal base images (eclipse-temurin:17-slim)
   - Non-root user execution
   - Regular dependency updates

3. **Kubernetes Security**
   - RBAC configured with minimal permissions
   - Service accounts with role bindings
   - Network policies for traffic control

4. **Code Security**
   - SonarQube security hotspot scanning
   - Dependency vulnerability checking
   - Secret scanning in repositories

## 📊 Monitoring & Logs

### View Pipeline Logs

Jenkins Dashboard → Pipeline Name → Build Number → Console Output

### View Application Logs

```bash
# Kubernetes
kubectl logs -n smartdoc-dev -l app=smartdoc-backend -f

# Docker
docker logs smartdoc-jenkins
docker logs smartdoc-sonarqube
```

### Access Metrics

```bash
# Prometheus metrics endpoint
curl http://localhost:8080/actuator/prometheus

# Health endpoint
curl http://localhost:8080/actuator/health
```

## 🔄 Troubleshooting

### Jenkins Won't Start

```bash
# Check logs
docker logs smartdoc-jenkins --tail=50

# Restart service
docker restart smartdoc-jenkins

# Check disk space
docker exec smartdoc-jenkins df -h
```

### SonarQube Connection Failed

```bash
# Test connectivity
curl -u admin:admin http://localhost:9000/api/system/health

# Check PostgreSQL
docker logs smartdoc-postgres
```

### Kubernetes Deployment Failed

```bash
# Check pod status
kubectl describe pod <pod-name> -n smartdoc-dev

# Check events
kubectl get events -n smartdoc-dev --sort-by='.lastTimestamp'

# Check image availability
docker pull smartdoc-backend-auth:latest
```

## 🛠️ Customization

### Modify Build Parameters

Edit `Jenkinsfile`:
```groovy
environment {
    DOCKER_REGISTRY = 'docker.io'
    DOCKER_IMAGE_NAME = 'smartdoc-backend-auth'
    KUBE_NAMESPACE = 'smartdoc-dev'
}
```

### Add Custom Stages

Add new stage to Jenkinsfile:
```groovy
stage('Custom Stage') {
    steps {
        container('maven') {
            sh 'echo "Custom command"'
        }
    }
}
```

### Modify Kubernetes Resources

Edit `k8s/deployment.yaml`:
```yaml
resources:
  requests:
    memory: "512Mi"    # Increase memory
    cpu: "500m"        # Increase CPU
```

## 📚 Additional Resources

### Official Documentation
- [Jenkins Documentation](https://www.jenkins.io/doc/)
- [SonarQube Documentation](https://docs.sonarqube.org/)
- [Kubernetes Documentation](https://kubernetes.io/docs/)
- [Docker Documentation](https://docs.docker.com/)

### Related Guides
- [CI/CD Setup Guide](docs/CICD_SETUP_GUIDE.md)
- [Project Overview](PROJECT_OVERVIEW.md)
- [Architecture Documentation](docs/COMMUNICATION_ARCHITECTURE.md)

## 🎓 Learning Path

1. **Week 1**: Set up local infrastructure with docker-compose
2. **Week 2**: Configure Jenkins and create first pipeline job
3. **Week 3**: Set up Kubernetes cluster and test deployments
4. **Week 4**: Configure monitoring, alerts, and backups
5. **Week 5+**: Advanced features (multi-region, canary deployments, etc.)

## 🤝 Support & Contribution

For issues or improvements:
1. Check existing documentation
2. Review troubleshooting section
3. Create GitHub issue with details
4. Contact DevOps team

## 📝 Change Log

### Version 1.0 (Initial)
- ✅ Jenkins pipeline with Kubernetes agents
- ✅ SonarQube integration
- ✅ Docker build and push
- ✅ Kubernetes deployment
- ✅ GitHub Actions alternative
- ✅ Comprehensive documentation

## ⚠️ Important Notes

1. **Credentials**: Update all placeholder credentials before production
2. **Scaling**: Adjust resource limits based on your cluster capacity
3. **Cost**: Monitor container registry and Kubernetes costs
4. **Backups**: Implement database backup strategy
5. **Updates**: Keep all tools updated for security patches

## 🎉 You're Ready!

Your SmartDoc Backend now has:
- ✅ Automated CI/CD pipeline
- ✅ Code quality monitoring
- ✅ Containerized deployment
- ✅ Kubernetes orchestration
- ✅ Auto-scaling and high availability
- ✅ Comprehensive monitoring and logging

Happy deploying! 🚀
