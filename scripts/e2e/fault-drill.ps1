function Invoke-ReliabilityFaultDrill {
    param(
        [Parameter(Mandatory)][long]$VideoId,
        [Parameter(Mandatory)][string]$Token,
        [string]$AdminToken = 'change-me-in-production'
    )

    Write-Host '[fault] RocketMQ short outage -> Outbox catches up'
    Invoke-Compose stop rocketmq-broker
    $before = [long](Get-MySqlScalar "select count(*) from reliable_event_outbox where aggregate_type='video' and aggregate_id='$VideoId' and topic='video-view' and status='PUBLISHED'")
    $play = Invoke-BiliApi -Method GET -Uri "http://localhost:8082/api/video/$VideoId/play"
    Assert-True ($play.data.videoId -eq $VideoId) 'Playback must remain available while MQ is down'
    Wait-Until -TimeoutSeconds 20 -Description 'pending video-view outbox row' -Condition {
        [long](Get-MySqlScalar "select count(*) from reliable_event_outbox where aggregate_type='video' and aggregate_id='$VideoId' and topic='video-view' and status<>'PUBLISHED'") -gt 0
    }
    Invoke-Compose start rocketmq-broker
    Wait-ContainerHealthy bilibili-rocketmq-broker 180
    Wait-Until -TimeoutSeconds 120 -Description 'MQ outbox recovery' -Condition {
        [long](Get-MySqlScalar "select count(*) from reliable_event_outbox where aggregate_type='video' and aggregate_id='$VideoId' and topic='video-view' and status='PUBLISHED'") -gt $before
    }

    Write-Host '[fault] Redis short outage -> broker retry and Inbox recovery'
    Invoke-Compose stop redis
    $playDuringRedisFailure = Invoke-BiliApi -Method GET -Uri "http://localhost:8082/api/video/$VideoId/play"
    Assert-True ($playDuringRedisFailure.data.videoId -eq $VideoId) 'DB playback path must fail open when Redis is down'
    Start-Sleep -Seconds 3
    Invoke-Compose start redis
    Wait-ContainerHealthy bilibili-redis 120
    Wait-Until -TimeoutSeconds 180 -Description 'Redis consumer recovery' -Condition {
        $failed = [long](Get-MySqlScalar "select count(*) from mq_consumed_message where topic='video-view' and message_key like '${VideoId}:%' and status='FAILED'")
        $failed -eq 0
    }

    Write-Host '[fault] Elasticsearch short outage -> rebuild and reconciliation'
    Invoke-Compose stop elasticsearch
    $updateBody = @{ title = "reliability-e2e-$VideoId-recovered" }
    $headers = @{ satoken = $Token }
    Invoke-BiliApi -Method PUT -Uri "http://localhost:8082/api/video/$VideoId" -Body $updateBody -Headers $headers | Out-Null
    Start-Sleep -Seconds 3
    Invoke-Compose start elasticsearch
    Wait-ContainerHealthy bilibili-elasticsearch 180
    # The container probe exercises the same Elasticsearch health indicator from
    # inside the service network and avoids host proxy/port-forwarding variance.
    Wait-ContainerHealthy bilibili-canal-service 240
    $adminHeaders = @{ 'X-Admin-Token' = $AdminToken }
    $rebuild = Invoke-BiliApi -Method POST -Uri 'http://localhost:8086/api/admin/reliability/es/rebuild' -Headers $adminHeaders
    Assert-True $rebuild.data.verified 'Elasticsearch rebuild verification failed'
    $report = Invoke-BiliApi -Method GET -Uri 'http://localhost:8086/api/admin/reliability/cdc/reconcile' -Headers $adminHeaders
    Assert-True $report.data.consistent 'CDC reconciliation must converge after ES recovery'
}
