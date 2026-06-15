<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const password = ref('')
const error = ref('')
const loading = ref(false)
const showPassword = ref(false)

async function handleLogin() {
  if (!password.value) return
  loading.value = true
  error.value = ''
  try {
    await authStore.login(password.value)
    router.push('/dashboard')
  } catch (e) {
    error.value = e.message || '密钥无效'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-bg"></div>
    <div class="login-petals">
      <i style="left:6%;top:-8vh;animation-duration:11s"></i>
      <i style="left:24%;top:-12vh;animation-duration:13s"></i>
      <i style="left:52%;top:-16vh;animation-duration:12s"></i>
      <i style="left:72%;top:-10vh;animation-duration:10s"></i>
      <i style="left:86%;top:-18vh;animation-duration:14s"></i>
    </div>

    <div class="login-wrap">
      <div class="login-card">
        <div class="login-head">
          <div class="login-logo"></div>
          <h1 class="login-title">欢迎回来，指挥官</h1>
          <p class="login-sub">正在验证您的管理员身份</p>
        </div>
        <div class="login-body">
          <div v-if="error" class="login-error">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            {{ error }}
          </div>
          <form @submit.prevent="handleLogin">
            <div class="login-field">
              <input :type="showPassword ? 'text' : 'password'" v-model="password" class="login-input" placeholder=" " autocomplete="current-password" required />
              <span class="login-label">管理员密钥</span>
              <button type="button" class="login-eye" @click="showPassword = !showPassword">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" :stroke="showPassword ? '#ff7dc6' : '#cfe1ff'" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12Z"/><circle cx="12" cy="12" r="3"/></svg>
              </button>
            </div>
            <button class="login-btn" type="submit" :disabled="loading">
              {{ loading ? '验证中...' : '立即进入' }}
            </button>
          </form>
          <div class="login-foot">GuYi Access System</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page { min-height: 100vh; position: relative; overflow: hidden; }
.login-bg { position: fixed; inset: 0; background: url('https://www.loliapi.com/acg/pc/') center/cover no-repeat; z-index: 0; }
.login-bg::after { content: ''; position: fixed; inset: 0; background: rgba(0,0,0,0.3); backdrop-filter: blur(20px); }
.login-petals { position: fixed; inset: 0; pointer-events: none; z-index: 1; overflow: hidden; }
.login-petals i { position: absolute; width: 12px; height: 10px; background: linear-gradient(135deg,#ffd1e6,#ff9aca); border-radius: 80% 80% 80% 20%/80% 80% 20% 80%; opacity: 0.5; animation: petal-fall linear infinite; }
@keyframes petal-fall { to { transform: translateY(110vh) rotate(360deg); } }
.login-wrap { min-height: 100vh; display: grid; place-items: center; padding: clamp(16px,4vw,32px); position: relative; z-index: 2; }
.login-card { width: min(480px,92vw); background: rgba(12,14,28,0.55); backdrop-filter: blur(20px) saturate(140%); border: 1px solid rgba(255,255,255,0.18); border-radius: 24px; box-shadow: 0 18px 60px rgba(5,9,20,0.45); overflow: hidden; position: relative; }
.login-card::before { content: ''; position: absolute; inset: -1px; border-radius: inherit; padding: 1px; background: conic-gradient(from 200deg,#ff7dc6,#7aa8ff,#ff7dc6); -webkit-mask: linear-gradient(#000 0 0) content-box,linear-gradient(#000 0 0); -webkit-mask-composite: xor; mask-composite: exclude; opacity: 0.7; pointer-events: none; }
.login-head { padding: 26px 22px 8px; display: grid; place-items: center; row-gap: 8px; }
.login-logo { width: 64px; height: 64px; border-radius: 50%; background: linear-gradient(135deg,#ff7dc6,#7aa8ff); box-shadow: 0 8px 26px rgba(255,154,202,0.25); }
.login-title { margin: 4px 0 0; font-weight: 900; letter-spacing: 0.6px; font-size: clamp(18px,2.6vw,22px); color: white; }
.login-sub { margin: 0 0 6px; color: #b9c3e6; font-size: 12px; text-align: center; }
.login-body { padding: 16px 22px 22px; }
.login-field { position: relative; margin: 16px 0 22px; }
.login-input { width: 100%; height: 48px; padding: 12px 44px 12px 14px; border-radius: 16px; border: 1px solid rgba(255,255,255,0.18); background: rgba(255,255,255,0.06); color: #f3f6ff; outline: none; transition: all 0.18s ease; font-size: 14px; }
.login-input:focus { border-color: rgba(255,255,255,0.38); box-shadow: 0 0 0 3px rgba(255,125,198,0.18); background: rgba(255,255,255,0.08); }
.login-input::placeholder { color: transparent; }
.login-label { position: absolute; left: 14px; top: 50%; transform: translateY(-52%); font-size: 13px; color: #b9c3e6; pointer-events: none; transition: all 0.2s cubic-bezier(0.4,0,0.2,1); }
.login-input:focus + .login-label, .login-input:not(:placeholder-shown) + .login-label { top: -9px; font-size: 11px; background: rgba(10,12,24,0.95); padding: 0 8px; border-radius: 999px; color: #e9eaff; border: 1px solid rgba(255,255,255,0.15); transform: translateY(0); }
.login-eye { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); width: 34px; height: 34px; border-radius: 10px; border: none; background: transparent; display: grid; place-items: center; cursor: pointer; }
.login-btn { width: 100%; height: 48px; border: none; border-radius: 14px; cursor: pointer; color: #fff; font-weight: 900; letter-spacing: 0.5px; margin-top: 10px; background: linear-gradient(135deg,#ffb6f0,#9ad6ff); box-shadow: 0 12px 30px rgba(122,168,255,0.35); transition: transform 0.1s ease; }
.login-btn:hover { transform: translateY(-2px); }
.login-btn:active { transform: translateY(1px) scale(0.98); }
.login-error { background: rgba(239,68,68,0.2); border: 1px solid rgba(239,68,68,0.3); color: #fca5a5; font-size: 12px; padding: 8px 12px; border-radius: 12px; margin-bottom: 12px; display: flex; align-items: center; gap: 6px; }
.login-foot { margin: 12px 0 8px; text-align: center; color: #dfe6ff; font-size: 12px; opacity: 0.7; }
</style>
