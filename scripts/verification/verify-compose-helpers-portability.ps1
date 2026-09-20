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

Write-Output "[portability] curl=$script:CurlExecutable"
