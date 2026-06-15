<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'

const settings = ref({ bg_blur: '0', api_encrypt: '1' })
const passwordForm = ref({ new_password: '', confirm_password: '' })
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await api.get('/api/admin/settings')
    if (res.data.code === 200 && res.data.data) settings.value = { ...settings.value, ...res.data.data }
  } catch (e) {}
  finally { loading.value = false }
})

async function saveSettings() {
  try {
    const res = await api.post('/api/admin/settings', settings.value)
    if (res.data.code === 200) alert('系统配置已保存')
    else alert(res.data.msg)
  } catch (e) { alert('保存失败') }
}

async function updatePassword() {
  if (passwordForm.value.new_password !== passwordForm.value.confirm_password) {
    alert('两次输入的密码不一致'); return
  }
  try {
    const res = await api.put('/api/admin/password', passwordForm.value)
    if (res.data.code === 200) { alert('密码已更新'); passwordForm.value = { new_password: '', confirm_password: '' } }
    else alert(res.data.msg)
  } catch (e) { alert('更新失败') }
}

async function exportData() {
  try {
    const res = await api.get('/api/admin/system/export', { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `System_Migrate_${new Date().toISOString().replace(/[-:T]/g, '').substring(0, 14)}.json`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) { alert('导出失败') }
}

async function importData(event) {
  const file = event.target.files[0]
  if (!file) return
  if (!confirm('警告：该操作将清空当前系统的全部数据并进行彻底覆盖！是否确认继续？')) return
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await api.post('/api/admin/system/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
    if (res.data.code === 200) alert(res.data.msg)
    else alert(res.data.msg)
  } catch (e) { alert('导入失败') }
}
</script>

<template>
  <div>
    <div class="rise" style="margin-bottom:24px">
      <h2 class="pg-title">全局配置</h2>
      <p class="pg-sub">个性化与安全</p>
    </div>

    <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:12px" class="rise rise-1">
      <!-- Global Settings -->
      <div class="glass" style="padding:20px">
        <h3 style="font-size:12px;font-weight:700;margin-bottom:16px;color:var(--sys-blue)"><i class="ph-fill ph-palette"></i> 全局设置</h3>
        <div style="display:flex;flex-direction:column;gap:12px">
          <label style="display:flex;align-items:center;justify-content:space-between;cursor:pointer;padding:16px;border-radius:16px;background:rgba(255,255,255,0.02);border:0.5px solid rgba(255,255,255,0.05)">
            <div><div style="font-size:12px;font-weight:700;color:rgba(255,255,255,0.8)">背景全局模糊</div><div style="font-size:9px;color:var(--text-4)">Glass Effect 沉浸式毛玻璃</div></div>
            <input type="checkbox" v-model="settings.bg_blur" true-value="1" false-value="0" style="accent-color:var(--sys-pink);width:18px;height:18px" />
          </label>
          <label style="display:flex;align-items:center;justify-content:space-between;cursor:pointer;padding:16px;border-radius:16px;background:rgba(255,255,255,0.02);border:0.5px solid rgba(255,255,255,0.05)">
            <div><div style="font-size:12px;font-weight:700;color:rgba(255,255,255,0.8)">API 通讯加密</div><div style="font-size:9px;color:var(--text-4)">AES-256-GCM 算法保护</div></div>
            <input type="checkbox" v-model="settings.api_encrypt" true-value="1" false-value="0" style="accent-color:var(--sys-pink);width:18px;height:18px" />
          </label>
          <button class="btn btn-sys-blue" style="width:100%;padding:12px;justify-content:center" @click="saveSettings"><i class="ph-bold ph-floppy-disk"></i> 保存设置</button>
        </div>
      </div>

      <!-- Password -->
      <div class="glass" style="padding:20px">
        <h3 style="font-size:12px;font-weight:700;margin-bottom:16px;color:var(--sys-red)"><i class="ph-fill ph-shield-check"></i> 安全设置</h3>
        <form @submit.prevent="updatePassword" style="display:flex;flex-direction:column;gap:12px">
          <div><label class="lbl">新密码</label><input v-model="passwordForm.new_password" type="password" class="field" required /></div>
          <div><label class="lbl">确认密码</label><input v-model="passwordForm.confirm_password" type="password" class="field" required /></div>
          <button type="submit" class="btn btn-sys-red" style="width:100%;padding:12px;justify-content:center"><i class="ph-bold ph-lock-key"></i> 更新密码</button>
        </form>
      </div>

      <!-- Migration -->
      <div class="glass" style="padding:20px;grid-column:1/-1">
        <h3 style="font-size:12px;font-weight:700;margin-bottom:16px;color:var(--sys-orange)"><i class="ph-fill ph-swap"></i> 完美系统迁移</h3>
        <div style="font-size:11px;color:var(--text-2);margin-bottom:16px">通过一键导出与导入功能，实现无缝数据迁移。</div>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:16px">
          <div class="glass-sunken" style="padding:16px;border-radius:16px">
            <h4 style="font-size:11px;font-weight:700;color:rgba(255,255,255,0.8);margin-bottom:8px">导出数据</h4>
            <p style="font-size:10px;color:var(--text-4);margin-bottom:16px">将全部数据打包为 JSON 备份文件。</p>
            <button class="btn btn-sys-orange" style="width:100%;padding:10px;justify-content:center" @click="exportData"><i class="ph-bold ph-download-simple"></i> 导出完整数据包</button>
          </div>
          <div class="glass-sunken" style="padding:16px;border-radius:16px">
            <h4 style="font-size:11px;font-weight:700;color:rgba(255,255,255,0.8);margin-bottom:8px">导入数据</h4>
            <p style="font-size:10px;color:var(--text-4);margin-bottom:16px"><span style="color:var(--sys-red)">注意：将完全覆盖现有数据。</span></p>
            <input type="file" accept=".json" @change="importData" style="font-size:10px;color:var(--text-2)" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
