<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const isLoggedIn = computed(() => authStore.isLoggedIn)
const currentPath = computed(() => route.path)

const navItems = [
  { path: '/dashboard', label: '数据总览', icon: 'ph-squares-four' },
  { path: '/apps', label: '应用管理', icon: 'ph-app-window' },
  { path: '/cards', label: '卡密库存', icon: 'ph-database' },
  { path: '/create', label: '批量制卡', icon: 'ph-magic-wand' },
  { path: '/blacklist', label: '云黑管理', icon: 'ph-shield-warning' },
  { path: '/logs', label: '审计日志', icon: 'ph-clock-counter-clockwise' },
  { path: '/settings', label: '全局配置', icon: 'ph-gear' },
  { path: '/about', label: '关于系统', icon: 'ph-info' },
]

onMounted(() => {
  if (authStore.isLoggedIn) {
    authStore.verifyToken()
  }
})

function logout() {
  authStore.logout()
  router.push('/login')
}
</script>

<template>
  <div class="app-root">
    <!-- Sidebar (desktop) -->
    <aside v-if="isLoggedIn" class="sidebar">
      <div style="display:flex;flex-direction:column;align-items:center;padding:24px 14px 20px;margin-bottom:6px;border-radius:22px;background:linear-gradient(170deg,rgba(255,55,95,0.04) 0%,rgba(191,90,242,0.025) 40%,rgba(10,132,255,0.02) 100%);border:0.5px solid rgba(255,255,255,0.05)">
        <div style="font-size:16px;font-weight:800;letter-spacing:-0.03em">GuYi Access <span style="background:linear-gradient(135deg,#ff375f,#bf5af2);-webkit-background-clip:text;-webkit-text-fill-color:transparent;font-weight:900">Pro</span></div>
        <div style="font-size:9.5px;color:var(--text-4);font-family:'JetBrains Mono',monospace;letter-spacing:0.14em;margin-top:8px">{{ authStore.username }}</div>
      </div>

      <div class="nav-group-label">概览</div>
      <router-link v-for="item in navItems.slice(0,1)" :key="item.path" :to="item.path" class="nav-link" :class="{ on: currentPath === item.path }">
        <i :class="'ph-fill ' + item.icon"></i> {{ item.label }}
      </router-link>

      <div class="nav-group-label">核心业务</div>
      <router-link v-for="item in navItems.slice(1,5)" :key="item.path" :to="item.path" class="nav-link" :class="{ on: currentPath === item.path }">
        <i :class="'ph-fill ' + item.icon"></i> {{ item.label }}
      </router-link>

      <div class="nav-group-label">系统监控</div>
      <router-link v-for="item in navItems.slice(5)" :key="item.path" :to="item.path" class="nav-link" :class="{ on: currentPath === item.path }">
        <i :class="'ph-fill ' + item.icon"></i> {{ item.label }}
      </router-link>

      <div style="margin-top:auto;padding-top:24px">
        <div style="height:0.5px;margin:0 14px 12px;background:linear-gradient(90deg,transparent,rgba(255,255,255,0.06),transparent)"></div>
        <a @click.prevent="logout" class="nav-link" style="color:rgba(255,69,58,0.45);cursor:pointer">
          <i class="ph-bold ph-sign-out"></i> 退出登录
        </a>
      </div>
    </aside>

    <!-- Main content -->
    <main class="main-content">
      <router-view />
    </main>

    <!-- Mobile bottom nav -->
    <nav v-if="isLoggedIn" class="m-bottom-nav" style="display:none">
      <router-link v-for="item in navItems" :key="item.path" :to="item.path" class="m-nav-item" :class="{ on: currentPath === item.path }">
        <i :class="'ph-fill ' + item.icon"></i>
        <span>{{ item.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<style>
.m-bottom-nav { display: none; position: fixed; bottom: 0; left: 0; right: 0; z-index: 60; background: rgba(8, 8, 14, 0.55); border-top: 0.5px solid rgba(255, 255, 255, 0.05); backdrop-filter: blur(20px) saturate(150%); justify-content: space-around; padding: 0 4px; }
.m-nav-item { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 3px; padding: 10px 0 8px; color: var(--text-4); transition: all 0.35s var(--apple); cursor: pointer; flex: 1; text-decoration: none; min-height: 52px; }
.m-nav-item i { font-size: 20px; }
.m-nav-item span { font-size: 9px; font-weight: 700; }
.m-nav-item.on { color: var(--text-1); }
.m-nav-item.on i { color: var(--sys-pink); }
@media (max-width: 768px) {
  .m-bottom-nav { display: flex !important; }
}
</style>
