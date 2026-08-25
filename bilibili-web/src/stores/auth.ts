import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { userApi } from '@/api'
import { clearAccessToken, observeAuthSession, setAccessToken } from '@/api/http'
import type { CurrentUser, LoginResult, UserInfo } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | CurrentUser | null>(null)
  const hydrated = ref(false)
  const hydration = ref<Promise<void> | null>(null)
  const isLoggedIn = computed(() => Boolean(user.value))

  observeAuthSession((session) => {
    user.value = session?.user || null
  })

  function save(result: LoginResult) {
    setAccessToken(result.token)
    user.value = result.user
  }

  async function hydrate() {
    if (hydrated.value) return
    if (!hydration.value) {
      hydration.value = (async () => {
        try {
          save((await userApi.refresh()).data)
          user.value = (await userApi.me()).data
        } catch {
          clear()
        } finally {
          hydrated.value = true
          hydration.value = null
        }
      })()
    }
    await hydration.value
  }

  function clear() {
    clearAccessToken()
    user.value = null
  }

  async function logout() {
    try { await userApi.logout() } finally { clear() }
  }

  return { user, isLoggedIn, save, hydrate, logout, clear }
})
