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
