@echo off
REM SmartDoc CI/CD Pipeline Setup Script (Windows)
REM This script automates the setup of the complete CI/CD infrastructure

setlocal enabledelayedexpansion

echo.
echo ==========================================
echo SmartDoc CI/CD Pipeline Setup (Windows)
echo ==========================================
echo.

REM Check prerequisites
echo Checking prerequisites...

where docker >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Docker is not installed
    exit /b 1
)
echo [OK] Docker found

where docker-compose >nul 2>nul
if errorlevel 1 (
    echo [WARNING] Docker Compose is not installed separately ^(might be built-in^)
) else (
    echo [OK] Docker Compose found
)

where kubectl >nul 2>nul
if errorlevel 1 (
    echo [WARNING] kubectl is not installed - needed for Kubernetes deployment
) else (
    echo [OK] kubectl found
)

where git >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Git is not installed
    exit /b 1
)
echo [OK] Git found

REM Create directories
echo.
echo Creating required directories...
if not exist "k8s" mkdir k8s
if not exist "jenkins-config" mkdir jenkins-config
if not exist "docs" mkdir docs
echo [OK] Directories created

REM Start services
echo.
echo Starting CI/CD services...

if not exist "docker-compose.cicd.yaml" (
    echo [ERROR] docker-compose.cicd.yaml not found
    exit /b 1
)

docker-compose -f docker-compose.cicd.yaml up -d
echo [OK] Services starting...

REM Wait for services
echo.
echo Waiting for services to be healthy...

timeout /t 10 /nobreak

REM Display services
echo.
echo ==========================================
echo Services Status
echo ==========================================
docker-compose -f docker-compose.cicd.yaml ps

REM Display access information
echo.
echo ==========================================
echo Access Information
echo ==========================================
echo.
echo Jenkins:
echo   URL: http://localhost:8080
echo   Check logs for initial admin password: docker logs smartdoc-jenkins
echo.
echo SonarQube:
echo   URL: http://localhost:9000
echo   Username: admin
echo   Password: admin
echo.
echo PostgreSQL:
echo   Host: localhost:5432
echo   Username: smartdoc
echo   Password: smartdoc123
echo.
echo Kafka:
echo   Bootstrap Servers: localhost:9092
echo.

echo ==========================================
echo Setup Complete!
echo ==========================================
echo.
echo Next Steps:
echo 1. Access Jenkins at http://localhost:8080
echo 2. Complete initial setup with admin password
echo 3. Install suggested plugins
echo 4. Add credentials ^(Docker Hub, SonarQube, GitHub^)
echo 5. Create pipeline job from Jenkinsfile
echo 6. Configure GitHub webhook for auto-triggers
echo.
echo For detailed setup instructions, see: docs/CICD_SETUP_GUIDE.md
echo.

endlocal
