import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DanmuConnection } from './danmuConnection'

class FakeSocket {
  static instances: FakeSocket[] = []
  static OPEN = 1
  readyState = FakeSocket.OPEN
  onopen: (() => void) | null = null
  onclose: (() => void) | null = null
  onerror: (() => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  send = vi.fn()
  close = vi.fn(() => { this.onclose?.() })
  constructor(readonly url: string) { FakeSocket.instances.push(this) }
}

describe('danmu connection', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    FakeSocket.instances = []
    vi.stubGlobal('WebSocket', FakeSocket)
  })
  afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals() })

  it('reissues a ticket and reconnects after a dropped socket', async () => {
    const onDanmu = vi.fn()
    const ticket = vi.fn().mockResolvedValueOnce('old').mockResolvedValueOnce('new')
    const connection = new DanmuConnection({ videoId: 42, ticket, onDanmu })
    await connection.connect()
    const first = FakeSocket.instances[0]
    expect(first.url).toContain('/api/danmu/ws/42?ticket=old')
    first.onopen?.()
    expect(first.send).toHaveBeenCalledWith('{"type":"heartbeat"}')
    first.onclose?.()
    await vi.advanceTimersByTimeAsync(1500)
    expect(FakeSocket.instances[1].url).toContain('ticket=new')
    FakeSocket.instances[1].onmessage?.({ data: JSON.stringify({ type: 'danmu', data: { id: 7, content: 'recovered' } }) })
    expect(onDanmu).toHaveBeenCalledWith(expect.objectContaining({ content: 'recovered' }))
    connection.dispose()
  })

  it('does not reconnect or deliver stale messages after leaving the page', async () => {
    const onDanmu = vi.fn()
    const connection = new DanmuConnection({ videoId: 42, onDanmu })
    await connection.connect()
    const first = FakeSocket.instances[0]
    first.onclose?.()
    connection.dispose()
    await vi.advanceTimersByTimeAsync(2000)
    expect(FakeSocket.instances).toHaveLength(1)
    expect(onDanmu).not.toHaveBeenCalled()
  })
})
