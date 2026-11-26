<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { BarChart3, RefreshCw } from 'lucide-vue-next'
import { creatorApi } from '@/api'
import type { CreatorAnalytics } from '@/types/api'
import { formatCount } from '@/utils/format'

function localDate(offsetDays = 0) {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const startDate = ref(localDate(-6))
const endDate = ref(localDate())
const analytics = ref<CreatorAnalytics | null>(null)
const loading = ref(false)
const rebuilding = ref(false)
const error = ref('')

const chartPoints = computed(() => {
  const trend = analytics.value?.trend || []
  if (!trend.length) return ''
  const maximum = Math.max(1, ...trend.map(item => item.viewCount))
  return trend.map((item, index) => {
    const x = trend.length === 1 ? 50 : (index / (trend.length - 1)) * 100
    const y = 94 - (item.viewCount / maximum) * 82
    return `${x},${y}`
  }).join(' ')
})

const metricCards = computed(() => {
  const totals = analytics.value?.totals
  return [
    ['播放', totals?.viewCount || 0],
    ['点赞', totals?.likeCount || 0],
    ['收藏', totals?.collectCount || 0],
    ['评论', totals?.commentCount || 0],
    ['弹幕', totals?.danmuCount || 0],
  ] as const
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    analytics.value = (await creatorApi.analytics({ startDate: startDate.value, endDate: endDate.value })).data
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '数据加载失败'
  } finally {
    loading.value = false
  }
}

async function rebuild() {
  rebuilding.value = true
  error.value = ''
  try {
    analytics.value = (await creatorApi.rebuildAnalytics({ startDate: startDate.value, endDate: endDate.value })).data
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '重新聚合失败'
  } finally {
    rebuilding.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page-shell analytics-page">
    <header class="analytics-hero">
      <div>
        <span class="eyebrow"><BarChart3 :size="16" /> Creator Analytics</span>
        <h1>创作者数据中心</h1>
        <p>查看作品趋势和 Top 视频。数据按自然日确定性聚合，迟到事件会自动重算。</p>
      </div>
      <div class="date-controls">
        <label>开始日期<input v-model="startDate" type="date" :max="endDate" /></label>
        <label>结束日期<input v-model="endDate" type="date" :min="startDate" :max="localDate()" /></label>
        <button class="soft-button" :disabled="loading" @click="load">查询</button>
        <button class="primary-button" :disabled="rebuilding" @click="rebuild">
          <RefreshCw :size="16" :class="{ spinning: rebuilding }" />重新聚合
        </button>
      </div>
    </header>

    <p v-if="error" class="error-banner">{{ error }}</p>
    <div v-if="loading && !analytics" class="analytics-loading">正在汇总创作数据…</div>

    <template v-else-if="analytics">
      <section class="metric-grid" aria-label="数据概览">
        <article v-for="([label, value]) in metricCards" :key="label">
          <span>{{ label }}</span><strong>{{ formatCount(value) }}</strong>
        </article>
      </section>

      <section class="analytics-panel trend-panel">
        <div class="panel-heading">
          <div><h2>播放趋势</h2><p>{{ analytics.startDate }} 至 {{ analytics.endDate }}</p></div>
          <span>最近聚合：{{ analytics.lastAggregatedAt?.replace('T', ' ') || '暂无数据' }}</span>
        </div>
        <svg class="trend-chart" viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="每日播放趋势图">
          <line x1="0" y1="94" x2="100" y2="94" />
          <polyline v-if="chartPoints" :points="chartPoints" />
        </svg>
        <div class="trend-labels">
          <span v-for="day in analytics.trend" :key="day.date">{{ day.date.slice(5) }}<b>{{ day.viewCount }}</b></span>
        </div>
      </section>

      <section class="analytics-panel">
        <div class="panel-heading"><div><h2>Top 视频</h2><p>按区间播放量与互动量排序</p></div></div>
        <div v-if="analytics.topVideos.length" class="top-table">
          <div v-for="(video, index) in analytics.topVideos" :key="video.videoId" class="top-row">
            <b class="rank">{{ index + 1 }}</b>
            <RouterLink :to="`/video/${video.videoId}`" class="video-title">{{ video.title }}</RouterLink>
            <span><b>{{ formatCount(video.viewCount) }}</b> 播放</span>
            <span>{{ formatCount(video.likeCount + video.collectCount + video.commentCount + video.danmuCount) }} 互动</span>
          </div>
        </div>
        <p v-else class="empty-analytics">当前区间还没有创作数据</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.analytics-page { padding-top: 42px; }
.analytics-hero { display: flex; align-items: flex-end; justify-content: space-between; gap: 30px; padding: 28px; border-radius: 22px; background: linear-gradient(135deg, #1f2330, #34394c); color: #fff; }
.analytics-hero h1 { margin: 10px 0 8px; font-size: 32px; }
.analytics-hero p { margin: 0; color: #c9cdd8; font-size: 14px; }
.eyebrow { display: flex; align-items: center; gap: 7px; color: #ff9eb2; font-size: 12px; font-weight: 700; letter-spacing: .08em; text-transform: uppercase; }
.date-controls { display: flex; align-items: flex-end; gap: 10px; flex-wrap: wrap; }
.date-controls label { display: grid; gap: 5px; color: #c9cdd8; font-size: 11px; }
.date-controls input { border: 1px solid #555b70; border-radius: 9px; padding: 9px; color: #fff; background: #292e3d; }
.date-controls button { padding: 10px 15px; }
.metric-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 14px; margin: 22px 0; }
.metric-grid article { padding: 22px; border: 1px solid var(--line); border-radius: 16px; background: #fff; }
.metric-grid span { color: var(--muted); font-size: 13px; }
.metric-grid strong { display: block; margin-top: 8px; font-size: 27px; }
.analytics-panel { margin-top: 18px; padding: 24px; border: 1px solid var(--line); border-radius: 18px; background: #fff; }
.panel-heading { display: flex; justify-content: space-between; align-items: flex-start; }
.panel-heading h2 { margin: 0; font-size: 20px; }
.panel-heading p, .panel-heading > span { margin: 5px 0 0; color: var(--muted); font-size: 12px; }
.trend-chart { width: 100%; height: 210px; margin-top: 20px; overflow: visible; }
.trend-chart line { stroke: #e7e9ef; stroke-width: .8; }
.trend-chart polyline { fill: none; stroke: var(--pink); stroke-width: 2.2; vector-effect: non-scaling-stroke; stroke-linejoin: round; stroke-linecap: round; }
.trend-labels { display: flex; justify-content: space-between; gap: 8px; color: var(--muted); font-size: 11px; }
.trend-labels span { display: grid; text-align: center; }
.trend-labels b { color: var(--text); font-size: 12px; }
.top-table { margin-top: 14px; }
.top-row { display: grid; grid-template-columns: 40px minmax(180px, 1fr) 120px 120px; align-items: center; min-height: 58px; border-top: 1px solid var(--line); font-size: 13px; color: var(--muted); }
.rank { color: var(--pink); font-size: 18px; }
.video-title { color: var(--text); font-weight: 600; }
.top-row span b { color: var(--text); }
.analytics-loading, .empty-analytics { padding: 70px; text-align: center; color: var(--muted); }
.spinning { animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 900px) {
  .analytics-hero { align-items: flex-start; flex-direction: column; }
  .metric-grid { grid-template-columns: repeat(2, 1fr); }
  .top-row { grid-template-columns: 32px 1fr; gap: 6px; padding: 10px 0; }
  .top-row span { grid-column: 2; }
}
</style>
