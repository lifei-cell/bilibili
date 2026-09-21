param(
    [Parameter(Mandatory)][long]$VideoId,
    [string]$BaseUrl = 'http://host.docker.internal:8080/api',
    [string]$SloConfigPath,
    [string]$ResultsDirectory,
    [string]$E2eReportPath,
    [string]$ReportPath,
    [string]$Duration,
    [int]$PlaybackRate = 0,
    [int]$DanmuRate = 0,
    [int]$InteractionRate = 0,
    [int]$SampleIntervalSeconds = 0,
    [bool]$RequireMqMetrics = $true
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
if (!(Test-Path -LiteralPath $SloConfigPath)) {
    throw "Write-path SLO configuration does not exist: $SloConfigPath"
}

$sloConfig = Get-Content -Raw -LiteralPath $SloConfigPath | ConvertFrom-Json
$Duration = if ([string]::IsNullOrWhiteSpace($Duration)) { $sloConfig.workload.duration } else { $Duration }
$PlaybackRate = if ($PlaybackRate -gt 0) { $PlaybackRate } else { [int]$sloConfig.workload.playbackRate }
$DanmuRate = if ($DanmuRate -gt 0) { $DanmuRate } else { [int]$sloConfig.workload.danmuRate }
$InteractionRate = if ($InteractionRate -gt 0) { $InteractionRate } else { [int]$sloConfig.workload.interactionRate }
$SampleIntervalSeconds = if ($SampleIntervalSeconds -gt 0) { $SampleIntervalSeconds } else { [int]$sloConfig.workload.sampleIntervalSeconds }
New-Item -ItemType Directory -Force -Path $ResultsDirectory | Out-Null

. (Join-Path $repositoryRoot 'scripts/e2e/compose-helpers.ps1')
. (Join-Path $PSScriptRoot 'resource-normalization.ps1')

function Get-ResourceTargets {
    param([Parameter(Mandatory)][string[]]$FixedContainers)

    $workerContainers = @(& docker ps --filter 'label=com.docker.compose.service=bilibili-transcode-worker' --format '{{.Names}}')
    if ($LASTEXITCODE -ne 0) {
        throw 'docker ps failed while discovering transcode Worker containers'
    }
    $containers = @($FixedContainers + $workerContainers | Where-Object { ![string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)
    return @($containers | ForEach-Object {
        $container = [string]$_
        $inspection = Get-DockerContainerInspection -Container $container
        $labels = Get-ObjectPropertyValue -Object $inspection.Config -Name 'Labels'
        $service = Get-ObjectPropertyValue -Object $labels -Name 'com.docker.compose.service'
        $role = Get-ResourceRole -ComposeService $service
        [pscustomobject]@{
            container = $container
            service = if ([string]::IsNullOrWhiteSpace($service)) { $container } else { $service }
            role = $role
            cpuQuotaCores = Get-CpuQuotaCoresFromInspect -Inspection $inspection
        }
    })
}

function Get-ContainerResources {
    param([Parameter(Mandatory)][object[]]$Targets)

    $containers = @($Targets | ForEach-Object { $_.container })
    $rawRows = @( & docker stats --no-stream --format '{{json .}}' @containers )
    if ($LASTEXITCODE -ne 0) { throw 'docker stats failed while sampling the write-path load test' }
    $rowsByName = @{}
    foreach ($rawRow in $rawRows) {
        $row = $rawRow | ConvertFrom-Json
        $rowsByName[[string]$row.Name] = $row
    }
    return @($Targets | ForEach-Object {
        $target = $_
        $row = $rowsByName[$target.container]
        if ($null -eq $row) {
            throw "docker stats did not return container: $($target.container)"
        }
        $rawCpuPercent = Convert-ToPercentage -Value $row.CPUPerc
        if ($null -eq $rawCpuPercent) {
            throw "docker stats returned an invalid CPU percentage for container: $($target.container)"
        }
        [pscustomobject]@{
            container = $target.container
            service = $target.service
            role = $target.role
            cpuRawPercent = $rawCpuPercent
            cpuQuotaCores = $target.cpuQuotaCores
            cpuQuotaPercent = Convert-RawCpuToQuotaPercent -RawCpuPercent $rawCpuPercent -QuotaCores $target.cpuQuotaCores
            # Keep the old field as a compatibility alias. It now means quota usage.
            cpuPercent = Convert-RawCpuToQuotaPercent -RawCpuPercent $rawCpuPercent -QuotaCores $target.cpuQuotaCores
            memoryPercent = Convert-ToPercentage -Value $row.MemPerc
            memoryUsage = $row.MemUsage
            netIo = $row.NetIO
            blockIo = $row.BlockIO
            pids = $row.PIDs
        }
    })
}

function Get-RocketMqBacklog {
    try {
        # Some hosts route localhost traffic through a configured proxy.
        $rawResult = Invoke-CurlNoProxy -CurlArguments @(
            '--connect-timeout', '3', '--max-time', '5', '-fsS',
            'http://localhost:9090/api/v1/query?query=rocketmq_group_diff')
        if ($script:CurlExitCode -ne 0) { return @() }
        $result = $rawResult | ConvertFrom-Json
        if ($result.status -ne 'success') { return @() }
        return @($result.data.result | ForEach-Object {
            $broker = if ($null -ne $_.metric.PSObject.Properties['brokerName']) {
                $_.metric.brokerName
            } elseif ($null -ne $_.metric.PSObject.Properties['instance']) {
                $_.metric.instance
            } else {
                $null
            }
            [pscustomobject]@{
                topic = $_.metric.topic
                group = if ($_.metric.group) { $_.metric.group } else { $_.metric.consumerGroup }
                broker = $broker
                messages = [double]$_.value[1]
            }
        })
    } catch {
        return @()
    }
}

function Get-MetricP95 {
    param([pscustomobject]$Summary, [string]$Metric)

    $metricValue = $Summary.metrics.PSObject.Properties[$Metric].Value
    if ($null -eq $metricValue) { return $null }
    $values = if ($null -ne $metricValue.PSObject.Properties['values']) {
        $metricValue.values
    } else {
        $metricValue
    }
    $p95 = $values.PSObject.Properties['p(95)']
    if ($null -eq $p95) { return $null }
    return [double]$p95.Value
}

function Get-MetricRate {
    param([pscustomobject]$Summary, [string]$Metric)

    $metricValue = $Summary.metrics.PSObject.Properties[$Metric].Value
    if ($null -eq $metricValue) { return $null }
    $values = if ($null -ne $metricValue.PSObject.Properties['values']) {
        $metricValue.values
    } else {
        $metricValue
    }
    $rate = if ($null -ne $values.PSObject.Properties['rate']) {
        $values.PSObject.Properties['rate']
    } else {
        $values.PSObject.Properties['value']
    }
    if ($null -eq $rate) { return $null }
    return [double]$rate.Value
}

function Get-ResourcePeaks {
    param([object[]]$Samples)

    $rows = @($Samples | ForEach-Object { $_.resources })
    return @($rows | Group-Object container | ForEach-Object {
        $first = $_.Group[0]
        [pscustomobject]@{
            container = $_.Name
            service = $first.service
            role = $first.role
            cpuQuotaCores = [double]$first.cpuQuotaCores
            maxCpuRawPercent = [double](($_.Group | Measure-Object -Property cpuRawPercent -Maximum).Maximum)
            maxCpuQuotaPercent = [double](($_.Group | Measure-Object -Property cpuQuotaPercent -Maximum).Maximum)
            # Compatibility alias; all gate comparisons use maxCpuQuotaPercent.
            maxCpuPercent = [double](($_.Group | Measure-Object -Property cpuQuotaPercent -Maximum).Maximum)
            maxMemoryPercent = [double](($_.Group | Measure-Object -Property memoryPercent -Maximum).Maximum)
        }
    })
}

function Get-ResourceRolePeaks {
    param([object[]]$Samples)

    $rows = @($Samples | ForEach-Object { $_.resources })
    return @($rows | Group-Object role | ForEach-Object {
        [pscustomobject]@{
            role = $_.Name
            containers = @($_.Group | Select-Object -ExpandProperty container -Unique)
            maxCpuRawPercent = [double](($_.Group | Measure-Object -Property cpuRawPercent -Maximum).Maximum)
            maxCpuQuotaPercent = [double](($_.Group | Measure-Object -Property cpuQuotaPercent -Maximum).Maximum)
            maxMemoryPercent = [double](($_.Group | Measure-Object -Property memoryPercent -Maximum).Maximum)
        }
    })
}

function Get-ResourceLimits {
    param([Parameter(Mandatory)][string]$Container)

    $resources = $sloConfig.slo.resources
    $overrideProperty = if ($null -ne $resources.PSObject.Properties['containerLimits']) {
        $resources.containerLimits.PSObject.Properties[$Container]
    } else {
        $null
    }
    $override = if ($null -ne $overrideProperty) { $overrideProperty.Value } else { $null }
    $defaultCpuLimit = if ($null -ne $resources.PSObject.Properties['maxCpuQuotaPercent']) {
        [double]$resources.maxCpuQuotaPercent
    } elseif ($null -ne $resources.PSObject.Properties['maxCpuPercent']) {
        [double]$resources.maxCpuPercent
    } else {
        throw 'SLO resources must define maxCpuQuotaPercent'
    }
    return [pscustomobject]@{
        maxCpuQuotaPercent = if ($null -ne $override -and $null -ne $override.PSObject.Properties['maxCpuQuotaPercent']) {
            [double]$override.maxCpuQuotaPercent
        } elseif ($null -ne $override -and $null -ne $override.PSObject.Properties['maxCpuPercent']) {
            [double]$override.maxCpuPercent
        } else {
            $defaultCpuLimit
        }
        maxMemoryPercent = if ($null -ne $override -and $null -ne $override.PSObject.Properties['maxMemoryPercent']) {
            [double]$override.maxMemoryPercent
        } else {
            [double]$resources.maxMemoryPercent
        }
    }
}

function Get-MqBacklogPeaks {
    param([object[]]$Samples)

    $rows = @($Samples | ForEach-Object { $_.mqBacklog })
    return @($rows | Group-Object topic, group | ForEach-Object {
        [pscustomobject]@{
            topic = $_.Group[0].topic
            group = $_.Group[0].group
            maxMessages = [double](($_.Group | Measure-Object -Property messages -Maximum).Maximum)
        }
    })
}

function Write-MarkdownReport {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][pscustomobject]$Report
    )

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('# 写链路 SLO 与资源曲线报告')
    $lines.Add('')
    $lines.Add("- 生成时间：$($Report.generatedAt)")
    $lines.Add("- 临时视频：$($Report.videoId)")
    $lines.Add("- k6 结果：$($Report.k6.passed)")
    $lines.Add("- 总体结果：$($Report.passed)")
    $lines.Add('')
    $lines.Add('## SLO 结果')
    $lines.Add('')
    $lines.Add('| 链路 | 指标 | 实际 | 门槛 | 结果 |')
    $lines.Add('| --- | --- | ---: | ---: | --- |')
    foreach ($row in $Report.sloResults) {
        $lines.Add("| $($row.path) | $($row.metric) | $($row.actual) | $($row.limit) | $($row.passed) |")
    }
    $lines.Add('')
    $lines.Add('## 资源峰值')
    $lines.Add('')
    $lines.Add('| 角色 | 容器 | 原始 CPU 峰值 | 配额 CPU 峰值/门槛 | 内存峰值/门槛 |')
    $lines.Add('| --- | --- | ---: | ---: | ---: |')
    foreach ($peak in $Report.resourcePeaks) {
        $lines.Add("| $($peak.role) | $($peak.container) ($($peak.cpuQuotaCores) CPU) | $($peak.maxCpuRawPercent)% | $($peak.maxCpuQuotaPercent)% / $($peak.maxCpuLimit)% | $($peak.maxMemoryPercent)% / $($peak.maxMemoryLimit)% |")
    }
    $lines.Add('')
    $lines.Add('### 角色汇总')
    $lines.Add('')
    $lines.Add('| 角色 | 容器 | 原始 CPU 峰值 | 配额 CPU 峰值 | 内存峰值 |')
    $lines.Add('| --- | --- | ---: | ---: | ---: |')
    foreach ($peak in $Report.resourceRolePeaks) {
        $lines.Add("| $($peak.role) | $($peak.containers -join ', ') | $($peak.maxCpuRawPercent)% | $($peak.maxCpuQuotaPercent)% | $($peak.maxMemoryPercent)% |")
    }
    $lines.Add('')
    $lines.Add('## RocketMQ 积压峰值')
    $lines.Add('')
    $lines.Add('| Topic | Consumer group | 峰值积压 |')
    $lines.Add('| --- | --- | ---: |')
    foreach ($peak in $Report.mqBacklogPeaks) {
        $lines.Add("| $($peak.topic) | $($peak.group) | $($peak.maxMessages) |")
    }
    $lines.Add('')
    $lines.Add('完整时序曲线、k6 汇总与 Compose E2E 分阶段结果位于同目录 JSON 文件。')
    Set-Content -Encoding utf8 -Path $Path -Value $lines
}

$requiredContainers = @(
    'bilibili-gateway', 'bilibili-video-service', 'bilibili-danmu-service',
    'bilibili-social-service', 'bilibili-rocketmq-broker', 'bilibili-mysql', 'bilibili-redis'
)
foreach ($container in $requiredContainers) {
    Wait-ContainerHealthy -Container $container -TimeoutSeconds 30
}
$resourceTargets = @(Get-ResourceTargets -FixedContainers $requiredContainers)
if (@($resourceTargets | Where-Object role -eq 'video-service').Count -ne 1) {
    throw 'Expected exactly one bilibili-video-service resource target'
}
$targetSummary = @($resourceTargets | ForEach-Object {
    '{0}={1}:{2}cpu' -f $_.role, $_.container, $_.cpuQuotaCores
}) -join ', '
Write-Host "[loadtest] resource targets: $targetSummary"

# Prometheus owns the canonical RocketMQ lag metric. Start only the two
# observability services required for this report; the application stack is
# prepared by the Compose E2E phase.
Invoke-Compose up --detach prometheus rocketmq-exporter
Wait-ContainerHealthy -Container 'bilibili-prometheus' -TimeoutSeconds 60
Start-Sleep -Seconds 20

$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runId = "write-slo-$timestamp"
$summaryName = "$runId-k6.json"
$summaryPath = Join-Path $ResultsDirectory $summaryName
$outputPath = Join-Path $ResultsDirectory "$runId-k6.out.log"
$errorPath = Join-Path $ResultsDirectory "$runId-k6.err.log"
if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $reportPath = Join-Path $ResultsDirectory "$runId-report.json"
} else {
    $reportPath = $ReportPath
}
$markdownPath = [IO.Path]::ChangeExtension($reportPath, '.md')
$containerSummaryPath = "/results/$summaryName"

$k6Arguments = @(
    'run', '--rm', '--add-host=host.docker.internal:host-gateway',
    '-e', "BASE_URL=$BaseUrl",
    '-e', "VIDEO_ID=$VideoId",
    '-e', "DURATION=$Duration",
    '-e', "PLAYBACK_RATE=$PlaybackRate",
    '-e', "DANMU_RATE=$DanmuRate",
    '-e', "INTERACTION_RATE=$InteractionRate",
    '-e', "RUN_ID=$runId",
    '-e', "PLAYBACK_P95_MS=$($sloConfig.slo.playback.p95Ms)",
    '-e', "DANMU_P95_MS=$($sloConfig.slo.danmu.p95Ms)",
    '-e', "INTERACTION_P95_MS=$($sloConfig.slo.interaction.p95Ms)",
    '-e', "PLAYBACK_MAX_ERROR_RATE=$($sloConfig.slo.playback.maxErrorRate)",
    '-e', "DANMU_MAX_ERROR_RATE=$($sloConfig.slo.danmu.maxErrorRate)",
    '-e', "INTERACTION_MAX_ERROR_RATE=$($sloConfig.slo.interaction.maxErrorRate)",
    '-v', "$loadtestDirectory`:/scripts",
    '-v', "$ResultsDirectory`:/results",
    'grafana/k6', 'run', "--summary-export=$containerSummaryPath", '/scripts/write-baseline.js'
)

$samples = [System.Collections.Generic.List[object]]::new()
function Add-PerformanceSample {
    $samples.Add([pscustomobject]@{
        at = [DateTimeOffset]::UtcNow.ToString('o')
        resources = @(Get-ContainerResources -Targets $resourceTargets)
        mqBacklog = @(Get-RocketMqBacklog)
    })
}

Add-PerformanceSample
$process = Start-Process -FilePath 'docker' -ArgumentList $k6Arguments -PassThru -NoNewWindow `
    -RedirectStandardOutput $outputPath -RedirectStandardError $errorPath
while (!$process.HasExited) {
    Start-Sleep -Seconds $SampleIntervalSeconds
    $process.Refresh()
    Add-PerformanceSample
}
$process.WaitForExit()
$process.Refresh()
$k6ExitCode = $process.ExitCode
Add-PerformanceSample

if (!(Test-Path -LiteralPath $summaryPath)) {
    throw "k6 did not produce its summary export: $summaryPath"
}
$summary = Get-Content -Raw -LiteralPath $summaryPath | ConvertFrom-Json
$sloResults = @(
    [pscustomobject]@{ path = 'upload'; metric = 'single Compose E2E duration (ms)'; actual = $null; limit = $sloConfig.slo.upload.maxDurationMs; passed = $null }
    [pscustomobject]@{ path = 'transcode'; metric = 'single Compose E2E duration (ms)'; actual = $null; limit = $sloConfig.slo.transcode.maxDurationMs; passed = $null }
    [pscustomobject]@{ path = 'playback'; metric = 'k6 p95 (ms)'; actual = (Get-MetricP95 -Summary $summary -Metric 'write_slo_playback_latency'); limit = $sloConfig.slo.playback.p95Ms; passed = $false }
    [pscustomobject]@{ path = 'playback'; metric = 'k6 business error rate'; actual = (Get-MetricRate -Summary $summary -Metric 'write_slo_playback_failure'); limit = $sloConfig.slo.playback.maxErrorRate; passed = $false }
    [pscustomobject]@{ path = 'danmu'; metric = 'k6 p95 (ms)'; actual = (Get-MetricP95 -Summary $summary -Metric 'write_slo_danmu_latency'); limit = $sloConfig.slo.danmu.p95Ms; passed = $false }
    [pscustomobject]@{ path = 'danmu'; metric = 'k6 business error rate'; actual = (Get-MetricRate -Summary $summary -Metric 'write_slo_danmu_failure'); limit = $sloConfig.slo.danmu.maxErrorRate; passed = $false }
    [pscustomobject]@{ path = 'interaction'; metric = 'k6 p95 (ms)'; actual = (Get-MetricP95 -Summary $summary -Metric 'write_slo_interaction_latency'); limit = $sloConfig.slo.interaction.p95Ms; passed = $false }
    [pscustomobject]@{ path = 'interaction'; metric = 'k6 business error rate'; actual = (Get-MetricRate -Summary $summary -Metric 'write_slo_interaction_failure'); limit = $sloConfig.slo.interaction.maxErrorRate; passed = $false }
)

if (![string]::IsNullOrWhiteSpace($E2eReportPath) -and (Test-Path -LiteralPath $E2eReportPath)) {
    $e2eReport = Get-Content -Raw -LiteralPath $E2eReportPath | ConvertFrom-Json
    foreach ($path in @('upload', 'transcode')) {
        $stage = $e2eReport.stages.PSObject.Properties[$path].Value
        $sloResults | Where-Object path -eq $path | ForEach-Object {
            $_.actual = $stage.durationMs
            $_.passed = [bool]$stage.passed
        }
    }
}
foreach ($row in $sloResults | Where-Object { $_.metric -like 'k6 *' }) {
    $row.passed = $null -ne $row.actual -and $row.actual -le [double]$row.limit
}

$resourcePeaks = @(Get-ResourcePeaks -Samples $samples.ToArray())
$resourceRolePeaks = @(Get-ResourceRolePeaks -Samples $samples.ToArray())
$mqBacklogPeaks = @(Get-MqBacklogPeaks -Samples $samples.ToArray())
$resourcePeaks | ForEach-Object {
    $limits = Get-ResourceLimits -Container $_.container
    $_ | Add-Member -NotePropertyName maxCpuLimit -NotePropertyValue $limits.maxCpuQuotaPercent
    $_ | Add-Member -NotePropertyName maxCpuQuotaLimit -NotePropertyValue $limits.maxCpuQuotaPercent
    $_ | Add-Member -NotePropertyName maxMemoryLimit -NotePropertyValue $limits.maxMemoryPercent
}
$resourceBreaches = @($resourcePeaks | Where-Object {
    $_.maxCpuQuotaPercent -gt $_.maxCpuQuotaLimit -or
    $_.maxMemoryPercent -gt $_.maxMemoryLimit
})
$backlogBreaches = @($mqBacklogPeaks | Where-Object { $_.maxMessages -gt [double]$sloConfig.slo.mqBacklog.maxMessages })
$mqMetricObserved = $mqBacklogPeaks.Count -gt 0
$httpErrorRate = Get-MetricRate -Summary $summary -Metric 'http_req_failed'
# On Windows, Start-Process can leave ExitCode unavailable for a Docker CLI
# child even after WaitForExit. The exported k6 summary is authoritative: it
# exists only after k6 completed and exposes the same HTTP/error thresholds.
$k6ExitCodePassed = $null -eq $k6ExitCode -or $k6ExitCode -eq 0
$k6Passed = $k6ExitCodePassed -and $null -ne $httpErrorRate -and $httpErrorRate -le 0.005
$allStageSlosPassed = @($sloResults | Where-Object { $_.passed -ne $true }).Count -eq 0
$passed = $k6Passed -and $allStageSlosPassed -and $resourceBreaches.Count -eq 0 -and $backlogBreaches.Count -eq 0 `
    -and (!$RequireMqMetrics -or $mqMetricObserved)

$report = [pscustomobject]@{
    schemaVersion = 2
    type = 'write-path-slo'
    generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
    passed = $passed
    videoId = $VideoId
    workload = [pscustomobject]@{
        duration = $Duration
        playbackRate = $PlaybackRate
        danmuRate = $DanmuRate
        interactionRate = $InteractionRate
        sampleIntervalSeconds = $SampleIntervalSeconds
    }
    k6 = [pscustomobject]@{
        passed = $k6Passed
        exitCode = $k6ExitCode
        summaryPath = $summaryPath
        outputPath = $outputPath
        errorPath = $errorPath
        httpErrorRate = $httpErrorRate
        businessErrorRate = Get-MetricRate -Summary $summary -Metric 'write_slo_business_failure'
    }
    e2eReportPath = $E2eReportPath
    sloResults = $sloResults
    resourceTargets = $resourceTargets
    resourcePeaks = $resourcePeaks
    resourceRolePeaks = $resourceRolePeaks
    resourceBreaches = $resourceBreaches
    mqBacklogPeaks = $mqBacklogPeaks
    mqBacklogBreaches = $backlogBreaches
    mqMetricObserved = $mqMetricObserved
    samples = $samples.ToArray()
}
$report | ConvertTo-Json -Depth 12 | Set-Content -Encoding utf8 -Path $reportPath
Write-MarkdownReport -Path $markdownPath -Report $report
Write-Host "[loadtest] report=$markdownPath passed=$passed mqMetricObserved=$mqMetricObserved"

if (!$passed) {
    throw "Write-path SLO validation failed. See $markdownPath"
}

return $report
