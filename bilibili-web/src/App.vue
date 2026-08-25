<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Clock3, Menu, Search, Upload, X } from 'lucide-vue-next'
import { categoryApi } from '@/api'
import { useAuthStore } from '@/stores/auth'
import { avatarFallback, categories, replaceCategories } from '@/utils/format'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const keyword = ref('')
const mobileOpen = ref(false)
const isImmersive = computed(() => route.name === 'auth')

function submitSearch() {
  const value = keyword.value.trim()
  if (value) router.push({ name: 'search', query: { keyword: value } })
}

onMounted(async () => {
  await auth.hydrate()
  try {
    replaceCategories((await categoryApi.list()).data || [])
  } catch {
    // Keep the built-in list while the backend is unavailable.
  }
})
</script>

<template>
  <header v-if="!isImmersive" class="site-header">
    <div class="header-inner">
      <RouterLink class="brand" to="/" aria-label="BiliCloud 首页">
        <span class="brand-mark">B</span><span>BiliCloud</span>
      </RouterLink>

      <nav class="main-nav">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink :to="{ name: 'search', query: { sort: 'hot' } }">热门</RouterLink>
        <a href="#categories">频道</a>
      </nav>

      <form class="header-search" @submit.prevent="submitSearch">
        <input v-model="keyword" placeholder="搜索你感兴趣的视频" aria-label="搜索" />
        <button type="submit" aria-label="提交搜索"><Search :size="19" /></button>
      </form>

      <div class="header-actions">
        <template v-if="auth.isLoggedIn">
          <RouterLink class="avatar-link" :to="`/space/${auth.user?.id}`">
            <img :src="auth.user?.avatar || avatarFallback(auth.user?.nickname || auth.user?.username)" alt="头像" />
          </RouterLink>
          <button class="icon-button" title="消息"><Bell :size="20" /></button>
          <button class="icon-button" title="历史记录"><Clock3 :size="20" /></button>
        </template>
        <RouterLink v-else class="login-button" to="/auth">登录</RouterLink>
        <RouterLink class="upload-button" to="/upload"><Upload :size="18" />投稿</RouterLink>
        <button class="mobile-toggle" @click="mobileOpen = !mobileOpen"><X v-if="mobileOpen" /><Menu v-else /></button>
      </div>
    </div>
    <div v-if="mobileOpen" class="mobile-menu">
      <RouterLink to="/" @click="mobileOpen = false">首页</RouterLink>
      <RouterLink v-for="item in categories.slice(0, 6)" :key="item.id" :to="{ name: 'search', query: { categoryId: item.id } }" @click="mobileOpen = false">{{ item.name }}</RouterLink>
    </div>
  </header>

  <main :class="{ 'main-content': !isImmersive }">
    <RouterView />
  </main>

  <footer v-if="!isImmersive" class="site-footer">
    <div><span class="brand-mark brand-mark--small">B</span><strong>BiliCloud</strong></div>
    <p>分享每一份热爱，让创作被更多人看见。</p>
    <span>基于 Vue 3 · 接入 Bilibili Cloud API</span>
  </footer>
</template>
