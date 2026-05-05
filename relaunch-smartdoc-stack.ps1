param(
    [string]$RootPath = (Split-Path -Parent $MyInvocation.MyCommand.Path),
    [string]$ProviderApiKey = "",
    [string]$ModelName = "",
    [string]$ServiceToken = "",
    [int]$StartupTimeoutSec = 120,
    [int]$CheckTimeoutSec = 10,
    [string]$SpringProfilesActive = "dev",
    [switch]$AllowNoProviderKey
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[smartdoc-relaunch] $Message"
}

function Stop-ListeningPort {
    param([int]$Port)

    $listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $listeners) {
        Write-Step "Port $Port already free"
        return
    }

    $pids = $listeners | Select-Object -ExpandProperty OwningProcess -Unique
    foreach ($processId in $pids) {
        if ($processId -gt 0) {
            try {
                Stop-Process -Id $processId -Force -ErrorAction Stop
                Write-Step "Stopped process $processId on port $Port"
            }
            catch {
                Write-Step "Unable to stop process $processId on port ${Port}: $($_.Exception.Message)"
            }
        }
    }
}

function Wait-HttpOk {
    param(
        [string]$Name,
        [string]$Url,
        [int]$TimeoutSec
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -Method GET -TimeoutSec 5 -ErrorAction Stop
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                Write-Step "$Name is reachable at $Url"
                return
            }
        }
        catch {
            Start-Sleep -Seconds 2
        }
    }

    throw "$Name did not become reachable in $TimeoutSec seconds. Last URL checked: $Url"
}

function Initialize-JavaRuntime {
    if (Get-Command java -ErrorAction SilentlyContinue) {
        return
    }

    $candidateHomes = @(
        $env:JAVA_HOME,
        "C:\Users\seifa\.jdks\corretto-23.0.2",
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Adoptium",
        "C:\Program Files\Java"
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

    foreach ($candidateHome in $candidateHomes) {
        $javaExe = Join-Path $candidateHome "bin\java.exe"
        if (Test-Path -LiteralPath $javaExe) {
            $env:JAVA_HOME = $candidateHome
            if ($env:Path -notlike "*$($candidateHome)\bin*") {
                $env:Path = "$candidateHome\bin;$env:Path"
            }
            return
        }
    }

    throw "Java runtime not found. Set JAVA_HOME or install a JDK before relaunching the stack."
}

$smartdocPath = (Resolve-Path -LiteralPath $RootPath).Path
$startScript = Join-Path $smartdocPath "start-all-services.ps1"
$checkScript = Join-Path $smartdocPath "AiSmartDoc\check-ai-integration.ps1"
$tokenFile = Join-Path $smartdocPath "logs\service-token.txt"

if (-not (Test-Path -LiteralPath $startScript)) {
    throw "start-all-services.ps1 not found at: $startScript"
}
if (-not (Test-Path -LiteralPath $checkScript)) {
    throw "check-ai-integration.ps1 not found at: $checkScript"
}

Write-Step "Stopping any listeners on 8000, 8087, and 8088"
foreach ($port in 8000, 8087, 8088) {
    Stop-ListeningPort -Port $port
}

Initialize-JavaRuntime
if ([string]::IsNullOrWhiteSpace($env:SPRING_PROFILES_ACTIVE)) {
    $env:SPRING_PROFILES_ACTIVE = $SpringProfilesActive
    Write-Step "Using Spring profile '$SpringProfilesActive' for local relaunch"
}

Write-Step "Starting the SmartDoc stack"
$startupArgs = @(
    "-ExecutionPolicy", "Bypass",
    "-File", $startScript,
    "-StartupTimeoutSec", $StartupTimeoutSec
)
if (-not [string]::IsNullOrWhiteSpace($ProviderApiKey)) {
    $startupArgs += @("-ProviderApiKey", $ProviderApiKey)
}
if (-not [string]::IsNullOrWhiteSpace($ModelName)) {
    $startupArgs += @("-ModelName", $ModelName)
}
if (-not [string]::IsNullOrWhiteSpace($ServiceToken)) {
    $startupArgs += @("-ServiceToken", $ServiceToken)
}
if ($AllowNoProviderKey) {
    $startupArgs += "-AllowNoProviderKey"
}

& powershell.exe @startupArgs

if ($LASTEXITCODE -ne 0) {
    throw "start-all-services.ps1 exited with code $LASTEXITCODE"
}

Wait-HttpOk -Name "smartdoc-ai" -Url "http://127.0.0.1:8000/health" -TimeoutSec $StartupTimeoutSec
Wait-HttpOk -Name "AiSmartDoc" -Url "http://127.0.0.1:8088/api/v1/ai/health" -TimeoutSec $StartupTimeoutSec
Wait-HttpOk -Name "smartdoc" -Url "http://127.0.0.1:8087/v3/api-docs" -TimeoutSec $StartupTimeoutSec

$sharedToken = ""
if (Test-Path -LiteralPath $tokenFile) {
    $sharedToken = (Get-Content -LiteralPath $tokenFile -Raw).Trim()
}

Write-Step "Running AI integration checks"
 $checkArgs = @(
    "-ExecutionPolicy", "Bypass",
    "-File", $checkScript,
    "-FastApiBaseUrl", "http://127.0.0.1:8000",
    "-SpringBaseUrl", "http://127.0.0.1:8088"
)
if (-not [string]::IsNullOrWhiteSpace($sharedToken)) {
    $checkArgs += @("-ServiceToken", $sharedToken)
}

& powershell.exe @checkArgs
if ($LASTEXITCODE -ne 0) {
    throw "check-ai-integration.ps1 exited with code $LASTEXITCODE"
}

Write-Step "Validating root API docs endpoint"
$docsResponse = Invoke-WebRequest -Uri "http://127.0.0.1:8087/v3/api-docs" -UseBasicParsing -Method GET -TimeoutSec $CheckTimeoutSec -ErrorAction Stop
if ($docsResponse.StatusCode -ne 200) {
    throw "Root OpenAPI check failed with status code $($docsResponse.StatusCode)"
}

Write-Step "Relaunch completed successfully"
Write-Host "- smartdoc-ai: http://127.0.0.1:8000/health"
Write-Host "- AiSmartDoc:  http://127.0.0.1:8088/api/v1/ai/health"
Write-Host "- smartdoc:    http://127.0.0.1:8087/v3/api-docs"

