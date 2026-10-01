<script setup>
import { ref, onMounted, computed } from 'vue'
import { adminApi } from '../api/admin'

const logs = ref([])
const total = ref(0)
const page = ref(1)
const limit = ref(30)
const loading = ref(true)

onMounted(loadLogs)

async function loadLogs() {
  loading.value = true
  try {
    const res = await adminApi.getLogs({ page: page.value - 1, limit: limit.value })
    if (res.data.code === 200) {
      const data = res.data.data
      logs.value = data?.content || data || []
      total.value = data?.totalElements || logs.value.length || 0
    }
  } catch (e) {}
  finally { loading.value = false }
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / limit.value)))
</script>

<template>
  <div>
    <div class="rise" style="margin-bottom:24px">
      <h2 class="pg-title">审计日志</h2>
      <p class="pg-sub">各应用访问及心跳记录</p>
    </div>

    <div class="glass rise rise-1" style="overflow:hidden">
      <div style="overflow-x:auto">
        <table style="width:100%;font-size:13px;border-collapse:collapse">
          <thead><tr style="border-bottom:0.5px solid var(--liquid-border)">
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">时间</th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">应用</th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">动作</th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">对象</th>
            <th style="padding:14px;text-align:left;font-size:9px;font-weight:800;color:var(--text-4);text-transform:uppercase">IP</th>
          </tr></thead>
          <tbody>
            <tr v-for="log in logs" :key="log.id" style="border-bottom:0.5px solid rgba(255,255,255,0.02)">
              <td style="padding:14px;font-size:9.5px;font-family:'JetBrains Mono',monospace;color:var(--text-4)">{{ log.accessTime ? new Date(log.accessTime).toLocaleString() : '-' }}</td>
              <td style="padding:14px"><span class="pill pill-big" style="font-size:9px">{{ log.appName || 'System' }}</span></td>
              <td style="padding:14px"><span :class="log.result?.includes('拦截') || log.result?.includes('封禁') ? 'pill pill-banned' : 'pill pill-free'" style="font-size:9px">{{ log.result }}</span></td>
              <td style="padding:14px;font-size:10px;color:var(--text-2);max-width:150px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ log.cardCode }}</td>
              <td style="padding:14px;font-size:9.5px;font-family:'JetBrains Mono',monospace;color:rgba(10,132,255,0.6)">{{ log.ipAddress }}</td>
            </tr>
            <tr v-if="!logs.length"><td colspan="5" style="text-align:center;color:var(--text-4);font-size:11px;padding:40px">暂无日志</td></tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div style="display:flex;align-items:center;justify-content:space-between;padding:10px;border-top:0.5px solid rgba(255,255,255,0.04)">
        <select v-model="limit" class="field-s" style="font-size:10px;padding:4px 24px 4px 8px;border-radius:8px" @change="page=1; loadLogs()">
          <option :value="10">10/页</option>
          <option :value="30">30/页</option>
          <option :value="50">50/页</option>
        </select>
        <div style="display:flex;align-items:center;gap:8px">
          <button v-if="page > 1" class="btn btn-liquid" style="font-size:10px;padding:6px 8px" @click="page--; loadLogs()"><i class="ph-bold ph-caret-left"></i></button>
          <span style="font-size:10px;font-family:'JetBrains Mono',monospace;color:var(--text-4)">{{ page }}/{{ totalPages }}</span>
          <button v-if="page < totalPages" class="btn btn-liquid" style="font-size:10px;padding:6px 8px" @click="page++; loadLogs()"><i class="ph-bold ph-caret-right"></i></button>
        </div>
      </div>
    </div>
  </div>
</template>
