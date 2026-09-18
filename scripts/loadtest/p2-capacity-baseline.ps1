param(
    [switch]$SkipBuild,
    [switch]$KeepRunning,
    [string]$BaseUrl = 'http://host.docker.internal:8080/api',
    [string]$ConfigPath,
    [string]$ResultsDirectory,
    [string]$AdminToken = 'change-me-in-production',
    [string]$Duration,
    [string]$WarmupDuration,
    [int]$ReadRate = 0,
    [int]$WriteRate = 0,
    [int]$DanmuRate = 0,
    [int]$TranscodeRate = 0,
    [int]$TranscodeWorkers = 2,
    [int]$SampleIntervalSeconds = 0,
    [switch]$AllowMissingMqMetrics
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$loadtestDirectory = Join-Path $repositoryRoot 'loadtest'
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $loadtestDirectory 'capacity-baseline.json'
}
if (!(Test-Path -LiteralPath $ConfigPath)) {
    throw "Capacity baseline configuration does not exist: $ConfigPath"
}
$config = Get-Content -Raw -LiteralPath $ConfigPath | ConvertFrom-Json

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runId = "p2-capacity-baseline-$timestamp"
if ([string]::IsNullOrWhiteSpace($ResultsDirectory)) {
    $ResultsDirectory = Join-Path $loadtestDirectory "results/$runId"
}
New-Item -ItemType Directory -Force -Path $ResultsDirectory | Out-Null

$Duration = if ([string]::IsNullOrWhiteSpace($Duration)) { [string]$config.workload.duration } else { $Duration }
$WarmupDuration = if ([string]::IsNullOrWhiteSpace($WarmupDuration)) { [string]$config.workload.warmupDuration } else { $WarmupDuration }
$ReadRate = if ($ReadRate -gt 0) { $ReadRate } else { [int]$config.workload.read.rate }
$WriteRate = if ($WriteRate -gt 0) { $WriteRate } else { [int]$config.workload.write.rate }
$DanmuRate = if ($DanmuRate -gt 0) { $DanmuRate } else { [int]$config.workload.danmu.rate }
$TranscodeRate = if ($TranscodeRate -gt 0) { $TranscodeRate } else { [int]$config.workload.transcode.rate }
$SampleIntervalSeconds = if ($SampleIntervalSeconds -gt 0) { $SampleIntervalSeconds } else { [int]$config.workload.sampleIntervalSeconds }
if ($TranscodeWorkers -lt 1) { throw 'TranscodeWorkers must be at least 1' }

. (Join-Path $repositoryRoot 'scripts/e2e/compose-helpers.ps1')
. (Join-Path $PSScriptRoot 'capacity-common.ps1')
Push-Location $repositoryRoot

$p2ComposeFiles = @(
    '-f', 'docker-compose.yml',
    '-f', 'docker-compose.service.yml',
    '-f', 'docker-compose.e2e.yml',
    '-f', 'docker-compose.p2.yml'
)

function Invoke-P2Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & docker compose @script:p2ComposeFiles @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($Arguments -join ' ')"
    }
}

function Save-CommandOutput {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][scriptblock]$Command
    )
    try {
        $output = & $Command 2>&1
        $exitCode = $LASTEXITCODE
        ($output -join "`n") | Set-Content -Encoding utf8 -Path $Path
        return $exitCode
    } catch {
        $_.Exception.Message | Set-Content -Encoding utf8 -Path $Path
        return 1
    }
}

function Capture-Environment {
    $sha = ((& git rev-parse HEAD 2>$null) -join '').Trim()
    $dirty = @(& git status --porcelain 2>$null)
    $dockerVersionPath = Join-Path $ResultsDirectory 'docker-version.json'
    $dockerInfoPath = Join-Path $ResultsDirectory 'docker-info.json'
    $composePath = Join-Path $ResultsDirectory 'compose-config.yml'
    $gitStatusPath = Join-Path $ResultsDirectory 'git-status.txt'
    $dockerVersion = (& docker version --format '{{json .}}' 2>&1) -join "`n"
    $dockerInfo = (& docker info --format '{{json .}}' 2>&1) -join "`n"
    $dockerVersion | Set-Content -Encoding utf8 -Path $dockerVersionPath
    $dockerInfo | Set-Content -Encoding utf8 -Path $dockerInfoPath
    ($dirty -join "`n") | Set-Content -Encoding utf8 -Path $gitStatusPath
    & docker compose @script:ComposeFiles config | Set-Content -Encoding utf8 -Path $composePath
    if ($LASTEXITCODE -ne 0) { throw 'docker compose config failed while capturing the baseline environment' }

    $hostInfo = [ordered]@{}
    try {
        $os = Get-CimInstance Win32_OperatingSystem
        $computer = Get-CimInstance Win32_ComputerSystem
        $hostInfo.osCaption = $os.Caption
        $hostInfo.osVersion = $os.Version
        $hostInfo.totalMemoryBytes = [long]$computer.TotalPhysicalMemory
        $hostInfo.logicalProcessors = [int]$computer.NumberOfLogicalProcessors
    } catch {
        $hostInfo.error = $_.Exception.Message
    }
    $environment = [ordered]@{
        schemaVersion = 1
        capturedAt = [DateTimeOffset]::UtcNow.ToString('o')
        runId = $runId
        revision = [ordered]@{
            sha = $sha
            dirty = $dirty.Count -gt 0
            statusPath = ConvertTo-CapacityRelativePath -Path $gitStatusPath -Root $ResultsDirectory
        }
        host = $hostInfo
        dockerVersionPath = ConvertTo-CapacityRelativePath -Path $dockerVersionPath -Root $ResultsDirectory
        dockerInfoPath = ConvertTo-CapacityRelativePath -Path $dockerInfoPath -Root $ResultsDirectory
        composeConfigPath = ConvertTo-CapacityRelativePath -Path $composePath -Root $ResultsDirectory
        baseUrl = $BaseUrl
        composeFiles = @('docker-compose.yml', 'docker-compose.service.yml', 'docker-compose.e2e.yml')
        p2ComposeFile = 'docker-compose.p2.yml'
    }
    $environmentPath = Join-Path $ResultsDirectory 'environment.json'
    $environment | ConvertTo-Json -Depth 12 | Set-Content -Encoding utf8 -Path $environmentPath
    return [pscustomobject]@{
        path = $environmentPath
        sha = $sha
        dirty = $dirty.Count -gt 0
        host = $hostInfo
    }
}

function Get-ScenarioMetricSummary {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][pscustomobject]$Summary
    )
    $metric = switch ($Name) {
        'read' { 'http_req_duration' }
        'write' { 'write_operation_latency' }
        'danmu' { 'danmu_send_latency' }
        'transcode' { 'transcode_e2e_latency' }
        default { throw "Unknown capacity scenario: $Name" }
    }
    $businessMetric = switch ($Name) {
        'read' { 'business_failure' }
        'write' { 'write_business_failure' }
        'danmu' { 'danmu_business_failure' }
        'transcode' { 'transcode_business_failure' }
    }
    $result = [ordered]@{
        p50Ms = Get-CapacityMetricValue -Summary $Summary -Metric $metric -ValueName 'p(50)'
        p95Ms = Get-CapacityMetricValue -Summary $Summary -Metric $metric -ValueName 'p(95)'
        p99Ms = Get-CapacityMetricValue -Summary $Summary -Metric $metric -ValueName 'p(99)'
        errorRate = Get-CapacityMetricValue -Summary $Summary -Metric 'http_req_failed' -ValueName 'rate'
        businessErrorRate = Get-CapacityMetricValue -Summary $Summary -Metric $businessMetric -ValueName 'rate'
        requestCount = Get-CapacityMetricValue -Summary $Summary -Metric 'http_reqs' -ValueName 'count'
        iterationCount = Get-CapacityMetricValue -Summary $Summary -Metric 'iterations' -ValueName 'count'
        metric = $metric
    }
    if ($Name -eq 'read') {
        $result.listP95Ms = Get-CapacityMetricValue -Summary $Summary -Metric 'http_req_duration{endpoint:list}' -ValueName 'p(95)'
        $result.detailP95Ms = Get-CapacityMetricValue -Summary $Summary -Metric 'http_req_duration{endpoint:detail}' -ValueName 'p(95)'
        $result.searchP95Ms = Get-CapacityMetricValue -Summary $Summary -Metric 'http_req_duration{endpoint:search}' -ValueName 'p(95)'
    }
    return [pscustomobject]$result
}

function Test-ScenarioMeasurement {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][pscustomobject]$Metrics,
        [Parameter(Mandatory)][object[]]$ResourcePeaks,
        [Parameter(Mandatory)][object[]]$MqPeaks,
        [Parameter(Mandatory)][bool]$MqObserved
    )
    $threshold = $config.thresholds.PSObject.Properties[$Name].Value
    $metricPass = $null -ne $Metrics.p95Ms -and $Metrics.p95Ms -le [double]$threshold.p95Ms `
        -and $null -ne $Metrics.p99Ms -and $Metrics.p99Ms -le [double]$threshold.p99Ms `
        -and $null -ne $Metrics.errorRate -and $Metrics.errorRate -le [double]$threshold.maxErrorRate `
        -and $null -ne $Metrics.businessErrorRate -and $Metrics.businessErrorRate -le [double]$threshold.maxErrorRate
    $resourceBreaches = @($ResourcePeaks | Where-Object {
        $limits = Get-CapacityResourceLimits -Config $config -Container $_.container
        $_.maxCpuQuotaPercent -gt $limits.maxCpuQuotaPercent -or $_.maxMemoryPercent -gt $limits.maxMemoryPercent
    })
    $mqBreaches = @($MqPeaks | Where-Object { $_.maxMessages -gt [double]$config.thresholds.resources.maxMqBacklog })
    $passed = $metricPass -and $resourceBreaches.Count -eq 0 -and $mqBreaches.Count -eq 0 `
        -and ($AllowMissingMqMetrics -or $MqObserved)
    [pscustomobject]@{
        passed = $passed
        metricPass = $metricPass
        resourceBreaches = $resourceBreaches
        mqBreaches = $mqBreaches
        mqObserved = $MqObserved
    }
}

function Invoke-K6CapacityPhase {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Phase,
        [Parameter(Mandatory)][string]$ScriptName,
        [Parameter(Mandatory)][hashtable]$Environment,
        [Parameter(Mandatory)][object[]]$Targets,
        [Parameter(Mandatory)][string]$PhaseDuration,
        [switch]$MountFixtures
    )
    $scenarioDirectory = Join-Path $ResultsDirectory $Name
    New-Item -ItemType Directory -Force -Path $scenarioDirectory | Out-Null
    $summaryName = "$Phase-k6.json"
    $summaryPath = Join-Path $scenarioDirectory $summaryName
    $outputPath = Join-Path $scenarioDirectory "$Phase-k6.out.log"
    $errorPath = Join-Path $scenarioDirectory "$Phase-k6.err.log"
    $containerSummaryPath = "/results/$Name/$summaryName"
    $arguments = @('run', '--rm', '--add-host=host.docker.internal:host-gateway')
    foreach ($entry in $Environment.GetEnumerator()) {
        $arguments += @('-e', "$($entry.Key)=$($entry.Value)")
    }
    $arguments += @('-e', "DURATION=$PhaseDuration", '-e', "RUN_ID=$runId")
    $arguments += @('-v', "$loadtestDirectory`:/scripts", '-v', "$ResultsDirectory`:/results")
    if ($MountFixtures) {
        $arguments += @('-v', "$(Join-Path $ResultsDirectory 'fixtures')`:/fixtures:ro")
    }
    $arguments += @('grafana/k6', 'run', "--summary-export=$containerSummaryPath", "/scripts/$ScriptName")

    $samples = [System.Collections.Generic.List[object]]::new()
    function Add-PhaseSample {
        $samples.Add([pscustomobject]@{
            at = [DateTimeOffset]::UtcNow.ToString('o')
            resources = @(Get-CapacityContainerResources -Targets $Targets)
            mqBacklog = @(Get-CapacityRocketMqBacklog)
        })
    }

    Add-PhaseSample
    $process = Start-Process -FilePath 'docker' -ArgumentList $arguments -PassThru -NoNewWindow `
        -RedirectStandardOutput $outputPath -RedirectStandardError $errorPath
    $exitCode = $null
    try {
        while (!$process.HasExited) {
            Start-Sleep -Seconds $SampleIntervalSeconds
            $process.Refresh()
            if (!$process.HasExited) { Add-PhaseSample }
        }
        $process.WaitForExit()
        $process.Refresh()
        $exitCode = $process.ExitCode
    } finally {
        if (!$process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    }
    Add-PhaseSample
    if (!(Test-Path -LiteralPath $summaryPath)) {
        throw "k6 did not produce its summary export: $summaryPath"
    }
    $summary = Get-Content -Raw -LiteralPath $summaryPath | ConvertFrom-Json
    $resourcePeaks = @(Get-CapacityResourcePeaks -Samples $samples.ToArray())
    $resourceRolePeaks = @(Get-CapacityResourceRolePeaks -Samples $samples.ToArray())
    $mqPeaks = @(Get-CapacityMqPeaks -Samples $samples.ToArray())
    $metrics = Get-ScenarioMetricSummary -Name $Name -Summary $summary
    $mqObserved = @($samples | Where-Object { @($_.mqBacklog).Count -gt 0 }).Count -gt 0
    [pscustomobject]@{
        phase = $Phase
        duration = $PhaseDuration
        exitCode = $exitCode
        passed = $true
        summaryPath = ConvertTo-CapacityRelativePath -Path $summaryPath -Root $ResultsDirectory
        outputPath = ConvertTo-CapacityRelativePath -Path $outputPath -Root $ResultsDirectory
        errorPath = ConvertTo-CapacityRelativePath -Path $errorPath -Root $ResultsDirectory
        metrics = $metrics
        resourcePeaks = $resourcePeaks
        resourceRolePeaks = $resourceRolePeaks
        mqBacklogPeaks = $mqPeaks
        mqObserved = $mqObserved
        samples = $samples.ToArray()
    }
}

function Invoke-CapacityScenario {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$ScriptName,
        [Parameter(Mandatory)][hashtable]$Environment,
        [Parameter(Mandatory)][string]$Rate,
        [switch]$MountFixtures
    )
    $targets = @(Get-CapacityResourceTargets -FixedContainers @(
        'bilibili-gateway', 'bilibili-user-service', 'bilibili-video-service',
        'bilibili-danmu-service', 'bilibili-social-service', 'bilibili-search-service',
        'bilibili-canal-service', 'bilibili-rocketmq-broker', 'bilibili-mysql', 'bilibili-redis'
    ))
    $phaseEnvironment = @{}
    foreach ($entry in $Environment.GetEnumerator()) { $phaseEnvironment[$entry.Key] = $entry.Value }
    $phaseEnvironment.RATE = $Rate
    $phaseEnvironment.BASE_URL = $BaseUrl
    $warmup = Invoke-K6CapacityPhase -Name $Name -Phase 'warmup' -ScriptName $ScriptName `
        -Environment $phaseEnvironment -Targets $targets -PhaseDuration $WarmupDuration -MountFixtures:$MountFixtures
    $measurement = Invoke-K6CapacityPhase -Name $Name -Phase 'measurement' -ScriptName $ScriptName `
        -Environment $phaseEnvironment -Targets $targets -PhaseDuration $Duration -MountFixtures:$MountFixtures
    $gate = Test-ScenarioMeasurement -Name $Name -Metrics $measurement.metrics `
        -ResourcePeaks $measurement.resourcePeaks -MqPeaks $measurement.mqBacklogPeaks `
        -MqObserved ($measurement.mqObserved -or $warmup.mqObserved)
    $measurement.passed = $gate.passed
    $measurement.metricPass = $gate.metricPass
    $measurement.resourceBreaches = $gate.resourceBreaches
    $measurement.mqBreaches = $gate.mqBreaches
    [pscustomobject]@{
        name = $Name
        script = $ScriptName
        rate = [int]$Rate
        warmup = $warmup
        measurement = $measurement
        threshold = $config.thresholds.PSObject.Properties[$Name].Value
        persistenceCount = $null
        persistencePassed = $null
        passed = $gate.passed
        error = $null
    }
}

function New-TranscodeFixture {
    $fixtureDirectory = Join-Path $ResultsDirectory 'fixtures'
    New-Item -ItemType Directory -Force -Path $fixtureDirectory | Out-Null
    $containerPath = '/tmp/p2-capacity-baseline.mp4'
    & docker exec bilibili-video-service sh -c "ffmpeg -loglevel error -y -f lavfi -i testsrc2=size=480x270:rate=24 -f lavfi -i sine=frequency=880 -t 3 -c:v libx264 -pix_fmt yuv420p -c:a aac $containerPath"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot generate P2 transcode fixture with FFmpeg' }
    $fixturePath = Join-Path $fixtureDirectory 'p2-capacity-baseline.mp4'
    & docker cp "bilibili-video-service:$containerPath" $fixturePath
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy P2 transcode fixture' }
    $file = Get-Item -LiteralPath $fixturePath
    $fixture = [ordered]@{
        path = '/fixtures/p2-capacity-baseline.mp4'
        name = 'p2-capacity-baseline.mp4'
        contentType = 'video/mp4'
        md5 = (Get-FileHash -LiteralPath $fixturePath -Algorithm MD5).Hash.ToLowerInvariant()
        size = [long]$file.Length
    }
    $manifestPath = Join-Path $fixtureDirectory 'manifest.json'
    ('[' + ($fixture | ConvertTo-Json -Compress) + ']') | Set-Content -Encoding utf8 -Path $manifestPath
    return [pscustomobject]@{
        path = $fixturePath
        manifestPath = $manifestPath
        md5 = $fixture.md5
        size = $fixture.size
    }
}

function Remove-TranscodeBaselineData {
    param([Parameter(Mandatory)][string]$FileMd5)
    if ([string]::IsNullOrWhiteSpace($FileMd5)) { return }
    $objects = @(& docker exec -e MYSQL_PWD=root bilibili-mysql mysql -N -B -uroot bilibili -e `
        "select source_object_name from video_transcode_task where file_md5='$FileMd5' and source_object_name is not null")
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read P2 transcode source objects for cleanup' }
    $commands = @('mc alias set local http://minio:9000 minioadmin minioadmin >/dev/null')
    foreach ($object in $objects) {
        $trimmed = ([string]$object).Trim()
        if ($trimmed -match '^source/direct/[0-9]+/direct_[a-f0-9]{32}\.[a-z0-9]+$') {
            $commands += "mc rm --force local/videos/$trimmed >/dev/null 2>&1 || true"
        }
    }
    $commands += "mc rm --recursive --force local/videos/play/$FileMd5 >/dev/null 2>&1 || true"
    & docker run --rm --network bilibili-net --entrypoint sh minio/mc -c ($commands -join '; ')
    if ($LASTEXITCODE -ne 0) { throw 'P2 transcode MinIO cleanup failed' }
    $cleanupSql = "delete i from mq_consumed_message i join video_transcode_task t on i.message_key=t.task_id where i.topic='video-transcode' and t.file_md5='$FileMd5'; delete from video_transcode_task where file_md5='$FileMd5'; delete from file_chunk where file_md5='$FileMd5'; delete from direct_upload_session where file_md5='$FileMd5';"
    & docker exec -e MYSQL_PWD=root bilibili-mysql mysql -uroot bilibili -e $cleanupSql | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'P2 transcode database cleanup failed' }
}

function Invoke-E2eFixture {
    param(
        [Parameter(Mandatory)][string]$ContextPath,
        [Parameter(Mandatory)][string]$ReportPath,
        [Parameter(Mandatory)][string]$LogPath
    )
    $e2eScript = Join-Path $repositoryRoot 'scripts/e2e/reliability-e2e.ps1'
    $arguments = @('-KeepRunning', '-KeepTestData', '-TestContextPath', $ContextPath,
        '-SloReportPath', $ReportPath, '-AdminToken', $AdminToken)
    if ($SkipBuild) { $arguments += '-SkipBuild' }
    try {
        & $e2eScript @arguments *> $LogPath
        if ($LASTEXITCODE -ne 0) { throw "Compose E2E exited with code $LASTEXITCODE" }
    } catch {
        if (!(Test-Path -LiteralPath $LogPath)) { $_.Exception.Message | Set-Content -Encoding utf8 -Path $LogPath }
        throw
    }
    if (!(Test-Path -LiteralPath $ContextPath)) { throw 'Compose E2E did not write its test context' }
    return (Get-Content -Raw -LiteralPath $ContextPath | ConvertFrom-Json)
}

function Invoke-E2eCleanup {
    param(
        [Parameter(Mandatory)][string]$ContextPath,
        [Parameter(Mandatory)][string]$LogPath
    )
    if (!(Test-Path -LiteralPath $ContextPath)) { return $true }
    $e2eScript = Join-Path $repositoryRoot 'scripts/e2e/reliability-e2e.ps1'
    try {
        & $e2eScript -CleanupContextPath $ContextPath -AdminToken $AdminToken *> $LogPath
        if ($LASTEXITCODE -ne 0) { throw "Compose E2E cleanup exited with code $LASTEXITCODE" }
        return $true
    } catch {
        Write-Warning "E2E fixture cleanup failed: $($_.Exception.Message)"
        return $false
    }
}

function Write-CapacityMarkdown {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][pscustomobject]$Report
    )
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('# P2 分场景容量基线')
    $lines.Add('')
    $lines.Add("- 生成时间：$($Report.generatedAt)")
    $lines.Add("- 运行 ID：$($Report.runId)")
    $lines.Add("- Git SHA：$($Report.revision.sha)（工作区脏：$($Report.revision.dirty)）")
    $lines.Add("- Compose E2E 固件视频：$($Report.fixture.videoId)")
    $lines.Add("- 总体结果：**$($Report.passed)**")
    $lines.Add('')
    $lines.Add('## 负载与结果')
    $lines.Add('')
    $lines.Add('| 场景 | 负载 | 预热 | P95/P99 (ms) | HTTP/业务错误率 | CPU 配额峰值 | 内存峰值 | MQ 积压峰值 | 结果 |')
    $lines.Add('| --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | --- |')
    foreach ($scenario in $Report.scenarios) {
        $measurement = $scenario.PSObject.Properties['measurement'].Value
        if ($null -eq $measurement) {
            $lines.Add("| $($scenario.name) | $($scenario.rate) /s | $WarmupDuration + $Duration | - | - | - | - | - | **失败**：$($scenario.error) |")
            continue
        }
        $m = $measurement.metrics
        $cpu = if (@($measurement.resourceRolePeaks).Count -gt 0) {
            [math]::Round((@($measurement.resourceRolePeaks) | Measure-Object -Property maxCpuQuotaPercent -Maximum).Maximum, 2)
        } else { $null }
        $memory = if (@($measurement.resourcePeaks).Count -gt 0) {
            [math]::Round((@($measurement.resourcePeaks) | Measure-Object -Property maxMemoryPercent -Maximum).Maximum, 2)
        } else { $null }
        $mq = if (@($measurement.mqBacklogPeaks).Count -gt 0) {
            [math]::Round((@($measurement.mqBacklogPeaks) | Measure-Object -Property maxMessages -Maximum).Maximum, 2)
        } else { 0 }
        $lines.Add("| $($scenario.name) | $($scenario.rate) /s | $WarmupDuration + $Duration | $($m.p95Ms) / $($m.p99Ms) | $($m.errorRate) / $($m.businessErrorRate) | $cpu% | $memory% | $mq | $($scenario.passed) |")
    }
    $lines.Add('')
    $lines.Add('## 场景说明')
    $lines.Add('')
    $lines.Add("- 读：``read-baseline.js`` 每次迭代请求列表、详情、搜索各一次，$ReadRate iterations/s 约等于 $($ReadRate * 3) HTTP RPS。")
    $lines.Add("- 写：``write-http-baseline.js`` 在 5 个隔离账号间轮换，每次迭代创建并删除一条评论，$WriteRate operations/s。")
    $lines.Add("- 弹幕：``danmu-baseline.js`` 在 5 个隔离账号间轮换，每次迭代发送一条带唯一 requestId 的弹幕，$DanmuRate messages/s；结束后校验并清理。")
    $lines.Add("- 转码：``transcode-baseline.js`` 使用固定 $($Report.fixture.transcodeFixtureSize) 字节 MP4，直传 → 完成 → 轮询 HLS；$TranscodeRate tasks/s、$TranscodeWorkers 个 Worker，超时 $($config.workload.transcode.timeoutSeconds)s。")
    $lines.Add('')
    $lines.Add('## 环境与原始证据')
    $lines.Add('')
    $lines.Add("- 环境清单：``$($Report.environment.path)``")
    $lines.Add("- Compose 配置：``$($Report.environment.composeConfigPath)``")
    $lines.Add("- E2E 固件报告：``$($Report.fixture.e2eReportPath)``；日志：``$($Report.fixture.logPath)``")
    foreach ($scenario in $Report.scenarios) {
        $measurement = $scenario.PSObject.Properties['measurement'].Value
        if ($null -eq $measurement) {
            $lines.Add("- $($scenario.name)：失败，原因 ``$($scenario.error)``。")
        } else {
            $lines.Add("- $($scenario.name)：预热 ``$($scenario.warmup.summaryPath)``，测量 ``$($measurement.summaryPath)``，原始 stdout/stderr 位于同目录。")
        }
    }
    $lines.Add('')
    $lines.Add('## 阈值与边界')
    $lines.Add('')
    $lines.Add("- 延迟/错误阈值来自 ``loadtest/capacity-baseline.json``；资源按容器 CPU 配额归一化，默认 CPU/内存门槛 $($config.thresholds.resources.maxCpuQuotaPercent)%/$($config.thresholds.resources.maxMemoryPercent)%，RocketMQ Broker CPU 例外 $($config.thresholds.resources.containerLimits.'bilibili-rocketmq-broker'.maxCpuQuotaPercent)%。")
    $lines.Add("- MQ 指标来自 Prometheus ``rocketmq_group_diff``；本次 ``AllowMissingMqMetrics=$AllowMissingMqMetrics``。")
    $lines.Add('- 这是当前 Docker Desktop、固定数据集和固定负载下的可复验基线，不是生产容量承诺，也不把读链路结果外推到写入、转码或弹幕。')
    Set-Content -Encoding utf8 -Path $Path -Value $lines
}

$environment = $null
$fixtureContextPath = Join-Path $ResultsDirectory 'fixture-context.json'
$fixtureE2eReportPath = Join-Path $ResultsDirectory 'fixture-e2e-slo.json'
$fixtureLogPath = Join-Path $ResultsDirectory 'fixture-e2e.out.log'
$cleanupLogPath = Join-Path $ResultsDirectory 'fixture-cleanup.out.log'
$scenarioResults = [System.Collections.Generic.List[object]]::new()
$fixture = [pscustomobject]@{ videoId = 0; e2eReportPath = ''; logPath = ''; context = $null; transcodeFixture = $null }
$cleanupPassed = $false
$runError = $null
$workersStarted = $false
$apiOverlayApplied = $false

try {
    $environment = Capture-Environment
    Write-Host "[p2] Starting Compose E2E fixture ($runId)"
    try { Invoke-P2Compose --profile transcode-scale rm --force --stop bilibili-transcode-worker } catch { Write-Warning "Existing Worker cleanup skipped: $($_.Exception.Message)" }
    $context = Invoke-E2eFixture -ContextPath $fixtureContextPath -ReportPath $fixtureE2eReportPath -LogPath $fixtureLogPath
    $fixture.videoId = [long]$context.videoId
    $fixture.context = $context
    $fixture.e2eReportPath = ConvertTo-CapacityRelativePath -Path $fixtureE2eReportPath -Root $ResultsDirectory
    $fixture.logPath = ConvertTo-CapacityRelativePath -Path $fixtureLogPath -Root $ResultsDirectory

    $observability = @('prometheus', 'rocketmq-exporter')
    Invoke-Compose up --detach @observability
    Wait-ContainerHealthy -Container 'bilibili-prometheus' -TimeoutSeconds 90
    Start-Sleep -Seconds 20

    Write-Host "[p2] Read baseline rate=$ReadRate duration=$Duration"
    try {
        $scenarioResults.Add((Invoke-CapacityScenario -Name 'read' -ScriptName 'read-baseline.js' `
            -Environment @{ VIDEO_IDS = [string]$fixture.videoId } -Rate ([string]$ReadRate)))
    } catch {
        $scenarioResults.Add([pscustomobject]@{ name = 'read'; script = 'read-baseline.js'; rate = $ReadRate; passed = $false; error = $_.Exception.Message })
        Write-Warning "Read baseline failed: $($_.Exception.Message)"
    }

    Write-Host "[p2] Write baseline rate=$WriteRate duration=$Duration"
    try {
        $scenarioResults.Add((Invoke-CapacityScenario -Name 'write' -ScriptName 'write-http-baseline.js' `
            -Environment @{ VIDEO_ID = [string]$fixture.videoId } -Rate ([string]$WriteRate)))
    } catch {
        $scenarioResults.Add([pscustomobject]@{ name = 'write'; script = 'write-http-baseline.js'; rate = $WriteRate; passed = $false; error = $_.Exception.Message })
        Write-Warning "Write baseline failed: $($_.Exception.Message)"
    }

    Write-Host "[p2] Danmu baseline rate=$DanmuRate duration=$Duration"
    try {
        $danmuResult = Invoke-CapacityScenario -Name 'danmu' -ScriptName 'danmu-baseline.js' `
            -Environment @{ VIDEO_ID = [string]$fixture.videoId } -Rate ([string]$DanmuRate)
        Wait-Until -TimeoutSeconds 120 -IntervalSeconds 2 -Description 'P2 danmu persistence' -Condition {
            [long](Get-MySqlScalar "select count(*) from danmu where video_id=$($fixture.videoId) and content like 'p2-capacity danmu $runId-%'") -gt 0
        }
        $danmuResult.persistenceCount = [long](Get-MySqlScalar "select count(*) from danmu where video_id=$($fixture.videoId) and content like 'p2-capacity danmu $runId-%'")
        $danmuResult.persistencePassed = $danmuResult.persistenceCount -gt 0
        $scenarioResults.Add($danmuResult)
    } catch {
        $scenarioResults.Add([pscustomobject]@{ name = 'danmu'; script = 'danmu-baseline.js'; rate = $DanmuRate; passed = $false; error = $_.Exception.Message })
        Write-Warning "Danmu baseline failed: $($_.Exception.Message)"
    }

    Write-Host '[p2] Preparing dedicated transcode Workers'
    $fixture.transcodeFixture = New-TranscodeFixture
    Invoke-P2Compose up --detach --no-build --force-recreate bilibili-video-service
    $apiOverlayApplied = $true
    Invoke-P2Compose --profile transcode-scale up --detach --no-build --scale bilibili-transcode-worker=$TranscodeWorkers bilibili-transcode-worker
    $workersStarted = $true
    Wait-ContainerHealthy -Container 'bilibili-video-service' -TimeoutSeconds 180
    $workerNames = @(& docker ps --filter 'label=com.docker.compose.service=bilibili-transcode-worker' --format '{{.Names}}')
    if ($workerNames.Count -ne $TranscodeWorkers) { throw "Expected $TranscodeWorkers transcode Workers, got $($workerNames.Count)" }
    foreach ($worker in $workerNames) { Wait-ContainerHealthy -Container $worker -TimeoutSeconds 120 }
    Write-Host "[p2] Transcode baseline workers=$TranscodeWorkers rate=$TranscodeRate duration=$Duration"
    try {
        $scenarioResults.Add((Invoke-CapacityScenario -Name 'transcode' -ScriptName 'transcode-baseline.js' `
            -Environment @{
                TRANSCODE_TIMEOUT_SECONDS = [string]$config.workload.transcode.timeoutSeconds
                MAX_VUS = [string]$config.workload.transcode.maxVus
                FIXTURES_MANIFEST = '/fixtures/manifest.json'
            } -Rate ([string]$TranscodeRate) -MountFixtures))
    } catch {
        $scenarioResults.Add([pscustomobject]@{ name = 'transcode'; script = 'transcode-baseline.js'; rate = $TranscodeRate; passed = $false; error = $_.Exception.Message })
        Write-Warning "Transcode baseline failed: $($_.Exception.Message)"
    }
} catch {
    $runError = $_.Exception.Message
    Write-Warning "P2 capacity baseline stopped before all scenarios: $runError"
} finally {
    if ($workersStarted) {
        try { Invoke-P2Compose --profile transcode-scale rm --force --stop bilibili-transcode-worker } catch { Write-Warning "Worker cleanup failed: $($_.Exception.Message)" }
    }
    if ($null -ne $fixture.transcodeFixture) {
        try { Remove-TranscodeBaselineData -FileMd5 $fixture.transcodeFixture.md5 } catch { Write-Warning "Transcode data cleanup failed: $($_.Exception.Message)" }
        try { Remove-Item -LiteralPath $fixture.transcodeFixture.path -Force -ErrorAction SilentlyContinue } catch { }
        try { Remove-Item -LiteralPath $fixture.transcodeFixture.manifestPath -Force -ErrorAction SilentlyContinue } catch { }
    }
    if ($apiOverlayApplied) {
        try {
            Invoke-Compose up --detach --no-build --force-recreate bilibili-video-service
            Wait-ContainerHealthy -Container 'bilibili-video-service' -TimeoutSeconds 180
        } catch { Write-Warning "API service restore failed: $($_.Exception.Message)" }
    }
    if (Test-Path -LiteralPath $fixtureContextPath) {
        $cleanupPassed = Invoke-E2eCleanup -ContextPath $fixtureContextPath -LogPath $cleanupLogPath
    }
    if (!$KeepRunning) {
        try {
            Invoke-Compose stop bilibili-gateway bilibili-user-service bilibili-video-service bilibili-danmu-service `
                bilibili-social-service bilibili-search-service bilibili-canal-service mysql redis nacos `
                rocketmq-namesrv rocketmq-broker elasticsearch minio canal prometheus rocketmq-exporter
        } catch { Write-Warning "Compose stop failed: $($_.Exception.Message)" }
    }

    $environmentPathRelative = if ($null -ne $environment) { ConvertTo-CapacityRelativePath -Path $environment.path -Root $ResultsDirectory } else { 'environment.json' }
    $report = [ordered]@{
        schemaVersion = 1
        type = 'p2-capacity-baseline'
        generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
        runId = $runId
        passed = $false
        error = $runError
        revision = if ($null -ne $environment) { [ordered]@{ sha = $environment.sha; dirty = $environment.dirty } } else { [ordered]@{ sha = $null; dirty = $null } }
        environment = [ordered]@{
            path = $environmentPathRelative
            composeConfigPath = 'compose-config.yml'
        }
        fixture = [ordered]@{
            videoId = $fixture.videoId
            e2eReportPath = $fixture.e2eReportPath
            logPath = $fixture.logPath
            contextPath = ConvertTo-CapacityRelativePath -Path $fixtureContextPath -Root $ResultsDirectory
            transcodeFixtureMd5 = if ($null -ne $fixture.transcodeFixture) { $fixture.transcodeFixture.md5 } else { $null }
            transcodeFixtureSize = if ($null -ne $fixture.transcodeFixture) { $fixture.transcodeFixture.size } else { $null }
        }
        workload = [ordered]@{
            warmupDuration = $WarmupDuration
            duration = $Duration
            sampleIntervalSeconds = $SampleIntervalSeconds
            readRate = $ReadRate
            writeRate = $WriteRate
            danmuRate = $DanmuRate
            transcodeRate = $TranscodeRate
            transcodeWorkers = $TranscodeWorkers
        }
        scenarios = @($scenarioResults.ToArray())
        cleanup = [ordered]@{
            e2ePassed = $cleanupPassed
            cleanupLogPath = if (Test-Path -LiteralPath $cleanupLogPath) { ConvertTo-CapacityRelativePath -Path $cleanupLogPath -Root $ResultsDirectory } else { $null }
        }
    }
    $report.passed = $null -ne $environment -and $fixture.videoId -gt 0 -and $cleanupPassed `
        -and $scenarioResults.Count -eq 4 -and @($scenarioResults | Where-Object { $_.passed -ne $true }).Count -eq 0
    $reportPath = Join-Path $ResultsDirectory 'p2-capacity-baseline.json'
    $report | ConvertTo-Json -Depth 20 | Set-Content -Encoding utf8 -Path $reportPath
    $markdownPath = Join-Path $ResultsDirectory 'p2-capacity-baseline.md'
    Write-CapacityMarkdown -Path $markdownPath -Report ([pscustomobject]$report)
    Write-Host "[p2] report=$markdownPath passed=$($report.passed)"
    Pop-Location
}

if (!$report.passed) {
    throw "P2 capacity baseline failed. See $(Join-Path $ResultsDirectory 'p2-capacity-baseline.md')"
}

return $report
