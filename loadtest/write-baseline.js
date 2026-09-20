import http from 'k6/http'
import { check } from 'k6'
import { Rate, Trend } from 'k6/metrics'

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080/api'
const videoId = Number(__ENV.VIDEO_ID)
const duration = __ENV.DURATION || '2m'
const playbackRate = Number(__ENV.PLAYBACK_RATE || 10)
const danmuRate = Number(__ENV.DANMU_RATE || 5)
const interactionRate = Number(__ENV.INTERACTION_RATE || 3)
const runId = __ENV.RUN_ID || `write-slo-${Date.now()}`

const playbackP95Ms = Number(__ENV.PLAYBACK_P95_MS || 500)
const danmuP95Ms = Number(__ENV.DANMU_P95_MS || 1000)
const interactionP95Ms = Number(__ENV.INTERACTION_P95_MS || 1500)
const playbackMaxErrorRate = Number(__ENV.PLAYBACK_MAX_ERROR_RATE || 0.005)
const danmuMaxErrorRate = Number(__ENV.DANMU_MAX_ERROR_RATE || 0.005)
const interactionMaxErrorRate = Number(__ENV.INTERACTION_MAX_ERROR_RATE || 0.005)

if (!Number.isInteger(videoId) || videoId <= 0) {
  throw new Error('VIDEO_ID must identify a disposable published video')
}

const businessFailure = new Rate('write_slo_business_failure')
const playbackFailure = new Rate('write_slo_playback_failure')
const danmuFailure = new Rate('write_slo_danmu_failure')
const interactionFailure = new Rate('write_slo_interaction_failure')
const playbackLatency = new Trend('write_slo_playback_latency', true)
const danmuLatency = new Trend('write_slo_danmu_latency', true)
const interactionLatency = new Trend('write_slo_interaction_latency', true)

function arrivalScenario(rate, exec) {
  return {
    executor: 'constant-arrival-rate',
    exec,
    rate,
    timeUnit: '1s',
    duration,
    preAllocatedVUs: Math.max(5, rate * 2),
    maxVUs: Math.max(20, rate * 10),
  }
}

export const options = {
  scenarios: {
    playback: arrivalScenario(playbackRate, 'playback'),
    danmu: arrivalScenario(danmuRate, 'danmu'),
    interaction: arrivalScenario(interactionRate, 'interaction'),
  },
  thresholds: {
    http_req_failed: ['rate<0.005'],
    write_slo_playback_failure: [`rate<${playbackMaxErrorRate}`],
    write_slo_danmu_failure: [`rate<${danmuMaxErrorRate}`],
    write_slo_interaction_failure: [`rate<${interactionMaxErrorRate}`],
    write_slo_playback_latency: [`p(95)<${playbackP95Ms}`],
    write_slo_danmu_latency: [`p(95)<${danmuP95Ms}`],
    write_slo_interaction_latency: [`p(95)<${interactionP95Ms}`],
  },
}

export function setup() {
  const usernames = (__ENV.WRITE_TEST_USERS || 'demo_alice,demo_bob,demo_carol,demo_dan,demo_eve,demo_admin')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
  const clients = usernames.map((username) => {
    const response = http.post(`${baseUrl}/user/login`, JSON.stringify({
      username,
      password: __ENV.PASSWORD || 'Demo@123',
      terminal: 'write-slo',
    }), {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'login' },
    })
    const passed = check(response, {
      [`login ${username}: HTTP 200`]: (item) => item.status === 200,
      [`login ${username}: business success`]: (item) => item.json('success') === true,
      [`login ${username}: token returned`]: (item) => Boolean(item.json('data.token')),
    })
    if (!passed) throw new Error(`write-path load test login failed for ${username}`)
    return {
      username,
      headers: { 'Content-Type': 'application/json', satoken: response.json('data.token') },
    }
  })
  return { clients }
}

function uniqueSuffix(kind) {
  return `${runId}-${kind}-${__VU}-${__ITER}`
}

function assertSuccess(response, name) {
  return check(response, {
    [`${name}: HTTP 200`]: (item) => item.status === 200,
    [`${name}: business success`]: (item) => item.json('success') === true,
  })
}

function currentClient(context) {
  return context.clients[(__VU + __ITER) % context.clients.length]
}

function recordOutcome(metric, passed) {
  businessFailure.add(!passed)
  metric.add(!passed)
}

export function playback() {
  const response = http.get(`${baseUrl}/video/${videoId}/play`, {
    tags: { endpoint: 'playback' },
  })
  playbackLatency.add(response.timings.duration)
  recordOutcome(playbackFailure, assertSuccess(response, 'playback'))
}

export function danmu(context) {
  const client = currentClient(context)
  const requestId = `${client.username}-${uniqueSuffix('danmu')}`.slice(0, 64)
  const response = http.post(`${baseUrl}/danmu/send`, JSON.stringify({
    videoId,
    content: `write-slo danmu ${requestId}`,
    color: '#FFFFFF',
    position: 0,
    fontSize: 16,
    videoTime: 1,
    requestId,
  }), {
    headers: client.headers,
    tags: { endpoint: 'danmu' },
  })
  danmuLatency.add(response.timings.duration)
  const accepted = assertSuccess(response, 'danmu') && response.json('data.accepted') === true
  recordOutcome(danmuFailure, accepted)
}

export function interaction(context) {
  const client = currentClient(context)
  const startedAt = Date.now()
  const commentResponse = http.post(`${baseUrl}/comment`, JSON.stringify({
    videoId,
    content: `write-slo comment ${uniqueSuffix('comment')}`,
    parentId: 0,
    replyToId: 0,
  }), {
    headers: client.headers,
    tags: { endpoint: 'comment-create' },
  })
  const commentCreated = assertSuccess(commentResponse, 'comment create')
  const commentId = commentCreated ? commentResponse.json('data.commentId') : null
  let commentDeleted = Boolean(commentId)

  if (commentId) {
    const deleteResponse = http.del(`${baseUrl}/comment/${commentId}`, null, {
      headers: client.headers,
      tags: { endpoint: 'comment-delete' },
    })
    commentDeleted = assertSuccess(deleteResponse, 'comment delete')
  }

  const likePayload = JSON.stringify({ targetType: 1, targetId: videoId })
  const likeResponse = http.post(`${baseUrl}/like`, likePayload, {
    headers: client.headers,
    tags: { endpoint: 'like' },
  })
  const liked = assertSuccess(likeResponse, 'like') && likeResponse.json('data.liked') === true

  const unlikeResponse = http.del(`${baseUrl}/like`, likePayload, {
    headers: client.headers,
    tags: { endpoint: 'unlike' },
  })
  const unliked = assertSuccess(unlikeResponse, 'unlike') && unlikeResponse.json('data.liked') === false
  interactionLatency.add(Date.now() - startedAt)
  recordOutcome(interactionFailure, commentCreated && commentDeleted && liked && unliked)
}
