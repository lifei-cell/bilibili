import http from 'k6/http'
import { check } from 'k6'
import { Rate, Trend } from 'k6/metrics'

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080/api'
const videoId = Number(__ENV.VIDEO_ID)
const rate = Number(__ENV.RATE || 2)
const duration = __ENV.DURATION || '60s'
const runId = __ENV.RUN_ID || `p2-write-${Date.now()}`

if (!Number.isInteger(videoId) || videoId <= 0) {
  throw new Error('VIDEO_ID must identify a disposable published video')
}

const businessFailure = new Rate('write_business_failure')
const operationLatency = new Trend('write_operation_latency', true)

export const options = {
  scenarios: {
    write_baseline: {
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
    write_business_failure: ['rate<0.005'],
    write_operation_latency: ['p(95)<1500', 'p(99)<3000'],
  },
}

export function setup() {
  const usernames = (__ENV.WRITE_TEST_USERS || 'demo_bob,demo_carol,demo_dan,demo_eve,demo_admin')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)
  const clients = usernames.map((username) => {
    const response = http.post(`${baseUrl}/user/login`, JSON.stringify({
      username,
      password: __ENV.PASSWORD || 'Demo@123',
      terminal: 'p2-write',
    }), {
      headers: { 'Content-Type': 'application/json' },
      tags: { endpoint: 'login' },
    })
    const passed = check(response, {
      [`login ${username}: HTTP 200`]: (item) => item.status === 200,
      [`login ${username}: business success`]: (item) => item.json('success') === true,
      [`login ${username}: token returned`]: (item) => Boolean(item.json('data.token')),
    })
    if (!passed) throw new Error(`write baseline login failed for ${username}`)
    return {
      username,
      headers: { 'Content-Type': 'application/json', satoken: response.json('data.token') },
    }
  })
  return { clients }
}

function currentClient(context) {
  return context.clients[(__VU + __ITER) % context.clients.length]
}

function successful(response, name) {
  return check(response, {
    [`${name}: HTTP 200`]: (item) => item.status === 200,
    [`${name}: business success`]: (item) => item.json('success') === true,
  })
}

export default function (context) {
  const client = currentClient(context)
  const suffix = `${runId}-${__VU}-${__ITER}`
  const startedAt = Date.now()
  const created = http.post(`${baseUrl}/comment`, JSON.stringify({
    videoId,
    content: `p2-capacity write ${suffix}`,
    parentId: 0,
    replyToId: 0,
  }), {
    headers: client.headers,
    tags: { endpoint: 'comment-create' },
  })
  const createdOk = successful(created, 'comment create')
  const commentId = createdOk ? created.json('data.commentId') : null
  let deletedOk = false
  if (commentId) {
    const deleted = http.del(`${baseUrl}/comment/${commentId}`, null, {
      headers: client.headers,
      tags: { endpoint: 'comment-delete' },
    })
    deletedOk = successful(deleted, 'comment delete')
  }
  const passed = createdOk && deletedOk
  businessFailure.add(!passed)
  operationLatency.add(Date.now() - startedAt)
}
