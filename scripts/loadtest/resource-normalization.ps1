Set-StrictMode -Version Latest

function Convert-ToPercentage {
    param([AllowNull()][string]$Value)

    if ($null -ne $Value -and $Value -match '([0-9]+(?:\.[0-9]+)?)%') {
        return [double]$matches[1]
    }
    return $null
}

function Get-ObjectPropertyValue {
    param(
        [AllowNull()][object]$Object,
        [Parameter(Mandatory)][string]$Name
    )

    if ($null -eq $Object) {
        return $null
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $null
    }
    return $property.Value
}

function Get-CpuSetCoreCount {
    param([AllowNull()][string]$CpuSet)

    if ([string]::IsNullOrWhiteSpace($CpuSet)) {
        return $null
    }
    $count = 0
    foreach ($part in $CpuSet.Split(',')) {
        $item = $part.Trim()
        if ($item -match '^(\d+)-(\d+)$') {
            $start = [int]$matches[1]
            $end = [int]$matches[2]
            if ($end -lt $start) {
                throw "Invalid cpuset range: $item"
            }
            $count += $end - $start + 1
        } elseif ($item -match '^\d+$') {
            $count++
        } else {
            throw "Invalid cpuset value: $item"
        }
    }
    if ($count -le 0) {
        throw "CPU set does not contain a core: $CpuSet"
    }
    return [double]$count
}

function Get-CpuQuotaCoresFromInspect {
    param([Parameter(Mandatory)][object]$Inspection)

    $hostConfig = Get-ObjectPropertyValue -Object $Inspection -Name 'HostConfig'
    if ($null -eq $hostConfig) {
        throw 'Docker inspection has no HostConfig; cannot determine CPU quota'
    }

    $nanoCpus = Get-ObjectPropertyValue -Object $hostConfig -Name 'NanoCpus'
    if ($null -ne $nanoCpus -and [double]$nanoCpus -gt 0) {
        return [math]::Round([double]$nanoCpus / 1e9, 6)
    }

    $cpuQuota = Get-ObjectPropertyValue -Object $hostConfig -Name 'CpuQuota'
    $cpuPeriod = Get-ObjectPropertyValue -Object $hostConfig -Name 'CpuPeriod'
    if ($null -ne $cpuQuota -and $null -ne $cpuPeriod `
            -and [double]$cpuQuota -gt 0 -and [double]$cpuPeriod -gt 0) {
        return [math]::Round([double]$cpuQuota / [double]$cpuPeriod, 6)
    }

    $cpuCount = Get-ObjectPropertyValue -Object $hostConfig -Name 'CpuCount'
    if ($null -ne $cpuCount -and [double]$cpuCount -gt 0) {
        return [double]$cpuCount
    }

    $cpusetCpus = Get-ObjectPropertyValue -Object $hostConfig -Name 'CpusetCpus'
    $cpusetCount = Get-CpuSetCoreCount -CpuSet $cpusetCpus
    if ($null -ne $cpusetCount) {
        return $cpusetCount
    }

    throw 'Container has no CPU quota or cpuset; refusing to compare host CPU percentage with quota SLO'
}

function Convert-RawCpuToQuotaPercent {
    param(
        [Parameter(Mandatory)][double]$RawCpuPercent,
        [Parameter(Mandatory)][double]$QuotaCores
    )

    if ($QuotaCores -le 0) {
        throw "CPU quota must be positive: $QuotaCores"
    }
    if ($RawCpuPercent -lt 0) {
        throw "Raw CPU percentage must not be negative: $RawCpuPercent"
    }
    return [math]::Round($RawCpuPercent / $QuotaCores, 6)
}

function Get-ResourceRole {
    param([AllowNull()][string]$ComposeService)

    switch ($ComposeService) {
        'bilibili-video-service' { return 'video-service' }
        'bilibili-transcode-worker' { return 'transcode-worker' }
        default { return 'infrastructure-or-application' }
    }
}

function Get-DockerContainerInspection {
    param([Parameter(Mandatory)][string]$Container)

    $raw = & docker inspect --format '{{json .}}' $Container
    if ($LASTEXITCODE -ne 0 -or $null -eq $raw) {
        throw "docker inspect failed for container: $Container"
    }
    try {
        return (($raw -join "`n") | ConvertFrom-Json)
    } catch {
        throw "docker inspect returned invalid JSON for container: $Container"
    }
}
