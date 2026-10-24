# 升级说明 · 兼容性与行为变更

本文件记录安全修复引入的**行为变更**，供运维与客户端对接方升级前核对。
括号内为对应审查报告的问题编号。

---

## 1. ban_machine / blacklist 现在需要鉴权（破坏性变更，H3）

- **变更前**：任何匿名请求只要携带 `device_hash` 即可把该设备写入**全局**黑名单，且对**所有应用**永久生效；黑名单无有效期。
- **变更后**：必须同时满足两点，否则返回 `403`：
  1. 请求携带**有效且未被禁用**的 `app_key`；
  2. 被拉黑设备**归属该应用**——该设备在本应用下有活跃会话，或请求同时提交了正绑定该设备的 `card_code`。
- **客户端要求**：调用自封禁（客户端自检到篡改）时须携带 `app_key`，并确保设备已完成过一次有效验证（存在活跃会话）或一并提交 `card_code`。
- **影响面**：只传 `device_hash` 的旧调用将失败。这是有意收紧，用于阻止匿名攻击者封禁任意他人设备。

## 2. 未认证请求由 403 改为 401（M1）

- `Authorization` 缺失或 token 过期时，返回 HTTP `401` + `{"code":401,"msg":"未登录或登录已过期"}`。
- 管理端前端仅识别 401，token 过期后可正常跳回登录页；自研客户端若以 403 判断登录态，请改为判断 401。

## 3. 生卡 `custom_hours` 语义（H4）

- **缺省或 ≤ 0** 一律视为「标准卡型」，时长按 `type` 计算；仅当 `> 8760` 时报错。
- 兼容旧 SDK：显式发送 `custom_hours: 0` 也能正常生成标准卡。
- 修复前，标准卡型（默认 `type=day`）在管理端与 API 端**必然返回 400**。

## 4. `JWT_SECRET` 长度要求（启动即失败，L6）

- `JWT_SECRET` 必须 **≥ 32 字节（256 位）**，否则应用**拒绝启动**。

## 5. 加密响应格式（H1）

- 密文格式为 `base64(iv ‖ tag ‖ ciphertext)`，与旧 PHP 客户端及其 SDK 的切分方式一致。
- 修复前实现误为 `iv ‖ ciphertext‖tag`，开启加密（默认）时**所有存量客户端都无法解密**。

## 6. 限流（过滤器双注册修复 + install 纳入限流）

- 登录 `5 次/分钟`、验证 `60 次/分钟`（此前因过滤器被注册两次，实际为 2 与 30）。
- `POST /api/admin/install` 现纳入登录级限流（此前不限流）。
- 窗口为**自然分钟**（与旧 PHP 一致），分钟边界最多可突发约 2 倍配额。
- `trust-proxy=true` 时客户端 IP 取 `X-Forwarded-For` 的**末段**（最近的受信代理所观测值），且必须是合法 IP 字面量。

## 7. 账号锁定（新增）

- 连续登录失败达到 `app.login.max-failed-attempts`（默认 **5**）次后，锁定 `app.login.lock-minutes`（默认 **15** 分钟），返回 **429**。
- **锁定键为「用户名 + 客户端 IP」**：攻击者无法用错误密码把唯一管理员账号锁死（避免自伤型拒绝服务）。
- 计数保存在内存中，**重启应用即清零**；多实例部署时按实例独立计数。

## 8. JWT 吊销（新增）

- 令牌携带版本声明 `ver`，与 `admin.token_version` 比对；**登出**与**修改密码**都会把版本 +1，使**此前签发的所有令牌立即失效**（不再需要等 3 天过期）。
- 新增 `POST /api/auth/logout`（需登录），前端登出按钮会先调用它再清理本地状态。
- 修改密码成功后，接口会**返回新令牌**，前端自动替换本地令牌，管理员无需重新登录；其他设备的旧令牌全部失效。
- 代价：每个带令牌的请求多一次 admin 行读取（验证接口不带令牌，不受影响）。
- 数据库新增 `admin.token_version` 列，`ddl-auto=update` 会自动添加；旧数据为 NULL 时按 0 处理，**存量令牌在首次吊销前仍然有效**。

## 9. 过期数据自动清理（新增）

- 启用定时任务（`@EnableScheduling`）：
  - **设备会话失活**（非破坏性，仅置状态）：默认每 10 分钟一次，看板「active」口径因此更准确；
  - **过期卡密清除**（破坏性，会删除记录）：默认每小时检查一次，但**仅当系统设置 `auto_clean_expired_cards = 1` 时才执行**（默认关闭，需管理员显式开启）。
- 原「验证请求 1% 概率随机清理」已移除（避免在验证热路径中写入）。
- 可配置项：`app.maintenance.enabled`、`app.maintenance.device-cleanup-cron`、`app.maintenance.expired-card-purge-cron`。
- 手动清理接口 `POST /api/admin/cards/clean-expired` 保持不变。

## 10. 导出脱敏（M6）

- `GET /api/admin/system/export` **默认不再导出 `admin` 表**（含 bcrypt 口令哈希）。
- 需要完整迁移凭据时显式加参数：`?include_credentials=true`。
- 导入不含 `admin` 的文件时，**保留目标机现有管理员账号**（导入逻辑本就如此），因此默认导出 + 导入不会锁死系统。

## 11. 安装接口加固（新增）

- 已安装后调用只返回「系统已安装」（不变）。
- **可选**安装令牌：设置环境变量 `INSTALL_TOKEN` 后，安装请求必须携带 `install_token`，否则 403。留空则沿用旧行为（先到先得）。
- 未安装时启动会打印**醒目告警**，提示该接口当前对外开放、请尽快完成安装。
- 建议公网部署务必设置 `INSTALL_TOKEN`。

## 12. 仪表盘统计口径（明确化）

`GET /api/admin/dashboard` 的 `stats` 现在包含并明确定义（前端原有用例不受影响，为新增字段）：

| 字段 | 含义 |
|---|---|
| `total` | 归属于某应用的全部卡密 |
| `unused` | status=0，未激活 |
| `used` | status=1，已激活（含已过期未清理） |
| `banned` | status=2，被管理员封禁 |
| `expired` | 已激活但已过到期时间 |
| `active` | 仍有效的设备会话数 |
| `apps` | 应用数量 |

## 13. 安装接口字段（L7）

- `POST /api/admin/install` 不再接受 `dbHost / dbName / dbUser / dbPass / dbPort`（数据库连接始终来自环境变量），多余字段被忽略。
- 管理员用户名取自 `app.admin.default-username`（默认 `GuYi`）。**注意**：前端登录页为「单密钥」设计，用户名在 `guyi-frontend/src/stores/auth.js` 中通过 `VITE_ADMIN_USERNAME` 配置（默认 `GuYi`）；若修改后端该配置，前端需同步。

## 14. 系统迁移导入（H2、M4）

- 导入后**应用主键可能被重新编号**；卡密、云变量、活跃设备通过 id 映射保持一致（修复前会挂到错误应用）。
- 响应明确告知管理员凭据处理结果（覆盖 / 保持不变）。
- 黑名单重复值不再重复写入，接口提示「该值已在黑名单中」。

## 15. 旧 PHP 版

- 硬编码的「万能卡密」旁路**已移除**（原实现对该卡密直接返回 2099 年到期，绕过全部校验）。
- 硬编码管理令牌 `GuYiAdmin123` **已移除**：管理动作令牌改为读取
  1. 环境变量 `GUYI_ADMIN_API_TOKEN`，其次
  2. 系统设置 `admin_api_token`；
  两者都未配置时**一律拒绝**管理动作（fail closed），并提示如何配置。比较使用 `hash_equals` 防时序侧信道。
- 升级动作：部署 PHP 版的实例需配置上述其一，否则 `generate/ban/unban/del_card/kick` 接口将返回 403。

## 16. 其他接口行为变化

- 卡密列表搜索支持 `sort` 与 `status/appId/type` 筛选。
- 批量加减时长、全局补偿：参数非法返回 400，不再「报成功但未生效」。
- 卡密批量导出未选择卡密时返回 400。
- 生卡：非法卡型、超长前缀、数量 ≤ 0 返回明确错误（不再静默生成按 1 天计的卡）。
- 系统设置：键名非空且 ≤ 50 字符，非法值返回 400。
- 请求参数超长（卡密 > 50、设备码 > 100、`custom_data` > 60000）返回 400，不再落库报 500。
- 响应不再回显内部异常消息（统一脱敏 + 记录日志）。

---

## 17. 端到端实测后追加的修复（2026-10）

首次真正启动应用（`EndToEndApiTest`，20 项测试）后修复的问题：

| 问题 | 影响与修复 |
|---|---|
| **首装接口无法使用** | `InstallRequest` 缺少 `@JsonProperty("admin_password")`，Jackson 不会把 `admin_password` 绑定到 `adminPassword`，首装**必然**返回「管理员密码长度不能少于6位」。已修正（并保留驼峰别名 `adminPassword`/`installToken`）。 |
| **未知接口返回 200 + 「服务器内部错误」** | 兜底的 `Exception` 处理器吞掉了 Spring 的 `NoResourceFoundException`，把 404 报成 500 且 HTTP 状态为 200。现 404/405/415/413 返回真实状态码 + 同一 JSON 信封；业务错误仍为 200 + body `code`。 |
| **单 JAR 模式下 `/api/**` 被 SPA 回退劫持** | 启用 Option A 后，任何无扩展名的未知路径（含 `/api/...`）都会返回前端 `index.html` 且状态 200，API 客户端会拿到 HTML。现 `/api/`、`/error` 前缀不参与 SPA 回退。 |
| **批量加减时长的 SQL 不可移植、无法测试** | 6 条 MySQL 原生 SQL（`DATE_ADD/INTERVAL`）改为 HQL `timestampadd`，MySQL 与 H2 行为一致，现已被测试覆盖。`globalAddTime` 的「全部应用」由 `NULL` 参数改为 `<= 0` 哨兵，避免可空参数比较。 |
| **JDBC 字符集参数** | `characterEncoding=utf8mb4` 改为 `UTF-8`（`utf8mb4` 是 MySQL 字符集名，驱动的 `characterEncoding` 需要 Java 字符集名）。 |
| **进程时区未固定** | 新增 `APP_TIMEZONE`（默认 `Asia/Shanghai`）在启动时设置 JVM 默认时区，与 JDBC `serverTimezone`、Jackson 时区保持一致，消除「卡密到期用 JVM 时间、设备清理用数据库时间」跨时区错位。 |

---

---

## 18. 全量代码审查追加的修复（2026-10）

第二轮全量审查（后端 + 前端 + 遗留 PHP 版）后修复的问题，均为**行为变更**或**安全加固**：

| # | 问题 | 影响与修复 |
|---|---|---|
| 1 | **PHP 版 `cards.php` 存在硬编码后门** | `?tab=156440000`（`base64_encode` 后等于一个固定常量）可无需口令直接写入 `$_SESSION['admin_logged_in']`，等价于万能管理员密码。**已彻底移除**。此前 README 声称后门已移除，与实际代码不符——部署 PHP 版的实例若曾暴露该路径，应按已泄露处理并立即改密。 |
| 2 | **PHP 版迁移导入存在 SQL 注入** | 导入 JSON 的列名直接拼入 `INSERT` 语句，构造 `{"applications":[{"app_name\`; ...--":"v"}]}` 即可注入。已改为按表**列名白名单**过滤（不在白名单的列直接丢弃）。 |
| 3 | **PHP 版迁移导出泄露口令哈希** | `exportAllDataStream()` 无条件导出 `admin` 表（含 bcrypt 哈希）。现默认不含，需在 UI 勾选「包含管理员凭据」或带 `?include_credentials=1`，与 Java 版一致。 |
| 4 | **PHP 版卡密前缀可注入 XSS** | `generateCards()` 对 `pre` 无任何校验，写入 `card_code` 后在后台 `onclick="copy('...')"` 中形成存储型 XSS；`card_code`/`app_key`/`apiUrl` 的输出也已统一补 `htmlspecialchars(..., ENT_QUOTES)`。Java 版同步增加前缀白名单 `[A-Za-z0-9_-]`。 |
| 5 | **导入「已激活但无到期时间」的卡会变成永久废卡** | PHP 版 schema 中 `expire_time` 为 NOT NULL，该状态本不可能出现；Java 版列可空，导入后 `verifyCard` 永远返回「卡密已过期」。现导入时降级为「未激活」并清空设备绑定，可正常激活使用。 |
| 6 | **导入缺省 `status` 的应用全部变成「禁用」** | `toInt(null) == 0`，导致导入文件没带 status 列时所有应用都被禁用、客户端全部 403。现缺省取实体默认值 1，文件中显式的 0/1 仍被尊重。 |
| 7 | **解除封禁未激活卡：接口谎报成功** | `updateCardStatus(id, 1)` 对无到期时间的卡静默改写为 status=0，但仍返回「卡密状态已更新」。现接口返回**实际写入的状态**，文案改为「该卡密尚未激活，已恢复为未激活状态」，响应 `data.status` 供前端校准。 |
| 8 | **匿名请求未知 API 路径返回 401 而非 404** | `SecurityConfig` 的 `/api/**` 认证规则先于 MVC 路径解析执行，任何未携带令牌的请求打到不存在的接口都得 401，与文档承诺的 404 不符，也让客户端无法区分「未登录」与「接口不存在」。现改为：路径能映射到真实控制器方法时才要求认证，否则放行交由 MVC 返回 404 JSON。**已映射端点的鉴权强度不变**（匿名令牌不计为已认证）。 |
| 9 | **限流表与登录失败计数无上界** | `RateLimitFilter.rateMap` 以客户端 IP 为键且只在每 5 分钟清理「非当前分钟」的条目，同分钟内新增的条目全部保留——攻击者轮换源 IP 可无上限增长。现加入 10 万条上限，触顶即整体重置（只会让限流更严格）。`AuthService.attempts` 同理：现仅在锁定过期时淘汰条目，正在累积的失败计数不受影响。 |
| 10 | **PHP 版搜索未转义 LIKE 通配符** | `searchCards()` 直接把用户输入包进 `%...%`，输入 `%` 可匹配全表。已加 `ESCAPE '!'` 转义（与 Java 版一致）。 |
| 11 | **前端批量导出丢失后端错误原因** | `POST /api/admin/cards/batch-export` 未选卡密时返回 HTTP 400 + JSON，axios 默认对 4xx reject，前端只弹「操作失败」。现从 blob 中解析后端 `msg` 展示。 |
| 12 | **前端加时/扣时/补偿未校验取值范围** | 只判断 `isNaN`，`0`、负数、超大值都直接提交。现按后端同一规则（1–8760 小时）前置校验并提示。 |
| 13 | **前端缺少 favicon 引用，三个资源文件无人使用** | `index.html` 未声明 icon，浏览器每次加载都请求 `/favicon.ico` 得到 404；`src/assets/hero.png`、`src/assets/vite.svg`、`public/icons.svg` 均无引用。已补 `<link rel="icon">` 并删除死资源。 |
| 14 | **`APP_TIMEZONE` 未进入 docker-compose** | 只设了 `TZ`，而 JVM 默认时区由 `APP_TIMEZONE` 决定；容器跑 UTC 时卡密到期与数据库时间会错位。已在 compose 与 `.env.example` 中显式注入（默认 `Asia/Shanghai`）。 |
| 15 | **前端编辑应用会静默清空备注** | 列表返回 `notes`，而 `PUT /api/admin/apps/{id}` 读 `app_notes`；前端直接展开行对象导致编辑框初始为空、保存时后端取默认空串覆盖原数据。已做显式字段映射。 |
| 16 | **前端系统导出参数名写错，含凭据导出静默失效** | `include_credentials` 与后端 `@RequestParam includeCredentials` 不匹配（Spring 不做蛇形转换），勾选后仍导出不含 admin 表的文件。已改为 `includeCredentials` 并补上 UI 勾选项。 |
| 17 | **前端切换筛选不重置页码** | 高位页码下切换应用/类型/状态/每页条数会请求越界页，返回空列表 + 「5/2」假象。已统一传 `resetPage`。 |
| 18 | **全局补偿与清理过期卡被「请先勾选卡密」误拦** | 二者作用域是全部在用卡，与勾选集无关。已拆为独立函数并自带确认。 |
| 19 | **PHP 版默认弱口令 `admin/admin123`** | 跳过安装器直接建库/导入旧库时保留可预测口令。现改为 `bin2hex(random_bytes(9))` 随机初始密码并写入 `system_settings.initial_password` 供管理员读取，安装流程仍由安装器覆盖。 |
| 20 | **PHP 版安装向导可被匿名重放** | `install.php` 自删失败时任何人可重写 `config.php`（含 `SYS_SECRET`）并重置管理员密码。已加数据库侧重装锁（admin 表已有账号即拒绝），并停止回显 PDO 错误详情。 |
| 21 | **PHP 版装饰性 cookie 泄露管理员 IP** | `admin_last_ip`/`admin_last_time` 以非 HttpOnly、3 个月有效期写入，无功能收益。已删除，改由页面变量直接渲染。 |
| 22 | **PHP 版分页 clamp 后 offset 未重算** | 页码归位到末页后仍按旧 offset 查询，末页显示错行/空行。已重算 offset 并重新取数。 |
| 23 | **前端错误消息被吞** | 登录/安装遇 429 显示 axios 英文默认文案；系统导入受全局 30s 超时限制（后端允许 50MB、nginx 120s）；导入后 file input 未清空导致同文件无法二次导入；`toggleApp`/`removeBlacklist` 失败完全静默。均已修复（导入超时单独设为 120s）。 |
| 24 | **PHP 版其他笔误与死代码** | `install.php` UA 正则 `'windows ce;iemobile'` 分号应为竖线（背景图选择失效）；`login.php` 的 `$conf_avatar` 未转义进入 CSS `url()`。已修正。 |

## 部署前检查清单

1. 配置 `JWT_SECRET`（≥ 32 字节）、`DB_USERNAME`、`DB_PASSWORD`、`API_TOKEN`（缺失则启动失败）。
2. 生产环境用 `DB_URL` 覆盖默认连接串并启用 TLS（默认值为 localhost 明文，仅适合本地开发）。
3. 公网部署设置 `INSTALL_TOKEN`，部署后立即完成安装。
4. 若客户端使用 `ban_machine`，确认携带 `app_key` 且设备有有效会话或提交 `card_code`。
5. 若客户端自行解析加密响应，确认按 `iv(12) ‖ tag(16) ‖ ciphertext` 切分。
6. 若此前依赖 403 判断登录态，改为 401；登出请调用 `POST /api/auth/logout` 以真正吊销令牌。
7. 如需自动清除过期卡密，在系统设置中把 `auto_clean_expired_cards` 设为 `1`（默认关闭）。
8. 迁移导出默认不含管理员凭据；需要时加 `?include_credentials=true`，并注意该文件含口令哈希。
9. 迁移导入前先备份数据库。
10. 部署在反向代理之后时设置 `TRUST_PROXY=true`，并设置 `APP_TIMEZONE` 与数据库时区一致
    （docker-compose 已默认注入 `Asia/Shanghai`；`TZ` 单独一项不会固定 JVM 默认时区）。
11. 生卡前缀仅允许 `[A-Za-z0-9_-]`：使用其他字符（含空格、引号、`<>`）的旧调用会返回 400。
12. 前端「导出完整数据包」默认不含管理员凭据；勾选后才带 `?includeCredentials=true`，
    该文件含 bcrypt 口令哈希，请按敏感数据处理。
13. 若此前部署过 PHP 版：**该版本曾存在 `?tab=156440000` 免密登录后门与 `admin/admin123` 默认口令**，
    请按已泄露处理——立即修改管理员密码、核对有无异常登录记录，并确认 `install.php` 已按新的重装锁生效。
