import api from './index'

/**
 * Admin API module (design Task 10): one function per backend endpoint, mirroring AdminController.
 *
 * Every helper returns the raw axios promise, so migrating a call site is a pure rename
 * (`api.get('/api/admin/cards', { params })` -> `adminApi.getCards(params)`) and the response shape
 * ({ code, msg, data }) is unchanged.
 */
export const adminApi = {
  // ---- dashboard ----
  getDashboard: () => api.get('/api/admin/dashboard'),

  // ---- applications ----
  getApps: () => api.get('/api/admin/apps'),
  createApp: (payload) => api.post('/api/admin/apps', payload),
  updateApp: (id, payload) => api.put(`/api/admin/apps/${id}`, payload),
  toggleApp: (id) => api.put(`/api/admin/apps/${id}/toggle`),
  deleteApp: (id) => api.delete(`/api/admin/apps/${id}`),

  // ---- cloud variables ----
  getVariables: (appId, onlyPublic = false) =>
    api.get(`/api/admin/apps/${appId}/variables`, { params: { onlyPublic } }),
  createVariable: (appId, payload) => api.post(`/api/admin/apps/${appId}/variables`, payload),
  updateVariable: (id, payload) => api.put(`/api/admin/variables/${id}`, payload),
  deleteVariable: (id) => api.delete(`/api/admin/variables/${id}`),

  // ---- cards ----
  getCards: (params) => api.get('/api/admin/cards', { params }),
  getCardTypes: () => api.get('/api/admin/card-types'),
  generateCards: (payload) => api.post('/api/admin/cards/generate', payload),
  deleteCard: (id) => api.delete(`/api/admin/cards/${id}`),
  setCardStatus: (id, status) => api.put(`/api/admin/cards/${id}/status`, { status }),
  unbindCard: (id) => api.put(`/api/admin/cards/${id}/unbind`),
  batchDelete: (ids) => api.post('/api/admin/cards/batch-delete', { ids }),
  batchUnbind: (ids) => api.post('/api/admin/cards/batch-unbind', { ids }),
  batchAddTime: (ids, hours) => api.post('/api/admin/cards/batch-add-time', { ids, hours }),
  batchSubTime: (ids, hours) => api.post('/api/admin/cards/batch-sub-time', { ids, hours }),
  globalCompensate: (body) => api.post('/api/admin/cards/global-compensate', body),
  cleanExpired: () => api.post('/api/admin/cards/clean-expired'),
  exportCards: (ids) => api.post('/api/admin/cards/batch-export', { ids }, { responseType: 'blob' }),

  // ---- blacklist ----
  getBlacklist: () => api.get('/api/admin/blacklist'),
  addBlacklist: (payload) => api.post('/api/admin/blacklist', payload),
  deleteBlacklist: (id) => api.delete(`/api/admin/blacklist/${id}`),

  // ---- audit log ----
  getLogs: (params) => api.get('/api/admin/logs', { params }),

  // ---- settings & credentials ----
  getSettings: () => api.get('/api/admin/settings'),
  saveSettings: (payload) => api.post('/api/admin/settings', payload),
  updatePassword: (payload) => api.put('/api/admin/password', payload),

  // ---- system migration ----
  /** Credentials are excluded by default; pass true to include the admin table. */
  exportSystem: (includeCredentials = false) =>
    api.get('/api/admin/system/export', {
      params: { include_credentials: includeCredentials },
      responseType: 'blob'
    }),
  importSystem: (formData) =>
    api.post('/api/admin/system/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }),

  // ---- first-run install (public endpoint) ----
  install: (payload) => api.post('/api/admin/install', payload)
}

export default adminApi
