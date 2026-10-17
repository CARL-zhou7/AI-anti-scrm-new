# 第三阶段（管理后台模块）开发计划

> **面向 AI 执行者：** 本计划的每一步均使用复选框（`- [ ]`）语法，请按顺序逐任务（Task）执行。每个 Task 结束都必须通过其验证步骤，并完成一次 git 提交后再进入下一个 Task。本计划建立在已完成的「登录注册模块 + 首页模块 + 场景画像配置模块」代码之上，请在现有文件与现有约定上增量开发，不要重写既有模块或改变第二阶段已交付的对外契约。

**目标（Goal）：** 交付可运行的「管理后台模块」垂直切片——管理员登录后进入后台首页，分别进入场景管理、骗子画像管理、受害者画像管理三个入口；场景仅可改名称与简介，画像可新增、编辑、停用、重新启用并上传头像；写操作一律携带读取时 `version`，过期版本返回 409 `VERSION_CONFLICT` 且不覆盖他人修改，同类最后一个可用画像不得停用；后台不得出现任何用户演绎或报告入口。对应《接口文档 V1.0》API-26～API-41 十六个接口。

**架构（Architecture）：** 延续 Spring Boot 模块化单体，新分包 `admin`（后台基础内容维护）与 `content` 并列：`content` 继续只读、面向 USER；`admin` 承担全部写操作与后台视图映射，复用 `content` 的实体、仓库与 `AvatarStore`，但**不修改** `content` 的对外行为。版本控制使用实体既有 `@Version row_version` 做条件更新；启停按数据库设计 §7.8 用「同类行按主键升序悲观锁 + 锁内重新计数」保证「各类至少一个可用」；头像上传写入同一受控目录、以内容 SHA-256 为键、不覆盖旧文件。前端新增 `/admin` 真实后台（替换占位页）与三个管理入口页，全部写操作走既有 `http.post/put` 与 `csrf.ensure()`，错误按稳定 `code` 分支处理。

**技术栈（Tech Stack）：** 沿用既有锁定版本：Java 21（Corretto 21.0.8）+ Spring Boot 3.5.16（web / data-jpa / security / validation / flyway-mysql）+ MySQL 8.0.45 + Flyway；前端 Vue 3.5.44 + Vue Router 4.5.1 + Pinia 3.0.3 + Vite 6.4.4 + TypeScript 5.9.3 + Vitest 5.0.5 + Vue Test Utils 2.4.6 + jsdom 26.1.0；契约校验 `@apidevtools/swagger-parser` 12.0.0。**不引入**新的运行期依赖（不使用 Lombok、MapStruct、任何图片处理库、任何前端表单/校验库）。

**Spec（本计划实现的规格文档，执行者必须同时阅读）：**
- `docs/PRD_V3.1_正式开发基线版.md`（§8.8 管理后台模块 FR-ADM-01～04 与 BR-P09 管理规则、§4.1/§4.2 权限与 PER-02～04、§6.3 管理员流程、§7 页面结构「管理后台」行、§11.3 PC-P02 画像完整信息、§12.2「管理操作失败」行、§13.1 AC-04/AC-05/AC-21、§13.2 AP-07）
- `docs/系统架构设计文档_V1.0.md`（§4.1「基础内容」职责、§13.1/§13.2 权限边界、§13.4 图片与临时媒体、§5.1 页面组织）
- `docs/数据库设计文档 V1.0.md`（§2.2 字段字典、§5.2/§5.3/§5.4 字段与容量、§6.1 唯一与生成列、§6.3 CHECK 与跨行规则分工、§7.8 画像编辑·最低可用数量·开始竞争、§10.3 头像与外部资源一致性、§12.1 的 DBV-11/DBV-12/DBV-14）
- `docs/接口文档_V1.0.md`（§2.1 传输与响应、§2.2 CSRF 调用顺序、§2.3 权限矩阵、§2.4 并发与缓存、§6 的 API-26～API-41、§7 字段字典、§8 错误码）
- `openapi.yaml`（机器可读契约，**字段名、约束、状态码与错误码的最终依据**）
- `README.md`（第一、二阶段的实际交付与验收记录：测试命令、`AVATAR_STORE`、`app.content.startup-check.enabled`、本地运行方式）

---

## 一、本阶段范围与模块边界

### 1.1 本阶段实现的接口（唯一交付范围）

| 编号 | 方法 | 完整路径 | 用途 | 权限 |
|---|---|---|---|---|
| API-26 | GET | `/anti.scam/v1/admin/scenarios` | 后台固定三场景（含 version） | ADMIN |
| API-27 | PUT | `/anti.scam/v1/admin/scenarios/{id}` | 编辑场景文案 | ADMIN |
| API-28 | GET | `/anti.scam/v1/admin/scammer-profiles` | 后台骗子画像列表（含停用项） | ADMIN |
| API-29 | GET | `/anti.scam/v1/admin/scammer-profiles/{id}` | 后台骗子画像详情 | ADMIN |
| API-30 | POST | `/anti.scam/v1/admin/scammer-profiles` | 新增骗子画像 | ADMIN |
| API-31 | PUT | `/anti.scam/v1/admin/scammer-profiles/{id}` | 编辑骗子画像 | ADMIN |
| API-32 | POST | `/anti.scam/v1/admin/scammer-profiles/{id}/disable` | 停用骗子画像 | ADMIN |
| API-33 | POST | `/anti.scam/v1/admin/scammer-profiles/{id}/enable` | 启用骗子画像 | ADMIN |
| API-34 | GET | `/anti.scam/v1/admin/victim-profiles` | 后台受害者画像列表（含停用项） | ADMIN |
| API-35 | GET | `/anti.scam/v1/admin/victim-profiles/{id}` | 后台受害者画像详情 | ADMIN |
| API-36 | POST | `/anti.scam/v1/admin/victim-profiles` | 新增受害者画像 | ADMIN |
| API-37 | PUT | `/anti.scam/v1/admin/victim-profiles/{id}` | 编辑受害者画像 | ADMIN |
| API-38 | POST | `/anti.scam/v1/admin/victim-profiles/{id}/disable` | 停用受害者画像 | ADMIN |
| API-39 | POST | `/anti.scam/v1/admin/victim-profiles/{id}/enable` | 启用受害者画像 | ADMIN |
| API-40 | POST | `/anti.scam/v1/admin/avatars` | 上传头像（multipart） | ADMIN |
| API-41 | GET | `/anti.scam/v1/admin/avatars/{key}` | 管理员头像预览 | ADMIN |

同时交付：前端 `/admin` 后台首页与 `/admin/scenarios`、`/admin/scammers`、`/admin/victims` 三个管理入口页；`tools/smoke-admin.ps1` 隔离实例验收脚本；README 更新。

### 1.2 本阶段明确不做（禁止提前实现、禁止预留占位接口）

- **API-42 `/timing/samples` 与 API-14～API-25 全部演绎/复盘/历史接口**：开始演绎、演绎状态、SSE、三类呈现回执、退出、临时语音、报告与重试、历史列表与详情。
- **数据库不新增任何表、不新增任何列**：不引入管理员操作审计表、通用资源表、任务表或统计表；本阶段所有改动落在既有 `fraud_scenario`、`scammer_profile`、`victim_profile` 三张表与既有受控头像目录上，**不执行新的 Flyway 迁移**。
- **画像永久删除、场景新增/删除/停用、图片裁剪、AI 头像生成、用户管理、角色切换、管理员查看用户演绎或报告**一律不实现（PRD §5.3、§8.8 MVP 边界）。后台页面**不得**出现任何用户记录、演绎、报告或 AI 反向入口。
- 不实现头像引用计数、上传残留自动清理、旧头像删除；不实现 Prompt 管理页面；不实现统计大屏。
- 不允许把 `content` 包的只读语义改成可写：USER 端接口在本阶段结束后必须**逐字保持**第二阶段行为（包括拒绝写入、隐藏启停状态与版本）。

---

## 二、全局约束（Global Constraints，所有任务默认包含）

以下数值来自规格文档与已交付实现，**必须逐字实现，不得「优化」**：

1. 接口前缀固定 `/anti.scam/v1`；JSON 字段 `lowerCamelCase`；成功 `code="OK"`，失败 `code` 为稳定英文码；统一封套四字段 `code`/`message`/`data`/`requestId` 始终存在（沿用既有 `ApiResponse` 与 `RequestIdFilter`）。
2. 业务 ID 传输为正整数十进制字符串（`pattern ^[1-9][0-9]{0,19}$`）；路径参数用既有 `PageQuery.id(String)` 校验，格式错误 400（字段 `id`），超出 `Long` 范围 404。
3. **后台专属响应字段**：场景 `AdminScenario` = `id`、`code`、`name`、`description`、`focus`、`version`、`createdAt`、`updatedAt`；画像 `Admin*Profile` = 用户端全部业务字段 + `enabled`、`version`、`createdAt`、`updatedAt`。三者 `additionalProperties: false`，不得多字段也不得少字段。
4. 后台画像 `avatar.url` 前缀必须是 `/anti.scam/v1/admin/avatars/`（用户端是 `/anti.scam/v1/avatars/`）；`key` 仍为 64 位小写十六进制、不带扩展名（沿用第二阶段约定）。
5. `version` 为**内容编辑版本**（非负整数，条件更新用），不是演绎 `stateVersion`；`row_version` 数据库默认 0，每次成功编辑或启停 +1，冲突的请求**不得**使其变化。
6. 场景只允许编辑 `name`、`description`；`code`、`focus`、关键目标、`id` 一概不可编辑、不可新增、不可删除、不可停用；固定三行。
7. 画像新增默认 `enabled = true`、`version = 0`；新增/编辑/启停都**不接受** `id`、`enabled`（编辑接口）、`createdAt`、`updatedAt`、`rowVersion`、`role` 等字段，出现即 400 且字段错误码为 `UNKNOWN_FIELD`。
8. 画像写入字段约束（逐字来自 openapi `ScammerWrite`/`VictimWrite`，两方同形）：`name` 1—64、`gender` 1—16、`identity` 1—128、`contextTime` 1—128、`contextPlace` 1—128、`age` 1—65535、`description`/`currentState`/`behaviorTraits`/`personalityTraits` 非空、`Level ∈ {LOW, MEDIUM, HIGH}`、`avatarKey` 匹配 `^[A-Za-z0-9_-]+$` 且必须指向受控存储中**真实可解码**的 JPG/PNG。
9. 写操作（API-27、API-30～API-33、API-36～API-39、API-40）必须同时具备有效 Session Cookie 与 `X-CSRF-TOKEN`；CSRF 失败 403 `CSRF_INVALID`。
10. 权限：匿名 → 401 `UNAUTHENTICATED`；USER 访问 `/admin/**` → 403 `FORBIDDEN`；ADMIN 访问任何用户接口（`/home`、`/news`、`/scenarios`、画像读取、头像读取、未来演绎与历史）→ 403 `FORBIDDEN`。隐藏导航不构成权限控制，服务端必须实际拦截。
11. 稳定错误码（本阶段使用）：400 `VALIDATION_FAILED`、400 `INVALID_CURSOR`、400 `INVALID_IMAGE`、401 `UNAUTHENTICATED`、403 `FORBIDDEN`、403 `CSRF_INVALID`、404 `NOT_FOUND`、409 `VERSION_CONFLICT`、409 `LAST_ENABLED_PROFILE`、413 `FILE_TOO_LARGE`、415 `UNSUPPORTED_MEDIA_TYPE`、500 `INTERNAL_ERROR`、500 `DATA_INCONSISTENT`、503 `SERVICE_UNAVAILABLE`。
12. `409 VERSION_CONFLICT` 的 `message` 恰为「内容已被修改，请重新读取」，`data` 为 `ErrorDetail` 且 `currentVersion` 为服务端当前版本（字段错误数组为空、`activeSessionId` 为 `null`）。
13. `409 LAST_ENABLED_PROFILE` 的 `message` 恰为「该类必须保留至少一个可用画像」；停用最后一个可用画像必须失败，且事务回滚后该行仍为启用状态、版本不变。
14. **启停幂等**：目标已处于请求状态时返回 200 与当前内容，**不递增版本**；只有真正改变状态才校验 `version` 并递增。
15. 头像上传：`multipart/form-data` 单字段 `file`；以**真实内容**判定格式（PNG 签名 `\x89PNG\r\n\x1a\n` 或 JPEG 签名 `\xFF\xD8\xFF` 且 `ImageIO.read` 可解码），≤ **5 MB**（5 × 1024 × 1024 字节，含临界值），成功返回 201 与 `AvatarUpload`（`key`、`previewUrl`、`mediaType`、`sizeBytes`）；超出大小 413 `FILE_TOO_LARGE`；非静态 JPG/PNG 400 `INVALID_IMAGE`；`Content-Type` 不被 multipart 支持时 415 `UNSUPPORTED_MEDIA_TYPE`。
16. 头像文件写入必须**先临时文件、后原子移动**到受控目录，文件名取内容 SHA-256；已存在的同名文件不得改写；上传不直接更新任何画像，只返回键，由后续保存画像显式引用。
17. 后台头像预览只允许受控头像目录中的有效资源（含尚未被引用的上传物与被引用版本），**不得**把 `key` 解析为任意本地路径、不得进入临时语音目录、不得读取业务配置文件；无 `..`、`/`、大小写异常或扩展名异常的值一律 400 或 404，绝不 200。
18. 所有响应 `Cache-Control: no-store`（沿用 `RequestIdFilter`），头像预览为 `private, no-store` 且带 `X-Content-Type-Options: nosniff`。
19. 时间传输为 UTC ISO 8601 含三位毫秒与 `Z`（如 `2026-10-15T02:00:00.000Z`）；`createdAt`/`updatedAt` 由数据库维护，实体只读映射，不由应用写入。
20. 对话、报告、密码、会话值、CSRF 令牌、SQL、堆栈、文件绝对路径**不得**出现在响应、错误 `message` 或日志中；日志只记 ID、操作类别、结果与耗时。
21. 本阶段**不修改** `backend/src/main/resources/db/migration/` 下任何已执行文件（V1/V2/V3 保持原样）；`spring.jpa.hibernate.ddl-auto` 保持 `validate`。
22. 前端后台页面不得硬编码场景或画像内容，不得把「启用/停用」的结论建立在前端状态上，不得用本地缓存替代服务端返回内容；写操作成功后必须用服务端返回对象刷新页面（不能用本地拼装的对象假装成功）。

---

## 三、复审重点（Review Focus）

以下五类输入或失败模式是本阶段最可能伤害真实使用者的位置，规格文档暗示了但常规任务不会自然覆盖。**每条已在括号中注明其测试落在哪个任务的步骤，实现这些任务时必须写出对应测试，不得省略：**

1. **过期版本与并发编辑（两位管理员同时改同一条内容）**——A 读取到 `version=0`，B 先保存成功（版本变 1），A 随后提交仍带 `version=0`。合理预期：A 得到 409 `VERSION_CONFLICT` 且 `data.currentVersion = 1`，B 的内容原样保留、`version` 不因 A 的失败请求而变化；即使 A 提交的内容与库里当前内容完全相同，也仍返回 409，前端必须重新读取而不是静默覆盖。（Task 2 步骤 1/3、Task 5 步骤 1/3、Task 6 步骤 1/3、Task 10 步骤 1/3）
2. **「同类至少一个可用」的停用边界与并发**——只剩一个可用骗子画像时停用它；两个管理员同时停用同类最后两个画像中的各自一个（每个单独看都合法，合起来会把该类型清零）。合理预期：前者 409 `LAST_ENABLED_PROFILE` 且该行仍启用、版本不变；后者**恰好一个成功、另一个被拒**，最终该类型至少保留一个可用画像。（Task 6 步骤 1/3/5、Task 10 步骤 1/3）
3. **头像上传的真实性与尺寸边界**——把 `.txt` 改名为 `.png`、只有 PNG 头但内容截断不可解码、声称 `image/png` 实为 JPEG 字节、恰好 5 MB 与 5 MB + 1 字节、0 字节文件、`avatarKey` 指向从未上传或已被删除的键、`key` 形如 `../application.yml` 或含 `/`。合理预期：前几类分别 400 `INVALID_IMAGE`／413 `FILE_TOO_LARGE`／400 字段错误，1 字节与恰好 5 MB 的成功；指向不存在资源的 `avatarKey` 在保存画像时被拒（不产生悬空引用）；预览接口对穿越值绝不返回业务文件内容。（Task 3 步骤 1/3、Task 5 步骤 1/3、Task 10 步骤 1/3）
4. **写请求体中的越权字段与类型错误**——在编辑接口里塞 `enabled`、`id`、`version` 之外的 `rowVersion`、`code`、`focus`、`role`、`createdAt`；把 `age` 写成字符串 `"30"`、把 `fraudExperience` 写成 `very_high`、`version` 写成 `"0"`、缺少必填字段、`name` 传 65 个字符或空串。合理预期：一律 400 且 `data.fieldErrors` 指向真实字段名（未知字段为 `UNKNOWN_FIELD`），**不得**被忽略后仍然保存成功，也不得出现 500。（Task 4 步骤 1/3、Task 5 步骤 1/3、Task 6 步骤 1/3）
5. **管理员与用户权限的双向穿透**——USER 直接请求 16 个 `/admin/**` 接口（含 GET 与写）；ADMIN 直接请求 `/home`、`/news`、`/scenarios`、画像读取、`/avatars/{key}`；匿名请求全部 16 个；登录后被降权的会话。合理预期：USER 与匿名全部被拒（403/401，写操作还必须先满足 CSRF 前提才说明角色结论），ADMIN 访问用户接口一律 403，且**任何**被拒响应都不含画像业务正文、版本或启停状态。（Task 1 步骤 1/3、Task 9 步骤 1/3、Task 10 步骤 1/3）

补充要求（同样必须在对应任务中测试）：后台列表必须能被识别为「包含停用项且含版本」——停用一条后列表仍返回它且 `enabled=false`、`version` 正确（Task 1 步骤 1/3、Task 6 步骤 1/3）；USER 端在后台完成增改启停后行为随之变化但**不暴露** `enabled`/`version`（Task 10 步骤 1/3）。

---

## 四、文件结构（先定边界，再拆任务）

```
D:\AI-anti-scam\
├── coding plan\
│   └── 2026-10-17-phase3-admin-console.md                    ← 本计划
├── tools\
│   ├── validate-openapi.mjs                                  ← 修改：增加 16 条后台路径断言
│   └── smoke-admin.ps1                                       ← 新增：隔离实例后台端到端验收
├── backend\src\
│   ├── main\java\com\aiscam\antiscam\
│   │   ├── common\api\ErrorCodes.java                        ← 修改：补齐后台错误码常量
│   │   ├── common\error\ApiException.java                    ← 修改：新增版本冲突/最后可用/图片类工厂
│   │   ├── common\error\GlobalExceptionHandler.java          ← 修改：新增参数校验与 multipart 异常出口
│   │   ├── common\web\RequestContent.java                    ← 新增：TEXT 字段字节容量防御校验
│   │   ├── content\
│   │   │   ├── avatar\AvatarKey.java                         ← 修改：新增 publicKeyOf(存储键) 与 isValidPublicKey
│   │   │   ├── avatar\AvatarUploader.java                    ← 新增：上传校验 + 原子写入受控目录
│   │   │   ├── domain\ScammerProfileRepository.java          ← 修改：新增升序悲观锁与计数查询
│   │   │   ├── domain\VictimProfileRepository.java           ← 修改：同上
│   │   │   ├── domain\FraudScenarioRepository.java           ← 修改：新增 findById 之外的后台查询（如需要）
│   │   │   └── domain\ScammerProfile.java / VictimProfile.java ← 修改：新增受控写方法（编辑与启停）
│   │   └── admin\
│   │       ├── service\AdminScenarioService.java             ← API-26 / API-27
│   │       ├── service\AdminProfileService.java              ← API-28～API-39（两方共用同形逻辑）
│   │       ├── service\AdminAvatarService.java               ← API-40 / API-41
│   │       ├── web\AdminScenarioController.java              ← API-26 / API-27
│   │       ├── web\AdminProfileController.java               ← API-28～API-39
│   │       ├── web\AdminAvatarController.java                ← API-40 / API-41
│   │       └── web\dto\
│   │           ├── AdminScenarioView.java / AdminScenarioUpdateRequest.java
│   │           ├── AdminAvatarRef.java
│   │           ├── AdminScammerView.java / AdminVictimView.java
│   │           ├── ScammerWriteRequest.java / ScammerUpdateRequest.java
│   │           ├── VictimWriteRequest.java / VictimUpdateRequest.java
│   │           ├── VersionRequest.java
│   │           └── AvatarUploadView.java
│   ├── main\resources\application.yml                        ← 修改：multipart 大小上限
│   └── test\java\com\aiscam\antiscam\
│       ├── support\MySqlTestBase.java                        ← 新增：Testcontainers + Flyway 基类（Task 1）
│       ├── admin\AdminScenarioApiTest.java                   ← MockMvc：后台场景读取与编辑
│       ├── admin\AdminProfileReadIT.java                     ← 真实 MySQL：后台画像列表与详情
│       ├── admin\AdminScammerProfileIT.java                  ← 真实 MySQL：骗子画像增改启停与并发
│       ├── admin\AdminVictimProfileIT.java                   ← 真实 MySQL：受害者画像增改启停
│       ├── admin\AdminAvatarApiTest.java                     ← MockMvc：上传与预览
│       ├── admin\AdminProfileRulesTest.java                  ← 纯单元：字段/容量/版本规则
│       └── admin\AdminPermissionTest.java                    ← 双向权限与 CSRF
└── frontend\src\
    ├── api\
    │   ├── types.ts                                          ← 修改：追加后台类型
    │   ├── http.ts                                           ← 修改：新增 put 与 postForm（multipart）
    │   └── admin.ts                                          ← 新增：adminApi
    ├── components\
    │   ├── AdminNav.vue                                      ← 后台一级导航
    │   ├── VersionConflictNotice.vue                         ← 409 冲突提示与重新读取
    │   ├── AvatarUploader.vue                                ← 上传 + 预览 + 失败反馈
    │   ├── ProfileFormFields.vue                             ← 画像表单字段（两方共用，按角色切换参数项）
    │   └── AdminProfileTable.vue                             ← 画像列表（含启停列与操作）
    ├── views\
    │   ├── AdminHomeView.vue                                 ← 替换 /admin 占位页
    │   ├── AdminScenariosView.vue                            ← /admin/scenarios
    │   ├── AdminScammersView.vue                             ← /admin/scammers
    │   └── AdminVictimsView.vue                              ← /admin/victims
    ├── router\index.ts                                       ← 修改：四个后台路由
    └── __tests__\
        ├── admin-api.spec.ts                                 ← adminApi 契约、PUT 与 multipart 请求
        ├── admin-scenarios.spec.ts                           ← 场景编辑与版本冲突
        └── admin-profiles.spec.ts                            ← 画像列表/新增/编辑/启停与最后一项保护
```

**结构约束：** 一个文件一个职责；`admin` 可以依赖 `content` 的实体、仓库、`AvatarStore`、`AvatarKey`，但 `content` **不得**依赖 `admin`（保持只读模块独立）；`admin/web/dto` 只做请求校验与响应映射，禁止把实体的 `rowVersion`、`avatarKey` 原样透出；两方画像的后台逻辑必须共用同一份服务代码（不得复制粘贴两份），差异只通过「角色枚举 + 字段映射」表达；前端 `api/types.ts` 是唯一类型来源，`api/admin.ts` 是唯一后台请求入口，组件不得直接调用 `fetch`；前端不得新增全局 store，后台页面状态留在各自视图内。

---

## 五、任务清单

### Task 1：后台错误码、只读后台接口与后台分页游标（API-26、API-28、API-29、API-34、API-35）

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/common/api/ErrorCodes.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/admin/web/dto/AdminScenarioView.java`、`AdminAvatarRef.java`、`AdminScammerView.java`、`AdminVictimView.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/admin/service/AdminProfileService.java`、`AdminScenarioService.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/admin/web/AdminScenarioController.java`、`AdminProfileController.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/avatar/AvatarKey.java`（新增 `publicKeyOf` 与 `isValidPublicKey`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/page/ContentCursor.java`（新增后台作用域常量）
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/domain/ScammerProfileRepository.java`、`VictimProfileRepository.java`（新增后台分页与全量查询）
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/domain/FraudScenario.java`、`ScammerProfile.java`、`VictimProfile.java`（只补只读访问器 `getRowVersion()` / `isEnabled()`，不加 setter）
- Test: `backend/src/test/java/com/aiscam/antiscam/support/MySqlTestBase.java`（Create；从既有 `MySqlSmokeIT` 抽出 Testcontainers + Flyway + `AVATAR_STORE` 基类，`MySqlSmokeIT` 改为继承它并保持断言不变）、`AdminScenarioApiTest.java`、`AdminProfileReadIT.java`

**Interfaces:**
- Consumes: 既有 `ApiResponse`、`ApiException`、`PageQuery`（`from`/`validate`/`id`）、`ContentCursor`（`encode`/`decode`）、`FraudScenarioRepository.findAllByOrderByIdAsc()`、`ScammerProfileRepository`/`VictimProfileRepository` 的既有查询、`AvatarKey.publicKey`、`SecurityConfig` 的 `/anti.scam/v1/admin/**` → `hasRole("ADMIN")` 规则
- Produces:
  - `ErrorCodes`：`public static final String` 常量，本阶段包含 `VALIDATION_FAILED`、`INVALID_CURSOR`、`INVALID_IMAGE`、`UNAUTHENTICATED`、`FORBIDDEN`、`CSRF_INVALID`、`NOT_FOUND`、`VERSION_CONFLICT`、`LAST_ENABLED_PROFILE`、`FILE_TOO_LARGE`、`UNSUPPORTED_MEDIA_TYPE`、`INTERNAL_ERROR`、`DATA_INCONSISTENT`、`SERVICE_UNAVAILABLE`
  - `AvatarKey.publicKeyOf(String storedKey) -> String`：从 `avatars/<64hex>.<ext>` 取出 64 位键，格式不符抛 `ApiException.dataInconsistent("头像资源键不正确")`；`AvatarKey.isValidPublicKey(String key) -> boolean`：`^[0-9a-f]{64}$`
  - `AdminAvatarRef(String key, String url)`，静态 `AdminAvatarRef.from(String storedKey)` → `url = "/anti.scam/v1/admin/avatars/" + key`
  - `AdminScenarioView(String id, ScenarioCode code, String name, String description, String focus, int version, Instant createdAt, Instant updatedAt)`（record；`id` 为字符串）
  - `AdminScammerView` / `AdminVictimView`：用户端字段 + `boolean enabled` + `int version` + `Instant createdAt` + `Instant updatedAt`，`avatar` 为 `AdminAvatarRef`；各带静态 `from(ScammerProfile)` / `from(VictimProfile)`
  - `ContentCursor.SCOPE_ADMIN_SCAMMERS = "ADMIN|SCAMMER|idAsc"`、`ContentCursor.SCOPE_ADMIN_VICTIMS = "ADMIN|VICTIM|idAsc"`
  - `AdminScenarioService.list() -> List<AdminScenarioView>`：行数不为 3 或 code 集合不齐时抛 `ApiException.dataInconsistent("基础场景数据不完整")`
  - `AdminProfileService.listScammers(PageQuery query, String adminAccount) -> PageView<AdminScammerView>`、`listVictims(...)`、`findScammer(String id) -> AdminScammerView`、`findVictim(String id) -> AdminVictimView`（后台详情**包含停用项**，查不到抛 `ApiException.notFound()`）
  - 后台仓库方法：`List<ScammerProfile> findByIdGreaterThanOrderByIdAsc(long id, Pageable page)`、`Optional<ScammerProfile> findById(long id)`（`VictimProfileRepository` 同形）
  - 实体只读访问器：`FraudScenario.getRowVersion()`、`ScammerProfile.getRowVersion()`、`ScammerProfile.isEnabled()`、`VictimProfile.getRowVersion()`、`VictimProfile.isEnabled()`（本任务不添加任何 setter）
  - 控制器方法签名：

```java
// AdminScenarioController：@RestController @RequestMapping("/anti.scam/v1/admin")
@GetMapping("/scenarios")
public ApiResponse<List<AdminScenarioView>> scenarios(@RequestParam MultiValueMap<String, String> query)

// AdminProfileController
@GetMapping("/scammer-profiles")
public ApiResponse<PageView<AdminScammerView>> scammers(@RequestParam MultiValueMap<String, String> query, Authentication auth)
@GetMapping("/scammer-profiles/{id}")
public ApiResponse<AdminScammerView> scammer(@PathVariable String id, @RequestParam MultiValueMap<String, String> query)
```

- [ ] **Step 1：写失败测试 `AdminScenarioApiTest`（MockMvc）与 `AdminProfileReadIT`（真实 MySQL）**

`AdminScenarioApiTest` 沿用既有 `AuthHomeSmokeTest` 的 `@WebMvcTest` + `@Import({...})` + `@MockitoBean` 风格（`properties = {"app.security.session-cookie-secure=false"}`），用 `SecurityMockMvcRequestPostProcessors.authentication(...)` 传入 `new Account("102","admin",UserRole.ADMIN)` 与 `ROLE_ADMIN`；`AdminProfileReadIT` 继承本任务新建的 `MySqlTestBase`，用真实种子数据（2 位骗子、3 位受害者）做断言：

```java
// AdminScenarioApiTest（MockMvc + 仓库 mock）
@Test void adminScenarios_returnThreeItemsWithVersionAndTimestamps()   // data.length()==3；每项恰好 8 个字段（id/code/name/description/focus/version/createdAt/updatedAt）；
                                                                      // version 为 number 0；createdAt 匹配 ^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$
@Test void adminScenarios_rejectAnyQueryParameter()                    // ?cursor=x、?limit=1 → 400 VALIDATION_FAILED，fieldErrors[0].field 为实际参数名
@Test void adminScenarios_return401AnonymouslyAnd403ForUser()
@Test void adminScenarios_exposeExactlyTheContractFields()             // 断言不存在 rowVersion/avatarKey/enabled/target 等额外字段

// AdminProfileReadIT（真实 MySQL 种子数据）
@Test void adminScammerList_includesDisabledItemsWithEnabledAndVersion()
    // 先用 SQL 停用梁悦，再 GET：2 项都返回，其中一项 enabled==false 且 version 存在（用户端此时只读到 1 项）
@Test void adminVictimList_returnsThreeItemsInIdOrder()              // 3 项，id 升序，含 enabled 与 version
@Test void adminScammerList_pagesByCursorWithoutDuplicatesOrGaps()   // limit=1 逐页取，id 序列完整且无重复
@Test void adminScammerList_rejectsEnabledQueryParameter()           // ?enabled=true → 400（后台列表不按启用过滤）
@Test void adminScammerList_crossResourceCursorIsInvalid()           // 骗子列表 nextCursor 传给受害者列表 → 400 INVALID_CURSOR
@Test void adminScammerDetail_returnsDisabledRecord()                // 停用项详情 200（与用户端 404 行为不同）
@Test void adminProfileDetail_malformedIdIs400_andUnknownIdIs404()   // "007"/"abc" → 400；"999999999999" → 404
@Test void adminProfileEndpoints_return401AnonymouslyAnd403ForUser()
@Test void adminAvatarUrlUsesAdminPrefix()                           // avatar.url 以 /anti.scam/v1/admin/avatars/ 开头，key 匹配 ^[0-9a-f]{64}$
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd test "-Dtest=AdminScenarioApiTest"` 与 `cd backend && .\mvnw.cmd verify "-Dit.test=AdminProfileReadIT" -DfailIfNoTests=false`
Expected: FAIL（`/admin/scenarios` 等路径尚不存在，返回 404 或编译失败）。

- [ ] **Step 3：实现错误码、DTO、服务与两个只读控制器**

要点：后台分页**不复用** `PageQuery.from`（它把 `enabled` 当合法参数），改为显式 `PageQuery.validate(query, Set.of("cursor", "limit"))` 后自行读取 `limit`（默认 20、1—100、越界字段错误，与用户端同文案），游标用 `ContentCursor.encode(SCOPE_ADMIN_SCAMMERS, adminAccount, lastId)`，账号取自 `Authentication#getName()`，使不同管理员之间的游标不可互换；`AdminScammerView.from` 必须用 `AdminAvatarRef`（`url` 前缀为 `/admin/avatars/`），**不得**直接复用 `ScammerView`；两个画像实体目前都**没有** `getRowVersion()`/`isEnabled()` 访问器，本任务先补上只读的 `getRowVersion()` 与 `isEnabled()`（只加 getter，不加 setter；启停的 `setEnabled` 属于 Task 6）；`createdAt`/`updatedAt` 由 `Instant` 直接序列化（既有 Jackson 配置应输出 UTC 毫秒 `Z`，实现前先用 Step 1 的时间断言确认，若不符再在 DTO 上固定格式，不要改全局 Jackson 配置）；场景列表用 `PageQuery.validate(query, Set.of())` 拒绝任何参数。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd test "-Dtest=AdminScenarioApiTest"` 与 `cd backend && .\mvnw.cmd verify "-Dit.test=AdminProfileReadIT" -DfailIfNoTests=false`
Expected: 均 PASS；同时确认既有 `AuthHomeSmokeTest`、`ContentRulesTest`、`MySqlSmokeIT`（已改为继承 `MySqlTestBase`）仍通过。

- [ ] **Step 5：提交**

```bash
git add backend/src/main/java/com/aiscam/antiscam/common/api/ErrorCodes.java backend/src/main/java/com/aiscam/antiscam/admin backend/src/main/java/com/aiscam/antiscam/content backend/src/test/java/com/aiscam/antiscam/admin
git commit -m "feat(admin): admin error codes and read-only scenario/profile endpoints (API-26/28/29/34/35)"
```

---

### Task 2：编辑场景文案与版本冲突（API-27）

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/admin/web/dto/AdminScenarioUpdateRequest.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/admin/service/AdminScenarioService.java`（新增 `update`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/admin/web/AdminScenarioController.java`（新增 `PUT /scenarios/{id}`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/common/error/ApiException.java`（新增 `versionConflict(int currentVersion)`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/common/error/GlobalExceptionHandler.java`（新增参数校验异常出口）
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/domain/FraudScenario.java`（新增受控 `rename(String name, String description)`）
- Test: `backend/src/test/java/com/aiscam/antiscam/admin/AdminScenarioApiTest.java`（追加编辑用例）

**Interfaces:**
- Consumes: Task 1 的 `AdminScenarioView`、`AdminScenarioService.list()`、`ErrorCodes`
- Produces:
  - `ApiException.versionConflict(int currentVersion)` → 409 / `VERSION_CONFLICT` / 「内容已被修改，请重新读取」/ `detail = new ErrorDetail(List.of(), null, currentVersion)`
  - `AdminScenarioUpdateRequest(String name, String description, Integer version)`：`name` `@NotBlank @Size(max = 100)`、`description` `@NotBlank`、`version` `@NotNull @Min(0)`；记录额外字段由既有 `fail-on-unknown-properties` 拒绝
  - `AdminScenarioService.update(String id, AdminScenarioUpdateRequest request) -> AdminScenarioView`：`version` 与当前 `rowVersion` 不等时抛 `ApiException.versionConflict(current)`；相等时改名改简介并返回新视图（`version` 为 +1 后的值）
  - `PUT /anti.scam/v1/admin/scenarios/{id}` → 200 `AdminScenarioView`
  - `GlobalExceptionHandler` 新增：`MethodArgumentNotValidException` → 400 `VALIDATION_FAILED` + `fieldErrors`（每条取 `field`、`defaultMessage` 与 `REQUIRED`/`LENGTH_INVALID`/`VALUE_INVALID` 规则）；`HandlerMethodValidationException` 同义处理

- [ ] **Step 1：写失败测试（在 `AdminScenarioApiTest` 追加）**

```java
@Test void updateScenario_changesOnlyNameAndDescription_andIncrementsVersion()
    // PUT {name:"新名称",description:"新简介",version:0} → 200；data.name=="新名称"、description=="新简介"、
    // version==1、code 仍为 TASK_REBATE、focus 仍为"青年群体"、id 不变
@Test void updateScenario_withStaleVersion_returns409WithCurrentVersion()
    // 先用 version:0 成功一次（当前为 1）；再用 version:0 提交 → 409 VERSION_CONFLICT，
    // message=="内容已被修改，请重新读取"，data.currentVersion==1，data.fieldErrors 为空数组，data.activeSessionId 为 null；
    // 再 GET 确认 name/description 仍为上一次成功写入的值、version 仍为 1
@Test void updateScenario_sameContentWithStaleVersion_stillConflicts()   // 内容与库中逐字相同但 version 过期 → 仍 409
@Test void updateScenario_forbidsCodeFocusAndUnknownFields()
    // 请求体含 code/focus/rowVersion/enabled/id → 400 VALIDATION_FAILED，fieldErrors[0].code=="UNKNOWN_FIELD"
@Test void updateScenario_validatesFields()
    // name 空串、name 101 字符、description 空串、version 缺失、version 为 -1、version 为字符串 "0" → 全部 400
@Test void updateScenario_malformedIdIs400_unknownIdIs404()
@Test void updateScenario_withoutCsrfIs403()                            // 无 X-CSRF-TOKEN → 403 CSRF_INVALID
@Test void updateScenario_is401AnonymouslyAnd403ForUser()
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd test "-Dtest=AdminScenarioApiTest"`
Expected: FAIL（`PUT /admin/scenarios/{id}` 未实现；`MethodArgumentNotValidException` 目前落到兜底 500）。

- [ ] **Step 3：实现版本条件更新、请求校验与 409 出口**

要点：**先比较版本，再改动实体**——`AdminScenarioService.update` 用 `@Transactional`（`Isolation.READ_COMMITTED`）读取实体，`rowVersion != request.version()` 时立即抛 `versionConflict(entity.getRowVersion())` 并且**不触碰任何字段**（避免 JPA 脏检查把失败请求也写成一次更新）；相等时调用 `FraudScenario.rename(...)`。`FraudScenario` 已有 `@Version private int rowVersion`，本任务只需新增 `rename`；若 `rowVersion` 为私有且无访问器，请补一个 `getRowVersion()`（`FraudScenario` 目前**没有**该访问器，Task 1 的视图映射也需要它——两处都要用同一个访问器，不要各写一份反射或直接读字段）。`rename` 只写 `name`/`description`，不提供 `setScenarioCode`/`setRowVersion` 访问器。`GlobalExceptionHandler` 的字段错误码映射规则固定为：`@NotNull`/`@NotBlank` → `REQUIRED`，`@Size` → `LENGTH_INVALID`，其余 → `VALUE_INVALID`，`message` 用注解自带的中文说明（例如「名称须为1至100个字符」），**不得**回显提交的原值。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd test "-Dtest=AdminScenarioApiTest"`
Expected: PASS。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(admin): scenario copy editing with version conflict (API-27)"
```

---

### Task 3：头像上传与管理员预览（API-40、API-41）

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/content/avatar/AvatarUploader.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/admin/service/AdminAvatarService.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/admin/web/AdminAvatarController.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/admin/web/dto/AvatarUploadView.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/avatar/AvatarStore.java`（抽取「仅按键读取」的 `loadStored(String storedKey)`，`load` 改为调用它；不改变 `load` 的既有语义与错误码）
- Modify: `backend/src/main/java/com/aiscam/antiscam/common/error/ApiException.java`（新增 `imageInvalid()`、`fileTooLarge()`、`unsupportedMediaType()`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/common/error/GlobalExceptionHandler.java`（新增 `MaxUploadSizeExceededException` → 413 出口）
- Modify: `backend/src/main/resources/application.yml`（新增 `spring.servlet.multipart.max-file-size: 5MB`、`max-request-size: 6MB`）
- Test: `backend/src/test/java/com/aiscam/antiscam/admin/AdminAvatarApiTest.java`

**Interfaces:**
- Consumes: 既有 `AvatarStore`（构造参数为 `${app.content.avatar-store:var}` 的 `Path`）、`AvatarKey.isValid`、`AvatarKey.publicKeyOf`、`AvatarResource(byte[] bytes, String mediaType)`、Task 1 的 `ErrorCodes`
- Produces:
  - `ApiException.imageInvalid()` → 400 / `INVALID_IMAGE` / 「图片内容无效或不是静态JPG/PNG」；`fileTooLarge()` → 413 / `FILE_TOO_LARGE` / 「头像超过允许大小」；`unsupportedMediaType()` → 415 / `UNSUPPORTED_MEDIA_TYPE` / 「不支持的请求或图片格式」
  - `AvatarStore.loadStored(String storedKey) -> AvatarResource`：只按键在受控目录内解析与校验（格式、签名、哈希、≤5MB），越界或不存在抛 `ApiException.notFound()`
  - `AvatarUploader.store(byte[] bytes) -> String`（返回 `avatars/<sha256>.<ext>`）：字节为 0 或超过 5 × 1024 × 1024 时抛 `fileTooLarge()`；不是可解码的静态 PNG/JPEG 时抛 `imageInvalid()`；写盘用「同目录临时文件 + `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING 之前先判断存在)`」，已存在同名目标时**不覆盖**并直接返回该键
  - `AdminAvatarService.upload(MultipartFile file) -> AvatarUploadView`、`AdminAvatarService.preview(String key) -> AvatarResource`
  - `AvatarUploadView(String key, String previewUrl, String mediaType, long sizeBytes)`；`previewUrl = "/anti.scam/v1/admin/avatars/" + key`
  - `POST /anti.scam/v1/admin/avatars` → 201 `AvatarUploadView`；`GET /anti.scam/v1/admin/avatars/{key}` → 200 二进制（`Content-Type`、`Cache-Control: private, no-store`、`X-Content-Type-Options: nosniff`）

- [ ] **Step 1：写失败测试 `AdminAvatarApiTest`**

```java
@Test void upload_storesRealPngAndReturnsKeyPreviewAndSize()
    // multipart 一个真实 PNG（用 backend/var/avatars 下既有文件之一作为字节来源）
    // → 201；data.key 匹配 ^[0-9a-f]{64}$；data.mediaType=="image/png"；data.sizeBytes==文件字节数；
    // data.previewUrl=="/anti.scam/v1/admin/avatars/"+key；随后 GET 该 previewUrl → 200 + image/png + nosniff
@Test void upload_rejectsNonImageAndMismatchedContent()   // text/plain 字节、PNG 头但内容截断不可解码 → 400 INVALID_IMAGE；
                                                          // JPEG 字节上传 → 201 且 mediaType=="image/jpeg"、key 以 .jpg 结尾（以真实内容判定，不看原始文件名）
@Test void upload_ignoresOriginalFilename()               // 文件名 "avatar.exe"、含路径 "..\\x.png" 的 PNG 字节 → 仍 201，存储键只由内容哈希决定
@Test void upload_rejectsOversize()                       // 5*1024*1024 字节 → 201；5*1024*1024+1 字节 → 413 FILE_TOO_LARGE
@Test void upload_isIdempotentForSameBytes()              // 同一字节上传两次 → 两次都 201 且 key 相同，目录中只有一个文件
@Test void upload_doesNotTouchAnyProfile()                // 上传后 GET 画像列表，所有 avatar.key 与上传前一致（上传只返回键）
@Test void upload_withoutFilePartOrWithExtraPartIs400()   // 缺 file → 400；多传 file2 → 400（不静默忽略）
@Test void upload_withoutCsrfIs403_andIs401Anonymously_and403ForUser()
@Test void preview_servesUnreferencedUpload()             // 刚上传、尚未被任何画像引用的键也能 200 预览
@Test void preview_rejectsTraversalAndMalformedKeys()
    // "..", "%2e%2e%2fapplication.yml", "x.png", 64 位大写、64 位+.png、含 "/" → 400 或 404，绝不 200 且响应体不含 YAML 内容
@Test void preview_unknownKeyIsIdenticalNotFound()        // 与不存在的画像详情 404 响应体逐字段一致
@Test void preview_is401Anonymously_and403ForUser()
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd test "-Dtest=AdminAvatarApiTest"`
Expected: FAIL（`/admin/avatars` 未实现）。

- [ ] **Step 3：实现上传、原子写入与预览**

要点：`AdminAvatarController` 的上传方法签名为 `@PostMapping(value = "/avatars", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)` + `@RequestPart("file") MultipartFile file`；缺字段或多字段由 Spring 抛 `MissingServletRequestPartException`/`MultipartException`，需在 `GlobalExceptionHandler` 映射为 400 `VALIDATION_FAILED`（字段名 `file`）。格式判定顺序固定为：字节长度检查（0 与 >5 MB）→ PNG/JPEG 签名 → `ImageIO.read` 可解码 → 计算 SHA-256 与扩展名 → 临时文件写入 → `Files.move(..., StandardCopyOption.ATOMIC_MOVE)` → 用 `AvatarStore.loadStored` 回读校验（复用既有哈希与签名双重校验）。**不要**相信 `MultipartFile#getContentType` 或原始文件名（不得使用 `getOriginalFilename()` 拼接任何路径）。预览接口的 `@PathVariable` 用 `{key:.+}` 并在方法内先 `AvatarKey.isValidPublicKey`，格式不符抛 `ApiException.field("key","VALUE_INVALID","头像标识格式不正确")`，格式正确但文件不存在由 `loadStored` 抛 404。`application.yml` 的 multipart 上限必须与业务上限一致，并确认 `MaxUploadSizeExceededException` 出口真的返回 413（若容器在解析阶段直接中断连接而无法返回 JSON，回报实测结果并在风险项记录，不要静默改成 400）。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd test "-Dtest=AdminAvatarApiTest"`
Expected: PASS；并确认既有 `ContentRulesTest#allSeedAvatarsAreDecodableAndContentAddressed` 仍通过（`AvatarStore.load` 语义未变）。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(admin): avatar upload and admin preview (API-40/41)"
```

---

### Task 4：画像写入请求校验与 TEXT 容量防御（纯单元）

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/common/web/RequestContent.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/admin/service/AdminProfileValidator.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/admin/web/dto/ScammerWriteRequest.java`、`ScammerUpdateRequest.java`、`VictimWriteRequest.java`、`VictimUpdateRequest.java`、`VersionRequest.java`
- Create: `backend/src/test/java/com/aiscam/antiscam/admin/AdminProfileRulesTest.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/common/error/GlobalExceptionHandler.java`（补齐参数校验与反序列化异常的字段错误映射）

**Interfaces:**
- Consumes: 既有 `Level` 枚举、`ApiException.field(field, code, message)`
- Produces:
  - `RequestContent.checkField(String field, String value, int maxChars) -> void`：`value` 为 `null` 或超过 `maxChars` 个**Unicode 码点**时抛 400 字段错误（`REQUIRED` / `LENGTH_INVALID`）；`RequestContent.MAX_TEXT_CHARS = 16000`（常量，注释说明依据：数据库设计 §2.2 要求提交前校验可容纳字节长度、禁止静默截断，MySQL `TEXT` 上限 65535 字节，16000 个码点对 UTF-8 最坏 4 字节/码点仍留有余量）
  - 四个请求 DTO 均为 record + Bean Validation 注解（**无 setter**）：
    - `ScammerWriteRequest(String name, Integer age, String gender, String identity, String description, String contextTime, String contextPlace, String currentState, Level fraudExperience, Level speechSkill, Level manipulationSkill, String behaviorTraits, String avatarKey)`：`name` `@NotBlank @Size(max=64)`、`age` `@NotNull @Min(1) @Max(65535)`、`gender` `@NotBlank @Size(max=16)`、`identity` `@NotBlank @Size(max=128)`、`description`/`currentState`/`behaviorTraits` `@NotBlank`、`contextTime`/`contextPlace` `@NotBlank @Size(max=128)`、三个 `Level` `@NotNull`、`avatarKey` `@NotBlank @Pattern(regexp="^[A-Za-z0-9_-]+$")`
    - `ScammerUpdateRequest` = 同一组字段 + `@NotNull @Min(0) Integer version`（**不含** `enabled`、`id`、`createdAt`、`updatedAt`）
    - `VictimWriteRequest` / `VictimUpdateRequest`：同形，行为参数换成 `antiFraudAwareness`、`riskTendency`，文本换成 `personalityTraits`
    - `VersionRequest(@NotNull @Min(0) Integer version)`
  - `AdminProfileValidator`（`@Component`，无状态、可直接 `new` 出来单测）：
    - `void checkScammerText(ScammerWriteRequest request)` / `checkVictimText(VictimWriteRequest request)`：对四个 TEXT 字段逐个调用 `RequestContent.checkField`，字段名与请求字段名一致（`description`/`currentState`/`behaviorTraits` 或 `personalityTraits`）
    - `void checkAvatarKeyUsable(String avatarKey)`：调用方传入的键在受控存储中既无 `.png` 也无 `.jpg` 时抛 400 字段错误 `avatarKey` / `VALUE_INVALID`（Task 5 会在服务层注入 `AvatarStore` 后调用；本任务先用可注入的 `Predicate<String>` 形式，签名固定为 `checkAvatarKeyUsable(String avatarKey, java.util.function.Predicate<String> readable) -> String`，返回实际存在的存储键 `avatars/<key>.<ext>`）
  - `GlobalExceptionHandler` 新增两个出口：`MethodArgumentNotValidException` → 400 `VALIDATION_FAILED` + 逐字段 `fieldErrors`；`HttpMessageNotReadableException` 的 `InvalidFormatException` 分支 → 400 字段错误（字段名取 `ex.getPath()` 末段的 `getFieldName()`，code 为 `TYPE_INVALID`）

- [ ] **Step 1：写失败测试 `AdminProfileRulesTest`**

```java
@Test void textCapacityRejectsTruncationRisk()      // 16000 个汉字通过；16001 个 → LENGTH_INVALID；
                                                    // 4000 个 emoji（每字符 4 字节）通过 —— 证明按码点而非字节计数、且不依赖默认平台编码
@Test void writeRequestRejectsMissingAndIllegalFields()
    // 用 jakarta.validation.Validator 直接校验 DTO 实例（无需 Spring Web）：
    // 缺 name/age/avatarKey、age=0 与 65536、gender 17 字符、identity 129 字符、name 65 字符 → 违规字段名集合与预期完全一致
@Test void updateRequestRequiresVersionAndRejectsEnabledFields()
    // 缺 version → 违规字段恰为 version；version=-1 → 违规字段恰为 version；
    // 用既有的 ObjectMapper（fail-on-unknown-properties=true）反序列化含 "enabled"/"id"/"createdAt" 的 JSON
    // → UnrecognizedPropertyException，propertyName 分别为 enabled/id/createdAt
@Test void avatarKeyPatternRejectsPaths()           // "avatars/x.png"、"../a"、64 位 hex + ".png"、空串 → 违规字段恰为 avatarKey
@Test void validatorRejectsUnreadableAvatarKey()    // checkAvatarKeyUsable("<64hex>", k -> false) → 400 字段错误 field=="avatarKey"、code=="VALUE_INVALID"；
                                                    // 可读时返回 "avatars/<64hex>.png"（优先 png，其次 jpg）
@Test void levelEnumRejectsUnknownValue()           // 用 ObjectMapper 读取 "fraudExperience":"very_high"/"high" → 反序列化失败（由枚举绑定抛出），
                                                    // 并断言 GlobalExceptionHandler 的 InvalidFormatException 分支给出 field=="fraudExperience"、code=="TYPE_INVALID"
```

- [ ] **Step 2：运行单元测试确认失败**

Run: `cd backend && .\mvnw.cmd test "-Dtest=AdminProfileRulesTest"`
Expected: FAIL（DTO、`RequestContent`、`AdminProfileValidator` 不存在，编译失败）。

- [ ] **Step 3：实现 DTO、容量校验、头像键可用性校验与字段错误映射**

要点：四个请求 DTO 全部用 record + Bean Validation 注解，**不写 setter**，也不提供 `@AssertTrue` 方法（容量校验放在 `AdminProfileValidator`，以便字段错误精确指向 `description`/`currentState`/`behaviorTraits`/`personalityTraits` 而不是一个 `textCapacity` 布尔属性）；`RequestContent.checkField` 用 `value.codePointCount(0, value.length())` 计数；`Level` 走 Jackson 枚举绑定，非法值产生 `HttpMessageNotReadableException`，需在既有 `unreadable` 出口中先判断 `InvalidFormatException`，**不得**再兜底成 500 或 `UNKNOWN_FIELD`；`MethodArgumentNotValidException` 的字段错误码映射规则固定为：`@NotNull`/`@NotBlank` → `REQUIRED`，`@Size`/`@Max`/`@Min` 中属容量者 → `LENGTH_INVALID`，其余 → `VALUE_INVALID`。

- [ ] **Step 4：运行单元测试确认通过**

Run: `cd backend && .\mvnw.cmd test "-Dtest=AdminProfileRulesTest"`
Expected: PASS。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(admin): profile write validation and text capacity guards"
```

---

### Task 5：新增与编辑骗子画像（API-30、API-31）

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/admin/service/AdminProfileService.java`（新增 `createScammer`、`updateScammer`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/admin/web/AdminProfileController.java`（新增 `POST` 与 `PUT`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/domain/ScammerProfile.java`（新增构造与受控写方法 `apply(ScammerProfileValues)`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/domain/ScammerProfileRepository.java`（新增 `save`）
- Test: `backend/src/test/java/com/aiscam/antiscam/admin/AdminScammerProfileIT.java`（新增；真实 MySQL 集成测试）

**Interfaces:**
- Consumes: Task 1 的 `AdminScammerView`/`AdminAvatarRef`、Task 3 的 `AvatarStore.loadStored`、Task 4 的 `ScammerWriteRequest`/`ScammerUpdateRequest`/`AdminProfileValidator`、既有 `ApiException.notFound()`/`versionConflict(int)`
- Produces:
  - `ScammerProfile(ScammerProfileValues values)` 构造器（`values` 为 content 包内的不可变记录 `ScammerProfileValues(String name, Integer age, String gender, String identity, String description, String contextTime, String contextPlace, String currentState, Level fraudExperience, Level speechSkill, Level manipulationSkill, String behaviorTraits, String avatarKey)`）与 `void apply(ScammerProfileValues values)`：只写业务字段，**不**触碰 `enabled`、`rowVersion`、`createdAt`、`updatedAt`
  - `AdminProfileService.createScammer(ScammerWriteRequest request) -> AdminScammerView`：先 `AdminProfileValidator.checkAvatarKeyUsable(...)` 得到实际存储键，再插入 `enabled = 1`、`rowVersion = 0` 的行并返回视图
  - `AdminProfileService.updateScammer(String id, ScammerUpdateRequest request) -> AdminScammerView`：查不到抛 404；`version` 不等抛 `versionConflict(current)`；相等则校验头像、`apply(...)` 并返回新视图（版本 +1，`enabled` 保持原值）
  - `POST /anti.scam/v1/admin/scammer-profiles` → 201 `AdminScammerView`；`PUT /anti.scam/v1/admin/scammer-profiles/{id}` → 200 `AdminScammerView`

> **测试形态说明（Task 5、6、7 共用）：** 本阶段的画像写入测试一律使用**真实 MySQL 集成测试**（`AdminScammerProfileIT`、`AdminVictimProfileIT`），因为它们要验证的是版本条件更新、唯一约束、数据库维护的 `created_at`/`updated_at` 与行锁行为，这些都无法用 mock 仓库证明（数据库设计 §12.1 明确要求）。集成测试基类沿用既有 `MySqlSmokeIT` 的 Testcontainers 写法，并为后端全部 IT 抽出共享基类 `backend/src/test/java/com/aiscam/antiscam/support/MySqlTestBase.java`（`mysql:8.0.45`、`utf8mb4`/`utf8mb4_0900_bin`、UTC、`spring.jpa.hibernate.ddl-auto=validate`、`AVATAR_STORE` 指向 `backend/var`）；`MySqlSmokeIT` 改为继承它并保持既有断言不变。若本机没有 Docker daemon，按 README 的隔离实例方式运行（见 Task 6 Step 4），**不得**改用 H2 并宣称已通过。

- [ ] **Step 1：写失败测试 `AdminScammerProfileIT`**

```java
@Test void createScammer_defaultsToEnabledWithVersionZero()
    // 201；data.enabled==true、data.version==0、id 为字符串、avatar.url 以 /anti.scam/v1/admin/avatars/ 开头；
    // 直接 SELECT 该行，断言 is_enabled=1、row_version=0、created_at 与 updated_at 非空且为 UTC
@Test void createScammer_rejectsAvatarKeyThatIsNotReadable()
    // avatarKey 指向不存在的键 → 400 VALIDATION_FAILED，fieldErrors[0].field=="avatarKey"；SELECT COUNT 未增加
@Test void createScammer_rejectsUnknownAndPrivilegedFields()   // enabled/id/rowVersion/createdAt → 400 UNKNOWN_FIELD，且未插入行
@Test void createScammer_validatesEveryRequiredField()          // 逐个缺字段一次，断言 400 且 fieldErrors 精确指向缺的字段
@Test void createScammer_makesTheProfileVisibleToUserEndpoint()
    // 新建后 GET /anti.scam/v1/scammer-profiles（USER 会话）数量由 2 变 3，且新画像字段与提交值逐字一致
@Test void updateScammer_replacesAllEditableFields_andKeepsEnabled()
    // 先用 SQL 把该行置为 is_enabled=0，再编辑 → 200；业务字段全部更新、enabled 仍为 false、version 由 0 变 1
@Test void updateScammer_withStaleVersionIs409AndDoesNotTouchRow()
    // 409 VERSION_CONFLICT，data.currentVersion==当前版本；随后 GET 与 SELECT 断言内容、row_version、updated_at 均未变化
@Test void updateScammer_avatarReplacementUsesNewKeyAndKeepsOldFile()
    // 用另一个真实头像字节上传得到新键 → 编辑成功；断言磁盘上旧文件仍存在且字节不变（历史/快照依赖）
@Test void updateScammer_doesNotResurrectDisabledProfile()      // 对停用项编辑不会把它改回启用
@Test void updateScammer_malformedIdIs400_unknownIdIs404_withoutCsrfIs403()
@Test void createAndUpdate_are401Anonymously_and403ForUser()
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd verify "-Dit.test=AdminScammerProfileIT" -DfailIfNoTests=false`
Expected: FAIL（`POST`/`PUT` 未实现）。

- [ ] **Step 3：实现创建与编辑**

要点：`apply` 之前必须完成全部校验（头像可读、容量、版本），**任何失败都不得先修改实体**；插入用 `saveAndFlush` 以便立即取到自增 ID 与数据库写入的 `created_at`；`updated_at` 由数据库 `ON UPDATE CURRENT_TIMESTAMP(3)` 维护，实体上保持 `insertable = false, updatable = false`（若回读发现视图里 `updatedAt` 仍是旧值，改为在同一事务内 `flush` + `refresh`，**不要**在实体里手工赋值时间）；编辑路径不得因为 `enabled=false` 而拒绝，也不得顺手把它改回 `true`。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd verify "-Dit.test=AdminScammerProfileIT" -DfailIfNoTests=false`
Expected: PASS。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(admin): scammer profile create and edit (API-30/31)"
```

---

### Task 6：启停与「同类至少一个可用」保护（API-32、API-33）

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/common/error/ApiException.java`（新增 `lastEnabledProfile()`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/domain/ScammerProfileRepository.java`（新增 `lockAllByOrderByIdAsc()`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/domain/ScammerProfile.java`（新增 `void setEnabled(boolean)`；`isEnabled()` 已在 Task 1 添加）
- Modify: `backend/src/main/java/com/aiscam/antiscam/admin/service/AdminProfileService.java`（新增 `disableScammer`、`enableScammer`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/admin/web/AdminProfileController.java`（新增两个 `POST`）
- Test: `backend/src/test/java/com/aiscam/antiscam/admin/AdminScammerProfileIT.java`（追加启停与并发用例）

**Interfaces:**
- Consumes: Task 4 的 `VersionRequest`、Task 5 的 `AdminScammerProfileIT` 与 `AdminProfileService` 结构、既有 `ApiException.notFound()`/`versionConflict(int)`
- Produces:
  - `ApiException.lastEnabledProfile()` → 409 / `LAST_ENABLED_PROFILE` / 「该类必须保留至少一个可用画像」
  - `ScammerProfileRepository.lockAllByOrderByIdAsc() -> List<ScammerProfile>`，实现为 `@Lock(LockModeType.PESSIMISTIC_WRITE)` + `@Query("select p from ScammerProfile p order by p.id asc")`
  - `AdminProfileService.setScammerEnabled(String id, VersionRequest request, boolean target) -> AdminScammerView`，并暴露 `disableScammer(id, request)` / `enableScammer(id, request)` 两个薄封装（受害者的同形方法属于 Task 7）
  - `POST /anti.scam/v1/admin/scammer-profiles/{id}/disable` → 200 `AdminScammerView`（`enabled=false`）；`POST .../enable` → 200（`enabled=true`）

- [ ] **Step 1：写失败测试（在 `AdminScammerProfileIT` 追加）**

```java
@Test void disable_thenEnable_roundTripsAndChangesUserVisibility()
    // 库中有 2 个可用骗子画像：停用其一 → 200 且 enabled==false、version+1；
    // USER 端 GET /scammer-profiles 数量由 2 变 1；再启用 → 200 且恢复为 2
@Test void disable_alreadyDisabled_returns200WithoutVersionBump()
    // 目标已停用 → 200；返回的 version 与请求传入的版本一致，SELECT row_version 未变化
@Test void enable_alreadyEnabled_returns200WithoutVersionBump()
@Test void disable_lastEnabledProfile_returns409LastEnabledProfile()
    // 同类只剩一个可用 → 409 LAST_ENABLED_PROFILE，message=="该类必须保留至少一个可用画像"；
    // SELECT 断言该行仍 is_enabled=1 且 row_version 未变化
@Test void disable_staleVersion_returns409VersionConflict()          // 有多个可用项但 version 过期 → VERSION_CONFLICT（不是 LAST_ENABLED_PROFILE）
@Test void disable_lastEnabledProfileWithStaleVersion_prefersLastEnabledError()
    // 固化本计划选择的优先级：先判"是否已处于目标状态"，再判"是否最后一个可用"，最后判版本
@Test void enable_whenOthersAvailable_stillChecksVersion()           // 启用一个停用项：version 过期 → 409
@Test void disableAndEnable_are401Anonymously_403ForUser_andRequireCsrf()
@Test void concurrentDisableOfLastTwo_keepsAtLeastOneEnabled()
    // 库里恰有两个可用骗子画像；用两个独立线程 + 两个独立事务（各自 CountDownLatch 对齐后同时发起）
    // 断言：恰好一个成功、另一个得到 LAST_ENABLED_PROFILE；最终 SELECT COUNT(is_enabled=1) == 1
@Test void concurrentDisableAndEnable_neverLoseTheOnlyEnabledRow()
    // 库里恰有一个可用项：线程 A 停用它、线程 B 启用另一个已停用项
    // 断言最终至少一个可用；且没有任何行的 row_version 一次跳变两格（锁内重判生效）
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd verify "-Dit.test=AdminScammerProfileIT" -DfailIfNoTests=false`
Expected: FAIL（启停接口未实现 / 并发用例失败）。

- [ ] **Step 3：实现启停的锁内重判**

要点（严格按数据库设计 §7.8）：整个过程在**同一个短事务**内按固定顺序执行——①`lockAllByOrderByIdAsc()` 锁定该类型**全部**行（升序，保证并发启停的加锁顺序一致，避免死锁）；②在锁定结果中找到目标行，找不到抛 404；③若目标已处于请求状态 → 直接返回当前视图，**不递增版本**；④若目标是停用且锁定结果中 `is_enabled = 1` 的行数为 1 → 抛 `lastEnabledProfile()`（即使在多可用项时也不能用事务外的 `COUNT` 先查后写）；⑤比较 `version`，不等抛 `versionConflict(current)`；⑥调用 `setEnabled(target)`，提交。启停**不接收也修改**任何业务字段。并发用例必须用两个真实连接（不是同一事务内的两个线程），并在断言后恢复数据。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd verify "-Dit.test=AdminScammerProfileIT" -DfailIfNoTests=false`
Expected: PASS。若本机没有 Docker daemon（2026-10-16 起一直未运行），按 README 中 `tools/smoke-content.ps1` 的方式用隔离 MySQL 8.0.45 实例运行该 IT，并在提交信息与 README 中如实记录「未使用 Testcontainers」，**不得**改用 H2 宣称通过。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(admin): profile enable/disable with last-enabled protection (API-32/33)"
```

---

### Task 7：受害者画像后台写入接口（API-36～API-39，读取已在 Task 1 交付）

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/admin/service/AdminProfileService.java`（新增 `createVictim`、`updateVictim`、`disableVictim`、`enableVictim`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/admin/web/AdminProfileController.java`（新增四个 `POST`/`PUT`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/domain/VictimProfile.java`（新增构造、`apply(VictimProfileValues)`、`setEnabled(boolean)`；`isEnabled()` 已在 Task 1 添加）
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/domain/VictimProfileRepository.java`（新增 `save`、`lockAllByOrderByIdAsc()`）
- Test: `backend/src/test/java/com/aiscam/antiscam/admin/AdminVictimProfileIT.java`（新增；继承 Task 1 的 `MySqlTestBase`）

**Interfaces:**
- Consumes: Task 1～6 的全部产物；`VictimWriteRequest`/`VictimUpdateRequest`（Task 4）；`ApiException.lastEnabledProfile()`（Task 6）
- Produces: `AdminProfileService` 的受害者同形四个方法；`POST /admin/victim-profiles` → 201；`PUT /admin/victim-profiles/{id}` → 200；`POST /admin/victim-profiles/{id}/disable|enable` → 200

- [ ] **Step 1：写失败测试 `AdminVictimProfileIT`**

```java
@Test void createVictim_usesVictimFieldsAndDefaults()          // 201；含 antiFraudAwareness/riskTendency/personalityTraits；
                                                               // 断言响应中不存在 fraudExperience/speechSkill/manipulationSkill/behaviorTraits
@Test void createVictim_appearsInUserEndpoint()                // USER 端 /victim-profiles 数量由 3 变 4
@Test void updateVictim_replacesAllEditableFields()            // 全量替换、version+1、enabled 不变
@Test void victimAndScammerAreIndependentFamilies()
    // 只剩一个可用受害者时，停用该受害者 → 409 LAST_ENABLED_PROFILE；
    // 但同一时刻停用骗子画像（骗子还有多个可用）→ 200：证明"最后一项"保护按类型分别判定，不是全局计数
@Test void victimDisable_alreadyDisabledIsIdempotent()         // 200 且 SELECT row_version 未变化
@Test void victimEndpoints_rejectScammerOnlyFields()           // 提交 fraudExperience 到受害者接口 → 400 UNKNOWN_FIELD
@Test void victimEndpoints_are401Anonymously_403ForUser_requireCsrf()
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd verify "-Dit.test=AdminVictimProfileIT" -DfailIfNoTests=false`
Expected: FAIL（受害者写入接口未实现）。

- [ ] **Step 3：实现受害者侧写入**

要点：**不得复制粘贴**骗子侧的整段逻辑——把「按类型分派」的公共流程抽成 `AdminProfileService` 内的私有方法（入参为角色枚举与字段值对象），两方仅提供各自的仓库、值对象映射与视图工厂；`@Transactional(isolation = READ_COMMITTED)` 的传播与锁顺序必须与骗子侧完全一致。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd verify "-Dit.test=AdminVictimProfileIT" -DfailIfNoTests=false` 与 `cd backend && .\mvnw.cmd test`
Expected: 全部 PASS（含既有 `AuthHomeSmokeTest`、`ContentRulesTest`、`AdminScenarioApiTest`、`AdminProfileRulesTest`）。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(admin): victim profile create, edit and enable/disable (API-36/37/38/39)"
```

---

### Task 8：前端后台骨架、场景管理页与版本冲突处理（API-26、API-27）

**Files:**
- Modify: `frontend/src/api/http.ts`（新增 `put` 与 `postForm`，抽出写请求公共逻辑）
- Modify: `frontend/src/api/types.ts`（追加后台类型）
- Create: `frontend/src/api/admin.ts`
- Create: `frontend/src/components/AdminNav.vue`、`frontend/src/components/VersionConflictNotice.vue`
- Create: `frontend/src/views/AdminHomeView.vue`、`frontend/src/views/AdminScenariosView.vue`
- Modify: `frontend/src/router/index.ts`（`/admin`、`/admin/scenarios`，`meta.role: 'ADMIN'`）
- Test: `frontend/src/__tests__/admin-api.spec.ts`、`frontend/src/__tests__/admin-scenarios.spec.ts`

**Interfaces:**
- Consumes: 既有 `request`、`csrf.ensure()/clear()/refresh()`、`ApiError`；`useAuthStore`；既有 `AppNav.vue`（ADMIN 已链接到 `/admin`）
- Produces:
  - `http.put<T>(path, body): Promise<T>` 与 `http.postForm<T>(path, form: FormData): Promise<T>`：两者都先 `await csrf.ensure()` 注入 `X-CSRF-TOKEN`，并在 `CSRF_INVALID` 时清缓存重取后原样抛错；`postForm` **不得**设置 `Content-Type`（由浏览器生成 boundary），`put` 设置 `application/json`
  - `types.ts` 追加：`AdminScenario { id: string; code: Scenario['code']; name: string; description: string; focus: string; version: number; createdAt: string; updatedAt: string }`、`AdminProfileBase = ProfilePayload & { enabled: boolean; version: number; createdAt: string; updatedAt: string }`、`AdminScammerProfile`、`AdminVictimProfile`、`AdminProfilePage<T>`、`AvatarUpload { key: string; previewUrl: string; mediaType: 'image/jpeg' | 'image/png'; sizeBytes: number }`、`ScenarioUpdateRequest`、`VersionRequest`
  - `adminApi`：`listScenarios()`、`updateScenario(id, body)`、`listScammerProfiles(params?)`、`getScammerProfile(id)`、`createScammer(body)`、`updateScammer(id, body)`、`disableScammer(id, version)`、`enableScammer(id, version)`、`listVictimProfiles(params?)`、`getVictimProfile(id)`、`createVictim(body)`、`updateVictim(id, body)`、`disableVictim(id, version)`、`enableVictim(id, version)`、`uploadAvatar(file: File)`、`adminAvatarUrl(ref: AdminAvatarRef): string`
  - `AdminNav`：`data-testid="admin-nav"`，三个入口链接 `admin-nav-scenarios` / `admin-nav-scammers` / `admin-nav-victims`
  - `AdminHomeView`：`data-testid="admin-home"`，三个入口卡 + 明确声明「不提供用户演绎与报告查看」
  - `AdminScenariosView`：`data-testid="admin-scenarios"`、`scenario-row`、`scenario-edit`、`scenario-name-input`、`scenario-description-input`、`scenario-save`、`scenario-cancel`、`scenario-error`、`scenario-version`、`scenario-conflict`
  - `VersionConflictNotice`：props `{ currentVersion: number | null }`，`data-testid="version-conflict"`，显示「内容已被修改，请重新读取」并提供 `version-conflict-reload` 按钮触发重新读取

- [ ] **Step 1：写失败测试**

```ts
// admin-api.spec.ts
it('put sends JSON with the csrf header and no multipart content type', async () => {})
it('postForm sends FormData without setting Content-Type and includes the csrf header', async () => {})
it('refreshes the csrf token once and rethrows when a write gets CSRF_INVALID', async () => {})
it('maps a 409 VERSION_CONFLICT envelope to ApiError with currentVersion', async () => {
  // data.currentVersion 必须能从错误对象读到（沿用 ApiError；若现有 ApiError 不带 currentVersion，
  // 在本任务中为其增加可选字段并保持既有构造调用兼容）
})
// admin-scenarios.spec.ts
it('renders the three scenarios with their version and current copy', async () => {})
it('saves edited copy and refreshes the row from the server response', async () => {
  // 断言保存后页面显示服务端返回的 name/description/version（不是本地拼装值）
})
it('shows the conflict notice on 409 and reloads on demand without retrying the write', async () => {
  // 断言：冲突时不重发写请求；点击 version-conflict-reload 后才重新 GET /admin/scenarios
})
it('blocks the save button while a write is in flight and surfaces field errors', async () => {})
it('renders no user-record, report or deletion entry on the admin pages', async () => {
  // 断言页面文本不含「用户演绎」「报告」「删除场景」「新增场景」「停用场景」
})
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd frontend && npx vitest run src/__tests__/admin-api.spec.ts src/__tests__/admin-scenarios.spec.ts`
Expected: FAIL（`adminApi`、组件与路由不存在）。

- [ ] **Step 3：实现 HTTP 写方法、后台 API 层、导航与场景页**

要点：把现有 `http.post` 的 CSRF 与错误处理逻辑抽成一个内部 `write<T>(path, init)` 供 `post`/`put`/`postForm` 复用（保持 `post` 的既有行为不变，既有测试必须继续通过）；`ApiError` 增加可选的 `currentVersion` 字段（从 `data.currentVersion` 读取），构造签名保持向后兼容；场景页在加载失败时给出可重试的错误态，在保存失败时保留用户已输入内容（不清空表单），冲突时显示 `VersionConflictNotice`；编辑是行内表单，取消恢复服务端当前值；页面**不得**出现新增/删除场景的任何控件，**不得**出现用户记录入口；`/admin` 的路由从 `PlaceholderView` 改为 `AdminHomeView`，`/history` 仍是占位页不受影响。

- [ ] **Step 4：运行测试与类型检查确认通过**

Run: `cd frontend && npm.cmd test` 与 `cd frontend && npx vue-tsc --noEmit`
Expected: 两条命令退出码为 0，既有 `phase1.spec.ts`、`configuration.spec.ts` 仍通过。

- [ ] **Step 5：提交**

```bash
git add frontend/src
git commit -m "feat(frontend): admin shell, http write methods and scenario management"
```

---

### Task 9：画像管理页（新增、编辑、停用、启用、头像上传）

**Files:**
- Create: `frontend/src/components/AvatarUploader.vue`、`ProfileFormFields.vue`、`AdminProfileTable.vue`
- Create: `frontend/src/views/AdminScammersView.vue`、`frontend/src/views/AdminVictimsView.vue`
- Modify: `frontend/src/router/index.ts`（`/admin/scammers`、`/admin/victims`）
- Test: `frontend/src/__tests__/admin-profiles.spec.ts`

**Interfaces:**
- Consumes: Task 7 的 `adminApi` 全部方法、Task 8 的 `AdminNav`、`VersionConflictNotice`、`ApiError.currentVersion`
- Produces:
  - `AvatarUploader`：props `{ modelValue: string | null; previewUrl: string | null }`；emits `update:modelValue`；`data-testid="avatar-uploader"`、`avatar-file-input`、`avatar-upload`、`avatar-preview`、`avatar-upload-error`；选中文件后显示本地预览，点击上传调用 `adminApi.uploadAvatar(file)`，成功后把 `key` 与 `previewUrl` 交给父组件（**不**自动保存画像）；失败时按 `ApiError.code` 显示「图片内容无效或不是静态JPG/PNG」/「头像超过允许大小」等可读文案且保留原值
  - `ProfileFormFields`：props `{ role: 'SCAMMER' | 'VICTIM'; modelValue: ProfileFormValues }`；按 `role` 渲染行为参数项（骗子：诈骗经验/话术能力/心理操控能力/行为特点；受害者：防骗意识/风险倾向/性格特点）；`data-testid` 前缀 `field-`（如 `field-name`、`field-age`、`field-fraudExperience`、`field-antiFraudAwareness`）
  - `AdminProfileTable`：props `{ items: AdminProfileBase[]; role: 'SCAMMER' | 'VICTIM' }`；emits `edit`、`toggle`；`data-testid="admin-profile-row"`、`profile-enabled-badge`、`profile-toggle`、`profile-edit`、`profile-version`
  - `AdminScammersView`：`data-testid="admin-scammers"`、`scammer-new`、`scammer-form`、`scammer-save`、`scammer-error`、`scammer-title`
  - `AdminVictimsView`：同形 `data-testid="admin-victims"`、`victim-new`、`victim-form`、`victim-save`、`victim-error`

- [ ] **Step 1：写失败测试 `admin-profiles.spec.ts`**

```ts
it('lists enabled and disabled profiles with version and enabled badge', async () => {})
it('creates a profile with the uploaded avatar key and refreshes from the server response', async () => {
  // 先上传（mock uploadAvatar 返回 {key,previewUrl,...}）→ 提交表单 → 断言 createScammer 收到 avatarKey==key，
  // 且新行内容来自服务端返回而不是本地对象
})
it('blocks saving before a valid avatar exists and explains why', async () => {
  // 未上传头像时 scammer-save 禁用并有可读提示；上传失败后仍禁用且显示 avatar-upload-error
})
it('edits a profile and sends its current version', async () => {
  // 断言 updateScammer 收到的 body.version == 该行 version，且不含 enabled 字段
})
it('shows a field error from the API next to the matching input', async () => {
  // 400 fieldErrors [{field:'name',code:'LENGTH_INVALID',message:'名称须为1至64个字符'}] → 渲染到 field-name 附近
})
it('disables a profile only after confirmation and shows the last-enabled rejection', async () => {
  // 停用需二次确认；返回 409 LAST_ENABLED_PROFILE → 显示「该类必须保留至少一个可用画像」，
  // 断言该行的启用状态未被本地改写成 false、未重发请求
})
it('re-enables a disabled profile and refreshes its row', async () => {})
it('shows the conflict notice and reloads on a 409 version conflict', async () => {})
it('renders no user-record or report entry in either management page', async () => {
  // 断言页面文本不含「用户」「演绎记录」「报告」「导出」，且不存在任何指向 /history 的链接
})
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd frontend && npx vitest run src/__tests__/admin-profiles.spec.ts`
Expected: FAIL。

- [ ] **Step 3：实现上传组件、表单字段、列表与两个管理页**

要点：新增与编辑共用同一个表单组件与提交流程（编辑时把当前行填入表单，保存时带上该行 `version`）；**提交的请求体必须由表单值显式构造**，逐字段列出，避免把服务端返回的 `enabled`/`createdAt`/`updatedAt` 透传回写接口（否则会被 400 拒绝）；停用/启用前用 `window.confirm` 做二次确认，确认文案包含画像姓名；所有写操作成功后必须用服务端响应替换本地行（不得本地构造）；列表分页沿用既有「加载更多」模式（`nextCursor`/`hasMore`），后台列表**不按启用过滤**；头像预览用 `adminApi.adminAvatarUrl(...)` 返回的服务端地址，`<img>` 的 `@error` 要显示可读失败态而不是破图；页面不出现删除按钮、用户记录或报告入口。

- [ ] **Step 4：运行测试与类型检查确认通过**

Run: `cd frontend && npm.cmd test` 与 `cd frontend && npx vue-tsc --noEmit` 与 `cd frontend && npm.cmd run build`
Expected: 三条命令退出码均为 0。

- [ ] **Step 5：提交**

```bash
git add frontend/src
git commit -m "feat(frontend): scammer and victim profile management with avatar upload"
```

---

### Task 10：双向权限、契约校验、端到端验收与文档更新

**Files:**
- Create: `backend/src/test/java/com/aiscam/antiscam/admin/AdminPermissionTest.java`
- Modify: `tools/validate-openapi.mjs`（追加 16 条后台路径断言）
- Create: `tools/smoke-admin.ps1`
- Modify: `README.md`（已实现范围、后台使用说明、验收记录、本计划链接）

**Interfaces:**
- Consumes: Task 1～9 的全部产物
- Produces: 可复现的验收记录（命令与输出）、后台冒烟脚本、更新后的运行说明

- [ ] **Step 1：写 `AdminPermissionTest`（双向穿透）**

```java
@Test void userIsForbiddenOnEveryAdminEndpoint()
    // 用 ROLE_USER 的 authentication 遍历 16 个接口（含 GET/PUT/POST、含头像上传）
    // 断言全部 403 FORBIDDEN；写操作带 csrf() 后处理器以确保结论是角色而不是 CSRF
@Test void anonymousIsUnauthenticatedOnEveryAdminEndpoint()   // 全部 401 UNAUTHENTICATED
@Test void adminIsForbiddenOnEveryUserReadEndpoint()
    // ROLE_ADMIN 访问 /home、/news、/scenarios、/scammer-profiles、/victim-profiles、/avatars/{key}
    // 断言全部 403 FORBIDDEN，且响应体不含任何画像业务字段
@Test void forbiddenResponsesCarryNoContentOrVersion()       // 断言 403/401 的 data 为 null、不含 enabled/version/name
@Test void adminCannotReachSimulationOrHistoryPaths()        // ROLE_ADMIN 访问 /simulations、/simulations/1、/simulations/1/report → 403（不是 404）
@Test void adminSessionCannotReadAvatarThroughUserEndpoint() // ADMIN 访问 /avatars/{key} → 403（用户头像读取仅 USER）
```

- [ ] **Step 2：运行权限测试确认先失败后通过**

Run: `cd backend && .\mvnw.cmd test "-Dtest=AdminPermissionTest"`
Expected: 首次运行若发现遗漏的放行规则则 FAIL，补齐 `SecurityConfig` 中 `GET /anti.scam/v1/admin/**` 与写方法的规则后 PASS。要求：`/anti.scam/v1/admin/**` 的所有方法都必须在 `hasRole("ADMIN")` 之内，且用户接口的 `hasRole("USER")` 规则不得被后台规则覆盖。

- [ ] **Step 3：扩展 `tools/validate-openapi.mjs`**

在既有断言之后追加 16 条：`/admin/scenarios`(get,put 分别在 `/admin/scenarios/{id}`)、`/admin/scammer-profiles`(get,post)、`/admin/scammer-profiles/{id}`(get,put)、`/admin/scammer-profiles/{id}/disable`(post)、`/admin/scammer-profiles/{id}/enable`(post)、`/admin/victim-profiles`(get,post)、`/admin/victim-profiles/{id}`(get,put)、`/admin/victim-profiles/{id}/disable`(post)、`/admin/victim-profiles/{id}/enable`(post)、`/admin/avatars`(post)、`/admin/avatars/{key}`(get)；并断言 `components.schemas.AdminScenario.additionalProperties === false`、`components.schemas.AdminScammerProfile.required` 含 `enabled`/`version`/`createdAt`/`updatedAt`。输出文案改为 `OpenAPI valid; phase 1 (7) + phase 2 (6) + phase 3 (16) operations present.`

Run: `node tools/validate-openapi.mjs`
Expected: 退出码 0 并打印上述文案。

- [ ] **Step 4：写 `tools/smoke-admin.ps1` 并运行**

以 `tools/smoke-content.ps1` 为模板（同样使用 `.tools/admin-smoke-<随机值>` 隔离目录、`13309/18082` 端口、随机管理员口令、结束时不改动现有业务库），断言至少覆盖：

1. 匿名与 USER 访问 `/admin/scenarios` 分别 401 与 403；ADMIN 访问 `/home`、`/scenarios`、`/avatars/<key>` 全部 403。
2. ADMIN 登录后 `GET /admin/scenarios` 三项，`version` 为 0；`PUT` 改简介成功且 `version` 变 1；用旧 `version` 再 `PUT` → 409 `VERSION_CONFLICT` 且 `data.currentVersion == 1`。
3. `GET /admin/scammer-profiles` 返回 2 项且含 `enabled` 与 `version`；`GET /admin/victim-profiles` 返回 3 项。
4. 上传一张 `backend/var/avatars/` 中的既有 PNG → 201，`previewUrl` 可 200 取回 `image/png` + `nosniff`；上传超限字节 → 413；上传文本字节 → 400 `INVALID_IMAGE`。
5. `POST /admin/scammer-profiles` 新建一位骗子画像 → 201 且 `enabled=true`、`version=0`；随后 USER 端 `/scammer-profiles` 能读到该新画像（数量由 2 变 3）。
6. `PUT` 编辑刚新建的画像 → 200 且 `version=1`；用旧版本再编辑 → 409。
7. 停用一个受害者画像（还有两个可用）→ 200 且 `enabled=false`，USER 端列表数量由 3 变 2；再停用剩余两个中的第二个 → 200；停用最后一个 → 409 `LAST_ENABLED_PROFILE` 且 USER 端仍能读到 1 个可用项。
8. 重新启用被停用的画像 → 200 且 `enabled=true`，USER 端恢复可见；`GET /admin/victim-profiles?enabled=true` → 400（后台列表不接受该参数）。
9. 全程断言每个响应都带 `Cache-Control: no-store`，且响应中不出现 `password`、`sessionId`、SQL 或绝对路径。

Run: `pwsh -NoProfile -File tools/smoke-admin.ps1`
Expected: 脚本无抛错退出（`$LASTEXITCODE` 为 0），输出每条断言的通过标记。

- [ ] **Step 5：运行全量回归**

Run:
```
cd backend && .\mvnw.cmd package
cd frontend && npm.cmd test
cd frontend && npm.cmd run build
cd .. && node tools/validate-openapi.mjs
```
Expected: 后端打包成功且全部单元/HTTP 冒烟测试通过；前端测试与生产构建通过；契约脚本退出码 0。若 Docker 可用，另跑 `.\mvnw.cmd verify` 以覆盖 `AdminProfileReadIT`、`AdminScammerProfileIT`、`AdminVictimProfileIT`；不可用则用 `smoke-admin.ps1` 的隔离实例结果并在 README 如实记录。

- [ ] **Step 6：更新 `README.md`**

必须包含：本阶段 16 个接口表；后台三个入口页与使用说明（含「不提供用户演绎与报告查看」的明确声明）；后台写操作的版本语义（409 时的处理方式与「重新读取」要求）；启停的最低可用数量规则与幂等语义；头像上传的格式与 5 MB 上限、`AVATAR_STORE` 与后台预览的关系；`spring.servlet.multipart` 上限配置项；测试与冒烟命令（含 `tools/smoke-admin.ps1`）；本阶段已实现／未实现清单（明确写出「演绎、复盘、历史仍未实现」）；本次验收的实际结果与未执行项；并在文首设计文档列表中补上本计划 `coding plan/2026-10-17-phase3-admin-console.md` 的链接。

- [ ] **Step 7：提交**

```bash
git add backend/src frontend/src tools README.md
git commit -m "test(admin): two-way permissions, openapi checks, isolated smoke verification and docs"
```

---

## 六、完成判据（Definition of Done）

1. `.\mvnw.cmd package` 与 `.\mvnw.cmd test` 全部通过；`npm.cmd test`、`npm.cmd run build`、`npx vue-tsc --noEmit` 退出码 0；`node tools/validate-openapi.mjs` 退出码 0。
2. API-26～API-41 十六个接口的字段、状态码、错误码、`Cache-Control` 头与 `openapi.yaml` 逐项一致；`tools/smoke-admin.ps1` 全部断言通过。
3. 「复审重点」五条各自至少有一个通过的测试，且测试名能直接读出它守的是什么行为。
4. `git log` 中每个 Task 恰好一次提交，提交信息与 Task 编号可追溯。
5. 数据库结构与第二阶段完全一致：仍然只有 4 张业务表，**没有**新增迁移文件、没有新增表或列。
6. 第二阶段对外契约未回归：`content` 包仍为只读，USER 端接口仍不暴露 `enabled`/`version`/`createdAt`/`updatedAt`，既有 `ContentRulesTest`、`AuthHomeSmokeTest`、`configuration.spec.ts`、`phase1.spec.ts` 全部通过。
7. 后台页面无用户演绎/报告入口、无场景增删、无画像永久删除、无图片裁剪或 AI 头像生成；写操作成功后展示的内容来自服务端响应。
8. `README.md` 的说明可被未参与开发的人在干净环境复现；未执行的验证项如实列出。

## 七、风险与阻塞项（执行者必须回报而非自行决定）

- **Docker 不可用**：`AdminScammerProfileIT` 中的并发停用用例是唯一真正验证「并发停用不破坏最低可用数量」的测试，默认依赖 Testcontainers 的 `mysql:8.0.45`；本机 Docker daemon 自 2026-10-16 起未运行。处理方式是按 Task 6 Step 4 改用 README 记录的隔离 MySQL 方式，并在 README 记录「未使用 Testcontainers」；**不得**改用 H2/SQLite 宣称并发行为已验证（数据库设计 §12.1 明确禁止）。
- **`@Version` 与「不递增版本」的组合**：启停幂等与版本冲突都要求失败的请求**不得**改变 `row_version`。实现时务必「先判定与校验、后改动实体」，否则 JPA 脏检查会把失败请求也写成一次更新。若实测发现版本被意外递增，回报现象后再决定（可改为 `@Modifying` 条件更新），不要用放宽断言掩盖。
- **413 的实际可返回性**：Servlet 容器可能在解析 multipart 阶段就中断请求，导致无法返回 JSON 格式的 413。Task 3 Step 3 已要求实测；若确实无法返回，回报实测结果并在 README 写明客户端看到的行为，**不要**静默改成 400 或放弃该断言。
- **TEXT 字段容量上限（TBD-09）**：接口文档 §8 明确该限制尚未统一确认，而数据库设计 §2.2 要求提交前校验可容纳的字节长度、禁止静默截断。本计划采用 `RequestContent.MAX_TEXT_CHARS = 16000` 作为防御性上限并在代码注释中标注依据与不确定性；若需求方给出正式上限，替换该常量并同步测试，不要在两个模块里各写一个数字。
- **启停错误优先级**：接口文档同时列出 `LAST_ENABLED_PROFILE` 与 `VERSION_CONFLICT`，但未规定两者同时成立时的优先顺序。本计划固定为「已处于目标状态 → 最后一个可用 → 版本冲突」，并在 Task 6 Step 1 用一个测试固化；若执行时认为顺序有误，先回报再改，不要在两处写不同顺序。
- **`updated_at` 的可见性**：`updated_at` 由数据库 `ON UPDATE CURRENT_TIMESTAMP(3)` 维护，实体只读映射。同一事务内回读可能拿到更新前的值；Task 5 Step 3 给出了 `flush` + `refresh` 的处理方式。**不要**改成应用侧 `Instant.now()` 赋值（会与数据库时间源分叉），也不要新增迁移改列。
- **后台列表的 `enabled` 参数**：后台列表接口按契约**不接受** `enabled` 过滤（openapi 只列 `cursor`/`limit`），传入应 400。这与用户端列表「`enabled` 只接受 true」的规则不同，不要为「统一」而给后台加上过滤能力。
- **AP-07 中与演绎相关的条目**：涉及「已选画像被停用后开始校验」的端到端验收仍需演绎模块（下一阶段）才能完整覆盖。本阶段只验收「停用后 USER 端不再可选、详情 404、历史不受影响的结构前提」，其余在 README 中明确标注为后续阶段。
- **`createdAt`/`updatedAt` 的序列化格式**：契约要求 UTC ISO 8601 含三位毫秒与 `Z`。既有全局 Jackson 配置**未**显式设置 `WRITE_DATES_AS_TIMESTAMPS`/`WRITE_DATES_WITH_ZONE_ID`，默认行为依赖 Spring Boot 自动配置与 `spring.jackson` 属性。Task 1 Step 1 的时间格式断言必须先真实运行一次；若输出是数组或缺少毫秒/`Z`，在**后台 DTO 上**固定为字符串（例如在 `AdminScenarioView` 用 `@JsonFormat`），**不要**改动全局 Jackson 配置——那会同时改变第一阶段已交付接口的响应形状。
- **数据库结构与迁移脚本未独立验证**：第二阶段的交付记录显示，隔离实例冒烟是对**最终库状态**的断言，`V1→V3` 迁移脚本本身从未在空库上独立执行过。本计划不新增迁移，也不需要处理该历史缺口；但如果执行过程中在任何空库上首次跑通 V1→V3，请在 README 中如实记录该事实（可作为该缺口的补充证据），**不要**声称此前已验证。
