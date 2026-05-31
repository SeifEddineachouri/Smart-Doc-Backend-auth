param(
    [string]$RootPath = (Split-Path -Parent $MyInvocation.MyCommand.Path),
    [int]$TimeoutSec = 240,
    [switch]$Build,
    [switch]$Restart,
    [switch]$SkipRemotePull,
    [string]$DockerHubNamespace = "",
    [string]$DockerHubTag = "latest"
)

$ErrorActionPreference = 'Stop'

function Write-Step {
    param([string]$Message)
    Write-Host "[smartdoc-verify] $Message"
}

function Assert-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found in PATH."
    }
}

function Invoke-DockerCompose {
    param([string[]]$ComposeArgs)
    & docker compose @ComposeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($ComposeArgs -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Get-ContainerState {
    param([string]$Name)
    $result = & docker inspect --format '{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' $Name 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($result)) {
        return $null
    }
    return $result.Trim()
}

function Wait-ContainerHealthy {
    param(
        [string]$Name,
        [int]$TimeoutSec
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $lastState = $null
    while ((Get-Date) -lt $deadline) {
        $state = Get-ContainerState -Name $Name
        $lastState = $state
        if ([string]::IsNullOrWhiteSpace($state)) {
            Start-Sleep -Seconds 2
            continue
        }

        $parts = $state.Split('|', 2)
        $runtimeState = $parts[0]
        $healthState = if ($parts.Count -gt 1) { $parts[1] } else { 'none' }

        if ($runtimeState -eq 'running' -and ($healthState -eq 'healthy' -or $healthState -eq 'none')) {
            Write-Step "$Name is running ($runtimeState/$healthState)"
            return
        }

        if ($runtimeState -eq 'exited' -or $runtimeState -eq 'dead') {
            throw "$Name stopped unexpectedly while waiting for health. Last state: $state"
        }

        Start-Sleep -Seconds 3
    }

    throw "$Name did not become healthy within $TimeoutSec seconds. Last observed state: $lastState"
}

function Wait-HttpOk {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSec
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $lastError = $null
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -Method GET -TimeoutSec 8 -ErrorAction Stop
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                Write-Step "$Name responded with HTTP $($response.StatusCode) at $Url"
                return
            }
        }
        catch {
            $lastError = $_.Exception.Message
            Start-Sleep -Seconds 2
        }
    }

    throw "$Name did not respond successfully within $TimeoutSec seconds. Last error: $lastError"
}

function Get-ContainerEnv {
    param([string]$Name)
    $envLines = & docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' $Name 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to inspect environment for container '$Name'."
    }
    return @($envLines)
}

function Assert-EnvContains {
    param(
        [string]$Container,
        [string]$ExpectedFragment
    )

    $envLines = Get-ContainerEnv -Name $Container
    if (-not ($envLines -match [regex]::Escape($ExpectedFragment))) {
        throw "Container '$Container' does not contain expected environment fragment: $ExpectedFragment"
    }
    Write-Step "$Container contains '$ExpectedFragment'"
}

function Test-LocalImage {
    param([string]$Image)
    & docker image inspect $Image | Out-Null
    if ($LASTEXITCODE -ne 0) {
        return $false
    }
    return $true
}

function Test-RemoteImage {
    param([string]$Image)
    Write-Step "Pulling Docker Hub image $Image"
    & docker pull $Image | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to pull remote image '$Image'"
    }
}

$root = (Resolve-Path -LiteralPath $RootPath).Path
Set-Location $root

Assert-Command -Name 'docker'
Assert-Command -Name 'Invoke-WebRequest'

$localImages = @(
    'smartdoc:local',
    'aismartdoc:local',
    'smartdoc-ai:local',
    'smartdoc-payment-service:latest',
    'smartdoc-frontend:local',
    'postgres:16-alpine',
    'apache/kafka:3.8.1'
)

if ($Build -or $Restart) {
    Write-Step 'Starting compose stack with rebuild'
    Invoke-DockerCompose -ComposeArgs @('up', '-d', '--build')
}
else {
    $missing = @($localImages | Where-Object { -not (Test-LocalImage -Image $_) })
    if ($missing.Count -gt 0) {
        Write-Step "Missing local images: $($missing -join ', '); starting compose with rebuild"
        Invoke-DockerCompose -ComposeArgs @('up', '-d', '--build')
    }
    else {
        Write-Step 'All expected local images already exist; starting compose stack'
        Invoke-DockerCompose -ComposeArgs @('up', '-d')
    }
}

Write-Step 'Waiting for Docker containers to report healthy'
Wait-ContainerHealthy -Name 'smartdoc-postgres' -TimeoutSec $TimeoutSec
Wait-ContainerHealthy -Name 'smartdoc-kafka' -TimeoutSec $TimeoutSec
Wait-ContainerHealthy -Name 'smartdoc-ai' -TimeoutSec $TimeoutSec
Wait-ContainerHealthy -Name 'aismartdoc' -TimeoutSec $TimeoutSec
Wait-ContainerHealthy -Name 'smartdoc' -TimeoutSec $TimeoutSec
Wait-ContainerHealthy -Name 'smartdoc-payment-service' -TimeoutSec $TimeoutSec
Wait-ContainerHealthy -Name 'smartdoc-frontend' -TimeoutSec $TimeoutSec

Write-Step 'Validating container environment wiring'
Assert-EnvContains -Container 'smartdoc' -ExpectedFragment 'SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/SmartDoc'
Assert-EnvContains -Container 'smartdoc' -ExpectedFragment 'KAFKA_BOOTSTRAP_SERVERS=kafka:9092'
Assert-EnvContains -Container 'smartdoc' -ExpectedFragment 'APP_AI_GATEWAY_BASE_URL=http://aismartdoc:8088/api/v1/ai'
Assert-EnvContains -Container 'aismartdoc' -ExpectedFragment 'APP_AI_BASE_URL=http://smartdoc-ai:8000'
Assert-EnvContains -Container 'smartdoc-ai' -ExpectedFragment 'SERVICE_TOKEN='

Write-Step 'Validating databases and broker'
& docker exec smartdoc-postgres pg_isready -U postgres -d SmartDoc
if ($LASTEXITCODE -ne 0) {
    throw "PostgreSQL health check failed with exit code $LASTEXITCODE"
}

& docker exec smartdoc-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
if ($LASTEXITCODE -ne 0) {
    throw "Kafka health check failed with exit code $LASTEXITCODE"
}

Write-Step 'Validating HTTP endpoints'
Wait-HttpOk -Name 'smartdoc-ai health' -Url 'http://127.0.0.1:8000/health' -TimeoutSec $TimeoutSec
Wait-HttpOk -Name 'AiSmartDoc health' -Url 'http://127.0.0.1:8088/api/v1/ai/health' -TimeoutSec $TimeoutSec
Wait-HttpOk -Name 'smartdoc OpenAPI' -Url 'http://127.0.0.1:8087/v3/api-docs' -TimeoutSec $TimeoutSec
Wait-HttpOk -Name 'payment-service health' -Url 'http://127.0.0.1:8089/actuator/health' -TimeoutSec $TimeoutSec
Wait-HttpOk -Name 'frontend health' -Url 'http://127.0.0.1:4200/health' -TimeoutSec $TimeoutSec

if (-not $SkipRemotePull) {
    if ([string]::IsNullOrWhiteSpace($DockerHubNamespace)) {
        throw "Provide -DockerHubNamespace to verify Docker Hub pulls, or use -SkipRemotePull."
    }

    $remoteImages = @(
        "$DockerHubNamespace/smartdoc-backend-auth:$DockerHubTag",
        "$DockerHubNamespace/smartdoc-backend-fast-api:$DockerHubTag",
        "$DockerHubNamespace/smartdoc-backend-python:$DockerHubTag"
    )

    Write-Step 'Validating Docker Hub images by pulling the requested tags'
    foreach ($image in $remoteImages) {
        Test-RemoteImage -Image $image
    }
}

Write-Step 'All checks passed'
Write-Host '- Local images: smartdoc:local, aismartdoc:local, smartdoc-ai:local, smartdoc-payment-service:latest, smartdoc-frontend:local'
Write-Host '- Database: PostgreSQL healthy and accepting connections'
Write-Host '- Broker: Kafka healthy and responding'
Write-Host '- HTTP: smartdoc-ai, AiSmartDoc, smartdoc, payment-service, and frontend endpoints are healthy'
if (-not $SkipRemotePull) {
    Write-Host "- Docker Hub: $DockerHubNamespace/$DockerHubTag images pulled successfully"
}
