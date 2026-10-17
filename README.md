# AI反诈情景推演平台 · 第一至三阶段

已实现登录注册、首页、场景画像配置与管理后台的前后端代码。接口以根目录 `openapi.yaml` 为准。

设计文档集中在 `docs/`：

- [PRD V3.1 正式开发基线版](docs/PRD_V3.1_正式开发基线版.md)
- [系统架构设计文档 V1.0](docs/系统架构设计文档_V1.0.md)
- [数据库设计文档 V1.0](<docs/数据库设计文档 V1.0.md>)
- [接口文档 V1.0](docs/接口文档_V1.0.md)
- [第二阶段场景画像配置开发计划](<coding plan/2026-10-17-phase2-scenario-profile-configuration.md>)
- [第三阶段管理后台开发计划](<coding plan/2026-10-17-phase3-admin-console.md>)

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
| GET | `/scenarios` | 固定三场景，仅 USER |
| GET | `/scammer-profiles` | 可用骗子画像，游标分页，仅 USER |
| GET | `/scammer-profiles/{id}` | 可用骗子完整详情，仅 USER |
| GET | `/victim-profiles` | 可用受害者画像，游标分页，仅 USER |
| GET | `/victim-profiles/{id}` | 可用受害者完整详情，仅 USER |
| GET | `/avatars/{key}` | 可用画像引用的受控头像，仅 USER |

前端支持注册、登录、角色分流、刷新恢复登录态、退出、加载失败重试和移动端布局。`/configuration` 支持三场景、双方画像的自由选择、完整详情和确认、加载更多；所有内容来自服务端。头像加载失败会阻止开始，点击开始时重新请求双方详情，被停用项清除并要求重选。校验成功仅提示「开始演绎将在下一阶段开放」，不会发送 POST 或创建演绎。选中项只保存在页面内存中。`/history` 仍为后续阶段说明页，`/admin` 已接入管理后台。首页资讯为空。

未实现开始演绎、单场活动限制、历史记录、报告、真实资讯、短信/邮箱验证、密码找回和角色切换。数据库现有 4 张业务表：`sys_user`、`fraud_scenario`、`scammer_profile`、`victim_profile`，外加 `flyway_schema_history`。没有后续模块的占位表或接口。

## 第二阶段运行与验收

配置页支持再次点击已选场景/画像取消该项、点击顶部「重新选择」清空全部选择（不重新请求数据）、通过浏览器刷新重新获取内容并清空选择；正常页面不再显示「重新加载」按钮，加载失败时保留重试入口。选择场景后，在受害者卡片右侧标注「推荐」：刷单返利对应未满35岁，虚假投资对应35—59岁，冒充亲友借款对应60岁及以上。这是用户确认的展示推荐规则，不自动选择、不限制组合、不改变人物属性；取消场景后移除推荐标签。

启动方式与第一阶段相同。首次启动将自动执行新增的 V2 建表与 V3 初始化；V1 未修改，V3 仅插入三场景、两骗子和三受害者，不会在后续启动时覆盖文案。先备份目标库，再按下方本地启动说明运行后端与前端，普通用户登录后点击首页「开始体验」。

头像文件位于 `backend/var/avatars/`，是按 PRD 特征绘制的五张虚构人物静态插画，需人工确认视觉效果。`tools/generate-avatars.py` 使用 Pillow 生成初始 PNG；相同输入得到相同哈希，已有文件不覆盖。数据库保存 `avatars/<SHA-256>.png`，接口返回纯 SHA-256 键及 `/anti.scam/v1/avatars/<SHA-256>`，符合 OpenAPI 的不透明键约束；不返回磁盘路径或带扩展名的 URL。换图应新增文件并通过新的迁移/后续管理模块更新引用，不修改已执行的 V3 或覆盖旧图。

`app.content.avatar-store`（环境变量 `AVATAR_STORE`）指定包含 `avatars` 子目录的存储根，默认 `var`；仓库根目录启动时若没有 `var` 则使用 `backend/var`。部署 jar 时需一同部署头像目录，并推荐将 `AVATAR_STORE` 设为绝对路径。`app.content.startup-check.enabled` 默认 true，启动时校验三场景齐全、双方至少一项可用画像、全部可用头像的格式/解码/哈希；校验失败阻止启动。用户头像接口只能由 USER 读取，且必须仍被可用画像引用；历史快照的头像授权将在历史模块实现。

列表默认 limit=20，范围 1—100，enabled 仅接受字面量 true。游标使用 HMAC 签名并绑定账号、接口及固定筛选/排序；当前单实例进程重启后旧游标失效，重新加载页面即可。多实例部署前需改为共享持久签名密钥。场景、详情与头像接口拒绝任何查询参数，列表拒绝未知/重复参数并返回对应字段错误。格式正确但超出当前 Java Long 标识范围的详情 ID 返回统一 404，格式错误返回 400。响应不泄露启停状态、版本或数据库时间字段。

简要验证命令：

```powershell
cd backend
.\mvnw.cmd package       # 单元/HTTP 冒烟测试并打包；不运行 Docker IT
cd ../frontend
npm.cmd test
npm.cmd run build
cd ..
node tools/validate-openapi.mjs
pwsh -NoProfile -File tools/smoke-content.ps1
```

最后一条需本地 MySQL 8.0.45 和 Java 21，可通过 `-MySqlBin`、`-Java` 指定路径。脚本只创建 `.tools/content-smoke-<随机值>` 下的隔离实例，使用端口 13308/18081，不连接现有业务库；结束时停止自己启动的进程，日志及隔离数据保留在该目录。端口须空闲。人工测试完成前不执行 git commit 或 push。

2026-10-16 实际验收记录：

- 后端 `package` 成功，10 项测试通过（其中新增 3 项覆盖游标签名/作用域、参数和头像），使用本机已有 Maven 3.9.11 与项目缓存；沙箱内编译器资源关闭失败后，在沙箱外成功完成。
- 前端 9 项测试通过（新增 5 项），`vue-tsc` 和 Vite 生产构建通过。
- OpenAPI 静态校验通过：第一阶段 7 项 + 第二阶段 6 项操作。
- `tools/smoke-content.ps1` 在隔离 MySQL 8.0.45 上通过：V1→V3 迁移及 Hibernate validate、三场景五画像、六接口、分页、头像与缓存头、USER/ADMIN/匿名隔离、禁止写入、停用隐藏/统一404、仅四张业务表。未使用 Testcontainers，也未运行全量 `verify`。
- 浏览器人工点击、移动端视觉及头像美术验收未执行；单场活动限制、正式开始演绎和后台修改影响的完整验收留到后续模块。
- 配置交互调整后，前端 11 项测试及生产构建通过，新增简单检查覆盖单项取消、全部重选、重新加载和推荐年龄边界。

## 第三阶段：管理后台

按 [第三阶段开发计划](<coding plan/2026-10-17-phase3-admin-console.md>) 实现 18 项 ADMIN 接口（含后续新增的双方画像删除接口），沿用当前四张业务表，没有新增或修改迁移脚本。启动方式不变，使用预开通管理员登录后进入 `/admin`。

| 页面 | 功能 |
|---|---|
| `/admin/scenarios` | 修改三个固定场景的名称和简介 |
| `/admin/scammers` | 分页查看、新增、编辑、启用和停用骗子画像 |
| `/admin/victims` | 分页查看、新增、编辑、启用和停用受害者画像 |

统一接口前缀 `/anti.scam/v1`。写请求需要会话和 CSRF，普通用户禁止访问后台接口，管理员禁止访问用户内容接口。后台不提供用户演绎与报告查看；演绎、复盘、历史仍未实现。

| 方法 | 地址 | 用途 |
|---|---|---|
| GET | `/admin/scenarios` | 场景列表 |
| PUT | `/admin/scenarios/{id}` | 编辑场景 |
| GET | `/admin/scammer-profiles` | 列表骗子画像 |
| POST | `/admin/scammer-profiles` | 新增骗子画像 |
| GET | `/admin/scammer-profiles/{id}` | 详情骗子画像 |
| PUT | `/admin/scammer-profiles/{id}` | 编辑骗子画像 |
| POST | `/admin/scammer-profiles/{id}/disable` | 停用骗子画像 |
| POST | `/admin/scammer-profiles/{id}/enable` | 启用骗子画像 |
| GET | `/admin/victim-profiles` | 列表受害者画像 |
| POST | `/admin/victim-profiles` | 新增受害者画像 |
| GET | `/admin/victim-profiles/{id}` | 详情受害者画像 |
| PUT | `/admin/victim-profiles/{id}` | 编辑受害者画像 |
| POST | `/admin/victim-profiles/{id}/disable` | 停用受害者画像 |
| POST | `/admin/victim-profiles/{id}/enable` | 启用受害者画像 |
| DELETE | `/admin/scammer-profiles/{id}` | 删除骗子画像 |
| DELETE | `/admin/victim-profiles/{id}` | 删除受害者画像 |
| POST | `/admin/avatars` | 上传头像 |
| GET | `/admin/avatars/{key}` | 后台预览头像 |

保存使用版本号，版本冲突时保留输入并提供手动重新读取，不自动覆盖或重试写入。重复启用/停用不增加版本；每类至少保留一个启用画像，并通过数据库行锁保护并发停用。删除需确认并提交当前版本，最后一个可用画像不能删除；删除永久移除画像记录，头像文件保留以免影响共享引用。编辑表单在原卡片位置展开。头像选择统一使用「上传头像」，选图后可确认上传或取消恢复原头像。管理员可查看停用画像，普通用户只能看到启用画像；编辑不会改变启停状态。

头像必须先上传并成功预览才能保存画像，支持真实可解码的静态 JPG/PNG，最多 5 MiB。上传按 SHA-256 寻址，不覆盖已有文件；管理员可预览未引用的图片，用户仅能读取启用画像引用的图片。为避免图片解码耗尽内存，当前最多接受 1600 万像素，超出返回 INVALID_IMAGE。长文本按开发计划暂限 16000 个 Unicode 码点（TBD-09 待正式确认）。存储根仍使用 `AVATAR_STORE`，后台预览读取同一目录；`spring.servlet.multipart.max-file-size=5MB`、`max-request-size=6MB` 为文件和 multipart 请求上限。头像文件需与数据库一同备份；未引用文件的自动清理未纳入本阶段。

2026-10-17 验证结果：后端 12 项测试与打包通过，前端 14 项测试与生产构建通过；OpenAPI 静态校验通过（7 + 6 + 16 项操作），隔离 MySQL 管理后台冒烟通过。

简单验证：后端 `package`、前端 `npm test` / `npm run build`、`node tools/validate-openapi.mjs`。真实数据库冒烟使用：

```powershell
pwsh -NoProfile -File tools/smoke-admin.ps1
```

脚本需要 PowerShell 7.5+、MySQL 8.0.45 和 Java 21，使用独立端口 13309/18082，在 `.tools/admin-smoke-<随机值>` 内创建数据库和头像存储；不连接现有业务库，结束后关闭自己的进程并保留日志。覆盖角色隔离、CSRF、版本冲突、并发停用、双方画像写入和用户可见性、头像上传边界。未执行压力测试、全量 Testcontainers 或浏览器人工验收。本阶段代码保留在工作区，待人工验收后再提交 GitHub。

人工检查建议：管理员登录 → 修改场景 → 新增双方画像并上传头像 → 编辑/启停 → 双窗口产生版本冲突并重新读取 → 普通用户确认配置页内容和推荐展示。

## 项目结构与锁定版本

```text
backend/                  Spring Boot，auth/home/content/admin/common 按职责分包
  .mvn/                   Maven Wrapper 和 HTTPS Central 配置
  docker-compose.yml      MySQL 开发实例，仅绑定本机
  src/main/resources/db/migration/V1__create_sys_user.sql
  src/main/resources/db/migration/V2__create_content_tables.sql
  src/main/resources/db/migration/V3__seed_content_data.sql
  var/avatars/            五张内容寻址 PNG 头像
frontend/                 Vue 3 + TypeScript + Router + Pinia + fetch
  src/api/                契约类型、HTTP、CSRF 和接口调用
  src/views/              登录、注册、首页、场景画像配置和后续模块说明页
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

两项均未设置时不创建管理员；配置有效且该名字不存在时创建 ADMIN。重复启动不改写已有管理员密码或角色；如果该名字已被 USER 占用，拒绝启动并提示更换配置，避免静默提权。初次创建后可移除这两个环境变量。管理员共用登录页，进入 `/admin` 管理后台；访问用户首页和资讯接口均为 403。

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
