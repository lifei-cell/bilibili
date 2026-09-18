import http from 'k6/http'
import { check, sleep } from 'k6'
import { Rate, Trend } from 'k6/metrics'

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080/api'
const rate = Number(__ENV.RATE || 1)
const duration = __ENV.DURATION || '60s'
const timeoutSeconds = Number(__ENV.TRANSCODE_TIMEOUT_SECONDS || 180)
const manifestText = __ENV.FIXTURES_MANIFEST
  ? open(__ENV.FIXTURES_MANIFEST)
  : (__ENV.FIXTURES_JSON || '[]')
const fixtureManifest = JSON.parse(manifestText)
const fixtures = fixtureManifest.map((fixture) => Object.assign({}, fixture, {
  bytes: open(fixture.path, 'b'),
}))

if (fixtures.length === 0) {
  throw new Error('FIXTURES_JSON must contain at least one mounted MP4 fixture')
}

const businessFailure = new Rate('transcode_business_failure')
const uploadLatency = new Trend('transcode_upload_latency', true)
const completionLatency = new Trend('transcode_completion_latency', true)
const e2eLatency = new Trend('transcode_e2e_latency', true)

export const options = {
  scenarios: {
    transcode_baseline: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: Math.max(2, rate * 2),
      maxVUs: Number(__ENV.MAX_VUS || 8),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    transcode_business_failure: ['rate<0.05'],
    transcode_e2e_latency: ['p(95)<180000', 'p(99)<240000'],
  },
}

function toAccessibleUrl(url) {
  // The API signs localhost for host clients. k6 runs in a separate container,
  // so route the same exposed MinIO port through Docker's host gateway.
  return url.replace(/:\/\/(localhost|127\.0\.0\.1|minio)(?=[:/])/, '://host.docker.internal')
}

function successful(response, name) {
  return check(response, {
    [`${name}: HTTP success`]: (item) => item.status >= 200 && item.status < 300,
    [`${name}: business success`]: (item) => item.json('success') === true,
  })
}

export function setup() {
  const usernames = (__ENV.TRANSCODE_TEST_USERS || 'demo_bob,demo_carol,demo_dan,demo_eve,demo_admin')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
  const clients = usernames.map((username) => {
    const response = http.post(`${baseUrl}/user/login`, JSON.stringify({
      username,
      password: __ENV.PASSWORD || 'Demo@123',
      terminal: 'p2-transcode',
    }), {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'login' },
    })
    const passed = check(response, {
      [`login ${username}: HTTP 200`]: (item) => item.status === 200,
      [`login ${username}: business success`]: (item) => item.json('success') === true,
      [`login ${username}: token returned`]: (item) => Boolean(item.json('data.token')),
    })
    if (!passed) throw new Error(`transcode baseline login failed for ${username}`)
    return {
      username,
      headers: { 'Content-Type': 'application/json', satoken: response.json('data.token') },
    }
  })
  return { clients }
}

export default function (context) {
  const client = context.clients[(__VU + __ITER) % context.clients.length]
  const fixture = fixtures[(__VU + __ITER) % fixtures.length]
  const startedAt = Date.now()
  const init = http.post(`${baseUrl}/upload/direct/init`, JSON.stringify({
    fileName: fixture.name,
    contentType: fixture.contentType || 'video/mp4',
    fileSize: fixture.size,
    fileMd5: fixture.md5,
  }), {
    headers: client.headers,
    tags: { endpoint: 'transcode-init' },
  })
  const initOk = successful(init, 'transcode init') && Boolean(init.json('data.uploadId'))
  if (!initOk) {
    businessFailure.add(true)
    e2eLatency.add(Date.now() - startedAt)
    return
  }

  const uploadId = init.json('data.uploadId')
  const upload = http.put(toAccessibleUrl(init.json('data.uploadUrl')), fixture.bytes, {
    headers: { 'Content-Type': fixture.contentType || 'video/mp4' },
    tags: { endpoint: 'transcode-upload' },
  })
  const uploadOk = check(upload, {
    'transcode upload: HTTP success': (item) => item.status >= 200 && item.status < 300,
  })
  if (!uploadOk) {
    businessFailure.add(true)
    uploadLatency.add(Date.now() - startedAt)
    e2eLatency.add(Date.now() - startedAt)
    return
  }

  const complete = http.post(`${baseUrl}/upload/direct/complete`, JSON.stringify({ uploadId }), {
    headers: client.headers,
    tags: { endpoint: 'transcode-complete' },
  })
  const completeOk = successful(complete, 'transcode complete')
  uploadLatency.add(Date.now() - startedAt)
  if (!completeOk) {
    businessFailure.add(true)
    e2eLatency.add(Date.now() - startedAt)
    return
  }

  const completionStartedAt = Date.now()
  let completed = false
  for (let second = 0; second < timeoutSeconds; second += 1) {
    const status = http.get(`${baseUrl}/upload/transcode/${uploadId}`, {
      headers: client.headers,
      tags: { endpoint: 'transcode-status' },
    })
    const state = status.status === 200 ? status.json('data.status') : null
    if (state === 'completed') {
      completed = true
      break
    }
    if (state === 'failed') break
    sleep(1)
  }
  const completionDuration = Date.now() - completionStartedAt
  const totalDuration = Date.now() - startedAt
  completionLatency.add(completionDuration)
  e2eLatency.add(totalDuration)
  businessFailure.add(!completed)
  check({ completed }, { 'transcode completed': (item) => item.completed === true })
}
