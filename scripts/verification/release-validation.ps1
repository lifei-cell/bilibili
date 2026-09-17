param(
    [switch]$SkipBackendVerify,
    [switch]$RunFaultDrill,
    [switch]$KeepRunning,
    [string]$AdminToken = 'change-me-in-production',
    [string]$SloConfigPath,
    [string]$ResultsDirectory,
    [string]$Duration
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$loadtestDirectory = Join-Path $repositoryRoot 'loadtest'
if ([string]::IsNullOrWhiteSpace($SloConfigPath)) {
    $SloConfigPath = Join-Path $loadtestDirectory 'write-slo.json'
}
if ([string]::IsNullOrWhiteSpace($ResultsDirectory)) {
    $ResultsDirectory = Join-Path $loadtestDirectory 'results'
}
New-Item -ItemType Directory -Force -Path $ResultsDirectory | Out-Null

. (Join-Path $repositoryRoot 'scripts/e2e/compose-helpers.ps1')

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDirectory = Join-Path $ResultsDirectory "release-validation-$timestamp"
New-Item -ItemType Directory -Force -Path $runDirectory | Out-Null
$contextPath = Join-Path $runDirectory 'e2e-context.json'
$e2eReportPath = Join-Path $runDirectory 'compose-e2e-slo.json'
$writeReportPath = Join-Path $runDirectory 'write-slo.json'
$releaseReportPath = Join-Path $runDirectory 'release-validation.json'
$e2eScript = Join-Path $repositoryRoot 'scripts/e2e/reliability-e2e.ps1'
$writeSloScript = Join-Path $repositoryRoot 'scripts/loadtest/run-write-slo.ps1'
$resourceNormalizationScript = Join-Path $repositoryRoot 'scripts/loadtest/test-resource-normalization.ps1'

$services = @(
    'bilibili-gateway', 'bilibili-user-service', 'bilibili-video-service',
    'bilibili-danmu-service', 'bilibili-social-service',
    'bilibili-search-service', 'bilibili-canal-service'
)
$infrastructure = @(
    'mysql', 'redis', 'nacos', 'rocketmq-namesrv', 'rocketmq-broker',
    'elasticsearch', 'minio', 'canal', 'prometheus', 'rocketmq-exporter'
)

$steps = [ordered]@{
    resourceNormalization = 'NOT_RUN'
    backendVerify = 'NOT_RUN'
    composeE2e = 'NOT_RUN'
    writeSlo = 'NOT_RUN'
    cleanup = 'NOT_RUN'
}
$failure = $null
$overallPassed = $false
$activeStep = $null

try {
    $activeStep = 'resourceNormalization'
    Write-Host '[release] CPU quota normalization checks'
    & $resourceNormalizationScript
    $steps.resourceNormalization = 'PASSED'
    $activeStep = $null

    if ($SkipBackendVerify) {
        $steps.backendVerify = 'SKIPPED'
    } else {
        $activeStep = 'backendVerify'
        Write-Host '[release] Backend verify'
        Push-Location $repositoryRoot
        try {
            & mvn -s .mvn/settings.xml --batch-mode --no-transfer-progress verify
            if ($LASTEXITCODE -ne 0) { throw 'Maven verify failed' }
        } finally {
            Pop-Location
        }
        $steps.backendVerify = 'PASSED'
        $activeStep = $null
    }

    $activeStep = 'composeE2e'
    Write-Host '[release] Compose write-path E2E'
    & $e2eScript -SkipBuild -RunFaultDrill:$RunFaultDrill -KeepRunning -KeepTestData `
        -TestContextPath $contextPath -SloConfigPath $SloConfigPath -SloReportPath $e2eReportPath `
        -AdminToken $AdminToken
    $steps.composeE2e = 'PASSED'
    $activeStep = $null

    $activeStep = 'writeSlo'
    $context = Get-Content -Raw -LiteralPath $contextPath | ConvertFrom-Json
    Write-Host "[release] Write-path load test videoId=$($context.videoId)"
    & $writeSloScript -VideoId ([long]$context.videoId) -SloConfigPath $SloConfigPath `
        -ResultsDirectory $runDirectory -E2eReportPath $e2eReportPath -ReportPath $writeReportPath -Duration $Duration
    $steps.writeSlo = 'PASSED'
    $activeStep = $null
    $overallPassed = $true
} catch {
    $failure = $_.Exception.Message
    if ($null -ne $activeStep -and $steps[$activeStep] -eq 'NOT_RUN') {
        $steps[$activeStep] = "FAILED: $failure"
    }
    throw
} finally {
    if (Test-Path -LiteralPath $contextPath) {
        try {
            & $e2eScript -CleanupContextPath $contextPath -AdminToken $AdminToken
            $steps.cleanup = 'PASSED'
        } catch {
            $steps.cleanup = "FAILED: $($_.Exception.Message)"
            if ($null -eq $failure) { $failure = $_.Exception.Message }
            $overallPassed = $false
        }
    } elseif ($steps.composeE2e -eq 'PASSED') {
        $steps.cleanup = 'SKIPPED'
    }

    if (!$KeepRunning) {
        try {
            Invoke-Compose stop @services @infrastructure
        } catch {
            Write-Warning "Release validation service stop needs attention: $($_.Exception.Message)"
        }
    }

    [ordered]@{
        schemaVersion = 2
        type = 'release-validation'
        generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
        passed = $overallPassed
        failure = $failure
        steps = $steps
        composeE2eReport = $e2eReportPath
        writeSloReport = $writeReportPath
    } | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 -Path $releaseReportPath
    Write-Host "[release] report=$releaseReportPath passed=$overallPassed"
}
