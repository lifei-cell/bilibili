import http from 'k6/http'
import { check } from 'k6'
import { Rate, Trend } from 'k6/metrics'

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080/api'
const videoId = Number(__ENV.VIDEO_ID)
const rate = Number(__ENV.RATE || 2)
const duration = __ENV.DURATION || '60s'
const runId = __ENV.RUN_ID || `p2-danmu-${Date.now()}`

if (!Number.isInteger(videoId) || videoId <= 0) {
  throw new Error('VIDEO_ID must identify a disposable published video')
}

const businessFailure = new Rate('danmu_business_failure')
const sendLatency = new Trend('danmu_send_latency', true)

export const options = {
  scenarios: {
    danmu_baseline: {
      executor: 'constant-arrival-rate',
      rate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: Math.max(5, rate * 2),
      maxVUs: Math.max(20, rate * 10),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.005'],
    danmu_business_failure: ['rate<0.005'],
    danmu_send_latency: ['p(95)<1000', 'p(99)<3000'],
  },
}

export function setup() {
  const usernames = (__ENV.DANMU_TEST_USERS || 'demo_bob,demo_carol,demo_dan,demo_eve,demo_admin')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
  const clients = usernames.map((username) => {
    const response = http.post(`${baseUrl}/user/login`, JSON.stringify({
      username,
      password: __ENV.PASSWORD || 'Demo@123',
      terminal: 'p2-danmu',
    }), {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'login' },
    })
    const passed = check(response, {
      [`login ${username}: HTTP 200`]: (item) => item.status === 200,
      [`login ${username}: business success`]: (item) => item.json('success') === true,
      [`login ${username}: token returned`]: (item) => Boolean(item.json('data.token')),
    })
    if (!passed) throw new Error(`danmu baseline login failed for ${username}`)
    return {
      username,
      headers: { 'Content-Type': 'application/json', satoken: response.json('data.token') },
    }
  })
  return { clients }
}

export default function (context) {
  const client = context.clients[(__VU + __ITER) % context.clients.length]
  const requestId = `${runId}-${client.username}-${__VU}-${__ITER}`.slice(0, 64)
  const response = http.post(`${baseUrl}/danmu/send`, JSON.stringify({
    videoId,
    content: `p2-capacity danmu ${requestId}`,
    color: '#FFFFFF',
    position: 0,
    fontSize: 16,
    videoTime: 1,
    requestId,
  }), {
    headers: client.headers,
    tags: { endpoint: 'danmu-send' },
  })
  const passed = check(response, {
    'danmu send: HTTP 200': (item) => item.status === 200,
    'danmu send: business success': (item) => item.json('success') === true,
    'danmu send: accepted': (item) => item.json('data.accepted') === true,
  })
  businessFailure.add(!passed)
  sendLatency.add(response.timings.duration)
}
