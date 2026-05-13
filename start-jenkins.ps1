#!/usr/bin/env pwsh
# Start Docker Desktop and Jenkins stack

Write-Host "🐳 Checking Docker..." -ForegroundColor Blue

# Check if Docker is installed
$dockerInstalled = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerInstalled) {
    Write-Host "❌ Docker not found. Please install Docker Desktop:" -ForegroundColor Red
    Write-Host "   https://www.docker.com/products/docker-desktop" -ForegroundColor Yellow
    exit 1
}

# Try to ping Docker daemon
Write-Host "📌 Starting Docker Desktop..." -ForegroundColor Blue
$dockerRunning = docker ps 2>$null
if (-not $dockerRunning) {
    Write-Host "Docker not responding. Attempting to start Docker Desktop..." -ForegroundColor Yellow
    
    # Try to start Docker Desktop
    $dockerDesktopPath = "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
    if (Test-Path $dockerDesktopPath) {
        & $dockerDesktopPath
        Write-Host "⏳ Waiting for Docker to start (30 seconds)..." -ForegroundColor Yellow
        Start-Sleep -Seconds 30
    } else {
        Write-Host "❌ Docker Desktop executable not found at $dockerDesktopPath" -ForegroundColor Red
        exit 1
    }
}

# Verify Docker is now running
Write-Host "✓ Checking Docker is responsive..." -ForegroundColor Green
docker ps | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Docker still not responding. Please open Docker Desktop manually." -ForegroundColor Red
    exit 1
}

Write-Host "✅ Docker is running!" -ForegroundColor Green

# Start Jenkins stack
Write-Host "`n🚀 Starting Jenkins stack..." -ForegroundColor Blue
Set-Location "c:\Users\Mega Pc\OneDrive\Desktop\Back Auth\Smart-Doc-Backend-auth"
docker-compose -f docker-compose.jenkins.yaml up -d

if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Jenkins stack started!" -ForegroundColor Green
    Write-Host "`n📝 Next steps:" -ForegroundColor Yellow
    Write-Host "   1. Wait 60-90 seconds for services to fully start"
    Write-Host "   2. Open: http://localhost:8080"
    Write-Host "   3. Jenkins should load with auto-configured pipeline"
    Write-Host "`n📊 Monitoring services:" -ForegroundColor Yellow
    Write-Host "   Jenkins:    http://localhost:8080"
    Write-Host "   SonarQube:  http://localhost:9000"
    Write-Host "   PostgreSQL: localhost:5432"
    Write-Host "`n✋ To stop services:" -ForegroundColor Yellow
    Write-Host "   docker-compose -f docker-compose.jenkins.yaml down"
} else {
    Write-Host "❌ Failed to start Jenkins stack" -ForegroundColor Red
    exit 1
}
