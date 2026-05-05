param(
    [string]$RootPath = (Split-Path -Parent $MyInvocation.MyCommand.Path),
    [string]$ProviderApiKey = "",
    [string]$ModelName = "",
    [string]$ServiceToken = "",
    [int]$StartupTimeoutSec = 120,
    [switch]$CheckOnly,
    [switch]$AllowNoProviderKey
)

$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[smartdoc-stack] $Message"
}

function Set-EnvIfMissing {
    param(
        [string]$Name,
        [string]$Value
    )
    $current = (Get-Item -Path "Env:$Name" -ErrorAction SilentlyContinue).Value
    if ([string]::IsNullOrWhiteSpace($current) -and -not [string]::IsNullOrWhiteSpace($Value)) {
        Set-Item -Path "Env:$Name" -Value $Value
    }
}

function Read-DotEnvFile {
    param([string]$FilePath)

    if (-not (Test-Path -LiteralPath $FilePath)) {
        return
    }

    Get-Content -LiteralPath $FilePath | ForEach-Object {
        $line = $_.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
            return
        }

        $eqIndex = $line.IndexOf("=")
        if ($eqIndex -lt 1) {
            return
        }

        $name = $line.Substring(0, $eqIndex).Trim()
        $value = $line.Substring($eqIndex + 1).Trim().Trim('"').Trim("'")
        if (-not [string]::IsNullOrWhiteSpace($name)) {
            Set-EnvIfMissing -Name $name -Value $value
        }
    }
}

function Get-FirstNonEmpty {
    param([string[]]$Values)

    foreach ($value in $Values) {
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value
        }
    }

    return ""
}

function New-ServiceToken {
    return (([guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N")).ToLowerInvariant())
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
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
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

function Start-BackgroundProcess {
    param(
        [string]$Name,
        [string]$WorkingDirectory,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$LogDirectory
    )

    if (-not (Test-Path -LiteralPath $LogDirectory)) {
        New-Item -ItemType Directory -Path $LogDirectory | Out-Null
    }

    $outLog = Join-Path $LogDirectory ("{0}.out.log" -f $Name)
    $errLog = Join-Path $LogDirectory ("{0}.err.log" -f $Name)

    $process = Start-Process -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -PassThru

    Write-Step ("Started {0} (PID {1})" -f $Name, $process.Id)

    return [pscustomobject]@{
        Name = $Name
        Pid = $process.Id
        OutLog = $outLog
        ErrLog = $errLog
    }
}

$smartdocPath = (Resolve-Path -LiteralPath $RootPath).Path
$aiSmartDocPath = Join-Path $smartdocPath "AiSmartDoc"
$smartdocAiPath = Join-Path $smartdocPath "smartdoc-ai"

if (-not (Test-Path -LiteralPath $aiSmartDocPath)) {
    throw "AiSmartDoc folder not found at: $aiSmartDocPath"
}
if (-not (Test-Path -LiteralPath $smartdocAiPath)) {
    throw "smartdoc-ai folder not found at: $smartdocAiPath"
}

$envFile = Join-Path $smartdocAiPath ".env"
Read-DotEnvFile -FilePath $envFile

if (-not [string]::IsNullOrWhiteSpace($ProviderApiKey)) {
    Set-Item -Path "Env:GEMINI_API_KEY" -Value $ProviderApiKey
}

$effectiveApiKey = Get-FirstNonEmpty @(
    $env:GEMINI_API_KEY,
    $env:GOOGLE_API_KEY,
    $env:OPENAI_API_KEY
)

if ([string]::IsNullOrWhiteSpace($effectiveApiKey) -and -not $AllowNoProviderKey) {
    throw "No AI provider key found. Set GEMINI_API_KEY (or GOOGLE_API_KEY / OPENAI_API_KEY), or pass -AllowNoProviderKey."
}

if ([string]::IsNullOrWhiteSpace($ModelName)) {
    $ModelName = Get-FirstNonEmpty @($env:MODEL_NAME, $env:GEMINI_MODEL_NAME, "gemini-2.5-flash")
}
Set-Item -Path "Env:MODEL_NAME" -Value $ModelName

$effectiveServiceToken = Get-FirstNonEmpty @(
    $ServiceToken,
    $env:SERVICE_TOKEN,
    $env:APP_AI_SERVICE_TOKEN,
    $env:APP_AI_GATEWAY_SERVICE_TOKEN
)

if ([string]::IsNullOrWhiteSpace($effectiveServiceToken)) {
    $effectiveServiceToken = New-ServiceToken
    Write-Step "No shared service token was configured; generated a fresh one for this session."
}

if (-not [string]::IsNullOrWhiteSpace($effectiveServiceToken)) {
    Set-Item -Path "Env:SERVICE_TOKEN" -Value $effectiveServiceToken
    Set-Item -Path "Env:APP_AI_SERVICE_TOKEN" -Value $effectiveServiceToken
    Set-Item -Path "Env:APP_AI_GATEWAY_SERVICE_TOKEN" -Value $effectiveServiceToken
}

$logDirectory = Join-Path $smartdocPath "logs"
if (-not (Test-Path -LiteralPath $logDirectory)) {
    New-Item -ItemType Directory -Path $logDirectory | Out-Null
}

$serviceTokenFile = Join-Path $logDirectory "service-token.txt"
if (-not [string]::IsNullOrWhiteSpace($effectiveServiceToken)) {
    Set-Content -LiteralPath $serviceTokenFile -Value $effectiveServiceToken -NoNewline
}

Set-EnvIfMissing -Name "APP_AI_BASE_URL" -Value "http://localhost:8000"
Set-EnvIfMissing -Name "APP_AI_GATEWAY_BASE_URL" -Value "http://localhost:8088/api/v1/ai"

Write-Step "Configuration summary"
Write-Host ("- smartdoc root: {0}" -f $smartdocPath)
Write-Host ("- smartdoc-ai .env: {0}" -f $envFile)
Write-Host ("- provider key loaded: {0}" -f (-not [string]::IsNullOrWhiteSpace($effectiveApiKey)))
Write-Host ("- model: {0}" -f $env:MODEL_NAME)
Write-Host ("- service token loaded: {0}" -f (-not [string]::IsNullOrWhiteSpace($effectiveServiceToken)))
Write-Host ("- APP_AI_BASE_URL: {0}" -f $env:APP_AI_BASE_URL)
Write-Host ("- APP_AI_GATEWAY_BASE_URL: {0}" -f $env:APP_AI_GATEWAY_BASE_URL)

if ($CheckOnly) {
    Write-Step "CheckOnly mode enabled. No process started."
    exit 0
}

$pythonExe = Join-Path $smartdocAiPath ".venv\Scripts\python.exe"
if (-not (Test-Path -LiteralPath $pythonExe)) {
    $pythonExe = "python"
}

if (-not (Get-Command "$pythonExe" -ErrorAction SilentlyContinue)) {
    throw "Python not found. Create smartdoc-ai/.venv or install python and add it to PATH."
}

if (-not (Get-Command "java" -ErrorAction SilentlyContinue)) {
    throw "Java runtime not found in PATH."
}

$logDirectory = Join-Path $smartdocPath "logs"
$started = @()

$started += Start-BackgroundProcess -Name "smartdoc-ai" -WorkingDirectory $smartdocAiPath -FilePath $pythonExe -ArgumentList @("-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "8000") -LogDirectory $logDirectory
Wait-HttpOk -Name "smartdoc-ai" -Url "http://127.0.0.1:8000/health" -TimeoutSec $StartupTimeoutSec

$started += Start-BackgroundProcess -Name "AiSmartDoc" -WorkingDirectory $aiSmartDocPath -FilePath "cmd.exe" -ArgumentList @("/c", "mvnw.cmd -Dmaven.test.skip=true spring-boot:run") -LogDirectory $logDirectory
Wait-HttpOk -Name "AiSmartDoc" -Url "http://127.0.0.1:8088/api/v1/ai/health" -TimeoutSec $StartupTimeoutSec

$started += Start-BackgroundProcess -Name "smartdoc" -WorkingDirectory $smartdocPath -FilePath "cmd.exe" -ArgumentList @("/c", "mvnw.cmd -Dmaven.test.skip=true spring-boot:run") -LogDirectory $logDirectory
Wait-HttpOk -Name "smartdoc" -Url "http://127.0.0.1:8087/v3/api-docs" -TimeoutSec $StartupTimeoutSec

Write-Step "All services are up."
$started | Format-Table -AutoSize | Out-String | Write-Host
Write-Host "Use Stop-Process -Id <PID> to stop a service."
