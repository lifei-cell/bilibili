<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Check, Eye, EyeOff, Play, ShieldCheck, Sparkles } from 'lucide-vue-next'
import { userApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const mode = ref<'login' | 'register'>('login')
const loginType = ref<'password' | 'code'>('password')
const showPassword = ref(false)
const form = ref({ username: '', phone: '', code: '', password: '', confirmPassword: '' })
const loading = ref(false)
const error = ref('')
const seconds = ref(0)
let timer: number | undefined
const title = computed(() => mode.value === 'login' ? '欢迎回来' : '创建你的账号')

async function sendCode() {
  if (!/^1[3-9]\d{9}$/.test(form.value.phone)) { error.value = '请输入正确的手机号'; return }
  try {
    await userApi.sendCode(form.value.phone)
    seconds.value = 60
    timer = window.setInterval(() => { if (--seconds.value <= 0 && timer) clearInterval(timer) }, 1000)
  } catch (e) { error.value = e instanceof Error ? e.message : '验证码发送失败' }
}

async function submit() {
  error.value = ''
  if (mode.value === 'register' && form.value.password !== form.value.confirmPassword) { error.value = '两次输入的密码不一致'; return }
  loading.value = true
  try {
    const result = mode.value === 'login'
      ? await userApi.login(loginType.value === 'password'
        ? { username: form.value.username, password: form.value.password, terminal: 'web' }
        : { phone: form.value.phone, code: form.value.code, terminal: 'web' })
      : await userApi.register({ phone: form.value.phone, code: form.value.code, username: form.value.username, password: form.value.password, terminal: 'web' })
    auth.save(result.data)
    router.replace(String(route.query.redirect || `/space/${result.data.user.id}`))
  } catch (e) { error.value = e instanceof Error ? e.message : '操作失败' }
  finally { loading.value = false }
}

function switchMode(value: 'login' | 'register') { mode.value = value; error.value = '' }
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <div class="auth-page">
    <RouterLink class="back-home" to="/"><ArrowLeft :size="18" />返回首页</RouterLink>
    <section class="auth-showcase">
      <div class="auth-brand"><span class="brand-mark">B</span><strong>BiliCloud</strong></div>
      <div class="showcase-copy">
        <span><Sparkles :size="15" /> 热爱不设限</span>
        <h1>让每一次<br />表达都有回响。</h1>
        <p>加入充满创造力的视频社区，收藏灵感、结识同好，也分享属于你的精彩。</p>
        <div class="showcase-card">
          <div class="mock-cover"><Play :size="28" fill="currentColor" /></div>
          <div><strong>正在发生的新鲜事</strong><p>每天都有创作者分享新作品</p></div>
        </div>
      </div>
      <div class="trust-row"><span><Check :size="14" />安全登录</span><span><ShieldCheck :size="14" />隐私保护</span></div>
    </section>

    <section class="auth-panel">
      <div class="auth-box">
        <div class="auth-tabs"><button :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</button><button :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</button></div>
        <h2>{{ title }}</h2>
        <p class="auth-subtitle">{{ mode === 'login' ? '继续探索你感兴趣的内容' : '只需几步，开启你的创作之旅' }}</p>

        <div v-if="mode === 'login'" class="login-type"><button :class="{ active: loginType === 'password' }" @click="loginType = 'password'">密码登录</button><button :class="{ active: loginType === 'code' }" @click="loginType = 'code'">短信登录</button></div>
        <form @submit.prevent="submit">
          <div v-if="mode === 'login' && loginType === 'password' || mode === 'register'" class="field"><label>用户名</label><input v-model.trim="form.username" minlength="3" maxlength="50" required placeholder="请输入用户名" /></div>
          <div v-if="mode === 'register' || loginType === 'code'" class="field"><label>手机号</label><input v-model.trim="form.phone" inputmode="numeric" maxlength="11" required placeholder="请输入 11 位手机号" /></div>
          <div v-if="mode === 'register' || loginType === 'code'" class="field"><label>短信验证码</label><div class="code-field"><input v-model.trim="form.code" required placeholder="请输入验证码" /><button type="button" :disabled="seconds > 0" @click="sendCode">{{ seconds ? `${seconds}s 后重试` : '获取验证码' }}</button></div></div>
          <div v-if="mode === 'register' || loginType === 'password'" class="field"><label>密码</label><div class="password-field"><input v-model="form.password" :type="showPassword ? 'text' : 'password'" minlength="8" maxlength="64" required placeholder="至少 8 位字符" /><button type="button" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="18" /><Eye v-else :size="18" /></button></div></div>
          <div v-if="mode === 'register'" class="field"><label>确认密码</label><input v-model="form.confirmPassword" :type="showPassword ? 'text' : 'password'" minlength="8" required placeholder="再次输入密码" /></div>
          <div v-if="error" class="error-banner">{{ error }}</div>
          <button class="primary-button block-button submit-button" :disabled="loading">{{ loading ? '请稍候…' : mode === 'login' ? '登录' : '注册并登录' }}</button>
        </form>
        <p class="agreement">继续即表示你同意《用户协议》和《隐私政策》</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-page { min-height: 100vh; display: grid; grid-template-columns: 1.05fr .95fr; background: #fff; }.back-home { position: fixed; z-index: 3; top: 24px; right: 30px; display: flex; align-items: center; gap: 7px; color: #68707c; font-size: 13px; }.auth-showcase { position: relative; overflow: hidden; padding: 48px 8vw; color: #fff; background: radial-gradient(circle at 20% 20%, rgba(255,168,187,.55), transparent 28%), linear-gradient(145deg,#251f31 0%,#3d2b42 45%,#73505a 100%); }.auth-showcase::after { content: ''; position: absolute; width: 550px; height: 550px; right: -250px; bottom: -280px; border: 1px solid rgba(255,255,255,.12); border-radius: 50%; box-shadow: 0 0 0 70px rgba(255,255,255,.025),0 0 0 140px rgba(255,255,255,.018); }.auth-brand { display: flex; align-items: center; gap: 10px; font-size: 19px; }.showcase-copy { position: relative; z-index: 1; top: 50%; transform: translateY(-62%); }.showcase-copy > span { display: flex; align-items: center; gap: 7px; color: #ffc2ce; font-size: 12px; font-weight: 700; letter-spacing: .1em; }.showcase-copy h1 { max-width: 600px; margin: 18px 0; font-size: clamp(45px,5.5vw,78px); line-height: 1.08; letter-spacing: -.06em; }.showcase-copy > p { max-width: 510px; color: rgba(255,255,255,.68); line-height: 1.8; }.showcase-card { width: 360px; margin-top: 36px; padding: 11px; display: flex; align-items: center; gap: 14px; border: 1px solid rgba(255,255,255,.16); border-radius: 16px; background: rgba(255,255,255,.08); backdrop-filter: blur(12px); }.mock-cover { width: 90px; height: 58px; display: grid; place-items: center; border-radius: 10px; color: #fff; background: linear-gradient(135deg,#da8296,#785666); }.showcase-card strong { font-size: 13px; }.showcase-card p { margin: 6px 0 0; color: rgba(255,255,255,.55); font-size: 11px; }.trust-row { position: absolute; left: 8vw; bottom: 35px; display: flex; gap: 24px; color: rgba(255,255,255,.58); font-size: 11px; }.trust-row span { display: flex; align-items: center; gap: 5px; }
.auth-panel { display: flex; align-items: center; justify-content: center; padding: 60px; }.auth-box { width: min(420px,100%); }.auth-tabs { display: flex; gap: 26px; border-bottom: 1px solid var(--line); }.auth-tabs button { position: relative; border: 0; padding: 12px 2px; background: transparent; color: #8a909b; }.auth-tabs button.active { color: var(--pink); font-weight: 700; }.auth-tabs button.active::after { content: ''; position: absolute; height: 2px; left: 0; right: 0; bottom: -1px; background: var(--pink); }.auth-box h2 { margin: 32px 0 7px; font-size: 28px; }.auth-subtitle { margin: 0 0 24px; color: var(--muted); font-size: 13px; }.login-type { display: flex; padding: 4px; margin-bottom: 18px; border-radius: 10px; background: #f2f3f5; }.login-type button { flex: 1; padding: 8px; border: 0; border-radius: 8px; background: transparent; color: #747a86; font-size: 12px; }.login-type button.active { color: #343944; background: #fff; box-shadow: 0 2px 7px rgba(0,0,0,.06); }.auth-box form { display: flex; flex-direction: column; gap: 15px; }.code-field,.password-field { display: flex; border: 1px solid #dde0e6; border-radius: 10px; overflow: hidden; }.code-field input,.password-field input { border: 0; box-shadow: none; }.code-field button,.password-field button { flex: none; border: 0; background: transparent; color: var(--pink); padding: 0 12px; font-size: 12px; }.password-field button { color: #858b96; }.submit-button { min-height: 44px; margin-top: 5px; }.agreement { text-align: center; color: #9a9faa; font-size: 10px; }
@media (max-width: 900px) { .auth-page { grid-template-columns: 1fr; }.auth-showcase { display: none; }.auth-panel { padding: 80px 24px 30px; } }.error-banner { margin: 0; }
</style>
