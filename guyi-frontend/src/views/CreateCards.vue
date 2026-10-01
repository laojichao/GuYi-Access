<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '../api/admin'

const apps = ref([])
const form = ref({ app_id: '', type: 'day', num: 10, pre: '', note: '', custom_hours: 24 })
const showCustom = ref(false)
const result = ref(null)
const loading = ref(false)
const cardTypes = ref([])

function formatDuration(seconds) {
  if (seconds >= 86400 * 365) return Math.round(seconds / (86400 * 365)) + '年'
  if (seconds >= 86400 * 30) return Math.round(seconds / (86400 * 30)) + '个月'
  if (seconds >= 86400) return Math.round(seconds / 86400) + '天'
  if (seconds >= 3600) return Math.round(seconds / 3600) + '小时'
  return seconds + '秒'
}

onMounted(async () => {
  const [appsRes, typesRes] = await Promise.all([
    adminApi.getApps(),
    adminApi.getCardTypes()
  ])
  if (appsRes.data.code === 200) apps.value = appsRes.data.data.filter(a => a.status === 1)
  if (typesRes.data.code === 200) {
    cardTypes.value = Object.entries(typesRes.data.data).map(([key, cfg]) => ({
      key,
      name: cfg.name,
      dur: formatDuration(cfg.duration)
    }))
  }
})

async function generate(autoExport = false) {
  if (!form.value.app_id) { alert('请选择应用'); return }
  loading.value = true
  try {
    const payload = { ...form.value }
    if (payload.type !== 'custom') delete payload.custom_hours
    const res = await adminApi.generateCards(payload)
    if (res.data.code === 200) {
      result.value = res.data.data.cards
      if (autoExport && result.value) {
        const blob = new Blob([result.value.join('\r\n')], { type: 'text/plain' })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url; a.download = `new_cards_${Date.now()}.txt`; a.click()
        URL.revokeObjectURL(url)
      }
    } else alert(res.data.msg)
  } catch (e) { alert('生成失败') }
  finally { loading.value = false }
}
</script>

<template>
  <div>
    <div class="rise" style="margin-bottom:24px">
      <h2 class="pg-title">批量制卡</h2>
      <p class="pg-sub">快速为应用生成授权码</p>
    </div>

    <div class="glass rise rise-1" style="padding:20px;max-width:560px">
      <form @submit.prevent="generate(false)" style="display:flex;flex-direction:column;gap:16px">
        <div>
          <label class="lbl"><i class="ph-fill ph-app-window" style="color:var(--sys-purple);margin-right:4px"></i> 归属应用 (必选)</label>
          <select v-model="form.app_id" class="field-s" style="font-size:14px;font-weight:700;padding:12px" required>
            <option value="">-- 请选择目标应用 --</option>
            <option v-for="app in apps" :key="app.id" :value="app.id">{{ app.app_name }}</option>
          </select>
        </div>

        <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
          <div><label class="lbl">生成数量</label><input v-model.number="form.num" type="number" class="field" min="1" max="500" /></div>
          <div>
            <label class="lbl">套餐类型</label>
            <select v-model="form.type" class="field-s" @change="showCustom = form.type === 'custom'">
              <option v-for="t in cardTypes" :key="t.key" :value="t.key">{{ t.name }} ({{ t.dur }})</option>
              <option value="custom">任意自定义时长</option>
            </select>
          </div>
        </div>

        <div v-if="showCustom">
          <label class="lbl" style="color:var(--sys-pink)">自定义时长 (小时)</label>
          <input v-model.number="form.custom_hours" type="number" class="field" min="1" style="border-color:rgba(255,55,95,0.3)" />
        </div>

        <div style="display:grid;grid-template-columns:1fr 1fr;gap:12px">
          <div><label class="lbl">前缀 (选填)</label><input v-model="form.pre" class="field" placeholder="VIP-" /></div>
          <div><label class="lbl">备注 (选填)</label><input v-model="form.note" class="field" placeholder="批次说明" /></div>
        </div>

        <div style="display:flex;gap:8px;padding-top:16px;border-top:0.5px solid rgba(255,255,255,0.04)">
          <button type="submit" class="btn btn-sys-pink" style="flex:1;padding:12px;justify-content:center;font-size:12px" :disabled="loading">
            <i class="ph-bold ph-magic-wand"></i> 生成
          </button>
          <button type="button" class="btn btn-sys-green" style="flex:1;padding:12px;justify-content:center;font-size:12px" @click="generate(true)" :disabled="loading">
            <i class="ph-bold ph-download-simple"></i> 生成并导出(TXT)
          </button>
        </div>
      </form>
    </div>

    <!-- Result -->
    <div v-if="result" class="glass rise rise-2" style="padding:20px;margin-top:16px;max-width:560px">
      <h3 style="font-size:12px;font-weight:700;margin-bottom:12px;color:var(--sys-green)"><i class="ph-fill ph-check-circle"></i> 生成成功 ({{ result.length }} 张)</h3>
      <div style="max-height:200px;overflow-y:auto;font-family:'JetBrains Mono',monospace;font-size:11px;color:var(--text-2);line-height:1.8">
        <div v-for="(code, i) in result" :key="i">{{ code }}</div>
      </div>
    </div>
  </div>
</template>
