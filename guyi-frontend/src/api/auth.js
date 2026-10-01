import api from './index'

/**
 * Authentication API module (design Task 10).
 *
 * Every helper returns the raw axios promise, so call sites keep the existing response shape
 * ({ code, msg, data }) and error handling stays identical to calling the instance directly.
 */
export const authApi = {
  login: (username, password) => api.post('/api/auth/login', { username, password }),

  /** Revokes every issued token server-side (token_version is bumped). */
  logout: () => api.post('/api/auth/logout'),

  me: () => api.get('/api/auth/me')
}

export default authApi
