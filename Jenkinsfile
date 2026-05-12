pipeline {
    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
metadata:
  labels:
    jenkins: agent
spec:
  serviceAccountName: jenkins
  containers:
  - name: maven
    image: maven:3.9-eclipse-temurin-17
    command: ['cat']
    tty: true
    volumeMounts:
    - name: docker-sock
      mountPath: /var/run/docker.sock
  - name: docker
    image: docker:24-dind
    securityContext:
      privileged: true
    volumeMounts:
    - name: docker-sock
      mountPath: /var/run/docker.sock
  - name: kubectl
    image: bitnami/kubectl:latest
    command: ['cat']
    tty: true
  volumes:
  - name: docker-sock
    hostPath:
      path: /var/run/docker.sock
'''
        }
    }

    environment {
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REGISTRY_CREDENTIALS = 'docker-hub-credentials'
        DOCKER_IMAGE_NAME = 'smartdoc-backend-auth'
        DOCKER_IMAGE_TAG = "${BUILD_NUMBER}-${GIT_COMMIT.take(7)}"
        SONAR_HOST_URL = 'http://sonarqube:9000'
        SONAR_LOGIN = credentials('sonar-token')
        KUBE_NAMESPACE = 'smartdoc-dev'
        APP_NAME = 'smartdoc-backend'
    }

    options {
        timeout(time: 1, unit: 'HOURS')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Checkout') {
            steps {
                echo "🔍 Checking out repository..."
                checkout scm
            }
        }

        stage('Build') {
            steps {
                container('maven') {
                    echo "🔨 Building Maven project..."
                    sh '''
                        mvn clean package -DskipTests \
                            -Dorg.slf4j.simpleLogger.defaultLogLevel=warn
                    '''
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                container('maven') {
                    echo "📊 Running SonarQube analysis..."
                    sh '''
                        mvn sonar:sonar \
                            -Dsonar.projectKey=smartdoc-backend-auth \
                            -Dsonar.sources=src/main \
                            -Dsonar.tests=src/test \
                            -Dsonar.java.binaries=target/classes \
                            -Dsonar.host.url=${SONAR_HOST_URL} \
                            -Dsonar.login=${SONAR_LOGIN}
                    '''
                }
            }
        }

        stage('Unit Tests') {
            steps {
                container('maven') {
                    echo "✅ Running unit tests..."
                    sh '''
                        mvn test -B \
                            -Dorg.slf4j.simpleLogger.defaultLogLevel=warn
                    '''
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                container('docker') {
                    echo "🐳 Building Docker image..."
                    sh '''
                        docker build \
                            -t ${DOCKER_REGISTRY}/${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG} \
                            -t ${DOCKER_REGISTRY}/${DOCKER_IMAGE_NAME}:latest \
                            .
                    '''
                }
            }
        }

        stage('Push to Registry') {
            when {
                branch 'main'
            }
            steps {
                container('docker') {
                    echo "📤 Pushing image to Docker Hub..."
                    withCredentials([usernamePassword(credentialsId: '${DOCKER_REGISTRY_CREDENTIALS}',
                            passwordVariable: 'DOCKER_PASSWORD',
                            usernameVariable: 'DOCKER_USERNAME')]) {
                        sh '''
                            echo ${DOCKER_PASSWORD} | docker login -u ${DOCKER_USERNAME} --password-stdin
                            docker push ${DOCKER_REGISTRY}/${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG}
                            docker push ${DOCKER_REGISTRY}/${DOCKER_IMAGE_NAME}:latest
                            docker logout
                        '''
                    }
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when {
                branch 'main'
            }
            steps {
                container('kubectl') {
                    echo "🚀 Deploying to Kubernetes..."
                    sh '''
                        # Create namespace if it doesn't exist
                        kubectl create namespace ${KUBE_NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Create image pull secret if using private registry
                        kubectl create secret docker-registry regcred \
                            --docker-server=${DOCKER_REGISTRY} \
                            --docker-username=${DOCKER_USERNAME} \
                            --docker-password=${DOCKER_PASSWORD} \
                            -n ${KUBE_NAMESPACE} \
                            --dry-run=client -o yaml | kubectl apply -f -
                        
                        # Update deployment with new image
                        kubectl set image deployment/${APP_NAME} \
                            ${APP_NAME}=${DOCKER_REGISTRY}/${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG} \
                            -n ${KUBE_NAMESPACE} || true
                        
                        # Apply manifests
                        kubectl apply -f k8s/ -n ${KUBE_NAMESPACE}
                        
                        # Wait for rollout
                        kubectl rollout status deployment/${APP_NAME} -n ${KUBE_NAMESPACE} --timeout=5m
                    '''
                }
            }
        }

        stage('Verify Deployment') {
            when {
                branch 'main'
            }
            steps {
                container('kubectl') {
                    echo "✔️ Verifying deployment..."
                    sh '''
                        kubectl get deployment ${APP_NAME} -n ${KUBE_NAMESPACE}
                        kubectl get pods -n ${KUBE_NAMESPACE} -l app=${APP_NAME}
                        kubectl get svc ${APP_NAME} -n ${KUBE_NAMESPACE}
                    '''
                }
            }
        }
    }

    post {
        always {
            echo "📋 Pipeline execution completed"
            cleanWs()
        }
        success {
            echo "✅ Pipeline succeeded!"
        }
        failure {
            echo "❌ Pipeline failed!"
        }
    }
}
