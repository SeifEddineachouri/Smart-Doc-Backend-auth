param(
    [Parameter(Mandatory = $false)]
    [string]$DockerHubNamespace,

    [Parameter(Mandatory = $false)]
    [string]$DockerHubTag = "latest",

    [switch]$SkipLogin,
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-SecureTokenPlainText {
    param(
        [Parameter(Mandatory = $true)]
        [Security.SecureString]$SecureToken
    )

    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureToken)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    }
    finally {
        if ($ptr -ne [IntPtr]::Zero) {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
        }
    }
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker CLI not found. Install Docker Desktop or make sure docker is available in PATH."
}

if ([string]::IsNullOrWhiteSpace($DockerHubNamespace)) {
    $DockerHubNamespace = Read-Host "Docker Hub namespace / username"
}

if ([string]::IsNullOrWhiteSpace($DockerHubNamespace)) {
    throw "Docker Hub namespace cannot be empty."
}

$images = @(
    @{ Local = 'smartdoc:local'; Remote = "$DockerHubNamespace/smartdoc-backend-auth:$DockerHubTag" },
    @{ Local = 'aismartdoc:local'; Remote = "$DockerHubNamespace/smartdoc-backend-fast-api:$DockerHubTag" },
    @{ Local = 'smartdoc-ai:local'; Remote = "$DockerHubNamespace/smartdoc-backend-python:$DockerHubTag" }
)

foreach ($image in $images) {
    $inspect = & docker image inspect $image.Local 2>$null
    if ($LASTEXITCODE -ne 0) {
        if (-not $SkipBuild) {
            Write-Host "Local image '$($image.Local)' was not found. Build it first with: docker compose build smartdoc aismartdoc smartdoc-ai" -ForegroundColor Yellow
        }
        else {
            Write-Host "Skipping missing local image '$($image.Local)' because -SkipBuild was specified." -ForegroundColor Yellow
        }
    }
}

if (-not $SkipLogin) {
    $secureToken = Read-Host "Docker Hub token" -AsSecureString
    $plainToken = Get-SecureTokenPlainText -SecureToken $secureToken
    try {
        $plainToken | & docker login -u $DockerHubNamespace --password-stdin | Out-Host
    }
    finally {
        $plainToken = $null
        $secureToken.Dispose()
    }
}

foreach ($image in $images) {
    if (-not $SkipBuild) {
        $inspect = & docker image inspect $image.Local 2>$null
        if ($LASTEXITCODE -ne 0) {
            throw "Missing local image '$($image.Local)'. Build the stack first with 'docker compose build smartdoc aismartdoc smartdoc-ai'."
        }
    }

    Write-Host "Tagging $($image.Local) -> $($image.Remote)" -ForegroundColor Cyan
    & docker tag $image.Local $image.Remote

    Write-Host "Pushing $($image.Remote)" -ForegroundColor Cyan
    & docker push $image.Remote
}

Write-Host "Done. Pushed SmartDoc images to Docker Hub namespace '$DockerHubNamespace'." -ForegroundColor Green

