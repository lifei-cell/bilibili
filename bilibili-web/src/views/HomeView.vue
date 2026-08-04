<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ArrowRight, Flame, Play, Sparkles } from 'lucide-vue-next'
import { searchApi, videoApi } from '@/api'
import VideoCard from '@/components/VideoCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import type { VideoListItem } from '@/types/api'
import { categories, formatCount } from '@/utils/format'

const videos = ref<VideoListItem[]>([])
const hotWords = ref<string[]>([])
const selectedCategory = ref<number | undefined>()
const sort = ref('default')
const loading = ref(true)
const error = ref('')
const featured = computed(() => videos.value[0])

async function loadVideos() {
  loading.value = true
  error.value = ''
  try {
    const result = await videoApi.list({ page: 1, size: 20, categoryId: selectedCategory.value, sort: sort.value })
    videos.value = result.data || []
  } catch (e) {
    error.value = e instanceof Error ? e.message : '视频加载失败'
  } finally {
    loading.value = false
  }
}

watch([selectedCategory, sort], loadVideos)
onMounted(async () => {
  loadVideos()
  try { hotWords.value = (await searchApi.hot(8)).data || [] } catch { hotWords.value = ['微服务', 'Java', '摄影', '音乐'] }
})
</script>

<template>
  <div class="home-view">
    <section class="hero">
      <div class="page-shell hero__inner">
        <div class="hero__copy">
          <span class="eyebrow"><Sparkles :size="15" /> 今日精选</span>
          <h1>好奇心，<br /><em>从这里开始。</em></h1>
          <p>发现有趣的创作，和志同道合的人一起分享每一份热爱。</p>
          <div class="hero__actions">
            <RouterLink v-if="featured" class="primary-button" :to="`/video/${featured.id}`"><Play :size="18" fill="currentColor" />立即观看</RouterLink>
            <a class="ghost-button" href="#recommend">浏览推荐<ArrowRight :size="17" /></a>
          </div>
          <div v-if="hotWords.length" class="hot-words">
            <Flame :size="16" /><span>大家都在搜</span>
            <RouterLink v-for="word in hotWords.slice(0, 4)" :key="word" :to="{ name: 'search', query: { keyword: word } }">{{ word }}</RouterLink>
          </div>
        </div>
        <RouterLink v-if="featured" class="hero-card" :to="`/video/${featured.id}`">
          <img :src="featured.coverUrl" :alt="featured.title" />
          <div class="hero-card__shade"></div>
          <div class="hero-card__play"><Play :size="25" fill="currentColor" /></div>
          <div class="hero-card__caption">
            <span>EDITOR'S PICK</span>
            <h2>{{ featured.title }}</h2>
            <p>{{ featured.authorName }} · {{ formatCount(featured.viewCount) }} 次观看</p>
          </div>
        </RouterLink>
        <div v-else class="hero-card hero-card--placeholder"></div>
      </div>
    </section>

    <div class="page-shell">
      <section id="categories" class="category-strip">
        <button :class="{ active: selectedCategory === undefined }" @click="selectedCategory = undefined">全部</button>
        <button v-for="item in categories" :key="item.id" :class="{ active: selectedCategory === item.id }" @click="selectedCategory = item.id">{{ item.name }}</button>
      </section>

      <section id="recommend">
        <div class="section-heading">
          <div><h2>为你推荐</h2><p>每天发现一点新鲜灵感</p></div>
          <div class="sort-switch">
            <button :class="{ active: sort === 'default' }" @click="sort = 'default'">综合</button>
            <button :class="{ active: sort === 'hot' }" @click="sort = 'hot'">热门</button>
            <button :class="{ active: sort === 'new' }" @click="sort = 'new'">最新</button>
          </div>
        </div>
        <div v-if="error" class="error-banner">{{ error }} <button class="text-button" @click="loadVideos">重新加载</button></div>
        <div v-if="loading" class="loading-grid"><div v-for="i in 10" :key="i" class="skeleton"></div></div>
        <div v-else-if="videos.length" class="video-grid"><VideoCard v-for="video in videos" :key="video.id" :video="video" /></div>
        <EmptyState v-else title="这个频道暂时还没有视频" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.hero { background: radial-gradient(circle at 15% 15%, rgba(255,219,227,.75), transparent 34%), linear-gradient(180deg, #fff 0%, #f7f8fa 100%); padding: 55px 0 58px; overflow: hidden; }
.hero__inner { min-height: 420px; display: grid; grid-template-columns: .9fr 1.1fr; align-items: center; gap: 70px; }
.hero__copy { padding-left: 4%; }
.eyebrow { display: inline-flex; align-items: center; gap: 7px; color: var(--pink); font-size: 13px; font-weight: 700; letter-spacing: .08em; }
.hero h1 { margin: 17px 0 18px; font-size: clamp(44px, 5.1vw, 74px); line-height: 1.06; letter-spacing: -.055em; }
.hero h1 em { color: var(--pink); font-style: normal; }
.hero__copy > p { width: 85%; color: #676d79; line-height: 1.8; }
.hero__actions { display: flex; gap: 12px; margin-top: 28px; }
.hot-words { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-top: 35px; color: var(--pink); font-size: 12px; }
.hot-words > span { color: #8b909b; }
.hot-words a { color: #525864; background: rgba(255,255,255,.75); border: 1px solid #eceef2; padding: 5px 9px; border-radius: 7px; }
.hot-words a:hover { color: var(--pink); }
.hero-card { position: relative; overflow: hidden; height: 390px; border-radius: 26px; background: #e5e7eb; box-shadow: 0 30px 70px rgba(34,40,56,.18); transform: rotate(1.1deg); transition: .35s; }
.hero-card:hover { transform: rotate(0) translateY(-4px); }
.hero-card img { width: 100%; height: 100%; object-fit: cover; }
.hero-card__shade { position: absolute; inset: 0; background: linear-gradient(180deg, transparent 40%, rgba(13,16,23,.9)); }
.hero-card__play { position: absolute; inset: 50% auto auto 50%; width: 64px; height: 64px; display: grid; place-items: center; border-radius: 50%; color: #fff; background: rgba(255,255,255,.2); border: 1px solid rgba(255,255,255,.45); backdrop-filter: blur(8px); transform: translate(-50%,-50%); }
.hero-card__caption { position: absolute; left: 28px; right: 28px; bottom: 24px; color: #fff; }
.hero-card__caption span { color: #ffb3c1; font-size: 11px; font-weight: 700; letter-spacing: .14em; }
.hero-card__caption h2 { margin: 6px 0; font-size: 23px; }
.hero-card__caption p { margin: 0; color: rgba(255,255,255,.72); font-size: 12px; }
.hero-card--placeholder { background: linear-gradient(135deg, #f2dbe1, #dfe2e9); }
.category-strip { display: flex; gap: 9px; flex-wrap: wrap; padding: 27px 0 4px; border-bottom: 1px solid var(--line); }
.category-strip button, .sort-switch button { border: 0; background: transparent; color: #636976; padding: 8px 14px; border-radius: 8px; font-size: 13px; }
.category-strip button:hover, .category-strip button.active, .sort-switch button.active { color: var(--pink); background: var(--pink-soft); }
.sort-switch { display: flex; background: #eff1f4; padding: 3px; border-radius: 10px; }
.sort-switch button.active { background: #fff; box-shadow: 0 2px 7px rgba(0,0,0,.06); }
@media (max-width: 900px) { .hero__inner { grid-template-columns: 1fr; gap: 35px; } .hero__copy { padding: 0; } .hero-card { height: 330px; } }
@media (max-width: 600px) { .hero { padding-top: 40px; } .hero-card { height: 240px; border-radius: 18px; } .hero__copy > p { width: 100%; } .category-strip { flex-wrap: nowrap; overflow-x: auto; } .category-strip button { white-space: nowrap; } }
</style>
