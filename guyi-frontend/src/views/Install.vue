<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi } from '../api/admin'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const form = ref({ password: '', confirm: '', installToken: '' })
const error = ref('')
const loading = ref(false)

async function handleInstall() {
  error.value = ''
  if (!form.value.password || form.value.password.length < 6) {
    error.value = '管理员密钥至少 6 位'
    return
  }
  if (form.value.password !== form.value.confirm) {
    error.value = '两次输入的密钥不一致'
    return
  }
  loading.value = true
  try {
    const payload = { admin_password: form.value.password }
    // Only sent when the deployment configured INSTALL_TOKEN; the backend ignores it otherwise
    if (form.value.installToken) payload.install_token = form.value.installToken
    const res = await adminApi.install(payload)
    if (res.data.code !== 200) {
      error.value = res.data.msg || '安装失败'
      return
    }
    authStore.setToken(res.data.data?.token)
    await authStore.verifyToken()
    router.push('/dashboard')
  } catch (e) {
    error.value = '安装失败，请检查后端是否可用'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="install-page">
    <div class="install-bg"></div>
    <div class="install-wrap">
      <div class="install-card">
        <div class="install-head">
          <div class="install-logo"></div>
          <h1 class="install-title">初始化系统</h1>
          <p class="install-sub">首次部署：设置管理员密钥以完成安装</p>
        </div>
        <div class="install-body">
          <div v-if="error" class="install-error">{{ error }}</div>
          <form @submit.prevent="handleInstall">
            <div class="install-field">
              <input type="password" v-model="form.password" class="install-input" placeholder=" " autocomplete="new-password" required />
              <span class="install-label">管理员密钥（至少 6 位）</span>
            </div>
            <div class="install-field">
              <input type="password" v-model="form.confirm" class="install-input" placeholder=" " autocomplete="new-password" required />
              <span class="install-label">确认密钥</span>
            </div>
            <div class="install-field">
              <input type="text" v-model="form.installToken" class="install-input" placeholder=" " autocomplete="off" />
              <span class="install-label">安装令牌（仅后端设置了 INSTALL_TOKEN 时必填）</span>
            </div>
            <button class="install-btn" type="submit" :disabled="loading">
              {{ loading ? '安装中...' : '完成安装' }}
            </button>
          </form>
          <div class="install-foot">安装接口在系统安装完成后自动关闭</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.install-page { min-height: 100vh; position: relative; overflow: hidden; }
.install-bg { position: fixed; inset: 0; background: linear-gradient(160deg,#2b1b4d 0%,#141428 48%,#3d1130 100%); z-index: 0; }
.install-bg::after { content: ''; position: fixed; inset: 0; background: rgba(0,0,0,0.3); backdrop-filter: blur(20px); }
.install-wrap { min-height: 100vh; display: grid; place-items: center; padding: clamp(16px,4vw,32px); position: relative; z-index: 2; }
.install-card { width: min(480px,92vw); background: rgba(12,14,28,0.55); backdrop-filter: blur(20px) saturate(140%); border: 1px solid rgba(255,255,255,0.18); border-radius: 24px; box-shadow: 0 18px 60px rgba(5,9,20,0.45); overflow: hidden; }
.install-head { padding: 26px 22px 8px; display: grid; place-items: center; row-gap: 8px; }
.install-logo { width: 64px; height: 64px; border-radius: 50%; background: linear-gradient(135deg,#ff7dc6,#7aa8ff); box-shadow: 0 8px 26px rgba(255,154,202,0.25); }
.install-title { margin: 4px 0 0; font-weight: 900; font-size: clamp(18px,2.6vw,22px); color: white; }
.install-sub { margin: 0 0 6px; color: #b9c3e6; font-size: 12px; text-align: center; }
.install-body { padding: 16px 22px 22px; }
.install-field { position: relative; margin: 16px 0; }
.install-input { width: 100%; height: 48px; padding: 12px 14px; border-radius: 16px; border: 1px solid rgba(255,255,255,0.18); background: rgba(255,255,255,0.06); color: #f3f6ff; outline: none; font-size: 14px; }
.install-input:focus { border-color: rgba(255,255,255,0.38); box-shadow: 0 0 0 3px rgba(255,125,198,0.18); }
.install-input::placeholder { color: transparent; }
.install-label { position: absolute; left: 14px; top: 50%; transform: translateY(-52%); font-size: 13px; color: #b9c3e6; pointer-events: none; transition: all 0.2s cubic-bezier(0.4,0,0.2,1); }
.install-input:focus + .install-label, .install-input:not(:placeholder-shown) + .install-label { top: -9px; font-size: 11px; background: rgba(10,12,24,0.95); padding: 0 8px; border-radius: 999px; color: #e9eaff; border: 1px solid rgba(255,255,255,0.15); transform: translateY(0); }
.install-btn { width: 100%; height: 48px; border: none; border-radius: 14px; cursor: pointer; color: #fff; font-weight: 900; margin-top: 10px; background: linear-gradient(135deg,#ffb6f0,#9ad6ff); box-shadow: 0 12px 30px rgba(122,168,255,0.35); }
.install-error { background: rgba(239,68,68,0.2); border: 1px solid rgba(239,68,68,0.3); color: #fca5a5; font-size: 12px; padding: 8px 12px; border-radius: 12px; margin-bottom: 12px; }
.install-foot { margin: 12px 0 8px; text-align: center; color: #dfe6ff; font-size: 12px; opacity: 0.7; }
</style>
