Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot '..' 'e2e' 'compose-helpers.ps1')

if ($script:CurlExecutable -is [array]) {
    throw 'curl executable resolution returned more than one path'
}
if ([string]::IsNullOrWhiteSpace([string]$script:CurlExecutable)) {
    throw 'curl executable resolution returned an empty path'
}

$version = & $script:CurlExecutable --version
if ($LASTEXITCODE -ne 0 -or !$version) {
    throw "curl executable is not callable: $script:CurlExecutable"
}

$probePath = Join-Path ([IO.Path]::GetTempPath()) "bilibili-curl-probe-$PID.json"
try {
    [IO.File]::WriteAllText($probePath, '{"success":true}')
    $probeUri = [Uri]::new($probePath, [UriKind]::Absolute).AbsoluteUri
    $response = Invoke-CurlNoProxy -CurlArguments @('-fsS', $probeUri)
    if ($script:CurlExitCode -ne 0 -or ($response -join "`n") -ne '{"success":true}') {
        throw 'curl no-proxy argument was expanded or the probe request failed'
    }
} finally {
    Remove-Item -LiteralPath $probePath -Force -ErrorAction SilentlyContinue
}

Write-Output "[portability] curl=$script:CurlExecutable noProxy=environment"
