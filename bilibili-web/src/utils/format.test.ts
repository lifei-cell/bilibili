import { afterEach, describe, expect, it, vi } from 'vitest'
import { avatarFallback, formatCount, formatDuration, relativeTime, replaceCategories, categories } from './format'

describe('format utilities', () => {
  afterEach(() => vi.useRealTimers())

  it('formats counts and durations at display boundaries', () => {
    expect(formatCount(9999)).toBe('9999')
    expect(formatCount(10000)).toBe('1.0万')
    expect(formatCount(100000)).toBe('10万')
    expect(formatDuration(0)).toBe('0:00')
    expect(formatDuration(125)).toBe('2:05')
  })

  it('formats relative dates deterministically', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-25T12:00:00+08:00'))
    expect(relativeTime('')).toBe('')
    expect(relativeTime('2026-08-25T01:00:00+08:00')).toBe('今天')
    expect(relativeTime('2026-08-20T12:00:00+08:00')).toBe('5天前')
    expect(relativeTime('2026-06-01T12:00:00+08:00')).toBe('2026-06-01')
  })

  it('encodes avatar fallback and replaces non-empty category lists', () => {
    expect(avatarFallback('张 三')).toContain('seed=%E5%BC%A0')
    const before = [...categories]
    replaceCategories([])
    expect(categories).toEqual(before)
    replaceCategories([{ id: 99, name: '测试', parentId: 0, sort: 1 }])
    expect(categories).toHaveLength(1)
    expect(categories[0]?.id).toBe(99)
  })
})
