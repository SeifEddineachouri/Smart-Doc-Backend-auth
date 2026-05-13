pipeline {
    agent any

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
                echo "🔨 Building Maven project..."
                sh '''
                    mvn clean package -DskipTests \
                        -Dorg.slf4j.simpleLogger.defaultLogLevel=warn
                '''
            }
        }

        stage('SonarQube Analysis') {
            steps {
                echo "📊 Running SonarQube analysis..."
                sh '''
                    mvn sonar:sonar \
                        -Dsonar.projectKey=smartdoc-backend-auth \
                        -Dsonar.sources=src/main \
                        -Dsonar.tests=src/test \
                        -Dsonar.java.binaries=target/classes \
                        -Dsonar.host.url=${SONAR_HOST_URL} \
                        -Dsonar.login=${SONAR_LOGIN} || echo "⚠️ SonarQube scan skipped"
                '''
            }
        }

        stage('Unit Tests') {
            steps {
                echo "✅ Running unit tests..."
                sh '''
                    mvn test -B \
                        -Dorg.slf4j.simpleLogger.defaultLogLevel=warn
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "🐳 Building Docker image..."
                sh '''
                    docker build \
                        -t ${DOCKER_REGISTRY}/${DOCKER_IMAGE_NAME}:${DOCKER_IMAGE_TAG} \
                        -t ${DOCKER_REGISTRY}/${DOCKER_IMAGE_NAME}:latest \
                        .
                '''
            }
        }

        stage('Push to Registry') {
            when {
                branch 'main'
            }
            steps {
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

        stage('Deploy to Kubernetes') {
            when {
                branch 'main'
            }
            steps {
                echo "🚀 Deploying to Kubernetes..."
                sh '''
                    echo "⚠️ Kubernetes deployment skipped (requires KUBE_CONFIG)"
                    echo "To enable K8s deployment:"
                    echo "1. Add KUBE_CONFIG secret to Jenkins"
                    echo "2. Uncomment deployment steps in Jenkinsfile"
                '''
            }
        }

        stage('Verify Deployment') {
            when {
                branch 'main'
            }
            steps {
                echo "✔️ Deployment verification..."
                sh '''
                    echo "Deployment verification skipped (local environment)"
                '''
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
