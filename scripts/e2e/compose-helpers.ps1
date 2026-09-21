Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$curlCommand = @(Get-Command curl.exe -CommandType Application -ErrorAction SilentlyContinue) |
    Select-Object -First 1
if ($null -eq $curlCommand) {
    $curlCommand = @(Get-Command curl -CommandType Application -ErrorAction Stop) |
        Select-Object -First 1
}
$script:CurlExecutable = $curlCommand.Source
$script:CurlExitCode = 0
$script:NullDevice = if ([IO.Path]::DirectorySeparatorChar -eq [char]92) { 'NUL' } else { '/dev/null' }

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

function Invoke-CurlNoProxy {
    param(
        [Parameter(Mandatory)]
        [string[]]$CurlArguments
    )

    $hadUpper = Test-Path Env:NO_PROXY
    $hadLower = Test-Path Env:no_proxy
    $previousUpper = $env:NO_PROXY
    $previousLower = $env:no_proxy
    try {
        $env:NO_PROXY = '*'
        $env:no_proxy = '*'
        $output = & $script:CurlExecutable @CurlArguments
        $script:CurlExitCode = $LASTEXITCODE
        return $output
    } finally {
        if ($hadUpper) { $env:NO_PROXY = $previousUpper } else { Remove-Item Env:NO_PROXY -ErrorAction SilentlyContinue }
        if ($hadLower) { $env:no_proxy = $previousLower } else { Remove-Item Env:no_proxy -ErrorAction SilentlyContinue }
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
    $arguments = @(
        '--connect-timeout', '5',
        '--max-time', [string]$TimeoutSec,
        '-fsS', '-X', $Method,
        '-H', 'Accept: application/json'
    )
    foreach ($name in $Headers.Keys) {
        $arguments += @('-H', "${name}: $($Headers[$name])")
    }
    if ($null -ne $Body) {
        $jsonBody = $Body | ConvertTo-Json -Depth 10 -Compress
        $arguments += @('-H', 'Content-Type: application/json; charset=utf-8', '--data-raw', $jsonBody)
    }
    $arguments += $Uri
    $responseBody = Invoke-CurlNoProxy -CurlArguments $arguments
    if ($script:CurlExitCode -ne 0) {
        throw "API request failed: $Method $Uri (curl exit $script:CurlExitCode)"
    }
    try {
        $response = ($responseBody -join "`n") | ConvertFrom-Json
    } catch {
        throw "API returned invalid JSON: $Method $Uri"
    }
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
