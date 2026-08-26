param(
    [switch]$SkipBuild,
    [switch]$RunFaultDrill,
    [switch]$KeepRunning,
    [string]$AdminToken = 'change-me-in-production'
)

. "$PSScriptRoot/compose-helpers.ps1"
. "$PSScriptRoot/fault-drill.ps1"

$services = @(
    'bilibili-gateway', 'bilibili-user-service', 'bilibili-video-service',
    'bilibili-danmu-service', 'bilibili-social-service',
    'bilibili-search-service', 'bilibili-canal-service'
)
$infrastructure = @(
    'mysql', 'redis', 'nacos', 'rocketmq-namesrv', 'rocketmq-broker',
    'elasticsearch', 'minio', 'canal'
)
$temporaryDirectory = Join-Path ([IO.Path]::GetTempPath()) ("bilibili-reliability-e2e-" + [Guid]::NewGuid())
$videoId = 0L
$danmuId = 0L
$fileMd5 = ''
$started = $false

try {
    New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null
    if (!$SkipBuild) {
        & mvn -s .mvn/settings.xml -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw 'Maven package failed' }
    }

    Write-Host '[e2e] Starting infrastructure'
    Invoke-Compose up --detach @infrastructure
    Wait-ContainerHealthy bilibili-mysql
    Wait-ContainerHealthy bilibili-redis
    Wait-ContainerHealthy bilibili-rocketmq-broker
    Wait-ContainerHealthy bilibili-elasticsearch

    foreach ($topic in @('video-transcode', 'video-view', 'danmu-persist', 'cache-sync')) {
        & docker exec bilibili-rocketmq-broker sh /home/rocketmq/rocketmq-5.3.1/bin/mqadmin updateTopic `
            -n rocketmq-namesrv:9876 -c DefaultCluster -t $topic | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Cannot create RocketMQ topic $topic" }
    }

    Write-Host '[e2e] Building and starting application services'
    Invoke-Compose up --detach --build @services
    foreach ($container in @(
        'bilibili-gateway', 'bilibili-user-service', 'bilibili-video-service',
        'bilibili-danmu-service', 'bilibili-social-service',
        'bilibili-search-service', 'bilibili-canal-service')) {
        Wait-ContainerHealthy $container 240
    }
    $started = $true

    $login = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/user/login' -Body @{
        username = 'demo_alice'; password = 'Demo@123'; terminal = 'e2e'
    }
    $token = $login.data.token
    $creatorUserId = [long]$login.data.user.id
    Assert-True (![string]::IsNullOrWhiteSpace($token)) 'Login did not return an access token'
    $authHeaders = @{ satoken = $token }
    $adminHeaders = @{ 'X-Admin-Token' = $AdminToken }
    $adminLogin = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/user/login' -Body @{
        username = 'demo_admin'; password = 'Demo@123'; terminal = 'e2e-admin'
    }
    $contentAdminHeaders = @{ satoken = $adminLogin.data.token }

    Write-Host '[e2e] Upload -> transcode'
    $containerVideo = '/tmp/reliability-e2e.mp4'
    & docker exec bilibili-video-service sh -c `
        "ffmpeg -loglevel error -y -f lavfi -i testsrc=size=320x240:rate=24 -f lavfi -i sine=frequency=1000 -t 2 -c:v libx264 -pix_fmt yuv420p -c:a aac $containerVideo"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot generate E2E video with FFmpeg' }
    $videoPath = Join-Path $temporaryDirectory 'reliability-e2e.mp4'
    & docker cp "bilibili-video-service:$containerVideo" $videoPath
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy generated E2E video' }
    $fileMd5 = (Get-FileHash -Path $videoPath -Algorithm MD5).Hash.ToLowerInvariant()
    $fileSize = (Get-Item $videoPath).Length
    $direct = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/upload/direct/init' -Headers $authHeaders -Body @{
        fileMd5 = $fileMd5; fileName = 'reliability-e2e.mp4'; fileSize = $fileSize; contentType = 'video/mp4'
    }
    $uploadId = $direct.data.uploadId
    Assert-True (![string]::IsNullOrWhiteSpace($uploadId)) 'Direct upload did not create a session'
    Invoke-WebRequest -UseBasicParsing -Method PUT -Uri $direct.data.uploadUrl -InFile $videoPath `
        -ContentType 'video/mp4' -TimeoutSec 60 | Out-Null
    $merge = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/upload/direct/complete' -Headers $authHeaders -Body @{
        uploadId = $uploadId
    }
    Wait-Until -TimeoutSeconds 180 -Description 'video transcode completion' -Condition {
        $script:transcode = Invoke-BiliApi -Method GET -Uri "http://localhost:8080/api/upload/transcode/$uploadId" -Headers $authHeaders
        $script:transcode.data.status -eq 'completed'
    }
    Assert-True ($transcode.data.outputUrl -like '*/master.m3u8') 'Transcode did not produce an HLS master playlist'
    Assert-True ($transcode.data.coverUrl -like '*/cover.jpg') 'Transcode did not produce an automatic cover'

    Write-Host '[e2e] Publish -> play -> search -> interaction'
    $title = "reliability-e2e-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
    $published = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/video/publish' -Headers $authHeaders -Body @{
        title = $title; description = 'Compose reliability E2E'; coverUrl = ''
        sourceUrl = $merge.data.sourceUrl; fileMd5 = $fileMd5; fileSize = $fileSize
        duration = 2; resolution = '360P'; categoryId = 8; tags = @('reliability', 'e2e')
    }
    $videoId = [long]$published.data.videoId
    Invoke-BiliApi -Method POST -Uri "http://localhost:8080/api/admin/content/videos/$videoId/audit" -Headers $contentAdminHeaders -Body @{
        action = 'APPROVE'; remark = 'Compose E2E human review'
    } | Out-Null

    $play = Invoke-BiliApi -Method GET -Uri "http://localhost:8080/api/video/$videoId/play"
    Assert-True ($play.data.videoId -eq $videoId) 'Published video is not playable'
    Assert-True ($play.data.qualities.Count -ge 1) 'HLS quality list is empty'
    Assert-True ($play.data.qualities[0].url -like '*/index.m3u8') 'Playback did not return an HLS rendition'
    $master = Invoke-WebRequest -UseBasicParsing -Uri $transcode.data.outputUrl -TimeoutSec 20
    $masterContent = if ($master.Content -is [byte[]]) {
        [Text.Encoding]::UTF8.GetString($master.Content)
    } else {
        [string]$master.Content
    }
    Assert-True ($masterContent.Contains('#EXT-X-STREAM-INF')) 'HLS master playlist is invalid'
    $rebuild = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/admin/reliability/es/rebuild' -Headers $adminHeaders -TimeoutSec 120
    Assert-True $rebuild.data.verified 'Initial Elasticsearch rebuild did not verify'
    $search = Invoke-BiliApi -Method GET -Uri "http://localhost:8080/api/search?keyword=$title&page=1&size=10"
    Assert-True (@($search.data | Where-Object { $_.id -eq $videoId }).Count -eq 1) 'Published video is not searchable'

    $comment = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/comment' -Headers $authHeaders -Body @{
        videoId = $videoId; content = 'reliability e2e comment'; parentId = 0; replyToId = 0
    }
    Assert-True ($comment.data.commentId -gt 0) 'Comment interaction failed'
    $like = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/like' -Headers $authHeaders -Body @{
        targetType = 1; targetId = $videoId
    }
    Assert-True $like.data.liked 'Like interaction failed'
    $danmu = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/danmu/send' -Headers $authHeaders -Body @{
        videoId = $videoId; content = 'reliability e2e danmu'; color = '#FFFFFF'
        position = 0; fontSize = 16; videoTime = 1; requestId = [Guid]::NewGuid().ToString('N')
    }
    Assert-True $danmu.data.accepted 'Danmu interaction failed'
    $danmuId = [long]$danmu.data.danmuId
    Wait-Until -TimeoutSeconds 120 -Description 'danmu Inbox persistence' -Condition {
        [long](Get-MySqlScalar "select count(*) from danmu where id=$danmuId") -eq 1
    }

    $report = Invoke-BiliApi -Method GET -Uri 'http://localhost:8080/api/admin/reliability/cdc/reconcile' -Headers $adminHeaders
    Assert-True $report.data.consistent 'CDC reconciliation failed after the core journey'
    if ($RunFaultDrill) {
        Invoke-ReliabilityFaultDrill -VideoId $videoId -Token $token -AdminToken $AdminToken
    }
    Write-Host "[e2e] PASS videoId=$videoId uploadId=$uploadId faultDrill=$RunFaultDrill"
} finally {
    if ($started -and $videoId -gt 0) {
        try {
            $cleanupSql = "delete from mq_consumed_message where topic='video-view' and message_key like '${videoId}:%'; delete from mq_consumed_message where topic='danmu-persist' and message_key='$danmuId'; delete i from mq_consumed_message i join reliable_event_outbox o on i.topic=o.topic and i.message_key=o.event_id where o.aggregate_id='$videoId'; delete from reliable_event_outbox where aggregate_id='$videoId' or (aggregate_type='danmu' and aggregate_id='$danmuId'); delete from content_report where target_type='VIDEO' and target_id=$videoId; delete from content_audit_log where target_type='VIDEO' and target_id=$videoId; delete from content_risk_event where target_type='VIDEO' and target_id=$videoId; delete from danmu where video_id=$videoId; delete from comment where video_id=$videoId; delete from user_like where target_type=1 and target_id=$videoId; delete from collection where video_id=$videoId; delete from video_stats where video_id=$videoId; delete from video where id=$videoId; delete from video_transcode_task where file_md5='$fileMd5'; delete from file_chunk where file_md5='$fileMd5'; delete from direct_upload_session where upload_id='$uploadId';"
            & docker exec -e MYSQL_PWD=root bilibili-mysql mysql -uroot bilibili -e $cleanupSql | Out-Null
            & docker run --rm --network bilibili-net --entrypoint sh minio/mc -c "mc alias set local http://minio:9000 minioadmin minioadmin >/dev/null; mc rm --force local/videos/source/direct/$creatorUserId/$uploadId.mp4 local/videos/source/$fileMd5.mp4 local/tmp/$uploadId/0 >/dev/null 2>&1 || true; mc rm --recursive --force local/videos/play/$fileMd5 >/dev/null 2>&1 || true" | Out-Null
            Invoke-BiliApi -Method POST -Uri 'http://localhost:8086/api/admin/reliability/es/rebuild' -Headers @{ 'X-Admin-Token' = $AdminToken } -TimeoutSec 120 | Out-Null
            # Business cleanup itself emits CDC delete events. Let Canal consume
            # them, then remove only audit rows whose payload points at this video.
            Start-Sleep -Seconds 3
            $cdcScope = "(json_unquote(json_extract(payload, '$.table'))='video' and json_unquote(json_extract(payload, '$.data.id'))='$videoId') or (json_unquote(json_extract(payload, '$.table'))='video_stats' and json_unquote(json_extract(payload, '$.data.video_id'))='$videoId') or (json_unquote(json_extract(payload, '$.table'))='user_like' and json_unquote(json_extract(payload, '$.data.target_id'))='$videoId')"
            $auditCleanupSql = "delete i from mq_consumed_message i join reliable_event_outbox o on i.topic=o.topic and i.message_key=o.event_id where $cdcScope; delete from reliable_event_outbox where $cdcScope;"
            & docker exec -e MYSQL_PWD=root bilibili-mysql mysql -uroot bilibili -e $auditCleanupSql | Out-Null
        } catch {
            Write-Warning "E2E data cleanup needs attention: $($_.Exception.Message)"
        }
    }
    if (Test-Path $temporaryDirectory) {
        Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force
    }
    if (!$KeepRunning) {
        try { Invoke-Compose stop @services @infrastructure } catch { Write-Warning $_ }
    }
}
