# AI反诈情景推演平台 · 第一阶段

已实现登录注册与首页的前后端代码，对应 `coding plan/2026-10-17-phase1-auth-and-home.md` 和四份设计文档。接口以根目录 `openapi.yaml` 为准。

设计文档集中在 `docs/`：

- [PRD V3.1 正式开发基线版](docs/PRD_V3.1_正式开发基线版.md)
- [系统架构设计文档 V1.0](docs/系统架构设计文档_V1.0.md)
- [数据库设计文档 V1.0](<docs/数据库设计文档 V1.0.md>)
- [接口文档 V1.0](docs/接口文档_V1.0.md)

## 已实现范围

| 方法 | 地址（统一前缀 `/anti.scam/v1`） | 用途 |
|---|---|---|
| GET | `/auth/csrf` | 匿名或登录会话的 CSRF 令牌 |
| POST | `/auth/register` | 仅注册 USER；201 后仍未登录 |
| POST | `/auth/login` | 用户、管理员共用入口；角色由服务端判定 |
| POST | `/auth/logout` | 销毁当前 Session 并清除 Cookie |
| GET | `/auth/me` | 从 Session 获取当前账号 |
| GET | `/home` | 服务端项目介绍、资讯状态和两个导航入口，仅 USER |
| GET | `/news` | 固定空数组，仅 USER |

前端支持注册、登录、角色分流、刷新恢复登录态、退出、加载失败重试和移动端布局。`/configuration`、`/history`、`/admin` 为明确标注的后续阶段说明页，不调用未实现模块的接口。首页资讯为空，不使用虚构新闻。

未实现演绎、配置业务、历史记录、报告、后台管理接口、真实资讯、短信/邮箱验证、密码找回和角色切换。数据库只创建 `sys_user`；Flyway 自身另有迁移记录表。

## 项目结构与锁定版本

```text
backend/                  Spring Boot，auth/home/common 按职责分包
  .mvn/                   Maven Wrapper 和 HTTPS Central 配置
  docker-compose.yml      MySQL 开发实例，仅绑定本机
  src/main/resources/db/migration/V1__create_sys_user.sql
frontend/                 Vue 3 + TypeScript + Router + Pinia + fetch
  src/api/                契约类型、HTTP、CSRF 和接口调用
  src/views/              登录、注册、首页和后续模块说明页
tools/validate-openapi.mjs OpenAPI 静态校验
```

| 项目 | 锁定/验证版本 |
|---|---|
| Java | 21；本机验证为 Corretto 21.0.8 |
| Maven / Wrapper | 3.9.11 / 3.3.4 |
| Spring Boot | 3.5.16；后端传递依赖统一由其 BOM 管理 |
| MySQL | 8.0.45 |
| Node / npm | 本机 22.19.0 / 10.9.3；请使用 Node 22 LTS >=22.12 |
| Vue / Vue Router / Pinia | 3.5.44 / 4.5.1 / 3.0.3 |
| Vite / Vue 插件 | 6.4.4 / 5.2.4 |
| TypeScript / vue-tsc | 5.9.3 / 3.1.1 |
| Vitest / Vue Test Utils / jsdom | 5.0.5 / 2.4.6 / 26.1.0 |
| Swagger Parser | 12.0.0 |

前端全部依赖精确锁定于 `package-lock.json`，请用 `npm ci`。测试使用少量 fetch mock，无需额外引入 MSW。

## 本地启动（PowerShell 7）

需要 Java 21、Node 22 LTS，以及 MySQL 8.0.45（已有实例或 Docker Desktop）。以下命令从仓库根目录执行，不提供默认数据库或管理员口令。

### 已有 MySQL：使用 `.env` 配置（IDEA 运行推荐）

无需启动 Docker。在 `backend/.env` 中填写实际连接信息；不存在时可复制 `backend/.env.example` 为 `.env`。该文件已被 Git 忽略，不要把真实密码填写到 `.env.example` 或前端的 `.env.development`。

```properties
SPRING_PROFILES_ACTIVE=dev
DB_URL=jdbc:mysql://localhost:3306/你的实际数据库名?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true
DB_USERNAME=你的MySQL用户名
DB_PASSWORD=你的MySQL密码
```

`DB_URL` 中 `/` 后、`?` 前的部分指定存放项目表的数据库。IDEA 的连接名称不一定是数据库名，需在 Database 面板选中实际 schema，或执行 `SHOW DATABASES;` 查看。已有空数据库可以直接使用，不必叫 `anti_scam`；账号须能连接并拥有该库的建表、索引和读写权限。Flyway 会在指定库中创建项目表，不负责创建数据库本身，不会搬迁其他库的已有数据。

后端使用 Spring Boot 原生配置导入加载 `.env`，不需要 IDEA EnvFile 插件；具体机制见 [Spring Boot 外部配置文档](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html)。从仓库根目录或 `backend` 作为工作目录启动，均能读取 `backend/.env`。根目录的 `.env` 也支持，建议只在 `backend/.env` 维护后端配置。其他工作目录需在 IDEA Run Configuration 中调整 Working directory。填写后重启应用生效。

此 `.env` 按 Java properties 格式读取：等号两侧不加多余空白，值不要加引号或 `export`；密码中的反斜线需写成 `\\`。IDEA Run Configuration/系统中已有的同名环境变量优先于文件，排查时注意旧的 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`。保留兼容原有 `MYSQL_APP_PASSWORD`，只有未设置 `DB_PASSWORD` 时才使用该旧变量。`SPRING_PROFILES_ACTIVE=dev` 仅用于本地 HTTP 开发。

在 IDEA 以 JDK 21 运行 `AntiScamApplication` 即可，或按下方命令启动。日志 `1045 Access denied ...` 表示账号、密码或账号主机匹配失败；确认所填账号能够使用相同 host/port 连接。`1049 Unknown database ...` 则需核对/创建实际数据库。不要修改或关闭 Flyway 来绕过连接失败。

### 启动报错：Flyway V1 校验和不一致

`Migration checksum mismatch for migration version 1` 表示已连接到数据库，但库中的迁移历史与本项目脚本不一致。先核对目标库和历史，不要直接执行 `repair`、删除历史表或关闭校验：

```sql
SELECT DATABASE();
SELECT version, script, checksum, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

本项目 V1 是 `V1__create_sys_user.sql`。如果查询出现 `V1__init.sql`、`V2__user_ownership.sql` 等其他项目的迁移，应为本项目选择独立空数据库，然后修改 `backend/.env` 的 `DB_URL`。手动执行建表 SQL 不会登记本项目的 Flyway 历史，也不能消除旧 V1 的校验冲突。正常空库启动时，交由 Flyway 自动建表，不必在 IDEA 手动运行迁移文件。

2026-10-16 本机排查后，已按用户选择创建独立库 `anti_scam_phase1`，并将本地 `backend/.env` 的 URL 切换至该库，保留原连接账号与密码。原 `anti_scam` 库中的业务表和旧迁移记录保留；不要把本地 URL 改回旧库来运行本项目。

若必须共用旧库，需先设计两套项目的表和迁移历史如何兼容，不能仅把旧 V1 的校验和改成本项目的值。若确实是本项目已执行的迁移被改动，应恢复原文件，并通过新的迁移版本变更结构。

### 1. 使用 Docker 启动 MySQL（已有实例可跳过）

```powershell
cd D:\AI-anti-scam
$env:MYSQL_APP_PASSWORD = Read-Host '设置本地应用数据库口令' -MaskInput
$env:MYSQL_ROOT_PASSWORD = Read-Host '设置本地MySQL root口令' -MaskInput
docker compose -f backend/docker-compose.yml up -d --wait mysql
```

容器使用持久卷保存数据；重启容器不会删除用户。已初始化的卷应沿用原口令，修改环境变量不会替换已有 MySQL 用户密码。若 3306 被占用，请使用已有的 MySQL 8.0.45 或调整本地端口与 `DB_URL`。

### 2. 启动后端

已有 MySQL 的情况先填写上述 `backend/.env`；Docker 方式可在同一终端沿用 `MYSQL_APP_PASSWORD`，此时不要在 `.env` 中保留示例占位值。新终端若未使用 `.env`，需要重新设置该变量。确认 `JAVA_HOME` 指向 Java 21。

```powershell
# 按本机安装位置调整；本次验证使用此路径
$env:JAVA_HOME = 'C:\Users\lenovo\.jdks\corretto-21.0.8'
cd D:\AI-anti-scam\backend
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

后端默认监听 `http://localhost:8080`。Flyway 启动时执行迁移，Hibernate 仅校验表结构，不自动建表或删表。默认连接 `anti_scam` 库，应用用户名 `anti_scam_app`。已有 MySQL 可用以下变量覆盖地址和账号：

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/anti_scam?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
$env:DB_USERNAME = 'anti_scam_app'
```

使用已有实例时须先创建空库并配置应用账号权限；请勿将测试配置指向生产数据库。迁移版本一旦投入使用，应新增迁移文件，不能原地修改已执行版本。

### 3. 启动前端

另开终端：

```powershell
cd D:\AI-anti-scam\frontend
npm.cmd ci --registry=https://registry.npmjs.org
npm.cmd run dev
```

浏览器打开 `http://127.0.0.1:5173`（端口占用时以 Vite 输出为准）。Vite 将 `/anti.scam/v1` 代理到后端，使 Cookie/CSRF 保持同源；请在同一主机名下完成整个操作。后端未启动时，页面会明确显示连接失败。

### 可选：预开通管理员

在启动后端的终端设置环境变量后再启动：

```powershell
$env:ADMIN_USERNAME = Read-Host '管理员用户名'
$env:ADMIN_PASSWORD = Read-Host '管理员密码（6—24字符）' -MaskInput
```

两项均未设置时不创建管理员；配置有效且该名字不存在时创建 ADMIN。重复启动不改写已有管理员密码或角色；如果该名字已被 USER 占用，拒绝启动并提示更换配置，避免静默提权。初次创建后可移除这两个环境变量。管理员共用登录页，进入 `/admin` 说明页；访问用户首页和资讯接口均为 403。

### 后续新增或修改管理员

预开通入口是 `bootstrap/AdminAccountInitializer.java`：启动时读取 `ADMIN_USERNAME`、`ADMIN_PASSWORD`，验证账号规则，使用 `SecurityConfig` 中的 PBKDF2 `PasswordEncoder` 生成带算法标识和随机盐的哈希，然后写入当前数据库的 `sys_user`，固定角色为 `ADMIN`。账号保存在数据库中，创建后正常重启无需再次提供这两个变量。请勿将管理员密码写进 SQL 迁移或受 Git 跟踪的文件。

**新增另一位管理员**：先停止正在运行的后端，在 PowerShell 7 中执行，输入不同的用户名和相应密码，然后启动：

```powershell
cd D:\AI-anti-scam\backend
$env:ADMIN_USERNAME = Read-Host '新管理员用户名'
$env:ADMIN_PASSWORD = Read-Host '新管理员密码（6—24字符）' -MaskInput
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

创建成功后可按 Ctrl+C 停止，清除当前终端临时变量，再按正常方式启动后端。已有管理员会保留，可以按此方式逐个创建新账号。

```powershell
Remove-Item Env:ADMIN_USERNAME,Env:ADMIN_PASSWORD -ErrorAction SilentlyContinue
```

**修改用户名**：当前无账号管理界面。本地开发时可在 IDEA 数据库控制台执行有明确账号条件的 UPDATE，例如将现有 `adminZHL` 改名。新名字应满足用户名规则（2—24个汉字、ASCII字母、数字或下划线），且不与任何用户/管理员重名；`username_key` 由数据库自动生成，不要手工修改：

```sql
UPDATE anti_scam_phase1.sys_user
SET username = '新的管理员用户名'
WHERE username_key = LOWER('adminZHL') AND role = 'ADMIN';
```

执行前核对目标账号，执行后应恰好影响一行；修改后重启本地后端，再用新用户名登录，避免旧 Session 继续显示改名前的信息。

**修改密码**：当前初始化器只负责新增，修改环境变量中的 `ADMIN_PASSWORD` **不会重置同名已有账号的密码**。密码重置需要使用项目配置的同一个 `PasswordEncoder` 为新密码生成哈希，再更新目标账号的 `password_hash`，并撤销旧会话。不能把明文密码直接写入 `password_hash`，也不能从原哈希恢复密码。本阶段未实现密码修改/重置入口；不要通过删除账号重建来改密码，以免未来关联的历史记录丢失归属。

## 会话与契约说明

- `JSESSIONID`：`Path=/anti.scam/v1`、HttpOnly、SameSite=Lax、无 Domain，只通过 Cookie 传输。默认 Secure=true；只有本地 HTTP 使用 `dev` profile 关闭 Secure。生产需要 HTTPS 和同源反向代理；SPA 路由需回退到 `index.html`，API 请求转发至后端。
- Session 为单实例内存会话；后端重启需重新登录。当前采用内嵌 Tomcat 默认 30 分钟不活动超时；这是实现默认值，接口文档 TBD-02 的超时/续期策略仍待确认。同账号最多一个有效登录会话，新登录使旧会话失效。
- 写请求必须同时带 Session Cookie 和 `X-CSRF-TOKEN`。前端令牌只保存在内存；登录成功后重新获取，登出清空。CSRF 失败会刷新令牌并提示用户重新提交，不自动重放业务写请求；401 引导重新登录。
- 用户名先去首尾空白再验证，长度按 Unicode 码点计数；唯一性由 MySQL `LOWER(username)` 生成列和全局唯一索引保证。密码原样保存其哈希，不裁剪、规范化或改变大小写，允许 `123456`。
- 注册严格拒绝额外字段和错误类型，不能提交 `role` 等字段。ADMIN 禁止注册。返回账号 ID 为字符串；错误不返回密码、哈希、SQL 或堆栈；认证响应不缓存。

与原开发计划的两处必要调整（不改变对外契约）：

1. **PBKDF2 替代 BCrypt**：遵循数据库设计 §5.1 的建议，采用 Spring Security PBKDF2 v5.8 默认参数（16 字节盐、310000 次迭代、SHA-256、256 位哈希），通过 DelegatingPasswordEncoder 保存算法标识。BCrypt 的 72 字节上限无法完整接受 24 个任意多字节字符。本次已验证超过 72 字节的合法密码可注册登录；正式环境仍需测量哈希成本。
2. **Session 存储 CSRF 替代独立 CSRF Cookie**：采用框架 `HttpSessionCsrfTokenRepository`，确保令牌确实绑定当前 Session，登录时配合会话认证策略轮换 Session ID 并清除旧令牌。普通 CookieCSRF 仓库单独使用无法满足该绑定要求。前端仍按契约读取 `/auth/csrf` 并回传 `X-CSRF-TOKEN`，无需读取 Session Cookie。框架参考：[Spring Security CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)。

按业务基线接受弱密码，仍存在弱口令风险；不擅自新增复杂度、短信或验证码要求。

## 简单测试与本次验收记录

```powershell
cd D:\AI-anti-scam\backend
.\mvnw.cmd test        # 无需数据库，规则与 MockMvc 冒烟
.\mvnw.cmd package     # 同时打包可运行 jar

cd D:\AI-anti-scam\frontend
npm.cmd test
npm.cmd run build
npm.cmd run validate:api
```

可选真实容器验证（需要 Docker；会下载 `mysql:8.0.45`，使用独立容器数据库）：

```powershell
cd D:\AI-anti-scam\backend
.\mvnw.cmd verify
```

`MySqlSmokeIT` 只验证迁移、真实生成列/唯一键及多字节密码，不扩展成全面测试矩阵。

2026-10-16 实际执行结果：

| 检查 | 结果 |
|---|---|
| Java 21 + Spring Boot 3.5.16 编译、打包 | 通过 |
| 后端简单测试 | 6 项通过，规则 2 项 + MockMvc 4 项 |
| 前端简单测试 | 4 项通过 |
| TypeScript 检查与 Vite 生产构建 | 通过 |
| OpenAPI 静态校验及本阶段七项操作存在性 | 通过；不等同于自动穷举响应契约验证 |
| 前端依赖审计 | 更新修复版本后报告 0 个已知漏洞 |
| 独立本机 MySQL 8.0.45 + 打包后的后端 HTTP 冒烟 | 通过，未使用 H2/SQLite，未连接已有数据库 |
| 实际数据库结构 | 只有 `sys_user` 和 `flyway_schema_history` |
| HTTP 主流程 | 注册201且未登录 → 大小写重名409 → 越权字段400 → 错误密码401 → 登录 → 旧CSRF拒绝 → me/home/news → 登出 → 401，均通过 |
| 管理员 | 启动预开通、同入口登录、home/news/register拒绝、退出，均通过 |
| 多字节密码 | 24 个码点、超过 72 字节的密码注册并登录通过 |
| 临时测试服务 | 已关闭，不占用开发端口 |
| Docker / Testcontainers | 未执行：Docker daemon 未运行；真实MySQL验证使用本机独立实例完成 |
| 浏览器人工端到端、压力测试、全面安全测试 | 未执行，留待人工检验；不宣称全部计划验收项完成 |

人工检验建议：注册→登录→刷新页面→首页介绍/暂无资讯→两个说明页→退出；再使用预开通管理员验证分流。开发者工具可检查 Cookie 属性和七个 API 的状态码。请确认界面与流程后，再决定是否提交 GitHub。

本次应用了 Ponytail（控制范围与最小实现）、writing-plans（按既有计划组织步骤）、Spring Boot Engineer（后端会话与持久化）及 REST API Conventions（沿用项目契约）。用户关于简单测试、暂不提交的明确要求覆盖原计划中的逐任务提交及全面测试要求。
