param(
    [string]$RootPath = (Split-Path -Parent $MyInvocation.MyCommand.Path),
    [string]$ProviderApiKey = "",
    [string]$ModelName = "",
    [string]$ServiceToken = "",
    [string]$StripeSecretKey = "",
    [string]$JavaHome = "",
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

function Test-JavaHome {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path)) {
        return $false
    }
    return (Test-Path -LiteralPath (Join-Path $Path "bin\java.exe"))
}

function Resolve-JavaHome {
    param([string]$ExplicitJavaHome)

    # 1. Explicit -JavaHome parameter wins.
    if (Test-JavaHome -Path $ExplicitJavaHome) {
        return (Resolve-Path -LiteralPath $ExplicitJavaHome).Path
    }

    # 2. Existing JAVA_HOME, if it points at a real JDK.
    if (Test-JavaHome -Path $env:JAVA_HOME) {
        return (Resolve-Path -LiteralPath $env:JAVA_HOME).Path
    }

    # 3. java already on PATH -> derive its home from the exe location.
    $javaOnPath = Get-Command "java" -ErrorAction SilentlyContinue
    if ($javaOnPath) {
        $binDir = Split-Path -Parent $javaOnPath.Source
        $jdkHome = Split-Path -Parent $binDir
        if (Test-JavaHome -Path $jdkHome) {
            return $jdkHome
        }
    }

    # 4. Auto-detect from common install locations (newest version first).
    $candidateRoots = @(
        (Join-Path $env:USERPROFILE ".jdks"),
        "C:\Program Files\Eclipse Adoptium",
        "C:\Program Files\Java",
        "C:\Program Files\Amazon Corretto",
        "C:\Program Files\Microsoft\jdk",
        "C:\Program Files\Zulu"
    )

    foreach ($root in $candidateRoots) {
        if (-not (Test-Path -LiteralPath $root)) {
            continue
        }
        $candidates = Get-ChildItem -LiteralPath $root -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-JavaHome -Path $_.FullName } |
            Sort-Object Name -Descending
        if ($candidates.Count -gt 0) {
            return $candidates[0].FullName
        }
    }

    return ""
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
$paymentServicePath = Join-Path $smartdocPath "payment-service-spring"

if (-not (Test-Path -LiteralPath $aiSmartDocPath)) {
    throw "AiSmartDoc folder not found at: $aiSmartDocPath"
}
if (-not (Test-Path -LiteralPath $smartdocAiPath)) {
    throw "smartdoc-ai folder not found at: $smartdocAiPath"
}
if (-not (Test-Path -LiteralPath $paymentServicePath)) {
    throw "payment-service-spring folder not found at: $paymentServicePath"
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


Set-EnvIfMissing -Name "APP_AI_BASE_URL" -Value "http://localhost:8000"
Set-EnvIfMissing -Name "APP_AI_GATEWAY_BASE_URL" -Value "http://localhost:8088/api/v1/ai"

# --- Payment service / Stripe configuration (mirrors docker compose) ---
# Load the repo-root .env so PAYMENT_STRIPE_SECRET_KEY (and any PAYMENT_* overrides)
# are picked up exactly like compose reads .env. Real env vars still take priority.
$rootEnvFile = Join-Path $smartdocPath ".env"
Read-DotEnvFile -FilePath $rootEnvFile

if (-not [string]::IsNullOrWhiteSpace($StripeSecretKey)) {
    Set-Item -Path "Env:PAYMENT_STRIPE_SECRET_KEY" -Value $StripeSecretKey
}

# Defaults match compose.yaml so a local run behaves like the Docker stack
# (real Stripe-hosted checkout in test mode).
Set-EnvIfMissing -Name "PAYMENT_PROVIDER" -Value "stripe"
Set-EnvIfMissing -Name "PAYMENT_CHECKOUT_MODE" -Value "stripe"
Set-EnvIfMissing -Name "PAYMENT_CHECKOUT_BASE_URL" -Value "https://checkout.stripe.com/pay"
Set-EnvIfMissing -Name "PAYMENT_CHECKOUT_RETURN_URL" -Value "http://localhost:4200/billing/success"
Set-EnvIfMissing -Name "PAYMENT_CHECKOUT_CANCEL_URL" -Value "http://localhost:4200/billing/cancel"
Set-EnvIfMissing -Name "PAYMENT_STRIPE_WEBHOOK_SECRET" -Value "dev-webhook-secret"
Set-EnvIfMissing -Name "PAYMENT_INTERNAL_TOKEN" -Value "dev-internal-token"
Set-EnvIfMissing -Name "PAYMENT_WEBHOOK_TOLERANCE_SECONDS" -Value "300"

$stripeKeyLoaded = -not [string]::IsNullOrWhiteSpace($env:PAYMENT_STRIPE_SECRET_KEY)
if (($env:PAYMENT_CHECKOUT_MODE -eq "stripe") -and (-not $stripeKeyLoaded)) {
    Write-Step "WARNING: PAYMENT_CHECKOUT_MODE=stripe but PAYMENT_STRIPE_SECRET_KEY is empty. Set it in $rootEnvFile (or pass -StripeSecretKey); otherwise Stripe checkout will fail."
}

Write-Step "Configuration summary"
Write-Host ("- smartdoc root: {0}" -f $smartdocPath)
Write-Host ("- smartdoc-ai .env: {0}" -f $envFile)
Write-Host ("- provider key loaded: {0}" -f (-not [string]::IsNullOrWhiteSpace($effectiveApiKey)))
Write-Host ("- model: {0}" -f $env:MODEL_NAME)
Write-Host ("- service token loaded: {0}" -f (-not [string]::IsNullOrWhiteSpace($effectiveServiceToken)))
Write-Host ("- APP_AI_BASE_URL: {0}" -f $env:APP_AI_BASE_URL)
Write-Host ("- APP_AI_GATEWAY_BASE_URL: {0}" -f $env:APP_AI_GATEWAY_BASE_URL)
Write-Host ("- payment checkout mode: {0}" -f $env:PAYMENT_CHECKOUT_MODE)
Write-Host ("- stripe secret key loaded: {0}" -f $stripeKeyLoaded)

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

$resolvedJavaHome = Resolve-JavaHome -ExplicitJavaHome $JavaHome
if ([string]::IsNullOrWhiteSpace($resolvedJavaHome)) {
    throw "Java runtime not found. Pass -JavaHome <path-to-jdk>, set JAVA_HOME, or add a JDK 'bin' folder to PATH."
}

Set-Item -Path "Env:JAVA_HOME" -Value $resolvedJavaHome
$javaBin = Join-Path $resolvedJavaHome "bin"
if (($env:PATH -split ';') -notcontains $javaBin) {
    Set-Item -Path "Env:PATH" -Value ("{0};{1}" -f $javaBin, $env:PATH)
}
Write-Step ("Using JAVA_HOME: {0}" -f $resolvedJavaHome)

$logDirectory = Join-Path $smartdocPath "logs"
$started = @()

$started += Start-BackgroundProcess -Name "smartdoc-ai" -WorkingDirectory $smartdocAiPath -FilePath $pythonExe -ArgumentList @("-m", "uvicorn", "app.main:app", "--host", "127.0.0.1", "--port", "8000") -LogDirectory $logDirectory
Wait-HttpOk -Name "smartdoc-ai" -Url "http://127.0.0.1:8000/health" -TimeoutSec $StartupTimeoutSec

$started += Start-BackgroundProcess -Name "AiSmartDoc" -WorkingDirectory $aiSmartDocPath -FilePath "cmd.exe" -ArgumentList @("/c", ".\mvnw.cmd -Dmaven.test.skip=true spring-boot:run") -LogDirectory $logDirectory
Wait-HttpOk -Name "AiSmartDoc" -Url "http://127.0.0.1:8088/api/v1/ai/health" -TimeoutSec $StartupTimeoutSec

$started += Start-BackgroundProcess -Name "smartdoc" -WorkingDirectory $smartdocPath -FilePath "cmd.exe" -ArgumentList @("/c", ".\mvnw.cmd -Dmaven.test.skip=true spring-boot:run") -LogDirectory $logDirectory
Wait-HttpOk -Name "smartdoc" -Url "http://127.0.0.1:8087/v3/api-docs" -TimeoutSec $StartupTimeoutSec

# payment-service-spring has no Maven wrapper of its own; use the root mvnw.cmd via a relative path.
$started += Start-BackgroundProcess -Name "payment-service" -WorkingDirectory $paymentServicePath -FilePath "cmd.exe" -ArgumentList @("/c", "..\mvnw.cmd -Dmaven.test.skip=true spring-boot:run") -LogDirectory $logDirectory
Wait-HttpOk -Name "payment-service" -Url "http://127.0.0.1:8089/actuator/health" -TimeoutSec $StartupTimeoutSec

Write-Step "All services are up."
$started | Format-Table -AutoSize | Out-String | Write-Host
Write-Host "Use Stop-Process -Id <PID> to stop a service."
