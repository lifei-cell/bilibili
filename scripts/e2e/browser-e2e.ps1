param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runId = [Guid]::NewGuid().ToString('N')
$videoPath = Join-Path ([IO.Path]::GetTempPath()) "browser-e2e-$runId.mp4"
$containerPath = "/tmp/browser-e2e-$runId.mp4"
$previousVideoPath = $env:E2E_VIDEO_PATH
$npmCommand = if ($IsWindows) { 'npm.cmd' } else { 'npm' }

try {
    # Unique metadata prevents an earlier run's MD5 from bypassing upload/transcode.
    & docker exec bilibili-video-service ffmpeg -loglevel error -y `
        -f lavfi -i 'testsrc=size=320x240:rate=24' -f lavfi -i 'sine=frequency=1000' `
        -t 4 -c:v libx264 -pix_fmt yuv420p -c:a aac -metadata "comment=$runId" $containerPath
    if ($LASTEXITCODE -ne 0) { throw 'Cannot generate browser E2E video' }
    & docker cp "bilibili-video-service:$containerPath" $videoPath
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy browser E2E video' }
    $env:E2E_VIDEO_PATH = $videoPath
    Push-Location (Join-Path $repositoryRoot 'bilibili-web')
    try {
        & $npmCommand run test:e2e:real
        if ($LASTEXITCODE -ne 0) { throw 'Real browser journey failed' }
    } finally {
        Pop-Location
    }
} finally {
    $env:E2E_VIDEO_PATH = $previousVideoPath
    if (Test-Path -LiteralPath $videoPath) { Remove-Item -LiteralPath $videoPath -Force }
    & docker exec bilibili-video-service rm -f $containerPath
    if ($LASTEXITCODE -ne 0) { Write-Warning 'Could not remove temporary container video' }
}
