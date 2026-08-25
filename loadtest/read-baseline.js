import http from 'k6/http'
import { check } from 'k6'
import { Rate } from 'k6/metrics'

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:8080/api'
const iterationRate = Number(__ENV.RATE || 5)
const duration = __ENV.DURATION || '2m'
const videoIds = (__ENV.VIDEO_IDS || '2001,2002,2003,2004,2005')
  .split(',')
  .map((value) => Number(value.trim()))
  .filter((value) => Number.isInteger(value) && value > 0)

const businessFailure = new Rate('business_failure')

export const options = {
  scenarios: {
    read_baseline: {
      executor: 'constant-arrival-rate',
      rate: iterationRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: Math.max(10, iterationRate * 2),
      maxVUs: Math.max(50, iterationRate * 10),
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.005'],
    business_failure: ['rate<0.005'],
    http_req_duration: ['p(95)<1000'],
    'http_req_duration{endpoint:list}': ['p(95)<500'],
    'http_req_duration{endpoint:detail}': ['p(95)<500'],
    'http_req_duration{endpoint:search}': ['p(95)<500'],
  },
}

function successful(response, name) {
  const passed = check(response, {
    [`${name}: HTTP 200`]: (item) => item.status === 200,
    [`${name}: business success`]: (item) => item.json('success') === true,
  })
  businessFailure.add(!passed)
}

export default function () {
  const videoId = videoIds[Math.floor(Math.random() * videoIds.length)] || 2001
  const responses = http.batch([
    ['GET', `${baseUrl}/video/list?page=1&size=20&sort=hot`, null,
      { tags: { endpoint: 'list' } }],
    ['GET', `${baseUrl}/video/${videoId}`, null,
      { tags: { endpoint: 'detail' } }],
    ['GET', `${baseUrl}/search?keyword=Java&page=1&size=20&sort=hot`, null,
      { tags: { endpoint: 'search' } }],
  ])

  successful(responses[0], 'list')
  successful(responses[1], 'detail')
  successful(responses[2], 'search')
}
