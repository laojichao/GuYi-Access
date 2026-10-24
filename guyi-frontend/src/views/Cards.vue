<script setup>
import { ref, onMounted, computed } from 'vue'
import { adminApi } from '../api/admin'

const cards = ref([])
const apps = ref([])
const total = ref(0)
const loading = ref(true)
const page = ref(1)
const limit = ref(20)
const filter = ref('all')
const appFilter = ref('')
const typeFilter = ref('')
const searchQuery = ref('')
const sort = ref('create_desc')
const selectedIds = ref([])

const cardTypes = ref({})

onMounted(async () => {
  try {
    const [appsRes, typesRes] = await Promise.all([
      adminApi.getApps(),
      adminApi.getCardTypes()
    ])
    if (appsRes.data.code === 200) apps.value = appsRes.data.data
    if (typesRes.data.code === 200) cardTypes.value = typesRes.data.data
  } catch (e) {}
})

/**
 * @param {boolean} [resetPage] set when the filter itself changed (app, type, status, keyword,
 *   page size). Keeping the current page across a filter change can ask for a page beyond the new
 *   result set, which answers an empty list that looks like "all data is gone".
 */
async function loadCards(resetPage = false) {
  loading.value = true
  selectedIds.value = []
  if (resetPage) page.value = 1
  try {
    const params = { page: page.value - 1, limit: limit.value, sort: sort.value }
    if (filter.value === 'unused') params.status = 0
    else if (filter.value === 'active') params.status = 1
    else if (filter.value === 'banned') params.status = 2
    if (appFilter.value) params.appId = appFilter.value
    if (typeFilter.value) params.type = typeFilter.value
    if (searchQuery.value) params.q = searchQuery.value

    const res = await adminApi.getCards(params)
    if (res.data.code === 200) {
      cards.value = res.data.data.cards || []
      total.value = res.data.data.total || 0
    }
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

function getStatusLabel(card) {
  if (card.status === 2) return { text: '已封禁', cls: 'pill-banned' }
  if (card.status === 1) {
    if (new Date(card.expireTime) <= new Date()) return { text: '已过期', cls: 'pill-banned' }
    if (!card.deviceHash) return { text: '待绑定', cls: 'pill-admin' }
    return { text: '使用中', cls: 'pill-on' }
  }
  return { text: '闲置', cls: 'pill-free' }
}

async function action(act, id) {
  if (!confirm('确定操作？')) return
  try {
    let res
    if (act === 'delete') res = await adminApi.deleteCard(id)
    else if (act === 'ban') res = await adminApi.setCardStatus(id, 2)
    else if (act === 'unban') res = await adminApi.setCardStatus(id, 1)
    else if (act === 'unbind') res = await adminApi.unbindCard(id)
    if (res?.data?.code === 200) loadCards()
    else alert(res?.data?.msg || '操作失败')
  } catch (e) { alert('操作失败') }
}

/** prompt() returns a string; validate it the same way the backend does (1-8760 hours). */
function parseHours(input, label) {
  const hours = parseFloat(input)
  if (!isFinite(hours) || hours <= 0 || hours > 8760) {
    alert(`${label}必须是 1-8760 之间的数字`)
    return null
  }
  return hours
}

function toggleSelect(id) {
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) selectedIds.value.splice(idx, 1)
  else selectedIds.value.push(id)
}

function toggleSelectAll() {
  if (selectedIds.value.length === cards.value.length) selectedIds.value = []
  else selectedIds.value = cards.value.map(c => c.id)
}

async function batchAction(act) {
  // These two act on every in-use card of the selected application (or of the whole system), not on
  // the checked rows - gating them behind a selection is both misleading and blocking.
  if (act === 'global-compensate') return globalCompensate()
  if (act === 'clean-expired') return cleanExpired()

  if (!selectedIds.value.length) { alert('请先勾选卡密'); return }
  if (!confirm('确定执行批量操作？')) return
  try {
    let res
    if (act === 'delete') res = await adminApi.batchDelete(selectedIds.value)
    else if (act === 'unbind') res = await adminApi.batchUnbind(selectedIds.value)
    else if (act === 'add-time') {
      const input = prompt('增加小时数', '24')
      if (input === null) return
      const hours = parseHours(input, '增加时长')
      if (hours === null) return
      res = await adminApi.batchAddTime(selectedIds.value, hours)
    }
    else if (act === 'sub-time') {
      const input = prompt('扣除小时数', '24')
      if (input === null) return
      const hours = parseHours(input, '扣除时长')
      if (hours === null) return
      res = await adminApi.batchSubTime(selectedIds.value, hours)
    }
    else if (act === 'export') {
      try {
        const res = await adminApi.exportCards(selectedIds.value)
        if (res.status !== 200) { alert('导出失败'); return }
        const url = URL.createObjectURL(res.data)
        const a = document.createElement('a'); a.href = url; a.download = `cards_export_${Date.now()}.txt`; a.click()
        URL.revokeObjectURL(url)
      } catch (err) {
        // The endpoint answers a transport-level error (400 "未选择要导出的卡密") with a real HTTP
        // status, so axios rejects: read the JSON body out of the blob to show the actual reason.
        let msg = '导出失败'
        try {
          const text = await err.response?.data?.text?.()
          if (text) msg = JSON.parse(text).msg || msg
        } catch (e) { /* keep the generic message */ }
        alert(msg)
      }
      return
    }
    if (res?.data?.code === 200) loadCards()
    else alert(res?.data?.msg || '操作失败')
  } catch (e) { alert('操作失败') }
}

/**
 * Adds time to every in-use card of the current application filter (or of the whole system when no
 * application is selected). Independent of the checked rows on purpose - the backend only reads
 * hours/app_id, so requiring a selection here would be a meaningless extra step.
 */
async function globalCompensate() {
  const input = prompt('为所有在用卡密统一补偿小时数:', '12')
  if (input === null) return
  const hours = parseHours(input, '补偿时长')
  if (hours === null) return
  if (!confirm(`将为${appFilter.value ? '当前应用' : '全部应用'}的所有在用卡密增加 ${hours} 小时，确定？`)) return
  const body = { hours }
  if (appFilter.value) body.app_id = parseInt(appFilter.value)
  try {
    const res = await adminApi.globalCompensate(body)
    if (res.data.code === 200) loadCards()
    else alert(res.data.msg || '操作失败')
  } catch (e) { alert('操作失败') }
}

/** Deletes every expired card system-wide. Also independent of the checked rows. */
async function cleanExpired() {
  if (!confirm('将清理所有已过期的卡密（不可恢复），确定？')) return
  try {
    const res = await adminApi.cleanExpired()
    if (res.data.code === 200) loadCards()
    else alert(res.data.msg || '操作失败')
  } catch (e) { alert('操作失败') }
}

/** Three states so the user can get back to the default (newest first) ordering. */
function toggleSort() {
  if (sort.value === 'expire_asc') sort.value = 'expire_desc'
  else if (sort.value === 'expire_desc') sort.value = 'create_desc'
  else sort.value = 'expire_asc'
  loadCards()
}

// Neutral glyph while the list is sorted by creation time, so the header does not advertise an
// expiry ordering that is not in effect.
const sortIconClass = computed(() =>
  sort.value === 'expire_asc' ? 'ph-bold ph-sort-ascending'
    : sort.value === 'expire_desc' ? 'ph-bold ph-sort-descending'
      : 'ph-bold ph-arrows-down-up')

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

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / limit.value)))
</script>

<template>
  <div>
    <div class="rise" style="margin-bottom:24px">
      <h2 class="pg-title">卡密库存</h2>
      <p class="pg-sub">共 {{ total }} 条记录</p>
    </div>

    <!-- Search & Filters -->
    <div class="rise rise-1" style="display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px">
      <div style="position:relative;flex:1;min-width:150px">
        <input v-model="searchQuery" class="field" style="padding-left:36px;height:38px;font-size:12px" placeholder="模糊搜索..." @keyup.enter="loadCards(true)" />
        <i class="ph-bold ph-magnifying-glass" style="position:absolute;left:12px;top:50%;transform:translateY(-50%);color:var(--text-4);font-size:13px"></i>
      </div>
      <select v-model="appFilter" class="field-s" style="height:38px;font-size:11px;min-width:120px" @change="typeFilter=''; loadCards(true)">
        <option value="">全部应用</option>
        <option v-for="app in apps" :key="app.id" :value="app.id">{{ app.app_name }}</option>
      </select>
      <select v-if="appFilter" v-model="typeFilter" class="field-s" style="height:38px;font-size:11px;min-width:100px" @change="loadCards(true)">
        <option value="">全部类型</option>
        <option v-for="(cfg, key) in cardTypes" :key="key" :value="key">{{ cfg.name }}</option>
      </select>
      <button class="btn btn-sys-pink" style="height:38px" @click="loadCards"><i class="ph-bold ph-magnifying-glass"></i></button>
    </div>

    <!-- Filter Pills -->
    <div class="rise rise-1" style="display:flex;gap:6px;margin-bottom:12px;overflow-x:auto">
      <button v-for="f in [{v:'all',l:'全部'},{v:'unused',l:'未激活'},{v:'active',l:'已激活'},{v:'banned',l:'已封禁'}]" :key="f.v"
        :class="filter === f.v ? 'pill pill-vip' : 'pill pill-free'" style="font-size:9px;padding:6px 12px;cursor:pointer;border:none"
        @click="filter = f.v; loadCards(true)">{{ f.l }}</button>
    </div>

    <!-- Cards Table -->
    <div class="glass rise rise-2" style="overflow:hidden">
      <!-- Batch Toolbar -->
      <div style="display:flex;gap:6px;padding:8px;border-bottom:0.5px solid rgba(255,255,255,0.04);flex-wrap:wrap;background:rgba(255,255,255,0.012)">
        <button class="btn btn-sys-blue" style="font-size:10px;padding:6px 10px" @click="batchAction('export')"><i class="ph-bold ph-download-simple"></i> 导出</button>
        <button class="btn btn-sys-yellow" style="font-size:10px;padding:6px 10px" @click="batchAction('unbind')"><i class="ph-bold ph-link-break"></i> 解绑</button>
        <button class="btn btn-sys-green" style="font-size:10px;padding:6px 10px" @click="batchAction('add-time')"><i class="ph-bold ph-clock-plus"></i> 加时</button>
        <button class="btn btn-sys-teal" style="font-size:10px;padding:6px 10px" @click="batchAction('global-compensate')"><i class="ph-bold ph-lightning"></i> 全局加时</button>
        <button class="btn btn-sys-purple" style="font-size:10px;padding:6px 10px" @click="batchAction('sub-time')"><i class="ph-bold ph-clock-counter-clockwise"></i> 扣时</button>
        <button class="btn btn-sys-orange" style="font-size:10px;padding:6px 10px" @click="batchAction('clean-expired')"><i class="ph-bold ph-broom"></i> 清理</button>
        <button class="btn btn-sys-red" style="font-size:10px;padding:6px 10px" @click="batchAction('delete')"><i class="ph-bold ph-trash"></i> 删除</button>
        <span v-if="selectedIds.length" style="font-size:9px;color:var(--text-3);align-self:center;margin-left:auto">已选 {{ selectedIds.length }} 项</span>
      </div>

      <div style="overflow-x:auto">
        <table style="width:100%;font-size:13px;border-collapse:collapse">
          <thead><tr style="border-bottom:0.5px solid var(--liquid-border)">
            <th style="padding:14px;text-align:center;width:36px"><input type="checkbox" :checked="selectedIds.length === cards.length && cards.length > 0" @change="toggleSelectAll" style="accent-color:var(--sys-pink)" /></th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">应用</th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">卡密代码</th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">状态</th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">到期时间 <button @click="toggleSort" title="按到期时间排序" style="background:none;border:none;cursor:pointer;color:var(--sys-pink);font-size:10px"><i :class="sortIconClass"></i></button></th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">设备</th>
            <th style="padding:14px;text-align:right;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">操作</th>
          </tr></thead>
          <tbody>
            <tr v-for="card in cards" :key="card.id" style="border-bottom:0.5px solid rgba(255,255,255,0.02)">
              <td style="padding:14px;text-align:center"><input type="checkbox" :checked="selectedIds.includes(card.id)" @change="toggleSelect(card.id)" style="accent-color:var(--sys-pink)" /></td>
              <td style="padding:14px"><span class="pill pill-big" style="font-size:9px">{{ card.appName || apps.find(a => a.id === card.appId)?.app_name || '未分类' }}</span></td>
              <td style="padding:14px"><span class="pill pill-free" style="font-size:10px;font-family:'JetBrains Mono',monospace;cursor:pointer" @click="copyText(card.cardCode, 'card-' + card.id)"><i v-if="copiedKey === 'card-' + card.id" class="ph-bold ph-check" style="color:var(--sys-green);margin-right:4px"></i>{{ card.cardCode }}</span></td>
              <td style="padding:14px"><span class="pill" :class="getStatusLabel(card).cls" style="font-size:9px">{{ getStatusLabel(card).text }}</span></td>
              <td style="padding:14px;font-size:9.5px;font-family:'JetBrains Mono',monospace;color:var(--text-4)">{{ card.expireTime ? new Date(card.expireTime).toLocaleString() : '-' }}</td>
              <td style="padding:14px;font-size:9.5px;font-family:'JetBrains Mono',monospace;color:var(--text-4)">{{ card.deviceHash ? card.deviceHash.substring(0, 10) + '...' : '-' }}</td>
              <td style="padding:14px;text-align:right">
                <div style="display:flex;gap:4px;justify-content:flex-end">
                  <button v-if="card.status === 1 && card.deviceHash" class="btn btn-sys-yellow" style="font-size:10px;padding:4px 8px" @click="action('unbind', card.id)"><i class="ph-bold ph-link-break"></i></button>
                  <button v-if="card.status !== 2" class="btn btn-sys-orange" style="font-size:10px;padding:4px 8px" @click="action('ban', card.id)"><i class="ph-bold ph-prohibit"></i></button>
                  <button v-else class="btn btn-sys-green" style="font-size:10px;padding:4px 8px" @click="action('unban', card.id)"><i class="ph-bold ph-check"></i></button>
                  <button class="btn btn-sys-red" style="font-size:10px;padding:4px 8px" @click="action('delete', card.id)"><i class="ph-bold ph-trash"></i></button>
                </div>
              </td>
            </tr>
            <tr v-if="!cards.length"><td colspan="7" style="text-align:center;color:var(--text-4);font-size:11px;padding:40px">暂无符合条件的卡密</td></tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div style="display:flex;align-items:center;justify-content:space-between;padding:10px;border-top:0.5px solid rgba(255,255,255,0.04)">
        <select v-model="limit" class="field-s" style="font-size:10px;padding:4px 24px 4px 8px;border-radius:8px" @change="loadCards(true)">
          <option :value="10">10/页</option>
          <option :value="20">20/页</option>
          <option :value="50">50/页</option>
        </select>
        <div style="display:flex;align-items:center;gap:8px">
          <button v-if="page > 1" class="btn btn-liquid" style="font-size:10px;padding:6px 8px" @click="page--; loadCards()"><i class="ph-bold ph-caret-left"></i></button>
          <span style="font-size:10px;font-family:'JetBrains Mono',monospace;color:var(--text-4)">{{ page }}/{{ totalPages }}</span>
          <button v-if="page < totalPages" class="btn btn-liquid" style="font-size:10px;padding:6px 8px" @click="page++; loadCards()"><i class="ph-bold ph-caret-right"></i></button>
        </div>
      </div>
    </div>
  </div>
</template>
