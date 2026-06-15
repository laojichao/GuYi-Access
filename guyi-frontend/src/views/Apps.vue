<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'

const apps = ref([])
const loading = ref(true)
const showCreate = ref(false)
const createForm = ref({ app_name: '', app_version: '', app_notes: '' })
const editModal = ref(false)
const editForm = ref({ id: 0, app_name: '', app_version: '', app_notes: '', update_url: '', force_update: 0 })

onMounted(loadApps)

async function loadApps() {
  loading.value = true
  try {
    const res = await api.get('/api/admin/apps')
    if (res.data.code === 200) apps.value = res.data.data
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

async function createApp() {
  try {
    const res = await api.post('/api/admin/apps', createForm.value)
    if (res.data.code === 200) { createForm.value = { app_name: '', app_version: '', app_notes: '' }; loadApps() }
    else alert(res.data.msg)
  } catch (e) { alert('创建失败') }
}

function openEdit(app) {
  editForm.value = { ...app }
  editModal.value = true
}

async function saveEdit() {
  try {
    const res = await api.put(`/api/admin/apps/${editForm.value.id}`, editForm.value)
    if (res.data.code === 200) { editModal.value = false; loadApps() }
    else alert(res.data.msg)
  } catch (e) { alert('更新失败') }
}

async function toggleApp(id) {
  await api.put(`/api/admin/apps/${id}/toggle`)
  loadApps()
}

async function deleteApp(id) {
  if (!confirm('确定删除？')) return
  try {
    const res = await api.delete(`/api/admin/apps/${id}`)
    if (res.data.code === 200) loadApps()
    else alert(res.data.msg)
  } catch (e) { alert(e.response?.data?.msg || '删除失败') }
}
</script>

<template>
  <div>
    <div class="rise" style="margin-bottom:24px">
      <h2 class="pg-title">应用管理</h2>
      <p class="pg-sub">多项目/软件隔离授权</p>
    </div>

    <!-- App List -->
    <div class="glass rise rise-1" style="overflow:hidden;margin-bottom:16px">
      <div style="overflow-x:auto">
        <table style="width:100%;font-size:13px;border-collapse:collapse">
          <thead><tr style="border-bottom:0.5px solid var(--liquid-border)">
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase;letter-spacing:0.12em">应用信息</th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase;letter-spacing:0.12em">App Key</th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase;letter-spacing:0.12em">统计</th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase;letter-spacing:0.12em">状态</th>
            <th style="padding:14px;text-align:right;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase;letter-spacing:0.12em">操作</th>
          </tr></thead>
          <tbody>
            <tr v-for="app in apps" :key="app.id" style="border-bottom:0.5px solid rgba(255,255,255,0.02)">
              <td style="padding:14px">
                <div style="font-weight:700;font-size:13px;color:rgba(255,255,255,0.9)">{{ app.app_name }}</div>
                <div style="font-size:10px;color:var(--text-4);margin-top:4px">{{ app.notes || '无备注' }}</div>
              </td>
              <td style="padding:14px"><span class="pill pill-free" style="font-size:9px;cursor:pointer" @click="navigator.clipboard?.writeText(app.app_key)"><i class="ph-bold ph-key" style="color:#6bb0ff;margin-right:4px"></i>{{ app.app_key?.substring(0, 16) }}...</span></td>
              <td style="padding:14px"><span class="pill pill-big" style="font-size:9px">{{ app.card_count }} 张</span></td>
              <td style="padding:14px"><span :class="app.status === 1 ? 'pill pill-on' : 'pill pill-banned'" style="font-size:9px">{{ app.status === 1 ? '正常' : '禁用' }}</span></td>
              <td style="padding:14px;text-align:right">
                <div style="display:flex;gap:4px;justify-content:flex-end">
                  <button class="btn btn-sys-blue" style="font-size:10px;padding:4px 8px" @click="openEdit(app)"><i class="ph-bold ph-pencil-simple"></i></button>
                  <button class="btn" :class="app.status === 1 ? 'btn-sys-orange' : 'btn-sys-green'" style="font-size:10px;padding:4px 8px" @click="toggleApp(app.id)"><i :class="app.status === 1 ? 'ph-bold ph-prohibit' : 'ph-bold ph-check'"></i></button>
                  <button class="btn btn-sys-red" style="font-size:10px;padding:4px 8px" @click="deleteApp(app.id)" :disabled="app.card_count > 0"><i class="ph-bold ph-trash"></i></button>
                </div>
              </td>
            </tr>
            <tr v-if="!apps.length"><td colspan="5" style="text-align:center;color:var(--text-4);font-size:11px;padding:40px">暂无应用数据</td></tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Create App -->
    <div class="glass rise rise-2" style="padding:20px;max-width:480px">
      <h3 style="font-size:12px;font-weight:700;margin-bottom:16px;color:var(--sys-teal)"><i class="ph-fill ph-plus-circle"></i> 创建新应用</h3>
      <form @submit.prevent="createApp" style="display:flex;flex-direction:column;gap:12px">
        <div><label class="lbl">应用名称</label><input v-model="createForm.app_name" class="field" required placeholder="如: Android客户端" /></div>
        <div><label class="lbl">版本号 (选填)</label><input v-model="createForm.app_version" class="field" placeholder="如: v1.0" /></div>
        <div><label class="lbl">备注</label><input v-model="createForm.app_notes" class="field" placeholder="简要说明" /></div>
        <button type="submit" class="btn btn-sys-teal" style="width:100%;padding:10px;justify-content:center"><i class="ph-bold ph-check"></i> 立即创建</button>
      </form>
    </div>

    <!-- Edit Modal -->
    <div v-if="editModal" style="position:fixed;inset:0;background:rgba(0,0,0,0.5);backdrop-filter:blur(8px);display:flex;align-items:center;justify-content:center;z-index:100;padding:16px" @click.self="editModal=false">
      <div style="background:rgba(12,12,18,0.4);border:0.5px solid var(--liquid-border-hover);border-radius:28px;padding:32px;width:100%;max-width:420px;backdrop-filter:blur(8px);box-shadow:0 24px 80px rgba(0,0,0,0.32)">
        <h3 style="font-size:14px;font-weight:700;margin-bottom:16px;color:rgba(255,255,255,0.9)">应用设置与更新</h3>
        <form @submit.prevent="saveEdit" style="display:flex;flex-direction:column;gap:12px">
          <div><label class="lbl">应用名称</label><input v-model="editForm.app_name" class="field" required /></div>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
            <div><label class="lbl">最新版本号</label><input v-model="editForm.app_version" class="field" /></div>
            <div><label class="lbl">更新下载链接</label><input v-model="editForm.update_url" class="field" /></div>
          </div>
          <div><label class="lbl">更新日志</label><textarea v-model="editForm.app_notes" class="field" rows="3"></textarea></div>
          <label style="display:flex;align-items:center;gap:8px;cursor:pointer;padding:12px;border-radius:12px;background:rgba(255,255,255,0.02);border:0.5px solid rgba(255,255,255,0.05)">
            <input type="checkbox" v-model="editForm.force_update" :true-value="1" :false-value="0" style="accent-color:var(--sys-pink)" />
            <span style="font-size:11px;font-weight:700;color:rgba(255,255,255,0.8)">开启强制更新</span>
          </label>
          <div style="display:flex;gap:8px;padding-top:8px">
            <button type="button" class="btn btn-liquid" style="flex:1;justify-content:center;padding:10px" @click="editModal=false">取消</button>
            <button type="submit" class="btn btn-sys-blue" style="flex:1;justify-content:center;padding:10px">保存</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>
