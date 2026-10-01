<script setup>
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '../stores/auth'
import { adminApi } from '../api/admin'
import {
  Chart as ChartJS, ArcElement, BarElement, CategoryScale, LinearScale, Tooltip, Legend
} from 'chart.js'
import { Doughnut, Bar } from 'vue-chartjs'

// Register only what the dashboard draws, keeping the bundled chart code minimal
ChartJS.register(ArcElement, BarElement, CategoryScale, LinearScale, Tooltip, Legend)

const authStore = useAuthStore()
const dashboard = ref({ stats: { total: 0, unused: 0, used: 0, banned: 0, expired: 0, active: 0, apps: 0 }, chart_types: {}, app_stats: [] })
const apps = ref([])
const logs = ref([])
const loading = ref(true)

const cardTypeNames = { hour: '小时卡', day: '天卡', week: '周卡', month: '月卡', season: '季卡', year: '年卡' }
const typeColors = ['#64d2ff', '#ff375f', '#ffd60a', '#0a84ff', '#bf5af2', '#30d158']

// ---- Chart.js data & options ----
const hasTypeData = computed(() => Object.keys(dashboard.value.chart_types || {}).length > 0)

// Entries keep Object.keys order, so the doughnut segments and the legend beside it share colours
const typeChartData = computed(() => {
  const entries = Object.entries(dashboard.value.chart_types || {})
  return {
    labels: entries.map(([key]) => cardTypeNames[key] || key),
    datasets: [{
      data: entries.map(([, count]) => count),
      backgroundColor: entries.map((_, i) => typeColors[i % typeColors.length]),
      borderWidth: 0
    }]
  }
})

const appChartData = computed(() => {
  const stats = dashboard.value.app_stats || []
  return {
    labels: stats.map(s => s.app_name),
    datasets: [{
      label: '卡密数量',
      data: stats.map(s => s.count),
      backgroundColor: 'rgba(255,55,95,0.55)',
      borderColor: '#ff375f',
      borderWidth: 1,
      borderRadius: 6
    }]
  }
})

const tooltipStyle = {
  backgroundColor: 'rgba(10,12,24,0.95)',
  borderColor: 'rgba(255,255,255,0.15)',
  borderWidth: 1,
  padding: 10
}

const doughnutOptions = {
  responsive: true,
  maintainAspectRatio: false,
  cutout: '62%',
  plugins: { legend: { display: false }, tooltip: tooltipStyle }
}

const barOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: false }, tooltip: tooltipStyle },
  scales: {
    x: { ticks: { color: '#b9c3e6', font: { size: 10 } }, grid: { display: false } },
    y: {
      beginAtZero: true,
      ticks: { color: '#b9c3e6', font: { size: 10 }, precision: 0 },
      grid: { color: 'rgba(255,255,255,0.06)' }
    }
  }
}

onMounted(async () => {
  try {
    const [dashRes, appsRes, logsRes] = await Promise.all([
      adminApi.getDashboard(),
      adminApi.getApps(),
      adminApi.getLogs({ limit: 5 })
    ])
    if (dashRes.data.code === 200) dashboard.value = dashRes.data.data
    if (appsRes.data.code === 200) apps.value = appsRes.data.data
    if (logsRes.data.code === 200) logs.value = logsRes.data.data?.content || logsRes.data.data || []
  } catch (e) { console.error(e) }
  finally { loading.value = false }
})

function formatNumber(n) {
  return n?.toLocaleString() || '0'
}

function getTypePercent(typeKey) {
  const total = Object.values(dashboard.value.chart_types || {}).reduce((a, b) => a + b, 0)
  return total > 0 ? Math.round(((dashboard.value.chart_types[typeKey] || 0) / total) * 100) : 0
}
</script>

<template>
  <div>
    <div class="rise" style="margin-bottom:24px">
      <h2 class="pg-title">欢迎，{{ authStore.username }}</h2>
      <p class="pg-sub">GuYi Access Pro Dashboard</p>
    </div>

    <!-- Stats Cards -->
    <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:12px;margin-bottom:24px" class="rise rise-1">
      <div class="stat" style="--stat-glow:rgba(10,132,255,0.025)">
        <div style="width:38px;height:38px;border-radius:13px;background:rgba(10,132,255,0.06);display:flex;align-items:center;justify-content:center"><i class="ph-fill ph-database" style="color:var(--sys-blue);font-size:16px"></i></div>
        <div class="stat-num" style="color:var(--sys-blue)">{{ formatNumber(dashboard.stats.total) }}</div>
        <div class="stat-lbl">总库存量</div>
      </div>
      <div class="stat" style="--stat-glow:rgba(48,209,88,0.025)">
        <div style="width:38px;height:38px;border-radius:13px;background:rgba(48,209,88,0.06);display:flex;align-items:center;justify-content:center"><i class="ph-fill ph-wifi-high" style="color:var(--sys-green);font-size:16px"></i></div>
        <div class="stat-num" style="color:var(--sys-green)">{{ formatNumber(dashboard.stats.active) }}</div>
        <div class="stat-lbl">活跃设备</div>
      </div>
      <div class="stat" style="--stat-glow:rgba(191,90,242,0.025)">
        <div style="width:38px;height:38px;border-radius:13px;background:rgba(191,90,242,0.06);display:flex;align-items:center;justify-content:center"><i class="ph-fill ph-app-window" style="color:var(--sys-purple);font-size:16px"></i></div>
        <div class="stat-num" style="color:var(--sys-purple)">{{ apps.length }}</div>
        <div class="stat-lbl">接入应用</div>
      </div>
      <div class="stat" style="--stat-glow:rgba(255,214,10,0.025)">
        <div style="width:38px;height:38px;border-radius:13px;background:rgba(255,214,10,0.06);display:flex;align-items:center;justify-content:center"><i class="ph-fill ph-tag" style="color:var(--sys-yellow);font-size:16px"></i></div>
        <div class="stat-num" style="color:var(--sys-yellow)">{{ formatNumber(dashboard.stats.unused) }}</div>
        <div class="stat-lbl">待售库存</div>
      </div>
      <div class="stat" style="--stat-glow:rgba(255,159,10,0.025)">
        <div style="width:38px;height:38px;border-radius:13px;background:rgba(255,159,10,0.06);display:flex;align-items:center;justify-content:center"><i class="ph-fill ph-hourglass-medium" style="color:var(--sys-orange);font-size:16px"></i></div>
        <div class="stat-num" style="color:var(--sys-orange)">{{ formatNumber(dashboard.stats.expired) }}</div>
        <div class="stat-lbl">已过期</div>
      </div>
      <div class="stat" style="--stat-glow:rgba(255,55,95,0.025)">
        <div style="width:38px;height:38px;border-radius:13px;background:rgba(255,55,95,0.06);display:flex;align-items:center;justify-content:center"><i class="ph-fill ph-prohibit" style="color:var(--sys-red);font-size:16px"></i></div>
        <div class="stat-num" style="color:var(--sys-red)">{{ formatNumber(dashboard.stats.banned) }}</div>
        <div class="stat-lbl">已封禁</div>
      </div>
    </div>

    <!-- Card Type Distribution + App Distribution -->
    <div style="display:grid;grid-template-columns:1fr 2fr;gap:12px;margin-bottom:16px" class="rise rise-2">
      <div class="glass" style="padding:20px">
        <h3 style="font-size:12px;font-weight:700;margin-bottom:16px;color:var(--text-3)"><i class="ph-fill ph-chart-donut" style="color:var(--sys-teal)"></i> 卡密类型分析</h3>
        <div v-if="hasTypeData" style="display:flex;align-items:center;gap:16px">
          <div style="width:132px;height:132px;flex-shrink:0">
            <Doughnut :data="typeChartData" :options="doughnutOptions" />
          </div>
          <div style="display:flex;flex-direction:column;gap:10px;flex:1;min-width:0">
            <div v-for="(count, key) in dashboard.chart_types" :key="key" style="display:flex;align-items:center;gap:8px">
              <div style="width:8px;height:8px;border-radius:50%;flex-shrink:0" :style="{ background: typeColors[Object.keys(dashboard.chart_types).indexOf(key) % 6] }"></div>
              <span style="font-size:10px;color:var(--text-2);flex:1">{{ cardTypeNames[key] || key }}</span>
              <span style="font-size:10px;font-weight:700;color:var(--text-1)">{{ getTypePercent(key) }}%</span>
            </div>
          </div>
        </div>
        <div v-else style="text-align:center;color:var(--text-4);font-size:10px;padding:12px">暂无数据</div>
      </div>
      <div class="glass" style="padding:20px">
        <h3 style="font-size:12px;font-weight:700;margin-bottom:16px;color:var(--text-3)"><i class="ph-fill ph-chart-bar" style="color:var(--sys-pink)"></i> 应用库存分布</h3>
        <div v-if="dashboard.app_stats?.length" style="height:190px">
          <Bar :data="appChartData" :options="barOptions" />
        </div>
        <div v-else style="text-align:center;color:var(--text-4);font-size:11px;padding:24px">暂无应用数据</div>
      </div>
    </div>

    <!-- Recent Events Log -->
    <div class="glass rise rise-3" style="padding:20px">
      <h3 style="font-size:12px;font-weight:700;margin-bottom:12px;color:var(--text-3)"><i class="ph-fill ph-history" style="color:var(--sys-orange)"></i> 最近事件记录</h3>
      <div v-if="logs.length" style="display:flex;flex-direction:column;gap:8px">
        <div v-for="log in logs" :key="log.id" style="display:flex;align-items:start;gap:12px;padding:10px;border-radius:12px;background:rgba(0,0,0,0.12);border:0.5px solid rgba(255,255,255,0.025)">
          <div style="width:24px;height:24px;border-radius:8px;background:rgba(255,159,10,0.1);display:flex;align-items:center;justify-content:center;flex-shrink:0"><i class="ph-fill ph-lightning" style="color:var(--sys-orange);font-size:11px"></i></div>
          <div style="flex:1;min-width:0">
            <div style="font-size:11px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:rgba(255,255,255,0.85);font-weight:700">{{ log.result || '系统事件' }} <span style="font-size:9px;color:var(--text-4);font-weight:400;margin-left:4px">({{ log.appName || 'System' }})</span></div>
            <div style="font-size:9px;color:var(--text-4);margin-top:2px;word-break:break-all">{{ log.cardCode || '执行了操作' }}</div>
            <div style="font-size:8.5px;color:rgba(255,255,255,0.2);font-family:'JetBrains Mono',monospace;margin-top:4px">{{ log.accessTime ? new Date(log.accessTime).toLocaleString() : '-' }}</div>
          </div>
        </div>
      </div>
      <div v-else style="text-align:center;color:var(--text-4);font-size:10px;padding:32px">风平浪静，暂无记录</div>
    </div>
  </div>
</template>
