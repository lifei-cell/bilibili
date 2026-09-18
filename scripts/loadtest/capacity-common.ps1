Set-StrictMode -Version Latest

. (Join-Path $PSScriptRoot 'resource-normalization.ps1')

function Get-CapacityResourceTargets {
    param(
        [Parameter(Mandatory)][string[]]$FixedContainers
    )

    $workerContainers = @(& docker ps --filter 'label=com.docker.compose.service=bilibili-transcode-worker' --format '{{.Names}}')
    if ($LASTEXITCODE -ne 0) {
        throw 'docker ps failed while discovering capacity baseline containers'
    }
    $containers = @($FixedContainers + $workerContainers |
        Where-Object { ![string]::IsNullOrWhiteSpace($_) } |
        Select-Object -Unique)
    return @($containers | ForEach-Object {
        $container = [string]$_
        $inspection = Get-DockerContainerInspection -Container $container
        $labels = Get-ObjectPropertyValue -Object $inspection.Config -Name 'Labels'
        $service = Get-ObjectPropertyValue -Object $labels -Name 'com.docker.compose.service'
        [pscustomobject]@{
            container = $container
            service = if ([string]::IsNullOrWhiteSpace($service)) { $container } else { $service }
            role = Get-ResourceRole -ComposeService $service
            cpuQuotaCores = Get-CpuQuotaCoresFromInspect -Inspection $inspection
        }
    })
}

function Get-CapacityContainerResources {
    param(
        [Parameter(Mandatory)][object[]]$Targets
    )

    $containers = @($Targets | ForEach-Object { $_.container })
    $rawRows = @( & docker stats --no-stream --format '{{json .}}' @containers )
    if ($LASTEXITCODE -ne 0) {
        throw 'docker stats failed while sampling the capacity baseline'
    }
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
        $memoryPercent = Convert-ToPercentage -Value $row.MemPerc
        if ($null -eq $rawCpuPercent -or $null -eq $memoryPercent) {
            throw "docker stats returned invalid resource percentages for container: $($target.container)"
        }
        [pscustomobject]@{
            container = $target.container
            service = $target.service
            role = $target.role
            cpuRawPercent = $rawCpuPercent
            cpuQuotaCores = $target.cpuQuotaCores
            cpuQuotaPercent = Convert-RawCpuToQuotaPercent -RawCpuPercent $rawCpuPercent -QuotaCores $target.cpuQuotaCores
            memoryPercent = $memoryPercent
            memoryUsage = $row.MemUsage
            netIo = $row.NetIO
            blockIo = $row.BlockIO
            pids = $row.PIDs
        }
    })
}

function Get-CapacityRocketMqBacklog {
    param(
        [string]$PrometheusUrl = 'http://localhost:9090/api/v1/query?query=rocketmq_group_diff'
    )

    try {
        $rawResult = & $script:CurlExecutable --noproxy '*' --connect-timeout 3 --max-time 5 -fsS $PrometheusUrl
        if ($LASTEXITCODE -ne 0) { return @() }
        $result = ($rawResult -join "`n") | ConvertFrom-Json
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

function Get-CapacityMetricValue {
    param(
        [Parameter(Mandatory)][pscustomobject]$Summary,
        [Parameter(Mandatory)][string]$Metric,
        [Parameter(Mandatory)][string]$ValueName
    )

    if ($null -eq $Summary.metrics) { return $null }
    $metricProperty = $Summary.metrics.PSObject.Properties[$Metric]
    if ($null -eq $metricProperty) { return $null }
    $metricValue = $metricProperty.Value
    $values = if ($null -ne $metricValue.PSObject.Properties['values']) {
        $metricValue.values
    } else {
        $metricValue
    }
    $valueProperty = $values.PSObject.Properties[$ValueName]
    if ($null -eq $valueProperty) { return $null }
    return [double]$valueProperty.Value
}

function Get-CapacityResourcePeaks {
    param(
        [Parameter(Mandatory)][object[]]$Samples
    )

    $rows = @($Samples | ForEach-Object { $_.resources })
    if ($rows.Count -eq 0) { return @() }
    return @($rows | Group-Object container | ForEach-Object {
        $first = $_.Group[0]
        [pscustomobject]@{
            container = $_.Name
            service = $first.service
            role = $first.role
            cpuQuotaCores = [double]$first.cpuQuotaCores
            maxCpuRawPercent = [double](($_.Group | Measure-Object -Property cpuRawPercent -Maximum).Maximum)
            maxCpuQuotaPercent = [double](($_.Group | Measure-Object -Property cpuQuotaPercent -Maximum).Maximum)
            maxMemoryPercent = [double](($_.Group | Measure-Object -Property memoryPercent -Maximum).Maximum)
        }
    })
}

function Get-CapacityResourceRolePeaks {
    param(
        [Parameter(Mandatory)][object[]]$Samples
    )

    $rows = @($Samples | ForEach-Object { $_.resources })
    if ($rows.Count -eq 0) { return @() }
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

function Get-CapacityMqPeaks {
    param(
        [Parameter(Mandatory)][object[]]$Samples
    )

    $rows = @($Samples | ForEach-Object { $_.mqBacklog })
    $rows = @($rows | Where-Object { $null -ne $_ })
    if ($rows.Count -eq 0) { return @() }
    return @($rows | Group-Object { "$(($_.topic))|$(($_.group))" } | ForEach-Object {
        $first = $_.Group[0]
        [pscustomobject]@{
            topic = $first.topic
            group = $first.group
            broker = $first.broker
            maxMessages = [double](($_.Group | Measure-Object -Property messages -Maximum).Maximum)
        }
    })
}

function Get-CapacityResourceLimits {
    param(
        [Parameter(Mandatory)][pscustomobject]$Config,
        [Parameter(Mandatory)][string]$Container
    )

    $resources = $Config.thresholds.resources
    $overrideProperty = if ($null -ne $resources.PSObject.Properties['containerLimits']) {
        $resources.containerLimits.PSObject.Properties[$Container]
    } else {
        $null
    }
    $override = if ($null -ne $overrideProperty) { $overrideProperty.Value } else { $null }
    $cpuLimit = if ($null -ne $override -and $null -ne $override.PSObject.Properties['maxCpuQuotaPercent']) {
        [double]$override.maxCpuQuotaPercent
    } else {
        [double]$resources.maxCpuQuotaPercent
    }
    return [pscustomobject]@{
        maxCpuQuotaPercent = $cpuLimit
        maxMemoryPercent = [double]$resources.maxMemoryPercent
    }
}

function ConvertTo-CapacityRelativePath {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Root
    )

    return [IO.Path]::GetRelativePath($Root, $Path).Replace([IO.Path]::DirectorySeparatorChar, '/')
}
