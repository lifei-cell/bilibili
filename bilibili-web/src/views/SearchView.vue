<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search, SlidersHorizontal, ThumbsUp, Play } from 'lucide-vue-next'
import { searchApi } from '@/api'
import EmptyState from '@/components/EmptyState.vue'
import type { SearchVideo } from '@/types/api'
import { categories, formatCount, relativeTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const videos = ref<SearchVideo[]>([])
const keyword = ref(String(route.query.keyword || ''))
const sort = ref(String(route.query.sort || 'default'))
const categoryId = ref<number | undefined>(route.query.categoryId ? Number(route.query.categoryId) : undefined)
const page = ref(1)
const total = ref(0)
const loading = ref(false)
const error = ref('')
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / 12)))

async function search() {
  loading.value = true
  error.value = ''
  try {
    const result = await searchApi.search({ keyword: keyword.value.trim(), categoryId: categoryId.value, sort: sort.value, page: page.value, size: 12 })
    videos.value = result.data || []
    total.value = result.total || 0
  } catch (e) {
    error.value = e instanceof Error ? e.message : '搜索失败'
  } finally { loading.value = false }
}

function commitSearch() {
  page.value = 1
  router.replace({ query: { keyword: keyword.value || undefined, sort: sort.value !== 'default' ? sort.value : undefined, categoryId: categoryId.value } })
}

watch(() => route.query, () => {
  keyword.value = String(route.query.keyword || '')
  sort.value = String(route.query.sort || 'default')
  categoryId.value = route.query.categoryId ? Number(route.query.categoryId) : undefined
  search()
}, { immediate: true })
watch(page, search)
</script>

<template>
  <div class="page-shell search-page">
    <section class="search-hero">
      <span>EXPLORE</span>
      <h1>找到你的下一份灵感</h1>
      <form @submit.prevent="commitSearch">
        <Search :size="21" />
        <input v-model="keyword" placeholder="输入视频标题、标签或创作者" autofocus />
        <button class="primary-button">搜索</button>
      </form>
    </section>

    <div class="search-layout">
      <aside class="filters">
        <h3><SlidersHorizontal :size="17" />筛选</h3>
        <div class="filter-group">
          <label>内容分区</label>
          <button :class="{ active: categoryId === undefined }" @click="categoryId = undefined; commitSearch()">全部分区</button>
          <button v-for="item in categories" :key="item.id" :class="{ active: categoryId === item.id }" @click="categoryId = item.id; commitSearch()">{{ item.name }}</button>
        </div>
      </aside>

      <section class="results">
        <div class="results-head">
          <div><h2>{{ keyword ? `“${keyword}” 的搜索结果` : '发现全部内容' }}</h2><p>共找到 {{ total }} 个相关视频</p></div>
          <select v-model="sort" @change="commitSearch"><option value="default">综合排序</option><option value="hot">最多播放</option><option value="new">最新发布</option></select>
        </div>
        <div v-if="error" class="error-banner">{{ error }}</div>
        <div v-if="loading" class="result-grid"><div v-for="i in 6" :key="i" class="skeleton"></div></div>
        <div v-else-if="videos.length" class="result-grid">
          <RouterLink v-for="(video, index) in videos" :key="video.id" class="result-card" :to="`/video/${video.id}`">
            <div class="result-card__visual" :class="`tone-${index % 4}`">
              <span>{{ categories.find(item => item.id === video.categoryId)?.name || '视频' }}</span>
              <div class="result-card__play"><Play :size="18" fill="currentColor" /></div>
            </div>
            <div class="result-card__body">
              <h3>{{ video.title }}</h3>
              <p>{{ video.description || '这个视频暂时没有简介' }}</p>
              <div class="tag-row"><span v-for="tag in video.tags?.slice(0, 3)" :key="tag"># {{ tag }}</span></div>
              <footer><span><Play :size="14" />{{ formatCount(video.viewCount) }}</span><span><ThumbsUp :size="14" />{{ formatCount(video.likeCount) }}</span><time>{{ relativeTime(video.createTime) }}</time></footer>
            </div>
          </RouterLink>
        </div>
        <EmptyState v-else title="没有找到相关视频" description="试试更短的关键词，或者换一个分区" />
        <div v-if="pageCount > 1" class="pagination">
          <button :disabled="page <= 1" @click="page--">上一页</button><span>{{ page }} / {{ pageCount }}</span><button :disabled="page >= pageCount" @click="page++">下一页</button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.search-page { padding-top: 34px; }
.search-hero { border-radius: 22px; padding: 40px; text-align: center; background: radial-gradient(circle at 20% 20%, rgba(255,195,208,.65), transparent 35%), linear-gradient(135deg, #fff4f6, #f2f5fb); }
.search-hero > span { color: var(--pink); font-size: 11px; font-weight: 700; letter-spacing: .18em; }
.search-hero h1 { margin: 8px 0 24px; font-size: 32px; }
.search-hero form { width: min(650px, 100%); height: 54px; margin: auto; padding: 5px 6px 5px 18px; display: flex; align-items: center; gap: 10px; border-radius: 14px; background: #fff; box-shadow: var(--shadow); }
.search-hero input { flex: 1; min-width: 0; border: 0; outline: 0; }
.search-hero form > svg { color: #9aa0ab; }
.search-layout { display: grid; grid-template-columns: 180px 1fr; gap: 35px; margin-top: 38px; }
.filters { border-right: 1px solid var(--line); padding-right: 20px; }
.filters h3 { display: flex; align-items: center; gap: 8px; margin: 0 0 22px; font-size: 15px; }
.filter-group { display: flex; flex-direction: column; align-items: stretch; gap: 3px; }
.filter-group label { color: #9a9fab; margin-bottom: 6px; font-size: 11px; letter-spacing: .1em; }
.filter-group button { border: 0; background: transparent; text-align: left; padding: 9px 11px; color: #646a76; border-radius: 8px; font-size: 13px; }
.filter-group button:hover, .filter-group button.active { color: var(--pink); background: var(--pink-soft); }
.results-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 22px; }
.results-head h2 { margin: 0 0 5px; font-size: 21px; }
.results-head p { margin: 0; color: var(--muted); font-size: 12px; }
.results-head select { border: 1px solid var(--line); background: #fff; border-radius: 9px; padding: 9px 12px; color: #555b67; }
.result-grid { display: grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap: 18px; }
.result-card { overflow: hidden; background: #fff; border: 1px solid #eceef2; border-radius: 14px; transition: .25s; }
.result-card:hover { transform: translateY(-4px); box-shadow: var(--shadow); }
.result-card__visual { height: 120px; padding: 14px; position: relative; display: flex; align-items: flex-end; overflow: hidden; color: rgba(255,255,255,.85); background: linear-gradient(135deg,#a9b7d0,#52607a); }
.result-card__visual::after { content: ''; position: absolute; width: 140px; height: 140px; right: -32px; top: -52px; border-radius: 45% 55% 55% 45%; background: rgba(255,255,255,.16); transform: rotate(20deg); }
.result-card__visual.tone-1 { background: linear-gradient(135deg,#d79eaa,#824f61); }.result-card__visual.tone-2 { background: linear-gradient(135deg,#9ac5b7,#3e7567); }.result-card__visual.tone-3 { background: linear-gradient(135deg,#d8bc8f,#846a45); }
.result-card__visual > span { font-size: 12px; }
.result-card__play { position: absolute; inset: 50% auto auto 50%; width: 42px; height: 42px; display: grid; place-items: center; border-radius: 50%; background: rgba(255,255,255,.25); transform: translate(-50%,-50%); }
.result-card__body { padding: 15px; }
.result-card h3 { margin: 0 0 8px; min-height: 42px; font-size: 15px; line-height: 1.4; }
.result-card__body > p { height: 36px; margin: 0; overflow: hidden; color: var(--muted); font-size: 12px; line-height: 1.5; }
.tag-row { display: flex; gap: 7px; height: 26px; margin-top: 8px; overflow: hidden; color: var(--pink); font-size: 10px; }
.result-card footer { display: flex; gap: 12px; padding-top: 10px; border-top: 1px solid #f0f1f4; color: #8b909b; font-size: 11px; }
.result-card footer span { display: flex; align-items: center; gap: 4px; }.result-card footer time { margin-left: auto; }
.pagination { display: flex; justify-content: center; align-items: center; gap: 14px; margin-top: 30px; }.pagination button { border: 1px solid var(--line); background: #fff; border-radius: 8px; padding: 8px 13px; }.pagination button:disabled { opacity: .45; }
@media (max-width: 900px) { .search-layout { grid-template-columns: 1fr; } .filters { border: 0; padding: 0; } .filter-group { flex-direction: row; overflow-x: auto; } .filter-group button { white-space: nowrap; } .result-grid { grid-template-columns: repeat(2,1fr); } }
@media (max-width: 600px) { .search-hero { padding: 28px 16px; border-radius: 0; margin-inline: -12px; } .search-hero h1 { font-size: 25px; }.result-grid { grid-template-columns: 1fr; }.results-head { align-items: flex-end; gap: 10px; } }
</style>
