<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bookmark, Eye, Heart, MessageCircle, Send, Share2, UserPlus } from 'lucide-vue-next'
import { danmuApi, socialApi, videoApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import type { CommentItem, DanmuItem, VideoDetail, VideoPlay } from '@/types/api'
import { avatarFallback, formatCount, relativeTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const videoEl = ref<HTMLVideoElement>()
const detail = ref<VideoDetail>()
const playInfo = ref<VideoPlay>()
const comments = ref<CommentItem[]>([])
const danmus = ref<DanmuItem[]>([])
const selectedQuality = ref('')
const liked = ref(false)
const collected = ref(false)
const following = ref(false)
const commentText = ref('')
const danmuText = ref('')
const danmuColor = ref('#ffffff')
const loading = ref(true)
const error = ref('')
const actionLoading = ref('')

const videoId = computed(() => Number(route.params.id))
const currentSource = computed(() => playInfo.value?.qualities.find(item => item.quality === selectedQuality.value)?.url || playInfo.value?.qualities[0]?.url || '')

function requireAuth(): boolean {
  if (auth.isLoggedIn) return true
  router.push({ name: 'auth', query: { redirect: route.fullPath } })
  return false
}

async function load() {
  if (!Number.isFinite(videoId.value)) return
  loading.value = true
  error.value = ''
  try {
    const [detailResult, playResult, commentResult, danmuResult] = await Promise.all([
      videoApi.detail(videoId.value), videoApi.play(videoId.value), socialApi.comments(videoId.value), danmuApi.list(videoId.value),
    ])
    detail.value = detailResult.data
    playInfo.value = playResult.data
    selectedQuality.value = playResult.data.defaultQuality || playResult.data.qualities?.[0]?.quality || ''
    comments.value = commentResult.data || []
    danmus.value = danmuResult.data || []
    if (auth.isLoggedIn) {
      const [likeResult, followResult] = await Promise.allSettled([
        socialApi.likeStatus(videoId.value), socialApi.followStatus(detailResult.data.author.id),
      ])
      if (likeResult.status === 'fulfilled') liked.value = likeResult.value.data.liked
      if (followResult.status === 'fulfilled') following.value = followResult.value.data.isFollowing
    }
  } catch (e) { error.value = e instanceof Error ? e.message : '视频加载失败' }
  finally { loading.value = false }
}

async function changeQuality(quality: string) {
  const time = videoEl.value?.currentTime || 0
  const paused = videoEl.value?.paused ?? true
  selectedQuality.value = quality
  await nextTick()
  if (videoEl.value) {
    videoEl.value.currentTime = time
    if (!paused) void videoEl.value.play()
  }
}

async function toggleLike() {
  if (!requireAuth()) return
  actionLoading.value = 'like'
  try {
    const previous = liked.value
    liked.value = !previous
    if (detail.value) detail.value.stats.likeCount += previous ? -1 : 1
    await socialApi.like(videoId.value, previous)
  } finally { actionLoading.value = '' }
}

async function toggleFollow() {
  if (!requireAuth() || !detail.value) return
  actionLoading.value = 'follow'
  try { await socialApi.follow(detail.value.author.id, following.value); following.value = !following.value }
  finally { actionLoading.value = '' }
}

async function toggleCollect() {
  if (!requireAuth()) return
  actionLoading.value = 'collect'
  try { await socialApi.collect(videoId.value, collected.value); collected.value = !collected.value }
  finally { actionLoading.value = '' }
}

async function publishComment() {
  if (!requireAuth() || !commentText.value.trim()) return
  actionLoading.value = 'comment'
  try {
    await socialApi.comment({ videoId: videoId.value, content: commentText.value.trim() })
    commentText.value = ''
    comments.value = (await socialApi.comments(videoId.value)).data || []
  } finally { actionLoading.value = '' }
}

async function sendDanmu() {
  if (!requireAuth() || !danmuText.value.trim()) return
  actionLoading.value = 'danmu'
  try {
    await danmuApi.send({ videoId: videoId.value, content: danmuText.value.trim(), color: danmuColor.value, position: 0, fontSize: 25, videoTime: Math.floor((videoEl.value?.currentTime || 0) * 1000), requestId: crypto.randomUUID() })
    danmus.value.push({ id: Date.now(), userId: auth.user?.id || 0, content: danmuText.value.trim(), color: danmuColor.value, position: 0, fontSize: 25, videoTime: Math.floor((videoEl.value?.currentTime || 0) * 1000), sendTime: new Date().toISOString() })
    danmuText.value = ''
  } finally { actionLoading.value = '' }
}

async function share() {
  const payload = { title: detail.value?.title || '精彩视频', url: location.href }
  if (navigator.share) await navigator.share(payload)
  else await navigator.clipboard.writeText(location.href)
}

watch(() => route.params.id, load, { immediate: true })
</script>

<template>
  <div class="page-shell video-page">
    <div v-if="loading" class="video-loading"><div class="skeleton player-skeleton"></div><div class="skeleton info-skeleton"></div></div>
    <div v-else-if="error" class="video-error"><h2>视频暂时无法播放</h2><p>{{ error }}</p><RouterLink class="primary-button" to="/">返回首页</RouterLink></div>
    <template v-else-if="detail && playInfo">
      <div class="video-layout">
        <section class="video-main">
          <div class="title-block">
            <h1>{{ detail.title }}</h1>
            <p><span><Eye :size="14" />{{ formatCount(detail.stats.viewCount) }} 播放</span><span><MessageCircle :size="14" />{{ formatCount(detail.stats.danmuCount) }} 弹幕</span></p>
          </div>
          <div class="player-wrap">
            <video ref="videoEl" :key="currentSource" controls autoplay :poster="detail.coverUrl" :src="currentSource"></video>
            <div class="danmu-layer" aria-hidden="true"><span v-for="(item, index) in danmus.slice(-8)" :key="item.id" :style="{ color: item.color || '#fff', top: `${12 + (index % 6) * 11}%`, animationDelay: `${index * .7}s` }">{{ item.content }}</span></div>
          </div>
          <div class="player-bar">
            <form @submit.prevent="sendDanmu"><input v-model="danmuColor" type="color" title="弹幕颜色" /><input v-model="danmuText" maxlength="100" placeholder="发一条友善的弹幕吧" /><button :disabled="actionLoading === 'danmu'">发送</button></form>
            <select :value="selectedQuality" @change="changeQuality(($event.target as HTMLSelectElement).value)"><option v-for="item in playInfo.qualities" :key="item.quality" :value="item.quality">{{ item.quality }}</option></select>
          </div>

          <div class="interaction-bar">
            <button :class="{ active: liked }" :disabled="actionLoading === 'like'" @click="toggleLike"><Heart :size="22" :fill="liked ? 'currentColor' : 'none'" /><span>{{ formatCount(detail.stats.likeCount) }}</span></button>
            <button :class="{ active: collected }" :disabled="actionLoading === 'collect'" @click="toggleCollect"><Bookmark :size="22" :fill="collected ? 'currentColor' : 'none'" /><span>{{ collected ? '已收藏' : '收藏' }}</span></button>
            <button @click="share"><Share2 :size="22" /><span>分享</span></button>
          </div>

          <article class="description-card">
            <p>{{ detail.description || '作者暂时没有填写视频简介。' }}</p>
            <div><RouterLink v-for="tag in detail.tags" :key="tag" :to="{ name: 'search', query: { keyword: tag } }"># {{ tag }}</RouterLink></div>
          </article>

          <section class="comment-section">
            <div class="comment-title"><h2>评论 <small>{{ detail.stats.commentCount }}</small></h2><span>热门优先</span></div>
            <form class="comment-form" @submit.prevent="publishComment">
              <img :src="auth.user?.avatar || avatarFallback(auth.user?.nickname || '访客')" alt="" />
              <textarea v-model="commentText" maxlength="1000" :placeholder="auth.isLoggedIn ? '分享你的想法…' : '登录后参与评论'" @focus="!auth.isLoggedIn && requireAuth()"></textarea>
              <button class="primary-button" :disabled="!commentText.trim() || actionLoading === 'comment'"><Send :size="16" />发布</button>
            </form>
            <div v-if="comments.length" class="comment-list">
              <article v-for="comment in comments" :key="comment.id" class="comment-item">
                <img :src="comment.avatar || avatarFallback(comment.nickname)" alt="" />
                <div><header><strong>{{ comment.nickname }}</strong><time>{{ relativeTime(comment.createTime) }}</time></header><p>{{ comment.content }}</p><span class="comment-like"><Heart :size="13" />{{ comment.likeCount }}</span>
                  <div v-if="comment.replies?.length" class="reply-list"><p v-for="reply in comment.replies" :key="reply.id"><strong>{{ reply.nickname }}</strong><template v-if="reply.replyToNickname"> 回复 <b>@{{ reply.replyToNickname }}</b></template>：{{ reply.content }}</p></div>
                </div>
              </article>
            </div>
            <p v-else class="no-comment">还没有评论，来抢沙发吧。</p>
          </section>
        </section>

        <aside class="video-side">
          <div class="author-card">
            <RouterLink :to="`/space/${detail.author.id}`"><img :src="detail.author.avatar || avatarFallback(detail.author.nickname)" alt="" /></RouterLink>
            <div><RouterLink :to="`/space/${detail.author.id}`"><strong>{{ detail.author.nickname }}</strong></RouterLink><p>持续分享有趣又有用的内容</p></div>
            <button :class="{ following }" :disabled="actionLoading === 'follow'" @click="toggleFollow"><UserPlus :size="16" />{{ following ? '已关注' : '关注' }}</button>
          </div>
          <div class="danmu-list-card">
            <header><h3>弹幕列表</h3><span>{{ danmus.length }} 条</span></header>
            <div v-if="danmus.length" class="danmu-list"><div v-for="item in danmus.slice(0, 40)" :key="item.id"><time>{{ Math.floor(item.videoTime / 60000) }}:{{ String(Math.floor(item.videoTime / 1000) % 60).padStart(2, '0') }}</time><span>{{ item.content }}</span></div></div>
            <p v-else>还没有弹幕，发一条试试吧</p>
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.video-page { padding-top: 30px; }.video-layout { display: grid; grid-template-columns: minmax(0,1fr) 320px; gap: 28px; }.title-block h1 { margin: 0 0 9px; font-size: 22px; line-height: 1.4; }.title-block p { display: flex; gap: 18px; margin: 0 0 16px; color: var(--muted); font-size: 12px; }.title-block p span { display: flex; align-items: center; gap: 5px; }
.player-wrap { position: relative; aspect-ratio: 16/9; overflow: hidden; background: #101116; border-radius: 14px 14px 0 0; }.player-wrap video { width: 100%; height: 100%; }.danmu-layer { position: absolute; inset: 0; overflow: hidden; pointer-events: none; }.danmu-layer span { position: absolute; left: 100%; white-space: nowrap; font-weight: 600; text-shadow: 0 1px 3px #000; animation: fly 9s linear infinite; }@keyframes fly { to { transform: translateX(calc(-100vw - 100%)); } }
.player-bar { display: flex; align-items: center; gap: 12px; padding: 10px 12px; background: #fff; border: 1px solid var(--line); border-top: 0; border-radius: 0 0 14px 14px; }.player-bar form { flex: 1; display: flex; height: 36px; overflow: hidden; border-radius: 8px; background: #f2f3f5; }.player-bar input[type=color] { width: 38px; padding: 8px; border: 0; background: transparent; }.player-bar input[type=text], .player-bar input:not([type]) { flex: 1; border: 0; outline: 0; background: transparent; }.player-bar form button { border: 0; padding: 0 16px; color: #fff; background: var(--pink); }.player-bar select { border: 0; color: #5f6571; background: transparent; }
.interaction-bar { display: flex; gap: 30px; padding: 20px 5px 15px; border-bottom: 1px solid var(--line); }.interaction-bar button { display: flex; align-items: center; gap: 7px; border: 0; background: transparent; color: #626875; }.interaction-bar button:hover, .interaction-bar button.active { color: var(--pink); }
.description-card { padding: 20px 0; border-bottom: 1px solid var(--line); }.description-card p { color: #3f4551; font-size: 14px; line-height: 1.8; }.description-card div { display: flex; gap: 8px; flex-wrap: wrap; }.description-card a { padding: 6px 11px; border-radius: 7px; color: #68707e; background: #eceef2; font-size: 12px; }.description-card a:hover { color: var(--pink); }
.video-side { padding-top: 64px; }.author-card, .danmu-list-card { padding: 18px; background: #fff; border: 1px solid var(--line); border-radius: 14px; }.author-card { display: grid; grid-template-columns: 52px 1fr; gap: 12px; align-items: center; }.author-card img { width: 52px; height: 52px; object-fit: cover; border-radius: 50%; }.author-card strong { font-size: 15px; }.author-card p { margin: 4px 0; color: var(--muted); font-size: 11px; }.author-card button { grid-column: 1/-1; height: 38px; display: flex; align-items: center; justify-content: center; gap: 7px; border: 0; border-radius: 9px; color: #fff; background: var(--pink); }.author-card button.following { color: #666d79; background: #eef0f3; }
.danmu-list-card { margin-top: 16px; }.danmu-list-card header { display: flex; justify-content: space-between; align-items: center; }.danmu-list-card h3 { margin: 0; font-size: 15px; }.danmu-list-card header span, .danmu-list-card > p { color: var(--muted); font-size: 11px; }.danmu-list { max-height: 460px; margin-top: 14px; overflow: auto; }.danmu-list div { display: grid; grid-template-columns: 46px 1fr; gap: 7px; padding: 8px 0; font-size: 12px; border-bottom: 1px solid #f1f2f4; }.danmu-list time { color: #a1a5ad; }.danmu-list span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.comment-section { margin-top: 30px; }.comment-title { display: flex; align-items: center; gap: 20px; }.comment-title h2 { font-size: 20px; }.comment-title small { color: var(--muted); font-size: 13px; }.comment-title > span { color: var(--pink); font-size: 12px; }.comment-form { display: grid; grid-template-columns: 46px 1fr auto; gap: 12px; align-items: start; }.comment-form img, .comment-item > img { width: 46px; height: 46px; object-fit: cover; border-radius: 50%; }.comment-form textarea { min-height: 72px; padding: 12px; resize: none; border: 1px solid var(--line); border-radius: 10px; outline: none; background: #fff; }.comment-list { margin-top: 28px; }.comment-item { display: grid; grid-template-columns: 46px 1fr; gap: 13px; padding: 18px 0; border-bottom: 1px solid var(--line); }.comment-item header { display: flex; gap: 10px; align-items: center; }.comment-item header time { color: var(--muted); font-size: 11px; }.comment-item p { margin: 8px 0; font-size: 14px; }.comment-like { display: flex; align-items: center; gap: 4px; color: var(--muted); font-size: 11px; }.reply-list { padding: 8px 12px; border-radius: 8px; background: #f0f2f5; }.reply-list p { margin: 4px 0; font-size: 12px; }.reply-list b { color: var(--pink); }.no-comment { text-align: center; padding: 30px; color: var(--muted); }
.video-loading { padding-top: 30px; }.player-skeleton { height: min(650px,55vw); }.info-skeleton { height: 80px; margin-top: 15px; }.video-error { min-height: 60vh; display: grid; place-items: center; align-content: center; gap: 10px; }.video-error h2,.video-error p { margin: 0; }.video-error p { color: var(--muted); }
@media (max-width: 1000px) { .video-layout { grid-template-columns: 1fr; }.video-side { padding-top: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 15px; }.danmu-list-card { margin: 0; } }
@media (max-width: 600px) { .video-page { width: 100%; padding-top: 0; }.title-block { padding: 15px 12px 0; }.player-wrap { border-radius: 0; }.player-bar { border-radius: 0; }.video-main > :not(.title-block):not(.player-wrap):not(.player-bar) { margin-left: 12px; margin-right: 12px; }.video-side { padding: 0 12px; grid-template-columns: 1fr; }.comment-form { grid-template-columns: 36px 1fr; }.comment-form img { width: 36px; height: 36px; }.comment-form button { grid-column: 2; justify-self: end; } }
</style>
