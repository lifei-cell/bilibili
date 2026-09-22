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

$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
$headerJob = $null
try {
    $listener.Start()
    $port = ([Net.IPEndPoint]$listener.LocalEndpoint).Port
    $helperPath = (Resolve-Path (Join-Path $PSScriptRoot '..' 'e2e' 'compose-helpers.ps1')).Path
    $headerJob = Start-Job -ScriptBlock {
        param($Helpers, $Uri)
        . $Helpers
        Invoke-BiliApi -Method POST -Uri $Uri -Headers @{
            'X-Admin-Token' = 'change-me-in-production'
        }
    } -ArgumentList $helperPath, "http://127.0.0.1:$port/probe"

    $acceptTask = $listener.AcceptTcpClientAsync()
    if (!$acceptTask.Wait(15000)) {
        throw 'curl header probe did not connect to the local listener'
    }
    $client = $acceptTask.Result
    try {
        $stream = $client.GetStream()
        $stream.ReadTimeout = 15000
        $buffer = [byte[]]::new(8192)
        $requestBytes = [IO.MemoryStream]::new()
        do {
            $read = $stream.Read($buffer, 0, $buffer.Length)
            if ($read -gt 0) { $requestBytes.Write($buffer, 0, $read) }
            $requestText = [Text.Encoding]::ASCII.GetString($requestBytes.ToArray())
        } while ($read -gt 0 -and !$requestText.Contains("`r`n`r`n"))

        $expectedHeader = "X-Admin-Token:change-me-in-production`r`n"
        if (!$requestText.Contains($expectedHeader)) {
            throw 'curl header argument was reconstructed incorrectly'
        }
        $responseBytes = [Text.Encoding]::ASCII.GetBytes(
            "HTTP/1.1 200 OK`r`nContent-Type: application/json`r`nContent-Length: 16`r`nConnection: close`r`n`r`n{`"success`":true}")
        $stream.Write($responseBytes, 0, $responseBytes.Length)
        $requestBytes.Dispose()
    } finally {
        $client.Dispose()
    }
    if (!(Wait-Job -Job $headerJob -Timeout 15)) {
        throw 'curl header probe did not complete'
    }
    Receive-Job -Job $headerJob -ErrorAction Stop | Out-Null
} finally {
    $listener.Stop()
    if ($null -ne $headerJob) {
        Stop-Job -Job $headerJob -ErrorAction SilentlyContinue
        Remove-Job -Job $headerJob -Force -ErrorAction SilentlyContinue
    }
}

Write-Output "[portability] curl=$script:CurlExecutable noProxy=environment header=preserved"
