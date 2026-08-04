<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Edit3, LogOut, Settings, UserPlus, X } from 'lucide-vue-next'
import { socialApi, userApi, videoApi } from '@/api'
import VideoCard from '@/components/VideoCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useAuthStore } from '@/stores/auth'
import type { CurrentUser, UserProfile, VideoListItem } from '@/types/api'
import { avatarFallback, formatCount } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const profile = ref<UserProfile>()
const videos = ref<VideoListItem[]>([])
const current = ref<CurrentUser>()
const loading = ref(true)
const error = ref('')
const activeTab = ref('videos')
const editOpen = ref(false)
const saving = ref(false)
const editForm = ref({ nickname: '', avatar: '', gender: 0, birthday: '', signature: '' })
const profileId = computed(() => Number(route.params.id || auth.user?.id))
const isOwn = computed(() => Boolean(auth.user?.id && profileId.value === auth.user.id))

async function load() {
  if (!profileId.value) { router.replace('/auth?redirect=/space'); return }
  loading.value = true
  error.value = ''
  try {
    const [profileResult, videoResult] = await Promise.all([userApi.profile(profileId.value), videoApi.userList(profileId.value, { page: 1, size: 20, sort: 'new' })])
    profile.value = profileResult.data
    videos.value = videoResult.data || []
    if (isOwn.value) {
      current.value = (await userApi.me()).data
      editForm.value = { nickname: current.value.nickname || '', avatar: current.value.avatar || '', gender: current.value.gender || 0, birthday: current.value.birthday || '', signature: current.value.signature || '' }
    }
  } catch (e) { error.value = e instanceof Error ? e.message : '个人空间加载失败' }
  finally { loading.value = false }
}

async function toggleFollow() {
  if (!auth.isLoggedIn) { router.push({ name: 'auth', query: { redirect: route.fullPath } }); return }
  if (!profile.value) return
  await socialApi.follow(profile.value.id, profile.value.isFollowing)
  profile.value.isFollowing = !profile.value.isFollowing
  profile.value.followerCount += profile.value.isFollowing ? 1 : -1
}

async function saveProfile() {
  saving.value = true
  try {
    await userApi.updateProfile(editForm.value)
    editOpen.value = false
    await auth.hydrate()
    await load()
  } finally { saving.value = false }
}

async function logout() { await auth.logout(); router.push('/') }
watch(profileId, load, { immediate: true })
</script>

<template>
  <div v-if="loading" class="page-shell profile-loading"><div class="skeleton banner-skeleton"></div><div class="loading-grid"><div v-for="i in 5" :key="i" class="skeleton"></div></div></div>
  <div v-else-if="error" class="page-shell"><div class="error-banner">{{ error }}</div></div>
  <div v-else-if="profile" class="profile-page">
    <section class="profile-banner"><div class="banner-shape shape-one"></div><div class="banner-shape shape-two"></div><span>BILICLOUD CREATOR</span></section>
    <div class="page-shell profile-shell">
      <section class="profile-card">
        <img class="profile-avatar" :src="profile.avatar || avatarFallback(profile.nickname || profile.username)" alt="头像" />
        <div class="profile-info"><div><h1>{{ profile.nickname || profile.username }}</h1><span v-if="isOwn" class="role-badge">{{ auth.user?.role || 'USER' }}</span></div><p>@{{ profile.username }} · {{ profile.signature || '这个人很神秘，什么都没有写。' }}</p></div>
        <div class="profile-actions">
          <button v-if="isOwn" class="ghost-button" @click="editOpen = true"><Edit3 :size="16" />编辑资料</button>
          <button v-if="isOwn" class="soft-button" title="退出登录" @click="logout"><LogOut :size="16" /></button>
          <button v-else class="primary-button" @click="toggleFollow"><UserPlus :size="16" />{{ profile.isFollowing ? '已关注' : '关注' }}</button>
        </div>
      </section>
      <section class="profile-stats"><div><strong>{{ formatCount(profile.videoCount) }}</strong><span>投稿</span></div><div><strong>{{ formatCount(profile.followerCount) }}</strong><span>粉丝</span></div><div><strong>{{ formatCount(profile.followingCount) }}</strong><span>关注</span></div></section>
      <nav class="profile-tabs"><button :class="{ active: activeTab === 'videos' }" @click="activeTab = 'videos'">TA 的投稿</button><button :class="{ active: activeTab === 'about' }" @click="activeTab = 'about'">关于</button></nav>
      <section v-if="activeTab === 'videos'">
        <div class="section-heading"><div><h2>{{ isOwn ? '我的投稿' : '全部投稿' }}</h2><p>{{ videos.length }} 个公开作品</p></div><RouterLink v-if="isOwn" class="primary-button" to="/upload">发布新作品</RouterLink></div>
        <div v-if="videos.length" class="video-grid"><VideoCard v-for="video in videos" :key="video.id" :video="video" /></div><EmptyState v-else title="还没有发布作品" :description="isOwn ? '去分享你的第一支视频吧' : '这位创作者还没有公开投稿'" />
      </section>
      <section v-else class="about-card"><h2>个人介绍</h2><p>{{ profile.signature || '这位用户暂时没有填写个人介绍。' }}</p><dl><div><dt>用户名</dt><dd>{{ profile.username }}</dd></div><div><dt>用户 ID</dt><dd>{{ profile.id }}</dd></div><div><dt>创作数量</dt><dd>{{ profile.videoCount }} 个视频</dd></div></dl></section>
    </div>

    <div v-if="editOpen" class="modal-backdrop" @click.self="editOpen = false"><form class="edit-modal" @submit.prevent="saveProfile"><header><div><h2>编辑个人资料</h2><p>让大家更好地认识你</p></div><button type="button" @click="editOpen = false"><X /></button></header><div class="field"><label>昵称</label><input v-model="editForm.nickname" maxlength="50" /></div><div class="field"><label>头像地址</label><input v-model="editForm.avatar" maxlength="500" type="url" /></div><div class="form-row"><div class="field"><label>性别</label><select v-model="editForm.gender"><option :value="0">保密</option><option :value="1">男</option><option :value="2">女</option></select></div><div class="field"><label>生日</label><input v-model="editForm.birthday" type="date" /></div></div><div class="field"><label>个性签名</label><textarea v-model="editForm.signature" maxlength="200"></textarea></div><footer><button type="button" class="ghost-button" @click="editOpen = false">取消</button><button class="primary-button" :disabled="saving"><Settings :size="16" />{{ saving ? '保存中…' : '保存修改' }}</button></footer></form></div>
  </div>
</template>

<style scoped>
.profile-banner { position: relative; height: 260px; overflow: hidden; display: flex; justify-content: center; align-items: center; background: linear-gradient(125deg,#283445,#5b4660 58%,#b7707e); }.profile-banner > span { color: rgba(255,255,255,.12); font-size: clamp(40px,8vw,110px); font-weight: 800; letter-spacing: .06em; }.banner-shape { position: absolute; border: 1px solid rgba(255,255,255,.12); border-radius: 50%; }.shape-one { width: 420px; height: 420px; left: -90px; bottom: -310px; box-shadow: 0 0 0 60px rgba(255,255,255,.025); }.shape-two { width: 280px; height: 280px; right: -40px; top: -180px; box-shadow: 0 0 0 45px rgba(255,255,255,.025); }.profile-shell { position: relative; margin-top: -54px; }.profile-card { min-height: 140px; padding: 25px 28px 20px 160px; display: flex; align-items: center; gap: 20px; border-radius: 18px; background: #fff; box-shadow: var(--shadow); }.profile-avatar { position: absolute; top: 18px; left: 32px; width: 106px; height: 106px; object-fit: cover; border: 5px solid #fff; border-radius: 50%; box-shadow: 0 4px 18px rgba(0,0,0,.16); }.profile-info { flex: 1; }.profile-info > div { display: flex; align-items: center; gap: 10px; }.profile-info h1 { margin: 0; font-size: 25px; }.profile-info p { margin: 9px 0 0; color: var(--muted); font-size: 13px; }.role-badge { padding: 3px 7px; border-radius: 5px; color: var(--pink); background: var(--pink-soft); font-size: 9px; font-weight: 700; }.profile-actions { display: flex; gap: 8px; }.profile-stats { display: flex; gap: 45px; padding: 24px 30px 15px; }.profile-stats div { display: flex; flex-direction: column; gap: 3px; }.profile-stats strong { font-size: 18px; }.profile-stats span { color: var(--muted); font-size: 11px; }.profile-tabs { display: flex; gap: 32px; border-bottom: 1px solid var(--line); }.profile-tabs button { position: relative; border: 0; padding: 13px 2px; background: none; color: #757b87; }.profile-tabs button.active { color: var(--pink); font-weight: 600; }.profile-tabs button.active::after { content: ''; position: absolute; left: 0; right: 0; bottom: -1px; height: 2px; background: var(--pink); }.about-card { margin-top: 30px; padding: 28px; border-radius: 15px; background: #fff; }.about-card p { color: #656b77; line-height: 1.8; }.about-card dl { display: grid; grid-template-columns: repeat(3,1fr); gap: 20px; }.about-card dl div { padding: 15px; border-radius: 10px; background: #f6f7f9; }.about-card dt { color: var(--muted); font-size: 11px; }.about-card dd { margin: 6px 0 0; }.modal-backdrop { position: fixed; z-index: 60; inset: 0; display: grid; place-items: center; padding: 20px; background: rgba(20,22,28,.52); backdrop-filter: blur(4px); }.edit-modal { width: min(520px,100%); padding: 26px; display: flex; flex-direction: column; gap: 16px; border-radius: 17px; background: #fff; }.edit-modal header { display: flex; justify-content: space-between; }.edit-modal header h2 { margin: 0; }.edit-modal header p { margin: 5px 0; color: var(--muted); font-size: 12px; }.edit-modal header button { border: 0; background: none; }.edit-modal .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }.edit-modal footer { display: flex; justify-content: flex-end; gap: 8px; }.profile-loading { padding-top: 30px; }.banner-skeleton { height: 260px; margin-bottom: 30px; }
@media (max-width: 700px) { .profile-banner { height: 180px; }.profile-shell { margin-top: -30px; }.profile-card { padding: 80px 18px 20px; display: block; }.profile-avatar { width: 90px; height: 90px; top: -25px; left: 24px; }.profile-actions { margin-top: 15px; }.profile-stats { justify-content: space-around; }.about-card dl { grid-template-columns: 1fr; }.edit-modal .form-row { grid-template-columns: 1fr; } }
</style>
