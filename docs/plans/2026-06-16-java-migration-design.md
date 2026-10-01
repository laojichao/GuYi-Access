# GuYi Access Pro - PHP to Java Migration Plan

## Overview
Migrate the entire GuYi Access Pro card authorization system from PHP to **Spring Boot + Vue 3 + Vite + MySQL**.

## Architecture
- **Backend**: Spring Boot 3.x, Spring Data JPA, Spring Security (JWT), MySQL
- **Frontend**: Vue 3 + Vite + Vue Router + Pinia + Naive UI + Axios
- **Auth**: JWT token-based (replacing PHP session + cookie trust)
- **Encryption**: AES-256-GCM via javax.crypto (compatible with existing PHP clients)

## Database
Keep MySQL with identical schema. JPA auto-creates tables on first run.
8 tables: applications, app_variables, cards, active_devices, usage_logs, blacklists, admin, system_settings

---

## Implementation Tasks

### Phase 1: Backend Foundation (Tasks 1-5)

**Task 1: Spring Boot Project Setup**
- Create Maven pom.xml with dependencies: spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-security, mysql-connector-j, jjwt, lombok
- Create application.yml with MySQL config, JPA settings, JWT secret
- Create main Application.java
- Create project directory structure

**Task 2: JPA Entity Classes (8 entities)**
- Application.java (applications table)
- AppVariable.java (app_variables table)
- Card.java (cards table)
- ActiveDevice.java (active_devices table)
- UsageLog.java (usage_logs table)
- Blacklist.java (blacklists table)
- Admin.java (admin table)
- SystemSetting.java (system_settings table)

**Task 3: Repository Layer (8 repositories)**
- JpaRepository interfaces for each entity
- Custom query methods as needed (e.g., findByAppKey, findByCardCode)

**Task 4: Service Layer - Core Business Logic**
- ApplicationService.java - CRUD for apps, variables
- CardService.java - Card verification (from database.php verifyCard), generation, batch operations
- AuthService.java - Login, JWT token generation/validation
- BlacklistService.java - Blacklist CRUD
- DashboardService.java - Stats aggregation
- SystemService.java - Settings, export/import
- AuditLogService.java - Usage log queries

**Task 5: Utility Classes**
- AesUtil.java - AES-256-GCM encrypt/decrypt (compatible with PHP openssl_encrypt)
- JwtUtil.java - JWT token generation/validation
- RateLimitFilter.java - IP-based rate limiting (60 req/min, from api.php logic)
- CardCodeGenerator.java - Secure random string generation (from database.php secureRandStr)

### Phase 2: API Layer (Tasks 6-8)

**Task 6: REST Controllers - Authentication**
- AuthController: POST /api/auth/login, POST /api/auth/logout, GET /api/auth/me
- JwtAuthenticationFilter - intercept & validate JWT from Authorization header
- SecurityConfig - CORS config, public/protected routes

**Task 7: REST Controllers - Admin API**
- AdminController:
  - GET/POST/PUT/DELETE /api/admin/apps
  - GET/POST /api/admin/apps/{id}/variables
  - GET /api/admin/dashboard
  - GET /api/admin/cards (with pagination, filtering, sorting)
  - POST /api/admin/cards/generate
  - POST /api/admin/cards/batch-delete, batch-unbind, batch-add-time, batch-sub-time
  - POST /api/admin/cards/global-compensate
  - PUT /api/admin/cards/{id}/status (ban/unban)
  - DELETE /api/admin/cards/{id}
  - GET/POST /api/admin/blacklist
  - DELETE /api/admin/blacklist/{id}
  - GET /api/admin/logs
  - POST /api/admin/settings
  - POST /api/admin/system/export
  - POST /api/admin/system/import
  - PUT /api/admin/password

**Task 8: REST Controllers - Client Verification API**
- VerifyController: POST /api/verify
  - Accepts: app_key, card_code, device_hash, action, custom_data
  - Actions: verify, generate, ban, unban, del_card, kick, unbind, ban_machine, blacklist
  - AES-256-GCM encrypted response when api_encrypt enabled
  - Rate limiting via RateLimitFilter
  - Compatible with existing client SDKs (C/C++, Python, Flutter, etc.)

### Phase 3: Frontend Foundation (Tasks 9-12)

**Task 9: Vue 3 Project Setup**
- Initialize with Vite: `npm create vue@latest`
- Install dependencies: vue-router, pinia, axios, naive-ui, @phosphor-icons/vue, chart.js
- Configure vite.config.js with proxy to Spring Boot backend
- Setup project structure: views/, components/, api/, stores/, router/, styles/

**Task 10: Frontend Core Infrastructure**
- router/index.js - Routes for all pages (login, dashboard, apps, cards, create, blacklist, logs, settings, about)
- stores/auth.js - Pinia store for auth state (token, user info)
- api/index.js - Axios instance with JWT interceptor (attach token, handle 401)
- api/auth.js, api/admin.js - API service modules
- styles/glass.css - Glassmorphism design system (from cards.css, adapted for Vue components)
- components/Sidebar.vue, components/MobileNav.vue, components/Toast.vue

**Task 11: Login Page**
- views/Login.vue - Glassmorphism login form with CSRF-free JWT auth
- Background image, floating petals animation, password toggle
- POST to /api/auth/login, store JWT in localStorage/Pinia

**Task 12: Layout & Dashboard**
- views/Dashboard.vue - Stats cards (total, active, apps, unused)
- Chart.js doughnut for card type distribution
- App inventory bar chart
- Active devices table, recent events log
- System announcements panel (fetch from remote URL)

### Phase 4: Frontend Business Pages (Tasks 13-17)

**Task 13: App Management Page**
- views/Apps.vue - App list table (desktop) + card list (mobile)
- Create app form, edit app modal, toggle status, delete
- Cloud variables tab - CRUD for app variables
- API endpoint display

**Task 14: Card Inventory Page**
- views/Cards.vue - Paginated card list with filters (status, app, type)
- Search, sort by expire time
- Batch operations toolbar (export, unbind, add time, subtract time, global compensate, clean expired, delete)
- Desktop table + mobile card layout

**Task 15: Card Generation Page**
- views/CreateCards.vue - Form with app selector, count, type, prefix, note
- Custom duration option
- Generate + auto-export to TXT

**Task 16: Blacklist Management Page**
- views/Blacklist.vue - Add blacklist form (device/IP), list with delete

**Task 17: Logs & Settings Pages**
- views/Logs.vue - Audit log table with pagination
- views/Settings.vue - Toggle settings (bg_blur, api_encrypt), password change, system migration (export/import JSON)
- views/About.vue - System info page with links

### Phase 5: Integration & Polish (Tasks 18-20)

**Task 18: Install Wizard**
- POST /api/admin/install - First-run setup endpoint
- Frontend Install.vue - DB config + admin password setup
- Auto-create tables, seed admin user, return JWT

**Task 19: Cross-cutting Concerns**
- Global exception handler (@ControllerAdvice)
- Request logging filter
- Health check endpoint
- CORS fine-tuning for production

**Task 20: Build & Deployment**
- Spring Boot: `mvn package` produces executable JAR
- Vue: `npm run build` produces static files
- Option A: Serve Vue static from Spring Boot (src/main/resources/static)
- Option B: Separate Nginx deployment
- Docker Compose file for MySQL + backend

---

## File Mapping (PHP → Java/Vue)

| PHP File | Java/Vue Equivalent |
|----------|-------------------|
| config.php | application.yml + SecurityConfig.java |
| database.php | Entity classes + Repository + Service layer |
| install.php | InstallController.java + Install.vue |
| login.php | AuthController.java + Login.vue |
| cards.php (1316 lines) | AdminController.java + 7 Vue pages |
| cards.css | styles/glass.css (Vue component styles) |
| Verifyfile/api.php | VerifyController.java |
| Verifyfile/captcha.php | CaptchaController.java (optional, JWT may not need captcha) |

---

## 实施偏差记录（2026-10 更新）

以下为实际实现与上文计划的差异，均为**已确认的取舍**，避免后续按原文验收时误判为缺失。

| 项 | 计划 | 实际实现 | 说明 |
|---|---|---|---|
| Task 9 前端依赖 | Naive UI + @phosphor-icons/vue + chart.js | **chart.js + vue-chartjs 已引入**；Naive UI 未采用（改手写 CSS `styles/glass.css`），图标改用 CDN（`index.html` 引入 `@phosphor-icons/web`） | 保留原 PHP 版毛玻璃视觉；图表按计划用 Chart.js，组件库与图标包不引入以避免风格改造 |
| Task 12 看板图表 | Chart.js 环形图 / 柱状图 | **已实现**：`Dashboard.vue` 用 Chart.js `Doughnut`（卡密类型分布，旁附图例与百分比）+ `Bar`（应用库存分布），并补充 `expired`/`banned` 统计卡 | 图表按原计划用 Chart.js；仅注册所需组件，且随看板路由懒加载 |
| Task 12 公告面板 | 从远程 URL 拉取公告 | 未实现 | 远程地址未定义，公告改由「云变量」下发 |
| Task 10 API 模块 | `api/auth.js`、`api/admin.js` | **已提供并全量采用**：覆盖全部端点，9 个视图 + store + App 共 40 处调用点已迁移（无直接调用残留） | 模块函数返回原始 axios promise，迁移为纯改名，行为不变 |
| Task 10 组件 | `components/Sidebar.vue`、`MobileNav.vue`、`Toast.vue` | 内联于 `App.vue` | 单页布局，拆分收益低 |
| Task 18 安装 | `InstallController.java` + `Install.vue` | 安装端点并入 `AdminController`；**`Install.vue` 已提供**（`/install` 路由 + 未安装自动跳转） | 避免为单端点单独建控制器 |
| Task 4 | `AuditLogService.java` | **已提供**（查询侧）；写入侧仍在 `CardService.logUsage` | 保持验证热路径不变 |
| Task 19 健康检查 | Health check endpoint | `GET /api/health`（自研，未引入 actuator） | 避免额外暴露 actuator 端点 |
| Task 19 请求日志 | Request logging filter | `RequestLoggingFilter`：仅记录方法/路径/状态/耗时/IP，**不记录请求体与查询串** | 防止卡密与令牌进入日志 |
| Task 20 静态托管 | Option A：Spring Boot 托管 Vue 静态文件 | **已实现**：`WebMvcConfig` 提供 SPA 回退（`/cards` 等深链回退 index.html，缺失资源仍 404），`-Pwith-frontend` profile 把 `dist/` 打进 JAR；已实测（JAR 内含 21 个 static 条目） | 默认部署仍推荐 Option B（Nginx 终结 TLS），单 JAR 方式见 `docs/deployment.md` 4.4 |
| Task 20 部署物料 | Docker Compose（MySQL + 后端） | **已提供**：`docker-compose.yml` + 后端/前端 `Dockerfile` + `nginx.conf` + `.env.example` | 作者环境无 Docker，**未实测** |
| 文件映射 | `CaptchaController.java` | 未实现 | 原文已标注 optional（JWT 方案无需验证码） |

### 安全修复对迁移语义的影响

原实现存在若干功能性缺陷（标准卡型生卡必然失败、加密响应与存量客户端不兼容、迁移导入关联错乱、
匿名可全局拉黑设备、未认证返回 403 等），已在同批修复中解决；相关**行为变更**（含破坏性的
`ban_machine` 鉴权收紧）集中记录在 `docs/upgrade-notes.md`，部署与升级前必读。
