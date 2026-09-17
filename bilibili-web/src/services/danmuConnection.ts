import type { DanmuItem } from '@/types/api'

interface DanmuConnectionOptions {
  videoId: number
  ticket?: () => Promise<string>
  onDanmu: (item: DanmuItem) => void
}

export class DanmuConnection {
  private socket?: WebSocket
  private reconnectTimer?: number
  private heartbeatTimer?: number
  private generation = 0

  constructor(private readonly options: DanmuConnectionOptions) {}

  async connect(): Promise<void> {
    const current = this.generation
    let query = ''
    if (this.options.ticket) {
      try {
        query = `?ticket=${encodeURIComponent(await this.options.ticket())}`
      } catch {
        // Public viewing remains available when an authenticated ticket fails.
      }
    }
    if (current !== this.generation) return
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    const socket = new WebSocket(`${protocol}//${location.host}/api/danmu/ws/${this.options.videoId}${query}`)
    this.socket = socket
    socket.onopen = () => {
      if (current !== this.generation) { socket.close(); return }
      const heartbeat = () => {
        if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: 'heartbeat' }))
      }
      heartbeat()
      this.heartbeatTimer = window.setInterval(heartbeat, 25000)
    }
    socket.onmessage = event => {
      if (current !== this.generation) return
      try {
        const message = JSON.parse(event.data) as { type?: string; data?: DanmuItem }
        if (message.type === 'danmu' && message.data) this.options.onDanmu(message.data)
      } catch {
        // Malformed broadcasts do not terminate the connection.
      }
    }
    socket.onerror = () => socket.close()
    socket.onclose = () => {
      if (current !== this.generation) return
      this.clearTimers()
      this.socket = undefined
      this.reconnectTimer = window.setTimeout(() => { void this.connect() }, 1500)
    }
  }

  dispose(): void {
    this.generation += 1
    this.clearTimers()
    const socket = this.socket
    this.socket = undefined
    if (socket) {
      socket.onopen = null
      socket.onmessage = null
      socket.onerror = null
      socket.onclose = null
      socket.close()
    }
  }

  private clearTimers(): void {
    if (this.reconnectTimer !== undefined) window.clearTimeout(this.reconnectTimer)
    if (this.heartbeatTimer !== undefined) window.clearInterval(this.heartbeatTimer)
    this.reconnectTimer = undefined
    this.heartbeatTimer = undefined
  }
}
