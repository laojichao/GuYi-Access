<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '../api/admin'

const blacklist = ref([])
const form = ref({ type: 'device', value: '', reason: '' })
const loading = ref(true)

onMounted(loadBlacklist)

async function loadBlacklist() {
  loading.value = true
  try {
    const res = await adminApi.getBlacklist()
    if (res.data.code === 200) blacklist.value = res.data.data
  } catch (e) {}
  finally { loading.value = false }
}

async function addBlacklist() {
  if (!form.value.value.trim()) { alert('封禁目标不能为空'); return }
  try {
    const res = await adminApi.addBlacklist(form.value)
    if (res.data.code === 200) { form.value = { type: 'device', value: '', reason: '' }; loadBlacklist() }
    else alert(res.data.msg)
  } catch (e) { alert('添加失败') }
}

async function removeBlacklist(id) {
  if (!confirm('确定解除封禁？')) return
  await adminApi.deleteBlacklist(id)
  loadBlacklist()
}
</script>

<template>
  <div>
    <div class="rise" style="margin-bottom:24px">
      <h2 class="pg-title">全局云黑</h2>
      <p class="pg-sub">跨应用拦截恶意设备与IP</p>
    </div>

    <!-- Add Form -->
    <div class="glass rise rise-1" style="padding:20px;margin-bottom:16px">
      <form @submit.prevent="addBlacklist" style="display:flex;flex-wrap:wrap;gap:8px;align-items:flex-end">
        <div style="flex:1;min-width:120px"><label class="lbl">封禁类型</label>
          <select v-model="form.type" class="field-s" style="padding:10px"><option value="device">设备特征码</option><option value="ip">IP 地址</option></select>
        </div>
        <div style="flex:2;min-width:150px"><label class="lbl">封禁目标</label><input v-model="form.value" class="field" required placeholder="设备Hash或IP" /></div>
        <div style="flex:2;min-width:150px"><label class="lbl">备注 (选填)</label><input v-model="form.reason" class="field" placeholder="如: 抓包破解" /></div>
        <button type="submit" class="btn btn-sys-red" style="padding:10px 20px"><i class="ph-bold ph-prohibit"></i> 全局拉黑</button>
      </form>
    </div>

    <!-- List -->
    <div class="glass rise rise-2" style="overflow:hidden">
      <div style="overflow-x:auto">
        <table style="width:100%;font-size:13px;border-collapse:collapse">
          <thead><tr style="border-bottom:0.5px solid var(--liquid-border)">
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">类型</th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">封禁目标</th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">原因</th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">时间</th>
            <th style="padding:14px;text-align:right;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">操作</th>
          </tr></thead>
          <tbody>
            <tr v-for="bl in blacklist" :key="bl.id" style="border-bottom:0.5px solid rgba(255,255,255,0.02)">
              <td style="padding:14px"><span :class="bl.type === 'ip' ? 'pill pill-admin' : 'pill pill-big'" style="font-size:9px">{{ bl.type === 'ip' ? 'IP 封禁' : '设备封禁' }}</span></td>
              <td style="padding:14px"><span class="pill pill-banned" style="font-size:10px;font-family:'JetBrains Mono',monospace">{{ bl.value }}</span></td>
              <td style="padding:14px;font-size:10px;color:var(--text-2)">{{ bl.reason || '无备注' }}</td>
              <td style="padding:14px;font-size:9.5px;font-family:'JetBrains Mono',monospace;color:var(--text-4)">{{ bl.createTime ? new Date(bl.createTime).toLocaleString() : '-' }}</td>
              <td style="padding:14px;text-align:right"><button class="btn btn-sys-green" style="font-size:10px;padding:4px 8px" @click="removeBlacklist(bl.id)"><i class="ph-bold ph-trash"></i> 解除</button></td>
            </tr>
            <tr v-if="!blacklist.length"><td colspan="5" style="text-align:center;color:var(--text-4);font-size:11px;padding:40px">当前无任何云黑记录</td></tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
