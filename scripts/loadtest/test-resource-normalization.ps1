Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'resource-normalization.ps1')

function Assert-NearlyEqual {
    param(
        [double]$Actual,
        [double]$Expected,
        [string]$Description,
        [double]$Tolerance = 0.000001
    )
    if ([math]::Abs($Actual - $Expected) -gt $Tolerance) {
        throw "$Description expected $Expected but got $Actual"
    }
}

function Assert-Equal {
    param(
        [AllowNull()][object]$Actual,
        [AllowNull()][object]$Expected,
        [string]$Description
    )
    if ($Actual -ne $Expected) {
        throw "$Description expected $Expected but got $Actual"
    }
}

$oneCoreInspection = [pscustomobject]@{
    HostConfig = [pscustomobject]@{ NanoCpus = 1000000000L; CpuQuota = 0; CpuPeriod = 0; CpuCount = 0; CpusetCpus = '' }
}
$fourCoreInspection = [pscustomobject]@{
    HostConfig = [pscustomobject]@{ NanoCpus = 0; CpuQuota = 400000L; CpuPeriod = 100000L; CpuCount = 0; CpusetCpus = '' }
}

$oneCoreQuota = Get-CpuQuotaCoresFromInspect -Inspection $oneCoreInspection
$fourCoreQuota = Get-CpuQuotaCoresFromInspect -Inspection $fourCoreInspection
Assert-NearlyEqual $oneCoreQuota 1 '1-core quota detection'
Assert-NearlyEqual $fourCoreQuota 4 '4-core quota detection'
Assert-NearlyEqual (Get-CpuSetCoreCount -CpuSet '0-1,3') 3 'cpuset quota detection'
Assert-Equal (Get-ResourceRole -ComposeService 'bilibili-video-service') 'video-service' 'video service role'
Assert-Equal (Get-ResourceRole -ComposeService 'bilibili-transcode-worker') 'transcode-worker' 'transcode Worker role'

$oneCoreLoad = Convert-RawCpuToQuotaPercent -RawCpuPercent 100 -QuotaCores $oneCoreQuota
$fourCoreLoad = Convert-RawCpuToQuotaPercent -RawCpuPercent 400 -QuotaCores $fourCoreQuota
$halfFourCoreLoad = Convert-RawCpuToQuotaPercent -RawCpuPercent 200 -QuotaCores $fourCoreQuota
Assert-NearlyEqual $oneCoreLoad 100 '1-core load normalization'
Assert-NearlyEqual $fourCoreLoad 100 '4-core load normalization'
Assert-NearlyEqual $halfFourCoreLoad 50 'half 4-core load normalization'

Write-Host '[resource-test] 1-core raw=100% quota=1 normalized=100% PASS'
Write-Host '[resource-test] 4-core raw=400% quota=4 normalized=100% PASS'
Write-Host '[resource-test] 4-core raw=200% quota=4 normalized=50% PASS'
