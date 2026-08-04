export const categories = [
  { id: 1, name: '动画' }, { id: 2, name: '番剧' }, { id: 3, name: '国创' },
  { id: 4, name: '音乐' }, { id: 5, name: '舞蹈' }, { id: 6, name: '游戏' },
  { id: 7, name: '知识' }, { id: 8, name: '科技' }, { id: 9, name: '运动' },
  { id: 10, name: '生活' }, { id: 11, name: '美食' }, { id: 12, name: '影视' },
]

export function formatCount(value = 0): string {
  if (value >= 10000) return `${(value / 10000).toFixed(value >= 100000 ? 0 : 1)}万`
  return String(value)
}

export function formatDuration(seconds = 0): string {
  const minute = Math.floor(seconds / 60)
  const second = Math.floor(seconds % 60)
  return `${minute}:${String(second).padStart(2, '0')}`
}

export function relativeTime(value: string): string {
  if (!value) return ''
  const date = new Date(value)
  const diff = Date.now() - date.getTime()
  const day = Math.floor(diff / 86400000)
  if (day <= 0) return '今天'
  if (day < 30) return `${day}天前`
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function avatarFallback(name = 'B'): string {
  const value = encodeURIComponent(name.slice(0, 1).toUpperCase())
  return `https://api.dicebear.com/9.x/initials/svg?seed=${value}&backgroundColor=ffd5dc&fontFamily=Arial`
}
