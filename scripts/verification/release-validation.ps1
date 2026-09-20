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
$environmentReportPath = Join-Path $runDirectory 'environment.json'
$composeConfigPath = Join-Path $runDirectory 'compose-config.yml'
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
    environment = 'NOT_RUN'
    resourceNormalization = 'NOT_RUN'
    backendVerify = 'NOT_RUN'
    composeE2e = 'NOT_RUN'
    writeSlo = 'NOT_RUN'
    cleanup = 'NOT_RUN'
}
$failure = $null
$overallPassed = $false
$activeStep = $null
$revision = $null
$branch = $null
$worktreeClean = $false
$backendTestSummary = $null

function Get-GitValue {
    param([Parameter(Mandatory)][string[]]$Arguments)

    $value = & git -C $repositoryRoot @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Git command failed: git $($Arguments -join ' ')"
    }
    return (($value -join "`n").Trim())
}

function Get-MavenTestSummary {
    $reports = @(Get-ChildItem -Path $repositoryRoot -Recurse -File -Filter 'TEST-*.xml' |
        Where-Object { $_.FullName -match '[\\/]+target[\\/]+(surefire|failsafe)-reports[\\/]+' })
    $summary = [ordered]@{ reports = $reports.Count; tests = 0; failures = 0; errors = 0; skipped = 0 }
    foreach ($report in $reports) {
        [xml]$document = Get-Content -Raw -LiteralPath $report.FullName
        $suite = $document.testsuite
        $summary.tests += [int]$suite.tests
        $summary.failures += [int]$suite.failures
        $summary.errors += [int]$suite.errors
        $summary.skipped += [int]$suite.skipped
    }
    return [pscustomobject]$summary
}

function Write-ReleaseEnvironment {
    $dockerVersion = & docker version --format '{{json .}}'
    if ($LASTEXITCODE -ne 0) { throw 'Docker version inspection failed' }
    $composeConfig = & docker compose @script:ComposeFiles config
    if ($LASTEXITCODE -ne 0) { throw 'Merged Compose configuration is invalid' }
    Set-Content -Encoding utf8 -Path $composeConfigPath -Value $composeConfig

    $mavenVersion = @(& mvn --version 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Maven version inspection failed' }
    $javaVersion = @(& java -version 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'Java version inspection failed' }

    [ordered]@{
        schemaVersion = 1
        generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
        revision = $revision
        branch = $branch
        worktreeClean = $worktreeClean
        github = [ordered]@{
            actions = $env:GITHUB_ACTIONS
            runId = $env:GITHUB_RUN_ID
            runAttempt = $env:GITHUB_RUN_ATTEMPT
            workflow = $env:GITHUB_WORKFLOW
            ref = $env:GITHUB_REF
            sha = $env:GITHUB_SHA
        }
        host = [ordered]@{
            os = [Environment]::OSVersion.VersionString
            architecture = [Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString()
            processorCount = [Environment]::ProcessorCount
            powershell = $PSVersionTable.PSVersion.ToString()
        }
        tools = [ordered]@{
            docker = (($dockerVersion -join "`n") | ConvertFrom-Json)
            maven = $mavenVersion
            java = $javaVersion
        }
        composeConfig = [IO.Path]::GetFileName($composeConfigPath)
    } | ConvertTo-Json -Depth 12 | Set-Content -Encoding utf8 -Path $environmentReportPath
}

try {
    $activeStep = 'environment'
    $revision = Get-GitValue -Arguments @('rev-parse', 'HEAD')
    $branch = Get-GitValue -Arguments @('branch', '--show-current')
    if ([string]::IsNullOrWhiteSpace($branch)) { $branch = $env:GITHUB_REF_NAME }
    $worktreeStatus = Get-GitValue -Arguments @('status', '--porcelain=v1', '--untracked-files=all')
    $worktreeClean = [string]::IsNullOrWhiteSpace($worktreeStatus)
    if (!$worktreeClean) {
        throw "Release validation requires a clean worktree:`n$worktreeStatus"
    }
    Write-ReleaseEnvironment
    $steps.environment = 'PASSED'
    $activeStep = $null

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
            & mvn -s .mvn/settings.xml --batch-mode --no-transfer-progress clean verify
            if ($LASTEXITCODE -ne 0) { throw 'Maven clean verify failed' }
            $backendTestSummary = Get-MavenTestSummary
            if ($backendTestSummary.tests -le 0 -or $backendTestSummary.failures -ne 0 `
                    -or $backendTestSummary.errors -ne 0 -or $backendTestSummary.skipped -ne 0) {
                throw "Backend test summary gate failed: $($backendTestSummary | ConvertTo-Json -Compress)"
            }
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
        revision = $revision
        branch = $branch
        worktreeClean = $worktreeClean
        steps = $steps
        backendTestSummary = $backendTestSummary
        environmentReport = $environmentReportPath
        composeConfig = $composeConfigPath
        composeE2eReport = $e2eReportPath
        writeSloReport = $writeReportPath
    } | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 -Path $releaseReportPath
    Write-Host "[release] report=$releaseReportPath passed=$overallPassed"
}
