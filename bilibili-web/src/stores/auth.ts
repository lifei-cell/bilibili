import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { userApi } from '@/api'
import type { CurrentUser, LoginResult, UserInfo } from '@/types/api'

const storedUser = (): UserInfo | null => {
  try { return JSON.parse(localStorage.getItem('bili_user') || 'null') }
  catch { return null }
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('bili_token') || '')
  const user = ref<UserInfo | CurrentUser | null>(storedUser())
  const isLoggedIn = computed(() => Boolean(token.value))

  function save(result: LoginResult) {
    token.value = result.token
    user.value = result.user
    localStorage.setItem('bili_token', result.token)
    localStorage.setItem('bili_user', JSON.stringify(result.user))
  }

  async function hydrate() {
    if (!token.value) return
    try {
      const result = await userApi.me()
      user.value = result.data
      localStorage.setItem('bili_user', JSON.stringify(result.data))
    } catch {
      clear()
    }
  }

  function clear() {
    token.value = ''
    user.value = null
    localStorage.removeItem('bili_token')
    localStorage.removeItem('bili_user')
  }

  async function logout() {
    try { await userApi.logout() } finally { clear() }
  }

  return { token, user, isLoggedIn, save, hydrate, logout, clear }
})
