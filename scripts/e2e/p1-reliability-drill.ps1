param(
    [string]$AdminToken = 'change-me-in-production',
    [string]$ResultsDirectory,
    [switch]$CrashAfterPlayable,
    [switch]$MeasureIndexCutover
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ([string]::IsNullOrWhiteSpace($ResultsDirectory)) {
    $ResultsDirectory = Join-Path $repositoryRoot 'loadtest/results'
}
$runId = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDirectory = Join-Path $ResultsDirectory "p1-reliability-drill-$runId"
$temporaryDirectory = Join-Path ([IO.Path]::GetTempPath()) ("bilibili-p1-drill-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Force -Path $runDirectory | Out-Null
New-Item -ItemType Directory -Force -Path $temporaryDirectory | Out-Null

. (Join-Path $repositoryRoot 'scripts/e2e/compose-helpers.ps1')
$script:ComposeFiles = @(
    '-p', 'bilibili',
    '-f', 'docker-compose.yml',
    '-f', 'docker-compose.service.yml',
    '-f', 'docker-compose.e2e.yml',
    '-f', 'docker-compose.p1.yml'
)

$infra = @(
    'mysql', 'redis', 'nacos', 'rocketmq-namesrv', 'rocketmq-broker',
    'elasticsearch', 'minio', 'canal'
)
$apps = @(
    'bilibili-gateway', 'bilibili-user-service', 'bilibili-video-service',
    'bilibili-danmu-service', 'bilibili-social-service',
    'bilibili-search-service', 'bilibili-canal-service'
)
$allComposeServices = @($apps + $infra + 'bilibili-canal-service-2')
$taskId = ''
$fileMd5 = ''
$uploadId = ''
$creatorUserId = 0L
$videoId = 0L
$sourceObject = ''
$workerCandidates = @()
$claimingWorker = ''
$transcodeRow = $null
$workerOne = ''
$workerTwo = ''
$rebuildJobs = @()
$overallPassed = $false
$failure = $null
$cleanupPassed = $true

$workerResult = [ordered]@{
    passed = $false
    taskId = $null
    firstWorker = $null
    secondWorker = $null
    firstGeneration = $null
    recoveredGeneration = $null
    firstWorkerExitState = $null
    takeoverLogObserved = $false
    outputUrl = $null
    lowOutputUrl = $null
    lowPlaybackReadableBeforeCrash = $false
    playbackPreserved = $false
    finalRenditionStatus = $null
    lastSnapshot = $null
    survivingWorkerLogs = @()
}
$canalResult = [ordered]@{
    passed = $false
    instanceOne = 'bilibili-canal-service'
    instanceTwo = 'bilibili-canal-service-2'
    connectorLogsObserved = $false
    rebuildResponses = @()
    aliasIndexCount = $null
    reconcileConsistent = $false
    updatedTitleSearchable = $false
    measurement = $null
}

$indexMeasurement = [ordered]@{
    sourceUpdates = 0
    capturedEvents = 0
    consumedEvents = 0
    maxUncaptured = 0
    maxUnconsumed = 0
    maxOutboxPending = 0
    drainMilliseconds = $null
    samples = @()
    lockTimers = @()
}

function Get-WorkerContainers {
    @(& docker ps -a --filter 'label=com.docker.compose.service=bilibili-transcode-worker' --format '{{.Names}}' |
        Where-Object { ![string]::IsNullOrWhiteSpace($_) })
}

function Get-TranscodeSnapshot {
    param([Parameter(Mandatory)][string]$Task)

    $sql = "select status, claim_generation, coalesce(claim_token,''), coalesce(output_url,''), coalesce(source_object_name,''), rendition_status from video_transcode_task where task_id='$Task' limit 1"
    $raw = (& docker exec -e MYSQL_PWD=root bilibili-mysql mysql -N -B -uroot bilibili -e $sql) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "MySQL transcode snapshot failed for task $Task" }
    $parts = $raw.Trim().Split("`t")
    if ($parts.Count -lt 6) { throw "Transcode task disappeared: $Task" }
    [pscustomobject]@{
        status = [int]$parts[0]
        generation = [long]$parts[1]
        token = $parts[2]
        outputUrl = $parts[3]
        sourceObject = $parts[4]
        renditionStatus = [int]$parts[5]
    }
}

function Test-ReadableHls {
    param([Parameter(Mandatory)][string]$Url)

    $playlist = & $script:CurlExecutable --noproxy '*' --connect-timeout 5 --max-time 15 -fsS $Url
    return $LASTEXITCODE -eq 0 -and ($playlist -join "`n").Contains('#EXTM3U')
}

function Invoke-RawAdminRequest {
    param(
        [Parameter(Mandatory)][string]$Url,
        [Parameter(Mandatory)][string]$Method,
        [Parameter(Mandatory)][string]$Token
    )

    $response = & $script:CurlExecutable --noproxy '*' --connect-timeout 5 --max-time 180 -sS `
        -X $Method -H 'Accept: application/json' -H "X-Admin-Token: $Token" `
        -w "`n__HTTP__:%{http_code}" $Url
    if ($LASTEXITCODE -ne 0) { throw "Admin request failed: $Method $Url" }
    $raw = ($response -join "`n").Trim()
    $marker = [regex]::Match($raw, '__HTTP__:(\d{3})$')
    if (!$marker.Success) { throw "Admin request did not return an HTTP status: $Url" }
    [pscustomobject]@{
        status = [int]$marker.Groups[1].Value
        body = $raw.Substring(0, $marker.Index).Trim()
    }
}

function Get-EsAliasIndexCount {
    $response = & $script:CurlExecutable --noproxy '*' --connect-timeout 5 --max-time 20 -fsS `
        'http://localhost:9200/_alias/video_search'
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect Elasticsearch video_search alias' }
    $json = ($response -join "`n") | ConvertFrom-Json
    @($json.PSObject.Properties).Count
}

function Get-VideoCdcProgress {
    param([Parameter(Mandatory)][long]$VideoId)

    $sql = "select count(*), coalesce(sum(o.status <> 'PUBLISHED'),0), coalesce(sum(i.status = 'SUCCEEDED'),0) from reliable_event_outbox o left join mq_consumed_message i on i.topic=o.topic and i.message_key=o.event_id and i.consumer_group='bilibili-canal-cache-sync' where o.topic='cache-sync' and o.aggregate_type='video' and o.aggregate_id='$VideoId'"
    $parts = ([string](Get-MySqlScalar $sql)).Split("`t")
    if ($parts.Count -ne 3) { throw 'Cannot read video CDC Outbox/Inbox progress' }
    [pscustomobject]@{ captured = [int]$parts[0]; pending = [int]$parts[1]; consumed = [int]$parts[2] }
}

function Get-IndexLockTimer {
    param([Parameter(Mandatory)][int]$Port, [Parameter(Mandatory)][string]$Operation)

    $uri = "http://localhost:$Port/actuator/metrics/bilibili.index.lock.wait?tag=operation:$Operation"
    $raw = & $script:CurlExecutable --noproxy '*' --connect-timeout 5 --max-time 15 -sS -w "`n__HTTP__:%{http_code}" $uri
    if ($LASTEXITCODE -ne 0) { throw "Cannot read index lock timer from port $Port" }
    $body = ($raw -join "`n").Trim()
    $marker = [regex]::Match($body, '__HTTP__:(\d{3})$')
    if (!$marker.Success) { throw "Index lock timer response lacked status on port $Port" }
    if ($marker.Groups[1].Value -eq '404') {
        return [pscustomobject]@{ port = $Port; operation = $Operation; count = 0; totalMilliseconds = 0; maxMilliseconds = 0 }
    }
    if ($marker.Groups[1].Value -ne '200') { throw "Index lock timer returned HTTP $($marker.Groups[1].Value) on port $Port" }
    $metric = $body.Substring(0, $marker.Index).Trim() | ConvertFrom-Json
    $values = @{}
    foreach ($measurement in $metric.measurements) { $values[$measurement.statistic] = [double]$measurement.value }
    [pscustomobject]@{
        port = $Port
        operation = $Operation
        count = [int]$values['COUNT']
        totalMilliseconds = [math]::Round(1000 * $values['TOTAL_TIME'], 3)
        maxMilliseconds = [math]::Round(1000 * $values['MAX'], 3)
    }
}

function Wait-CanalConnectorLogs {
    Wait-Until -TimeoutSeconds 120 -IntervalSeconds 2 -Description 'both Canal application connectors' -Condition {
        $one = ((& docker logs bilibili-canal-service 2>$null) -join "`n")
        $two = ((& docker logs bilibili-canal-service-2 2>$null) -join "`n")
        return $one.Contains('Canal connector connected') -and $two.Contains('Canal connector connected')
    }
}

function Remove-DrillData {
    if ($taskId -or $videoId -gt 0) {
        $sql = "delete i from mq_consumed_message i join reliable_event_outbox o on i.topic=o.topic and i.message_key=o.event_id where o.aggregate_id='$videoId'; delete from reliable_event_outbox where aggregate_id='$videoId' or (aggregate_type='danmu' and aggregate_id='0'); delete from content_report where target_type='VIDEO' and target_id=$videoId; delete from content_audit_log where target_type='VIDEO' and target_id=$videoId; delete from content_risk_event where target_type='VIDEO' and target_id=$videoId; delete from danmu where video_id=$videoId; delete from comment where video_id=$videoId; delete from user_like where target_type=1 and target_id=$videoId; delete from collection where video_id=$videoId; delete from video_stats where video_id=$videoId; delete from video where id=$videoId; delete from video_transcode_task where task_id='$taskId'; delete from file_chunk where file_md5='$fileMd5'; delete from direct_upload_session where upload_id='$uploadId';"
        & docker exec -e MYSQL_PWD=root bilibili-mysql mysql -uroot bilibili -e $sql | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'P1 database cleanup failed' }
    }
    if ($sourceObject -or $fileMd5) {
        $sourcePath = if ($sourceObject) { "videos/$sourceObject" } else { "videos/play/$fileMd5" }
        & docker run --rm --network bilibili-net --entrypoint sh minio/mc -c `
            "mc alias set local http://minio:9000 minioadmin minioadmin >/dev/null; mc rm --force local/$sourcePath >/dev/null 2>&1 || true; mc rm --recursive --force local/videos/play/$fileMd5 >/dev/null 2>&1 || true" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'P1 MinIO cleanup failed' }
    }
}

Push-Location $repositoryRoot
try {
    Write-Host '[p1] Starting infrastructure'
    Invoke-Compose up --detach @infra
    foreach ($container in @('bilibili-mysql', 'bilibili-redis', 'bilibili-rocketmq-broker', 'bilibili-elasticsearch')) {
        Wait-ContainerHealthy $container 240
    }
    foreach ($topic in @('video-transcode', 'video-view', 'danmu-persist', 'cache-sync')) {
        & docker exec bilibili-rocketmq-broker sh /home/rocketmq/rocketmq-5.3.1/bin/mqadmin updateTopic `
            -n rocketmq-namesrv:9876 -c DefaultCluster -t $topic | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Cannot create RocketMQ topic $topic" }
    }

    Write-Host '[p1] Starting API, dispatcher and two transcode Workers'
    Invoke-Compose up --detach --build @apps
    foreach ($container in @('bilibili-gateway', 'bilibili-user-service', 'bilibili-video-service',
            'bilibili-danmu-service', 'bilibili-social-service', 'bilibili-search-service',
            'bilibili-canal-service')) {
        Wait-ContainerHealthy $container 300
    }
    Wait-Until -TimeoutSeconds 120 -Description 'Gateway route convergence' -Condition {
        & $script:CurlExecutable --noproxy '*' --connect-timeout 3 --max-time 5 -fsS -o $script:NullDevice `
            'http://localhost:8080/api/video/list?page=1&size=1&sort=hot'
        return $LASTEXITCODE -eq 0
    }
    Invoke-Compose --profile transcode-scale up --detach --build --scale bilibili-transcode-worker=2 bilibili-transcode-worker
    $workerCandidates = @(Get-WorkerContainers | Where-Object { (& docker inspect --format '{{.State.Status}}' $_) -eq 'running' })
    if ($workerCandidates.Count -ne 2) { throw 'Both transcode Workers did not start' }
    foreach ($worker in $workerCandidates) { Wait-ContainerHealthy $worker 120 }

    Write-Host '[p1] Creating a real long-running upload task'
    $containerVideo = '/tmp/p1-worker-recovery.mp4'
    & docker exec bilibili-video-service ffmpeg -loglevel error -y `
        -f lavfi -i testsrc2=size=1280x720:rate=24 `
        -f lavfi -i sine=frequency=440:sample_rate=48000 `
        -t 60 -c:v libx264 -preset ultrafast -pix_fmt yuv420p -c:a aac $containerVideo
    if ($LASTEXITCODE -ne 0) { throw 'Cannot generate the P1 Worker drill source video' }
    $videoPath = Join-Path $temporaryDirectory 'p1-worker-recovery.mp4'
    & docker cp "bilibili-video-service:$containerVideo" $videoPath
    if ($LASTEXITCODE -ne 0) { throw 'Cannot copy the P1 Worker drill source video' }
    $fileMd5 = (Get-FileHash -Path $videoPath -Algorithm MD5).Hash.ToLowerInvariant()
    $fileSize = (Get-Item $videoPath).Length

    $login = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/user/login' -Body @{
        username = 'demo_alice'; password = 'Demo@123'; terminal = 'p1-worker'
    }
    $authHeaders = @{ satoken = $login.data.token }
    $creatorUserId = [long]$login.data.user.id
    $direct = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/upload/direct/init' -Headers $authHeaders -Body @{
        fileMd5 = $fileMd5; fileName = 'p1-worker-recovery.mp4'; fileSize = $fileSize; contentType = 'video/mp4'
    }
    $uploadId = [string]$direct.data.uploadId
    & $script:CurlExecutable --noproxy '*' --connect-timeout 5 --max-time 120 -fsS `
        -X PUT -H 'Content-Type: video/mp4' --upload-file $videoPath $direct.data.uploadUrl
    if ($LASTEXITCODE -ne 0) { throw 'P1 direct upload failed' }
    $merge = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/upload/direct/complete' -Headers $authHeaders -Body @{ uploadId = $uploadId }
    $taskId = [string](Get-MySqlScalar "select task_id from video_transcode_task where file_md5='$fileMd5' order by id desc limit 1")
    if ([string]::IsNullOrWhiteSpace($taskId)) { throw 'P1 transcode task was not persisted' }
    $sourceObject = [string](Get-MySqlScalar "select source_object_name from video_transcode_task where task_id='$taskId'")

    $script:transcodeRow = $null
    $firstStage = if ($CrashAfterPlayable) { 'first playable rendition' } else { 'first Worker to claim the task' }
    Wait-Until -TimeoutSeconds 180 -IntervalSeconds 1 -Description $firstStage -Condition {
        $row = Get-TranscodeSnapshot -Task $taskId
        $ready = if ($CrashAfterPlayable) {
            $row.status -eq 3 -and $row.renditionStatus -eq 1 -and
                $row.generation -eq 1 -and ![string]::IsNullOrWhiteSpace($row.outputUrl)
        } else {
            $row.status -eq 2 -and $row.generation -eq 1
        }
        if ($ready) {
            $script:transcodeRow = $row
            return $true
        }
        return $false
    }
    $workerResult.taskId = $taskId
    $workerResult.firstGeneration = $transcodeRow.generation
    if ($CrashAfterPlayable) {
        $workerResult.lowOutputUrl = $transcodeRow.outputUrl
        $workerResult.lowPlaybackReadableBeforeCrash = Test-ReadableHls $transcodeRow.outputUrl
        Assert-True $workerResult.lowPlaybackReadableBeforeCrash 'First rendition was not readable before the Worker crash'
    }
    $claimLog = "Transcode task claimed, taskId=$taskId, generation=1"
    $script:claimingWorker = ''
    Wait-Until -TimeoutSeconds 60 -IntervalSeconds 1 -Description 'claiming Worker log' -Condition {
        foreach ($candidate in $workerCandidates) {
            $logs = ((& docker logs $candidate 2>$null) -join "`n")
            if ($logs.Contains($claimLog)) {
                $script:claimingWorker = $candidate
                return $true
            }
        }
        return $false
    }
    $workerOne = $claimingWorker
    $workerTwo = ($workerCandidates | Where-Object { $_ -ne $workerOne } | Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($workerOne) -or [string]::IsNullOrWhiteSpace($workerTwo)) {
        throw 'Could not identify both Workers in the claim log'
    }
    $workerResult.firstWorker = $workerOne
    $workerResult.secondWorker = $workerTwo

    Write-Host "[p1] Crashing $workerOne while $workerTwo remains available"
    # Freeze the in-flight FFmpeg process while the second replica remains
    # available. This removes the timing race where the first replica can finish
    # before the crash, while the following kill still exercises a real
    # container crash and lease takeover.
    & docker pause $workerOne | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Cannot pause in-flight Worker $workerOne" }
    & docker update --restart=no $workerOne | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Cannot disable restart policy for $workerOne" }
    & docker kill $workerOne | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Cannot crash $workerOne" }
    $workerResult.firstWorkerExitState = (& docker inspect --format '{{.State.Status}}' $workerOne).Trim()
    Assert-True ($workerResult.firstWorkerExitState -eq 'exited') 'The crashed Worker did not stop'
    Wait-ContainerHealthy $workerTwo 120
    $script:transcodeRow = $null
    Wait-Until -TimeoutSeconds 240 -IntervalSeconds 1 -Description 'second Worker takeover and transcode recovery' -Condition {
        $row = Get-TranscodeSnapshot -Task $taskId
        if ($CrashAfterPlayable -and $row.generation -ge 2 -and $row.status -eq 3 -and $row.renditionStatus -ne 2) {
            Assert-True ($row.outputUrl -eq $workerResult.lowOutputUrl) 'Playable low rendition changed before compensation finished'
            if (Test-ReadableHls $row.outputUrl) { $workerResult.playbackPreserved = $true }
        }
        if ($row.status -eq 3 -and $row.generation -ge 2 -and [string]::IsNullOrWhiteSpace($row.token) -and
            $row.outputUrl -like '*attempt-2-*' -and (!$CrashAfterPlayable -or $row.renditionStatus -eq 2)) {
            $logs = ((& docker logs $workerTwo 2>$null) -join "`n")
            if ($logs.Contains("Transcode task claimed, taskId=$taskId, generation=2")) {
                $script:transcodeRow = $row
                $workerResult.takeoverLogObserved = $true
                return $true
            }
        }
        return $false
    }
    $workerResult.recoveredGeneration = $transcodeRow.generation
    $workerResult.outputUrl = $transcodeRow.outputUrl
    $workerResult.finalRenditionStatus = $transcodeRow.renditionStatus
    if ($CrashAfterPlayable) {
        Assert-True $workerResult.playbackPreserved 'No compensation interval preserved the low rendition was observed'
    }
    if (!(Test-ReadableHls $transcodeRow.outputUrl)) {
        throw 'Recovered Worker did not publish a readable HLS master playlist'
    }
    $workerResult.passed = $true

    Write-Host '[p1] Publishing the recovered asset and starting the second Canal instance'
    $published = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/video/publish' -Headers $authHeaders -Body @{
        title = "p1-worker-recovery-$runId"; description = 'P1 dual Worker recovery drill'; coverUrl = ''
        sourceUrl = $merge.data.sourceUrl; fileMd5 = $fileMd5; fileSize = $fileSize
        duration = 60; resolution = '720P'; categoryId = 8; tags = @('p1', 'reliability')
    }
    $videoId = [long]$published.data.videoId
    $adminLogin = Invoke-BiliApi -Method POST -Uri 'http://localhost:8080/api/user/login' -Body @{
        username = 'demo_admin'; password = 'Demo@123'; terminal = 'p1-admin'
    }
    Invoke-BiliApi -Method POST -Uri "http://localhost:8080/api/admin/content/videos/$videoId/audit" `
        -Headers @{ satoken = $adminLogin.data.token } -Body @{ action = 'APPROVE'; remark = 'P1 multi Canal drill' } | Out-Null
    $adminHeaders = @{ 'X-Admin-Token' = $AdminToken }
    $initialRebuild = Invoke-BiliApi -Method POST -Uri 'http://localhost:8086/api/admin/reliability/es/rebuild' `
        -Headers $adminHeaders -TimeoutSec 180
    Assert-True $initialRebuild.data.verified 'Initial P1 Elasticsearch rebuild did not verify'
    Invoke-Compose up --detach bilibili-canal-service-2
    Wait-ContainerHealthy bilibili-canal-service-2 300
    Wait-CanalConnectorLogs
    $canalResult.connectorLogsObserved = $true

    $updatedTitle = "p1-multi-canal-$runId"
    $cdcBaseline = $null
    if ($MeasureIndexCutover) {
        Wait-Until -TimeoutSeconds 90 -IntervalSeconds 2 -Description 'initial video CDC settled' -Condition {
            $progress = Get-VideoCdcProgress $videoId
            return $progress.captured -ge 1 -and $progress.captured -eq $progress.consumed
        }
        $cdcBaseline = Get-VideoCdcProgress $videoId
    }
    $rebuildScript = {
        param($curlPath, $url, $token)
        $body = & $curlPath --noproxy '*' --connect-timeout 5 --max-time 180 -sS `
            -X POST -H 'Accept: application/json' -H "X-Admin-Token: $token" `
            -w "`n__HTTP__:%{http_code}" $url
        [string]::Join("`n", @($body))
    }
    $jobOne = Start-Job -ScriptBlock $rebuildScript -ArgumentList @(
        $script:CurlExecutable, 'http://localhost:8086/api/admin/reliability/es/rebuild', $AdminToken)
    $rebuildJobs = @($jobOne)
    $jobTwo = Start-Job -ScriptBlock $rebuildScript -ArgumentList @(
        $script:CurlExecutable, 'http://localhost:8087/api/admin/reliability/es/rebuild', $AdminToken)
    $rebuildJobs += $jobTwo
    if ($MeasureIndexCutover) {
        $indexMeasurement.sourceUpdates = 20
        $updates = 1..$indexMeasurement.sourceUpdates | ForEach-Object {
            "update video set title='$updatedTitle-$_' where id=$videoId"
        }
        $sql = ($updates -join '; ') + ';'
        & docker exec -e MYSQL_PWD=root bilibili-mysql mysql -uroot bilibili -e $sql | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Cannot generate video CDC update workload' }
        $updatedTitle = "$updatedTitle-$($indexMeasurement.sourceUpdates)"
        $writeCompleted = [DateTimeOffset]::UtcNow
        $deadline = $writeCompleted.AddSeconds(180)
        do {
            $progress = Get-VideoCdcProgress $videoId
            $captured = [math]::Max(0, $progress.captured - $cdcBaseline.captured)
            $consumed = [math]::Max(0, $progress.consumed - $cdcBaseline.consumed)
            $unconsumed = [math]::Max(0, $captured - $consumed)
            $uncaptured = [math]::Max(0, $indexMeasurement.sourceUpdates - $captured)
            $indexMeasurement.maxUncaptured = [math]::Max($indexMeasurement.maxUncaptured, $uncaptured)
            $indexMeasurement.maxUnconsumed = [math]::Max($indexMeasurement.maxUnconsumed, $unconsumed)
            $indexMeasurement.maxOutboxPending = [math]::Max($indexMeasurement.maxOutboxPending, $progress.pending)
            $indexMeasurement.samples += [pscustomobject]@{
                millisecondsAfterWrites = [math]::Round(([DateTimeOffset]::UtcNow - $writeCompleted).TotalMilliseconds)
                captured = $captured
                consumed = $consumed
                uncaptured = $uncaptured
                unconsumed = $unconsumed
                outboxPending = $progress.pending
            }
            if ($captured -ge $indexMeasurement.sourceUpdates -and $consumed -ge $indexMeasurement.sourceUpdates) {
                $indexMeasurement.capturedEvents = $captured
                $indexMeasurement.consumedEvents = $consumed
                $indexMeasurement.drainMilliseconds = [math]::Round(([DateTimeOffset]::UtcNow - $writeCompleted).TotalMilliseconds)
                break
            }
            Start-Sleep -Milliseconds 500
        } while ([DateTimeOffset]::UtcNow -lt $deadline)
        Assert-True ($null -ne $indexMeasurement.drainMilliseconds) 'Video CDC did not drain within 180 seconds'
    } else {
        Invoke-BiliApi -Method PUT -Uri "http://localhost:8080/api/video/$videoId" -Headers $authHeaders -Body @{ title = $updatedTitle } | Out-Null
    }
    Wait-Job -Job $rebuildJobs -Timeout 240 | Out-Null
    foreach ($job in $rebuildJobs) {
        if ($job.State -ne 'Completed') { throw 'Concurrent multi Canal rebuild request did not complete' }
    }
    $responses = @($rebuildJobs | ForEach-Object {
        $raw = (Receive-Job -Job $_) -join "`n"
        $marker = [regex]::Match($raw.Trim(), '__HTTP__:(\d{3})$')
        if (!$marker.Success) { throw 'Concurrent rebuild response lacked an HTTP status' }
        [pscustomobject]@{
            instance = if ($_ -eq $jobOne) { 'bilibili-canal-service' } else { 'bilibili-canal-service-2' }
            status = [int]$marker.Groups[1].Value
            body = $raw.Substring(0, $marker.Index).Trim()
        }
    })
    $canalResult.rebuildResponses = @($responses | Select-Object instance, status, body)
    $responses | ForEach-Object {
        if ($_.status -eq 200) {
            $body = $_.body | ConvertFrom-Json
            Assert-True $body.success 'Successful rebuild response was not a business success'
            Assert-True $body.data.verified 'Successful rebuild was not verified'
        } elseif ($_.status -lt 500 -or $_.status -ge 600) {
            throw "Unexpected concurrent rebuild HTTP status: $($_.status)"
        }
    }
    Assert-True (@($responses | Where-Object status -eq 200).Count -ge 1) 'Neither Canal instance completed an index cutover'
    if ($MeasureIndexCutover) {
        $indexMeasurement.lockTimers = @(
            Get-IndexLockTimer -Port 8086 -Operation cdc
            Get-IndexLockTimer -Port 8086 -Operation cutover
            Get-IndexLockTimer -Port 8087 -Operation cdc
            Get-IndexLockTimer -Port 8087 -Operation cutover
        )
        $canalResult.measurement = $indexMeasurement
    }
    $canalResult.aliasIndexCount = Get-EsAliasIndexCount
    Assert-True ($canalResult.aliasIndexCount -eq 1) 'video_search alias must resolve to exactly one index'

    Wait-Until -TimeoutSeconds 180 -IntervalSeconds 2 -Description 'multi Canal CDC reconciliation' -Condition {
        try {
            $report = Invoke-BiliApi -Method GET -Uri 'http://localhost:8086/api/admin/reliability/cdc/reconcile' -Headers $adminHeaders
            $script:canalReconcile = $report.data
            return [bool]$report.data.consistent
        } catch {
            return $false
        }
    }
    $canalResult.reconcileConsistent = [bool]$script:canalReconcile.consistent
    $search = Invoke-BiliApi -Method GET -Uri "http://localhost:8080/api/search?keyword=$updatedTitle&page=1&size=10"
    $canalResult.updatedTitleSearchable = @($search.data | Where-Object { $_.title -eq $updatedTitle }).Count -eq 1
    Assert-True $canalResult.updatedTitleSearchable 'Updated video was not searchable after multi Canal cutover'
    $canalResult.passed = $true
    $overallPassed = $true
}
catch {
    $failure = $_.Exception.Message
    if (![string]::IsNullOrWhiteSpace($taskId)) {
        try { $workerResult.lastSnapshot = Get-TranscodeSnapshot -Task $taskId } catch { }
    }
    if (![string]::IsNullOrWhiteSpace($workerTwo)) {
        try { $workerResult.survivingWorkerLogs = @(& docker logs --tail 120 $workerTwo 2>&1 | ForEach-Object { [string]$_ }) } catch { }
    }
    Write-Warning "[p1] $failure"
}
finally {
    try {
        Remove-DrillData
    } catch {
        $cleanupPassed = $false
        Write-Warning "P1 data cleanup needs attention: $($_.Exception.Message)"
    }
    try {
        Invoke-Compose stop @allComposeServices
        Invoke-Compose --profile transcode-scale rm --force --stop bilibili-transcode-worker
    } catch {
        $cleanupPassed = $false
        Write-Warning "P1 Compose cleanup needs attention: $($_.Exception.Message)"
    }
    if ($rebuildJobs.Count -gt 0) {
        try {
            $rebuildJobs | Remove-Job -Force -ErrorAction Stop
        } catch {
            $cleanupPassed = $false
            Write-Warning "P1 rebuild job cleanup needs attention: $($_.Exception.Message)"
        }
    }
    if (Test-Path -LiteralPath $temporaryDirectory) {
        Remove-Item -LiteralPath $temporaryDirectory -Recurse -Force
    }
    $report = [ordered]@{
        schemaVersion = 1
        type = 'p1-reliability-drill'
        crashAfterPlayable = [bool]$CrashAfterPlayable
        generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
        passed = $overallPassed -and $cleanupPassed -and $workerResult.passed -and $canalResult.passed
        failure = $failure
        cleanupPassed = $cleanupPassed
        dockerHost = $env:DOCKER_HOST
        worker = $workerResult
        canal = $canalResult
    }
    $reportPath = Join-Path $runDirectory 'p1-reliability-drill.json'
    $report | ConvertTo-Json -Depth 12 | Set-Content -Encoding utf8 -Path $reportPath
    Write-Host "[p1] report=$reportPath passed=$($report.passed)"
}
Pop-Location

if (!$overallPassed -or !$cleanupPassed -or !$workerResult.passed -or !$canalResult.passed) {
    exit 1
}
