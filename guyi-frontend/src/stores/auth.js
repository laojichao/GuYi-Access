import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('guyi_token') || '')
  const username = ref(localStorage.getItem('guyi_username') || '')

  const isLoggedIn = computed(() => !!token.value)

  function setToken(newToken) {
    token.value = newToken || ''
    if (token.value) localStorage.setItem('guyi_token', token.value)
    else localStorage.removeItem('guyi_token')
  }

  /** Local-only clear. Safe to call from the 401 interceptor: no request, so no recursion. */
  function clearLocal() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('guyi_token')
    localStorage.removeItem('guyi_username')
  }

  async function login(password) {
    // Single-admin login. Override at build time with VITE_ADMIN_USERNAME to match the
    // backend's app.admin.default-username.
    const res = await authApi.login(import.meta.env.VITE_ADMIN_USERNAME || 'GuYi', password)
    if (res.data.code === 200) {
      setToken(res.data.data.token)
      username.value = res.data.data.username
      localStorage.setItem('guyi_username', username.value)
      return true
    }
    throw new Error(res.data.msg)
  }

  /**
   * User-initiated logout: revokes the token server-side first (tokens carry a version counter),
   * then clears local state. Best effort - if the call fails the session is still cleared locally.
   */
  async function logout() {
    try {
      if (token.value) await authApi.logout()
    } catch (e) {
      // offline or token already invalid: clearing locally is still the right outcome
    }
    clearLocal()
  }

  async function verifyToken() {
    if (!token.value) return false
    try {
      const res = await authApi.me()
      if (res.data.code === 200) {
        username.value = res.data.data.username
        localStorage.setItem('guyi_username', username.value)
        return true
      }
      clearLocal()
      return false
    } catch (e) {
      clearLocal()
      return false
    }
  }

  return { token, username, isLoggedIn, login, logout, clearLocal, setToken, verifyToken }
})
