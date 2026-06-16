import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '../api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('guyi_token') || '')
  const username = ref(localStorage.getItem('guyi_username') || '')

  const isLoggedIn = computed(() => !!token.value)

  async function login(password) {
    const res = await api.post('/api/auth/login', {
      username: 'GuYi',
      password
    })
    if (res.data.code === 200) {
      token.value = res.data.data.token
      username.value = res.data.data.username
      localStorage.setItem('guyi_token', token.value)
      localStorage.setItem('guyi_username', username.value)
      return true
    }
    throw new Error(res.data.msg)
  }

  function logout() {
    token.value = ''
    username.value = ''
    localStorage.removeItem('guyi_token')
    localStorage.removeItem('guyi_username')
  }

  async function verifyToken() {
    if (!token.value) return false
    try {
      const res = await api.get('/api/auth/me')
      if (res.data.code === 200) {
        username.value = res.data.data.username
        localStorage.setItem('guyi_username', username.value)
        return true
      }
      logout()
      return false
    } catch (e) {
      logout()
      return false
    }
  }

  return { token, username, isLoggedIn, login, logout, verifyToken }
})
