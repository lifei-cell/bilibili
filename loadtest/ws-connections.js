import { WebSocket } from 'k6/websockets'
import { Counter, Rate, Trend } from 'k6/metrics'

const wsUrl = __ENV.WS_URL || 'ws://host.docker.internal:8080/api/danmu/ws/2001'
const virtualUsers = Number(__ENV.VUS || 50)
const sessionDuration = Number(__ENV.SESSION_MS || 45000)
const heartbeatInterval = Math.max(1000, Math.min(15000, Math.floor(sessionDuration / 2)))

const connected = new Rate('ws_connected')
const receivedMessages = new Counter('ws_messages_received')
const authLatency = new Trend('ws_auth_latency', true)

export const options = {
  scenarios: {
    websocket_connections: {
      executor: 'per-vu-iterations',
      vus: virtualUsers,
      iterations: 1,
      maxDuration: `${Math.ceil(sessionDuration / 1000) + 10}s`,
      gracefulStop: '0s',
    },
  },
  thresholds: {
    ws_connected: ['rate>0.995'],
    ws_auth_latency: ['p(95)<1000'],
  },
}

export default function () {
  const startedAt = Date.now()
  const ws = new WebSocket(wsUrl)
  let opened = false
  let connectionRecorded = false
  let heartbeatId

  function recordConnection(value) {
    if (!connectionRecorded) {
      connected.add(value)
      connectionRecorded = true
    }
  }

  ws.addEventListener('open', () => {
    opened = true
    recordConnection(true)
    heartbeatId = setInterval(() => {
      try {
        if (ws.readyState === 1) {
          ws.send(JSON.stringify({ type: 'heartbeat' }))
        }
      } catch (_) {
        clearInterval(heartbeatId)
      }
    }, heartbeatInterval)
    setTimeout(() => {
      try {
        if (ws.readyState === 0 || ws.readyState === 1) ws.close()
      } catch (_) {
        // The socket has already been closed by the server.
      }
    }, sessionDuration)
  })

  ws.addEventListener('message', (event) => {
    receivedMessages.add(1)
    try {
      const message = JSON.parse(event.data)
      if (message.type === 'auth' && message.success === true) {
        authLatency.add(Date.now() - startedAt)
      }
    } catch (_) {
      // The metric above still records unexpected non-JSON frames.
    }
  })

  ws.addEventListener('error', () => {
    if (!opened) recordConnection(false)
  })

  ws.addEventListener('close', () => {
    if (heartbeatId !== undefined) clearInterval(heartbeatId)
    if (!opened) recordConnection(false)
  })
}
