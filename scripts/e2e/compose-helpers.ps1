Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ComposeFiles = @(
    '-f', 'docker-compose.yml',
    '-f', 'docker-compose.service.yml',
    '-f', 'docker-compose.e2e.yml'
)

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & docker compose @script:ComposeFiles @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed: $($Arguments -join ' ')"
    }
}

function Invoke-BiliApi {
    param(
        [Parameter(Mandatory)][ValidateSet('GET', 'POST', 'PUT', 'DELETE')][string]$Method,
        [Parameter(Mandatory)][string]$Uri,
        [object]$Body,
        [hashtable]$Headers = @{},
        [int]$TimeoutSec = 30
    )
    $parameters = @{
        Method = $Method
        Uri = $Uri
        Headers = $Headers
        TimeoutSec = $TimeoutSec
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }
    $response = Invoke-RestMethod @parameters
    if ($response.PSObject.Properties.Name -contains 'success' -and !$response.success) {
        throw "API business failure: $Uri - $($response.errorMsg)"
    }
    return $response
}

function Wait-Until {
    param(
        [Parameter(Mandatory)][scriptblock]$Condition,
        [int]$TimeoutSeconds = 120,
        [int]$IntervalSeconds = 2,
        [string]$Description = 'condition'
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    $lastFailure = $null
    do {
        try {
            if (& $Condition) { return }
            $lastFailure = 'condition returned false'
        } catch {
            # Dependencies may reject calls while they are recovering.
            $lastFailure = $_.Exception.Message
        }
        Start-Sleep -Seconds $IntervalSeconds
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Timed out waiting for $Description (last failure: $lastFailure)"
}

function Get-MySqlScalar {
    param([Parameter(Mandatory)][string]$Sql)
    $value = & docker exec -e MYSQL_PWD=root bilibili-mysql mysql -N -B -uroot bilibili -e $Sql
    if ($LASTEXITCODE -ne 0) { throw "MySQL query failed: $Sql" }
    return ($value | Select-Object -Last 1).Trim()
}

function Wait-ContainerHealthy {
    param([Parameter(Mandatory)][string]$Container, [int]$TimeoutSeconds = 180)
    Wait-Until -TimeoutSeconds $TimeoutSeconds -Description "$Container healthy" -Condition {
        $status = & docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $Container 2>$null
        $status -in @('healthy', 'running')
    }
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (!$Condition) { throw $Message }
}
