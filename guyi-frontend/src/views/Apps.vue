<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '../api/admin'

const apps = ref([])
const loading = ref(true)
const showCreate = ref(false)
const createForm = ref({ app_name: '', app_version: '', app_notes: '' })
const editModal = ref(false)
const editForm = ref({ id: 0, app_name: '', app_version: '', app_notes: '', update_url: '', force_update: 0 })

// Variables state
const varsModal = ref(false)
const varsAppId = ref(null)
const varsAppName = ref('')
const vars = ref([])
const varsLoading = ref(false)
const varForm = ref({ key: '', value: '', is_public: 0 })
const editingVarId = ref(null)
const editingVarForm = ref({ key: '', value: '', is_public: 0 })

onMounted(loadApps)

// navigator is not reachable from template expressions, so copying goes through here
const copiedKey = ref('')

async function copyText(text, key) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    }
    copiedKey.value = key
    setTimeout(() => { if (copiedKey.value === key) copiedKey.value = '' }, 1500)
  } catch (e) { console.error('copy failed', e) }
}

async function loadApps() {
  loading.value = true
  try {
    const res = await adminApi.getApps()
    if (res.data.code === 200) apps.value = res.data.data
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

async function createApp() {
  try {
    const res = await adminApi.createApp(createForm.value)
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
    const res = await adminApi.updateApp(editForm.value.id, editForm.value)
    if (res.data.code === 200) { editModal.value = false; loadApps() }
    else alert(res.data.msg)
  } catch (e) { alert('更新失败') }
}

async function toggleApp(id) {
  await adminApi.toggleApp(id)
  loadApps()
}

async function deleteApp(id) {
  if (!confirm('确定删除？')) return
  try {
    const res = await adminApi.deleteApp(id)
    if (res.data.code === 200) loadApps()
    else alert(res.data.msg)
  } catch (e) { alert(e.response?.data?.msg || '删除失败') }
}

// ========== Variables Management ==========

async function openVars(app) {
  varsAppId.value = app.id
  varsAppName.value = app.app_name
  varsModal.value = true
  editingVarId.value = null
  varForm.value = { key: '', value: '', is_public: 0 }
  await loadVars()
}

async function loadVars() {
  varsLoading.value = true
  try {
    const res = await adminApi.getVariables(varsAppId.value)
    if (res.data.code === 200) vars.value = res.data.data || []
  } catch (e) { console.error(e) }
  finally { varsLoading.value = false }
}

async function addVar() {
  if (!varForm.value.key.trim()) { alert('变量名不能为空'); return }
  try {
    const res = await adminApi.createVariable(varsAppId.value, varForm.value)
    if (res.data.code === 200) { varForm.value = { key: '', value: '', is_public: 0 }; loadVars() }
    else alert(res.data.msg)
  } catch (e) { alert('添加失败') }
}

function startEditVar(v) {
  editingVarId.value = v.id
  editingVarForm.value = { key: v.keyName || v.key_name || '', value: v.value || '', is_public: v.isPublic ?? v.is_public ?? 0 }
}

async function saveEditVar() {
  try {
    const res = await adminApi.updateVariable(editingVarId.value, editingVarForm.value)
    if (res.data.code === 200) { editingVarId.value = null; loadVars() }
    else alert(res.data.msg)
  } catch (e) { alert('更新失败') }
}

function cancelEditVar() {
  editingVarId.value = null
}

async function deleteVar(id) {
  if (!confirm('确定删除该变量？')) return
  try {
    const res = await adminApi.deleteVariable(id)
    if (res.data.code === 200) loadVars()
    else alert(res.data.msg)
  } catch (e) { alert('删除失败') }
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
              <td style="padding:14px"><span class="pill pill-free" style="font-size:9px;cursor:pointer" @click="copyText(app.app_key, 'app-' + app.id)"><i v-if="copiedKey === 'app-' + app.id" class="ph-bold ph-check" style="color:var(--sys-green);margin-right:4px"></i><i v-else class="ph-bold ph-key" style="color:#6bb0ff;margin-right:4px"></i>{{ app.app_key?.substring(0, 16) }}...</span></td>
              <td style="padding:14px"><span class="pill pill-big" style="font-size:9px">{{ app.card_count }} 张</span></td>
              <td style="padding:14px"><span :class="app.status === 1 ? 'pill pill-on' : 'pill pill-banned'" style="font-size:9px">{{ app.status === 1 ? '正常' : '禁用' }}</span></td>
              <td style="padding:14px;text-align:right">
                <div style="display:flex;gap:4px;justify-content:flex-end">
                  <button class="btn btn-sys-purple" style="font-size:10px;padding:4px 8px" @click="openVars(app)" title="变量管理"><i class="ph-bold ph-variable"></i></button>
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

    <!-- Variables Modal -->
    <div v-if="varsModal" style="position:fixed;inset:0;background:rgba(0,0,0,0.5);backdrop-filter:blur(8px);display:flex;align-items:center;justify-content:center;z-index:100;padding:16px" @click.self="varsModal=false">
      <div style="background:rgba(12,12,18,0.4);border:0.5px solid var(--liquid-border-hover);border-radius:28px;padding:32px;width:100%;max-width:580px;backdrop-filter:blur(8px);box-shadow:0 24px 80px rgba(0,0,0,0.32);max-height:80vh;display:flex;flex-direction:column">
        <h3 style="font-size:14px;font-weight:700;margin-bottom:16px;color:rgba(255,255,255,0.9)"><i class="ph-fill ph-variable" style="color:var(--sys-purple);margin-right:6px"></i>{{ varsAppName }} - 变量管理</h3>

        <!-- Add Variable Form -->
        <form @submit.prevent="addVar" style="display:flex;gap:8px;margin-bottom:16px;flex-wrap:wrap;align-items:flex-end">
          <div style="flex:1;min-width:100px"><label class="lbl">变量名</label><input v-model="varForm.key" class="field" required placeholder="如: api_url" /></div>
          <div style="flex:2;min-width:120px"><label class="lbl">值</label><input v-model="varForm.value" class="field" placeholder="变量值" /></div>
          <label style="display:flex;align-items:center;gap:4px;font-size:10px;color:var(--text-3);white-space:nowrap;padding-bottom:8px">
            <input type="checkbox" v-model="varForm.is_public" :true-value="1" :false-value="0" style="accent-color:var(--sys-purple)" /> 公开
          </label>
          <button type="submit" class="btn btn-sys-purple" style="padding:8px 14px;font-size:10px"><i class="ph-bold ph-plus"></i> 添加</button>
        </form>

        <!-- Variables List -->
        <div style="flex:1;overflow-y:auto">
          <div v-if="varsLoading" style="text-align:center;color:var(--text-4);font-size:11px;padding:24px">加载中...</div>
          <div v-else-if="!vars.length" style="text-align:center;color:var(--text-4);font-size:11px;padding:24px">暂无变量，添加后可在客户端通过API读取</div>
          <div v-else style="display:flex;flex-direction:column;gap:6px">
            <div v-for="v in vars" :key="v.id" style="padding:10px 12px;border-radius:12px;background:rgba(255,255,255,0.02);border:0.5px solid rgba(255,255,255,0.05)">
              <template v-if="editingVarId === v.id">
                <form @submit.prevent="saveEditVar" style="display:flex;gap:6px;align-items:flex-end;flex-wrap:wrap">
                  <input v-model="editingVarForm.key" class="field" style="flex:1;min-width:80px;font-size:11px;padding:6px 8px" required />
                  <input v-model="editingVarForm.value" class="field" style="flex:2;min-width:100px;font-size:11px;padding:6px 8px" />
                  <label style="display:flex;align-items:center;gap:3px;font-size:9px;color:var(--text-3);white-space:nowrap">
                    <input type="checkbox" v-model="editingVarForm.is_public" :true-value="1" :false-value="0" style="accent-color:var(--sys-purple)" /> 公开
                  </label>
                  <button type="submit" class="btn btn-sys-green" style="padding:5px 8px;font-size:9px"><i class="ph-bold ph-check"></i></button>
                  <button type="button" class="btn btn-liquid" style="padding:5px 8px;font-size:9px" @click="cancelEditVar"><i class="ph-bold ph-x"></i></button>
                </form>
              </template>
              <template v-else>
                <div style="display:flex;align-items:center;gap:8px">
                  <span style="font-family:'JetBrains Mono',monospace;font-size:11px;font-weight:700;color:var(--sys-purple)">{{ v.keyName || v.key_name }}</span>
                  <span v-if="(v.isPublic ?? v.is_public) === 1" class="pill pill-on" style="font-size:8px;padding:2px 6px">公开</span>
                  <span v-else class="pill pill-free" style="font-size:8px;padding:2px 6px">私有</span>
                  <span style="flex:1"></span>
                  <button class="btn btn-sys-blue" style="padding:3px 6px;font-size:9px" @click="startEditVar(v)"><i class="ph-bold ph-pencil-simple"></i></button>
                  <button class="btn btn-sys-red" style="padding:3px 6px;font-size:9px" @click="deleteVar(v.id)"><i class="ph-bold ph-trash"></i></button>
                </div>
                <div style="font-size:10px;color:var(--text-2);margin-top:4px;word-break:break-all;font-family:'JetBrains Mono',monospace">{{ v.value || '(空)' }}</div>
              </template>
            </div>
          </div>
        </div>

        <div style="margin-top:16px;padding-top:12px;border-top:0.5px solid rgba(255,255,255,0.05)">
          <button class="btn btn-liquid" style="width:100%;justify-content:center;padding:10px" @click="varsModal=false">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>
