# GuYi Access Pro · Java 版部署指南

本仓库包含两套实现，**本文件只描述 Java 版**（`guyi-backend` + `guyi-frontend`）。根目录的 `*.php`
与 `Verifyfile/` 是历史 PHP 版，见 [README](../README.md) 末尾说明。

```
guyi-backend/     Spring Boot 3.2.5 + Java 17 + MySQL（REST API，默认端口 8080）
guyi-frontend/    Vue 3 + Vite（管理后台，构建产物为静态文件）
docker-compose.yml  MySQL + 后端 + 前端（Nginx）一键编排
```

- 兼容性/行为变更：见 [upgrade-notes.md](upgrade-notes.md)
- 迁移设计文档：见 [plans/2026-06-16-java-migration-design.md](plans/2026-06-16-java-migration-design.md)

---

## 1. 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | 后端编译与运行 |
| Maven | 3.9+ | 建议用项目自带/系统 mvn，**不要用其他大版本的 gradle/mvn** |
| Node.js | 20+ | 仅构建前端需要 |
| MySQL | 8.0 | 需支持 utf8mb4 |

## 2. 环境变量（后端）

| 变量 | 必填 | 默认值 | 说明 |
|---|---|---|---|
| `DB_URL` | 否 | `jdbc:mysql://localhost:3306/guyi_access?...&useSSL=false` | 生产务必覆盖并启用 TLS（`useSSL=true&requireSSL=true` 且去掉 `allowPublicKeyRetrieval`） |
| `DB_USERNAME` | **是** | — | 数据库账号 |
| `DB_PASSWORD` | **是** | — | 数据库口令 |
| `JWT_SECRET` | **是** | — | **≥ 32 字节（256 位）**，否则应用拒绝启动；`openssl rand -hex 32` |
| `API_TOKEN` | **是** | — | 客户端管理动作（generate/ban/unban/del_card/kick）对接令牌 |
| `INSTALL_TOKEN` | 否 | 空 | 设置后，安装接口必须携带 `install_token`；公网部署强烈建议设置 |
| `CORS_ORIGINS` | 否 | `http://localhost:5173` | 允许的前端来源，逗号分隔 |
| `TRUST_PROXY` | 否 | `false` | 置于 Nginx 等反向代理之后时设为 `true`（限流与拉黑按 `X-Forwarded-For` 末段取真实 IP） |

可选调优（`application.yml` 中已有默认值）：`app.rate-limit.max-requests`（60/分钟）、
`app.rate-limit.login-max-requests`（5/分钟）、`app.login.max-failed-attempts`（5）、
`app.login.lock-minutes`（15）、`app.maintenance.enabled`、`app.maintenance.device-cleanup-cron`、
`app.maintenance.expired-card-purge-cron`、`app.admin.default-username`。

> 表结构由 JPA `ddl-auto=update` 在启动时自动创建/补齐，无需手工建表。

## 3. 方式一：Docker Compose（推荐）

```bash
cp .env.example .env      # 按注释填写（必填项留空会直接报错并拒绝启动）
docker compose up -d --build
docker compose logs -f backend
```

- 访问 `http://<host>/`，首次进入按提示完成安装（见第 5 节）。
- MySQL 数据持久化在命名卷 `mysql_data`。
- 前端容器内的 Nginx 会把 `/api/` 反代到后端，并把 SPA 路由回退到 `index.html`。

## 4. 方式二：手工部署

### 4.1 构建

```bash
# 后端：产出可执行 JAR
cd guyi-backend && mvn -DskipTests package
#   → target/guyi-access-2.0.0.jar

# 前端：产出静态文件
cd guyi-frontend && npm install && npm run build
#   → dist/
```

### 4.2 运行后端

```bash
export DB_URL='jdbc:mysql://127.0.0.1:3306/guyi_access?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai&useSSL=true&requireSSL=true'
export DB_USERNAME=guyi
export DB_PASSWORD='<db-password>'
export JWT_SECRET="$(openssl rand -hex 32)"
export API_TOKEN="$(openssl rand -hex 24)"
export INSTALL_TOKEN="$(openssl rand -hex 16)"   # 可选但推荐
export CORS_ORIGINS='https://admin.example.com'
export TRUST_PROXY=true

java -jar guyi-backend/target/guyi-access-2.0.0.jar
```

健康检查：`curl -s http://127.0.0.1:8080/api/health`
（`status=UP` 表示数据库可达；`installed=false` 表示尚未安装）

### 4.3 前端与反向代理

把 `guyi-frontend/dist/` 交给 Nginx，并反代 `/api`：

```nginx
server {
    listen 443 ssl;
    server_name admin.example.com;
    root /var/www/guyi/dist;
    index index.html;

    ssl_certificate     /etc/nginx/ssl/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/privkey.pem;

    # SPA history 路由：刷新 /cards 不能 404
    location / { try_files $uri $uri/ /index.html; }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 64m;   # 系统迁移导入（后端限制 50MB）
    }
}
```

### 4.4 方式三：单 JAR（Spring Boot 同时托管前端，Option A）

适合「一个进程搞定」的小型部署，无需 Nginx：

```bash
cd guyi-frontend && npm install && npm run build      # 必须先构建出 dist/
cd ../guyi-backend && mvn -Pwith-frontend -DskipTests package
java -jar target/guyi-access-2.0.0.jar
```

- `with-frontend` profile 会把 `guyi-frontend/dist/` 复制进 JAR 的 `static/` 目录；
- 后端已内置 **SPA 回退**（`WebMvcConfig`）：形如 `/cards` 的深链会返回 `index.html`，缺失的静态资源仍返回 404；
- `/api/**` 由控制器优先匹配，不会被静态资源规则遮蔽；
- 未使用该 profile 时行为不变（JAR 内无 `static/`，未知路径仍为 404）。

> 该方式下建议仍由 Nginx/负载均衡终结 TLS；若直接用 Spring Boot 暴露端口，请自行配置证书或置于内网。

## 5. 首次安装

安装接口 `POST /api/admin/install` 在系统未安装前对外开放（已纳入登录级限流），安装完成后自动关闭。

```bash
curl -s -X POST http://127.0.0.1:8080/api/admin/install \
  -H 'Content-Type: application/json' \
  -d '{"admin_password":"<至少6位>","install_token":"<若设置了 INSTALL_TOKEN>"}'
# → {"code":200,"msg":"安装成功","data":{"token":"<JWT>"}}
```

管理后台登录：访问 `https://admin.example.com/`，用**管理员密钥（密码）**登录（用户名默认 `GuYi`，
由 `app.admin.default-username` 与前端 `VITE_ADMIN_USERNAME` 共同决定，改动需两端一致）。

## 6. 运维要点

| 事项 | 说明 |
|---|---|
| 健康检查 | `GET /api/health`：数据库可达返回 200，否则 503 |
| 令牌吊销 | `POST /api/auth/logout` 使**全部**已签发令牌立即失效；修改密码同样吊销旧令牌并下发新令牌 |
| 登录保护 | 限流 5 次/分钟/IP；连续失败 5 次按「用户名+IP」锁定 15 分钟（返回 429） |
| 过期数据 | 过期设备会话每 10 分钟自动失活；**过期卡密清除默认关闭**，需系统设置 `auto_clean_expired_cards=1` |
| 备份 | 数据库定期备份；系统迁移用「导出 JSON」（默认不含管理员凭据，需要时加 `?include_credentials=true`） |
| 日志 | 后端输出到 stdout（容器 `docker compose logs`）；未内置文件轮转，请由部署侧收集 |

## 7. 常见问题

| 现象 | 原因与处理 |
|---|---|
| 启动即失败，日志提示 JWT 密钥不足 | `JWT_SECRET` 少于 32 字节，按第 2 节重新生成 |
| 启动即失败，提示占位符无法解析 | `DB_USERNAME`/`DB_PASSWORD`/`API_TOKEN`/`JWT_SECRET` 未设置 |
| 管理端一直跳回登录页 | 令牌过期或被吊销（401）。前端会自动跳转；重新登录即可 |
| 请求返回 403 | 权限/业务拒绝（如 AppKey 无效、`ban_machine` 未携带 app_key）；与 401 语义不同 |
| 登录返回 429 | 触发登录限流或账号锁定，等待窗口/锁定期结束 |
| 生卡报「无效的卡密类型」 | 卡型必须是 hour/day/week/month/season/year，或改用自定义时长 |
| 迁移导入后卡密归属异常 | 请使用修复后的版本；导入会重建 app_id 映射，导入前务必备份 |

## 8. 从 PHP 版迁移

1. 在 PHP 后台导出数据（或使用本系统 Java 版的「导入」功能，兼容 PHP 版导出格式的字段名）。
2. 按第 4 节部署 Java 版并完成安装。
3. 在「全局配置 → 系统迁移」中导入 JSON；导入后核对应用、卡密数量与到期时间。
4. 客户端对接：加密响应格式为 `base64(iv ‖ tag ‖ ciphertext)`，与旧 PHP 客户端一致；
   但 `ban_machine` 现在需要携带 `app_key` 且设备需归属该应用（见 [upgrade-notes.md](upgrade-notes.md) 第 1 节）。
