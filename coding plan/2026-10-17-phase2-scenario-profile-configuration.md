# 第二阶段（场景画像配置模块）开发计划

> **面向 AI 执行者：** 本计划的每一步均使用复选框（`- [ ]`）语法，请按顺序逐任务（Task）执行。每个 Task 结束都必须通过其验证步骤，并完成一次 git 提交后再进入下一个 Task。本计划建立在已完成的「登录注册模块 + 首页模块」代码之上，请在现有文件与现有约定上增量开发，不要重写既有模块。

**目标（Goal）：** 交付可运行的「场景画像配置模块」垂直切片——普通用户登录后从首页进入配置页，看到数据库中的三个固定诈骗场景，浏览两方全部可用画像的完整详情（姓名、年龄、性别、身份、人物描述、行为参数、时间、地点、当前状态、头像），自由选择一个场景 + 一个骗子画像 + 一个受害者画像，在页面上确认完整配置后点击开始；服务端提供 API-08～API-13 六个只读接口，字段、错误码、分页语义与缓存头严格实现《接口文档 V1.0》与 `openapi.yaml`。

**架构（Architecture）：** 延续 Spring Boot 模块化单体，新分包 `content`（基础内容：场景与画像读取）承担本模块全部后端职责；新增 Flyway `V2`（三张基础内容表）与 `V3`（三场景 + 五张初始人物卡种子数据）；头像为受控文件资源，键名取文件内容 SHA-256，落库只存键、不存临时地址，读取时校验文件签名。前端新增 `/configuration` 真实页面替换现有占位页，选中状态只存在于页面局部（Vue `ref`），不写入 localStorage，不以本地状态作为权限或归属依据。

**技术栈（Tech Stack）：** 沿用第一阶段锁定版本：Java 21（Corretto 21.0.8）+ Spring Boot 3.5.16（web / data-jpa / security / validation / flyway-mysql）+ MySQL 8.0.45 + Flyway；前端 Vue 3.5.44 + Vue Router 4.5.1 + Pinia 3.0.3 + Vite 6.4.4 + TypeScript 5.9.3 + Vitest 5.0.5 + Vue Test Utils 2.4.6 + jsdom 26.1.0；契约校验 `@apidevtools/swagger-parser` 12.0.0；头像资源生成脚本使用 Python + Pillow（仅开发期一次性使用，不进入运行期依赖）。

**Spec（本计划实现的规格文档，执行者必须同时阅读）：**
- `docs/PRD_V3.1_正式开发基线版.md`（§8.3 场景画像配置模块 FR-CFG-01～04、§11.1 PC-P01 三场景、§11.3 PC-P02 五张人物卡、§4.1/§4.2 权限、§7 页面结构、§12.1 UX-01～05、§13.1 AC-09～AC-11 与 AC-05、§13.2 AP-07 的「选中后被停用」一项）
- `docs/系统架构设计文档_V1.0.md`（§4.1「基础内容」模块职责、§5.1 页面组织与状态归属、§13.1、§13.4）
- `docs/数据库设计文档 V1.0.md`（§2.1 存储基线、§2.2 字段字典、§5.2、§5.3、§5.4、§6.1、§6.3、§6.4、§10.2、§10.3、§11.1、§11.2、§12.1 的 DBV-11/DBV-14）
- `docs/接口文档_V1.0.md`（§2.1、§2.2、§2.3 权限矩阵、§2.4 分页与缓存、§6 的 API-08～API-13、§7 字段字典、§8 错误码）
- `openapi.yaml`（机器可读契约，**字段名、约束、状态码与错误码的最终依据**）
- `coding plan/2026-10-17-phase1-auth-and-home.md`（已完成阶段的约定来源：封套、CSRF、会话、测试分层）

---

## 一、本阶段范围与模块边界

### 1.1 本阶段实现的接口（唯一交付范围）

| 编号 | 方法 | 完整路径 | 用途 | 权限 |
|---|---|---|---|---|
| API-08 | GET | `/anti.scam/v1/scenarios` | 固定三场景列表（不分页） | USER |
| API-09 | GET | `/anti.scam/v1/scammer-profiles` | 可用骗子画像（游标分页） | USER |
| API-10 | GET | `/anti.scam/v1/scammer-profiles/{id}` | 骗子画像详情 | USER |
| API-11 | GET | `/anti.scam/v1/victim-profiles` | 可用受害者画像（游标分页） | USER |
| API-12 | GET | `/anti.scam/v1/victim-profiles/{id}` | 受害者画像详情 | USER |
| API-13 | GET | `/anti.scam/v1/avatars/{key}` | 用户头像读取（二进制） | USER |

同时交付：数据库 `fraud_scenario`、`scammer_profile`、`victim_profile` 三张表与初始内容；前端 `/configuration` 真实配置页（替换现有 `PlaceholderView`）；配置页「开始演绎」按钮的**可用性校验 UI**（服务端开始接口未实现，按钮不发请求，见 Task 8）。

### 1.2 本阶段明确不做（禁止提前实现、禁止预留占位接口）

- **API-14 及之后的全部接口**：开始演绎、演绎状态、SSE、三类呈现回执、退出、临时语音、报告与重试、历史列表与详情、`/timing/samples`（API-42）。
- **全部 `/admin/**` 接口**（API-26～API-41）：后台场景文案编辑、画像新增/编辑/停用/启用、头像上传与管理员预览。这些属于「管理后台模块」，另立计划。本阶段**只读**基础内容。
- **数据库只建 3 张新表**：`simulation_session`、`simulation_round`、`simulation_message`、`simulation_report`、`agent_prompt` 一律**不在本阶段创建**，也不允许先建空表占位。
- 不允许在本阶段实现画像的写操作（新增、编辑、停用、启用），也不允许为本阶段未实现的接口预留 Controller 空方法、DTO 或路由。
- 前端 `/history`、`/admin` 继续保留「后续阶段」说明页，不发起任何请求。
- 不引入缓存中间件、CDN、图片处理库（裁剪/压缩/AI 头像生成）、JWT、Redis、SSE。

---

## 二、全局约束（Global Constraints，所有任务默认包含）

以下数值来自规格文档，**必须逐字实现，不得「优化」**：

1. 接口前缀固定 `/anti.scam/v1`；JSON 字段 `lowerCamelCase`；成功 `code="OK"`，失败 `code` 为稳定英文码。
2. 统一响应封套四个顶层字段始终存在：`code`、`message`、`data`、`requestId`；`requestId` 为服务端生成的 UUID（沿用既有 `RequestIdFilter`／`RequestIds`）。
3. 业务 ID 传输为**正整数十进制字符串**（`pattern ^[1-9][0-9]{0,19}$`，如 `"11"`），**不是 JSON number**；路径参数与游标里的 ID 同样按此规则校验。
4. 定性参数 `Level` 只允许 `LOW` / `MEDIUM` / `HIGH` 三个字符串；前端可映射为「低／中／高」展示，但**传输值不得改写**。
5. **画像完整字段（一个都不能少）**：
   - 骗子画像：`id`、`avatar{key,url}`、`name`、`age`、`gender`、`identity`、`description`、`contextTime`、`contextPlace`、`currentState`、`fraudExperience`、`speechSkill`、`manipulationSkill`、`behaviorTraits`。
   - 受害者画像：`id`、`avatar{key,url}`、`name`、`age`、`gender`、`identity`、`description`、`contextTime`、`contextPlace`、`currentState`、`antiFraudAwareness`、`riskTendency`、`personalityTraits`。
   - 两者 `additionalProperties: false`，响应中**禁止出现** `enabled`、`version`、`createdAt`、`updatedAt`、`isEnabled`、`rowVersion`、`avatarKey` 等后台字段。
6. 场景响应字段固定为 `id`、`code`、`name`、`description`、`focus` 五项；`code` 只能是 `TASK_REBATE` / `FAKE_INVESTMENT` / `IMPERSONATION_LOAN`；`focus` 由 `scenario_code` 按固定映射派生（青年群体／中年群体／老年群体），**不是数据库可编辑字段**。
7. `GET /scenarios` 恒返回**恰好 3 项**（`minItems: 3, maxItems: 3`），按场景 ID 升序；不分页，不提供 `cursor` / `limit`。
8. 画像列表只返回 `is_enabled = 1` 的记录，按 `id ASC` 排序；`limit` 默认 20、范围 1—100，越界返回 **400 `VALIDATION_FAILED`**，不静默截断；响应结构固定为 `{items, nextCursor, hasMore}`，**不提供 total、页码**；无下一页时 `nextCursor = null`、`hasMore = false`。
9. `enabled` 查询参数为 **boolean 且只接受 `true`**（省略等同 `true`）：传 `false`、`0`、`1`、`yes`、空串一律 **400 `VALIDATION_FAILED`**，字段错误指向 `enabled`，不得借此读取停用项。
10. 游标 `cursor` 是**服务端可校验的不透明值**，绑定本次查询的作用域（资源类型 + 排序 + 筛选）：损坏、被篡改、跨接口或跨筛选复用一律 **400 `INVALID_CURSOR`**（`message` = 「分页游标无效」，`data = null`）；游标不是授权凭据，每页独立鉴权。
11. 画像详情只返回**当前启用**的画像；停用或不存在一律 **404 `NOT_FOUND`**，`message` 恰为「资源不存在或不可访问」，`data` 为 `null`；两者响应体必须完全一致。
12. 权限（《接口文档》§2.3）：本模块 6 个接口匿名 **401 `UNAUTHENTICATED`**（`message` 沿用现有实现文案，不重定向 HTML）；**ADMIN 一律 403 `FORBIDDEN`**（先按角色拒绝，再谈资源）；只有 USER 可以读取。
13. 认证响应、私有 JSON 与头像一律 `Cache-Control: no-store`；头像为 `private, no-store` 且必须带 `X-Content-Type-Options: nosniff`，`Content-Type` 只能是 `image/png` 或 `image/jpeg`；不允许浏览器缓存绕过访问权校验。
14. 失败响应 `data` 默认为 `null`；字段错误放入 `data.fieldErrors`（每项 `field` / `code` / `message`），字段错误码只用 `REQUIRED`、`TYPE_INVALID`、`LENGTH_INVALID`、`VALUE_INVALID`、`UNKNOWN_FIELD`。错误 `message` 中不得出现 SQL、堆栈、文件绝对路径、供应商原文。
15. 数据库基线：MySQL **8.0.45**、InnoDB、`utf8mb4`、默认排序规则 `utf8mb4_0900_bin`、时间列 `DATETIME(3)` 且统一写 UTC、枚举用 `VARCHAR + CHECK`、无软删除字段、无级联删除、应用业务事务显式使用 `READ COMMITTED`。
16. 迁移版本化：`V1__create_sys_user.sql` **已执行且不可原地修改**；本阶段新增 `V2`、`V3`，不得改 V1。建表 SQL 只通过 Flyway 迁移产生，不使用 `ddl-auto`（保持 `validate`）。
17. 画像**不允许永久删除**，只有 `is_enabled` 启停字段；本阶段不实现任何写操作，因此数据库账号在运行期对本模块三张表**只需 SELECT**。
18. 头像文件不可覆盖：文件名取**文件内容 SHA-256**（64 位小写十六进制）+ `.png`/`.jpg`，`avatar_key` 存 `avatars/<sha256>.<ext>`；换头像必然产生新键，旧文件不删不改。头像必须是**真实可解码的静态 JPG/PNG**（Pillow 12.3.0 生成），**禁止**用描述文字、坏路径、0 字节文件或占位符冒充已完成初始化。
19. 前端只保存「选中的场景/骗子/受害者」这类展示状态；`/configuration` 页面**不得**把选中项写入 `localStorage`/`sessionStorage`，**不得**自行推导权限结论，服务端 401/403/404 才是事实。
20. 页面文案不得虚构信息：场景名称与简介逐字来自 `GET /scenarios`，画像字段逐字来自画像接口；不得在前端硬编码场景/画像内容，不得用示例人物（如「示例骗子甲」）替代 PRD 初始人物卡。

---

## 三、复审重点（Review Focus）

以下五类输入或失败模式是本阶段最可能伤害真实使用者的位置，规格文档暗示了但常规任务不会自然覆盖。**每条已在括号中注明其测试落在哪个任务的步骤，实现这些任务时必须写出对应测试，不得省略：**

1. **路径标识与查询参数的非法输入**——`/scammer-profiles/abc`、`/scammer-profiles/007`、`/scammer-profiles/0`、超长数字 ID、`?limit=0`、`?limit=101`、`?limit=abc`、`?limit=10.5`、`?enabled=false`、`?enabled=1`、`?cursor=乱码`、`?cursor=` 空串。合理预期：前一组合 400 `VALIDATION_FAILED` 且 `data.fieldErrors` 指向 `id`/`limit`/`enabled`/`cursor`；空 `cursor` 等同于首屏正常返回 200；**任何情况下不得出现 500 或把非法值静默当成合法值**。（Task 6 步骤 1/3、Task 7 步骤 1/3）
2. **游标的作用域绑定与篡改**——把 API-09 返回的 `nextCursor` 原样传给 API-11、把 `enabled=true` 的游标用于其他筛选、手工 base64 解码后改动 `id` 再编码、截断字符串。合理预期：全部 400 `INVALID_CURSOR`，且不泄露游标内部结构；同一接口原样回传时必须能正确续页且**不重复、不跳过**记录。（Task 3 步骤 1/3、Task 6 步骤 1/3，后者含 `list_paginationWithLimit1_walksAllPagesWithoutDuplicatesOrGaps`）
3. **空数据与尚未生成的字段**——数据库无任何可用画像时列表返回 `{"items":[],"nextCursor":null,"hasMore":false}`（不是 `data:null`、不是 404）；`GET /scenarios` 恰好 3 项时正常、若基础数据被破坏导致非 3 项则 500 `DATA_INCONSISTENT` 并给出可读说明。合理预期：空列表被渲染成明确的空状态，而不是「加载失败」或被补造出示例人物。（Task 5 步骤 1/3、Task 6 步骤 1/3、Task 7 步骤 1、Task 8 步骤 1/3）
4. **头像的缺失、格式不符与失败加载**——文件不存在、扩展名与真实内容不符（把 `.png` 写成 JPEG 字节）、`key` 形如 `avatars/../../application.yml` 或带 `/`、非 64 位小写十六进制、图片 `<img>` 加载失败。合理预期：非法 `key` 为 400、找不到文件为 404（响应体与其他 404 完全一致）、路径穿越绝不读到业务文件；前端在头像加载失败时给出可见提示并**阻止用该画像开始**，不得静默显示破图后照常放行。（Task 4 步骤 1/3、Task 8 步骤 1/3）
5. **选中项在读取后被停用（PC-P02 与 AP-07「选中后被停用」）**——用户在页面上选好画像 → 数据库把该画像置为 `is_enabled = 0` → 用户点击开始。合理预期：页面重新校验时发现该画像已不可用，提示「所选画像已不可用，请重新选择」并清除该选中项，**不得静默替换成另一个画像**，也不得带着无效配置继续。（Task 8 步骤 1/3/5）

补充要求（同样必须在对应任务中测试）：画像接口的响应**必须能被识别为「不含后台字段」**——`enabled`/`version`/`createdAt`/`updatedAt` 出现即为缺陷（Task 6 步骤 1/3）；`GET /avatars/{key}` 空 `key` 路径、正确键但文件被删除、ADMIN 访问，三者分别得到 400/404/403（Task 4 步骤 1/3）。

---

## 四、文件结构（先定边界，再拆任务）

```
D:\AI-anti-scam\
├── coding plan\
│   └── 2026-10-17-phase2-scenario-profile-configuration.md      ← 本计划
├── tools\
│   ├── validate-openapi.mjs                                     ← 修改：增加 6 条本阶段路径断言
│   └── generate-avatars.py                                      ← 新增：一次性生成 5 张初始头像 + 打印 SHA-256
├── backend\
│   ├── var\avatars\                                             ← 新增：受控头像目录（提交进 Git）
│   │   └── <sha256>.png × 5
│   └── src\
│       ├── main\
│       │   ├── java\com\aiscam\antiscam\
│       │   │   ├── common\
│       │   │   │   ├── api\ErrorCodes.java                      ← 新增：本阶段使用的稳定错误码常量
│       │   │   │   └── error\ApiException.java                  ← 修改：新增 invalidCursor / notFound / dataInconsistent 工厂
│       │   │   ├── config\ContentProperties.java                ← 新增：@ConfigurationProperties("app.content")
│       │   │   └── content\
│       │   │       ├── domain\
│       │   │       │   ├── FraudScenario.java / FraudScenarioRepository.java
│       │   │       │   ├── ScenarioCode.java                    ← 三码 + 固定 focus 映射
│       │   │       │   ├── ScammerProfile.java / ScammerProfileRepository.java
│       │   │       │   ├── VictimProfile.java / VictimProfileRepository.java
│       │   │       │   └── Level.java                           ← LOW/MEDIUM/HIGH
│       │   │       ├── page\
│       │   │       │   ├── ContentCursor.java                   ← 不透明游标编解码（base64url+JSON+作用域）
│       │   │       │   └── PageQuery.java                       ← limit/cursor 解析与校验
│       │   │       ├── avatar\
│       │   │       │   ├── AvatarKey.java                       ← 键格式校验
│       │   │       │   ├── AvatarResource.java                  ← bytes + mediaType
│       │   │       │   └── AvatarStore.java                     ← 受控目录读取 + 文件签名判定
│       │   │       ├── service\ContentService.java              ← 场景/画像查询与 DTO 映射
│       │   │       ├── startup\ContentStartupCheck.java         ← 启动校验（三场景/每类≥1可用/头像可读）
│       │   │       └── web\
│       │   │           ├── ScenarioController.java              ← API-08
│       │   │           ├── ScammerProfileController.java        ← API-09 / API-10
│       │   │           ├── VictimProfileController.java         ← API-11 / API-12
│       │   │           ├── AvatarController.java                ← API-13
│       │   │           └── dto\ScenarioView.java / ScammerView.java / VictimView.java /
│       │   │                  AvatarRef.java / PageView.java
│       │   └── resources\
│       │       ├── application.yml                              ← 修改：app.content.* 配置
│       │       └── db\migration\
│       │           ├── V2__create_content_tables.sql            ← 三张表 + 约束 + 索引
│       │           └── V3__seed_content_data.sql                ← 三场景 + 五张初始人物卡
│       └── test\java\com\aiscam\antiscam\
│           ├── support\MySqlTestBase.java                       ← 新增：抽出的 Testcontainers 基类
│           ├── content\ContentCursorTest.java                   ← 纯单元：游标编解码与 limit 规则（surefire）
│           ├── content\AvatarKeyTest.java                       ← 纯单元：键格式与文件签名（surefire）
│           ├── content\ContentSchemaIT.java                     ← 迁移与列/约束/长度
│           ├── content\ContentSeedIT.java                       ← 种子内容与头像哈希
│           ├── content\ContentStartupCheckIT.java                ← 启动校验与启停查询
│           ├── content\ScenarioApiIT.java                       ← API-08
│           ├── content\ProfileApiIT.java                        ← API-09～API-12（含游标/参数/权限）
│           ├── content\AvatarApiIT.java                         ← API-13（含路径穿越/签名/权限）
│           └── content\ContentAcceptanceIT.java                 ← AC-05/09/10/11 + DBV-11 验收
└── frontend\
    └── src\
        ├── api\
        │   ├── types.ts                                         ← 修改：追加配置模块类型
        │   └── content.ts                                       ← 新增：contentApi
        ├── components\
        │   ├── ScenarioPicker.vue                               ← 场景选择
        │   ├── ProfileCard.vue                                  ← 画像卡（含头像与加载失败态）
        │   ├── ProfileDetail.vue                                ← 画像详情（完整字段）
        │   └── ConfigurationSummary.vue                         ← 已选配置确认 + 开始入口
        ├── views\ConfigurationView.vue                          ← 新增：替换 /configuration 占位页
        ├── router\index.ts                                      ← 修改：/configuration 指向 ConfigurationView
        └── __tests__\
            ├── configuration-view.spec.ts                       ← 页面加载/空态/失败态
            ├── configuration-flow.spec.ts                       ← 选择、详情、确认、开始校验、停用重选
            └── content-api.spec.ts                              ← contentApi 契约与参数拼接
```

**结构约束：** 一个文件一个职责；`content` 包不 import `auth` 包的实体，只通过 `common` 与 Spring Security 的 `Authentication` 交互；`web/dto` 只做响应映射（禁止直接返回 Map 或 JPA 实体，避免漏出 `isEnabled`/`rowVersion`）；`content` 内不放任何写操作；前端 `api/types.ts` 是唯一类型来源，组件不得自行声明接口响应类型；头像文件的磁盘路径只能由 `AvatarStore` 计算，其他类不得自行拼接路径。

---

## 五、任务清单

### Task 1：基础内容三张表的 Flyway 迁移

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__create_content_tables.sql`
- Create: `backend/src/test/java/com/aiscam/antiscam/support/MySqlTestBase.java`
- Create: `backend/src/test/java/com/aiscam/antiscam/content/ContentSchemaIT.java`

**Interfaces:**
- Consumes: Task 1（第一阶段）的 Spring Boot 骨架、`application.yml` 的 Flyway/JPA 配置、`anti_scam_phase1` 本地库与 `spring.jpa.hibernate.ddl-auto=validate`
- Produces:
  - 表 `fraud_scenario`、`scammer_profile`、`victim_profile`（列名、类型、约束见 Step 1，后续所有任务直接使用，不得再改）
  - `MySqlTestBase`：抽象基类（`@SpringBootTest` + Testcontainers `mysql:8.0.45` + Flyway 自动迁移 + `MYSQL_APP_PASSWORD=test-only`），供本阶段所有 `*IT` 继承

- [ ] **Step 1：编写迁移文件 `V2__create_content_tables.sql`**

三个 `CREATE TABLE`，字段与约束必须逐字一致：

```sql
CREATE TABLE fraud_scenario (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  scenario_code VARCHAR(32)  NOT NULL,
  name          VARCHAR(100) NOT NULL,
  description   TEXT         NOT NULL,
  row_version   INT UNSIGNED NOT NULL DEFAULT 0,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uq_scenario_code (scenario_code),
  CONSTRAINT ck_scenario_code CHECK (scenario_code IN ('TASK_REBATE','FAKE_INVESTMENT','IMPERSONATION_LOAN')),
  CONSTRAINT ck_scenario_name_len CHECK (CHAR_LENGTH(name) BETWEEN 1 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE scammer_profile (
  id                  BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  name                VARCHAR(64)       NOT NULL,
  avatar_key          VARCHAR(255)      NOT NULL,
  age                 SMALLINT UNSIGNED NOT NULL,
  gender              VARCHAR(16)       NOT NULL,
  identity            VARCHAR(128)      NOT NULL,
  description         TEXT              NOT NULL,
  fraud_experience    VARCHAR(8)        NOT NULL,
  speech_skill        VARCHAR(8)        NOT NULL,
  manipulation_skill  VARCHAR(8)        NOT NULL,
  behavior_traits     TEXT              NOT NULL,
  context_time        VARCHAR(128)      NOT NULL,
  context_place       VARCHAR(128)      NOT NULL,
  current_state       TEXT              NOT NULL,
  is_enabled          TINYINT UNSIGNED  NOT NULL DEFAULT 1,
  row_version         INT UNSIGNED      NOT NULL DEFAULT 0,
  created_at          DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT ck_scammer_name_len    CHECK (CHAR_LENGTH(name) BETWEEN 1 AND 64),
  CONSTRAINT ck_scammer_age         CHECK (age BETWEEN 1 AND 65535),
  CONSTRAINT ck_scammer_gender_len  CHECK (CHAR_LENGTH(gender) BETWEEN 1 AND 16),
  CONSTRAINT ck_scammer_identity_len CHECK (CHAR_LENGTH(identity) BETWEEN 1 AND 128),
  CONSTRAINT ck_scammer_fraud_exp   CHECK (fraud_experience   IN ('LOW','MEDIUM','HIGH')),
  CONSTRAINT ck_scammer_speech      CHECK (speech_skill       IN ('LOW','MEDIUM','HIGH')),
  CONSTRAINT ck_scammer_manip       CHECK (manipulation_skill IN ('LOW','MEDIUM','HIGH')),
  CONSTRAINT ck_scammer_context_time_len  CHECK (CHAR_LENGTH(context_time) BETWEEN 1 AND 128),
  CONSTRAINT ck_scammer_context_place_len CHECK (CHAR_LENGTH(context_place) BETWEEN 1 AND 128),
  CONSTRAINT ck_scammer_enabled     CHECK (is_enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;

CREATE TABLE victim_profile (
  id                    BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  name                  VARCHAR(64)       NOT NULL,
  avatar_key            VARCHAR(255)      NOT NULL,
  age                   SMALLINT UNSIGNED NOT NULL,
  gender                VARCHAR(16)       NOT NULL,
  identity              VARCHAR(128)      NOT NULL,
  description           TEXT              NOT NULL,
  anti_fraud_awareness  VARCHAR(8)        NOT NULL,
  risk_tendency         VARCHAR(8)        NOT NULL,
  personality_traits    TEXT              NOT NULL,
  context_time          VARCHAR(128)      NOT NULL,
  context_place         VARCHAR(128)      NOT NULL,
  current_state         TEXT              NOT NULL,
  is_enabled            TINYINT UNSIGNED  NOT NULL DEFAULT 1,
  row_version           INT UNSIGNED      NOT NULL DEFAULT 0,
  created_at            DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at            DATETIME(3)       NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  CONSTRAINT ck_victim_name_len       CHECK (CHAR_LENGTH(name) BETWEEN 1 AND 64),
  CONSTRAINT ck_victim_age            CHECK (age BETWEEN 1 AND 65535),
  CONSTRAINT ck_victim_gender_len     CHECK (CHAR_LENGTH(gender) BETWEEN 1 AND 16),
  CONSTRAINT ck_victim_identity_len   CHECK (CHAR_LENGTH(identity) BETWEEN 1 AND 128),
  CONSTRAINT ck_victim_awareness      CHECK (anti_fraud_awareness IN ('LOW','MEDIUM','HIGH')),
  CONSTRAINT ck_victim_risk           CHECK (risk_tendency        IN ('LOW','MEDIUM','HIGH')),
  CONSTRAINT ck_victim_context_time_len  CHECK (CHAR_LENGTH(context_time) BETWEEN 1 AND 128),
  CONSTRAINT ck_victim_context_place_len CHECK (CHAR_LENGTH(context_place) BETWEEN 1 AND 128),
  CONSTRAINT ck_victim_enabled        CHECK (is_enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_bin;
```

不建业务外键（画像不被任何表引用）、不建 `is_enabled` 索引（低选择性，数据库设计 §6.4 明确暂不建）、不加软删除字段、不加级联。

- [ ] **Step 2：抽出 `MySqlTestBase` 并编写 `ContentSchemaIT`（先写测试）**

`MySqlTestBase` 从既有 `MySqlSmokeIT` 的容器配置迁移而来（保持 `mysql:8.0.45`、`utf8mb4`/`utf8mb4_0900_bin`、UTC 连接参数），`MySqlSmokeIT` 改为继承它、行为不变。`ContentSchemaIT` 断言四件事：

```java
@Test void contentTables_haveExactlyTheDesignedColumns()          // 用 JdbcTemplate 读 information_schema.columns，
                                                                   // 三张表的列名集合与 Step 1 完全一致（多一列少一列都失败）
@Test void levelAndEnumChecks_rejectIllegalValues()                // fraud_experience='VERY_HIGH' 与 is_enabled=2 抛 DataIntegrityViolationException
@Test void scenarioCode_isUniqueAndRestricted()                    // 重复 TASK_REBATE 冲突；'OTHER' 冲突
@Test void varcharLengths_matchTheContract()                       // name VARCHAR(64)、identity VARCHAR(128)、context_time VARCHAR(128)、avatar_key VARCHAR(255)
```

- [ ] **Step 3：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd -q verify "-Dit.test=ContentSchemaIT" -DfailIfNoTests=false`
Expected: FAIL（`fraud_scenario` 等三张表不存在）。

若本机没有 Docker，按 Task 2 Step 6 后的「本阶段所有 `*IT` 的运行方式」说明改用本地 MySQL 8.0.45，并在本步骤备注「未使用 Testcontainers」。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd -q verify "-Dit.test=ContentSchemaIT+MySqlSmokeIT" -DfailIfNoTests=false`
Expected: 两个测试类均 PASS；`flyway_schema_history` 中出现 `V2`，`V1` 记录未被改写。

- [ ] **Step 5：提交**

```bash
git add backend/src/main/resources/db/migration/V2__create_content_tables.sql backend/src/test
git commit -m "feat(content): V2 migration for scenario and profile tables"
```

---

### Task 2：五张初始头像资源与内容种子数据

**Files:**
- Create: `tools/generate-avatars.py`
- Create: `backend/var/avatars/<sha256>.png`（5 个文件）
- Create: `backend/src/main/resources/db/migration/V3__seed_content_data.sql`
- Test: `backend/src/test/java/com/aiscam/antiscam/content/ContentSeedIT.java`

**Interfaces:**
- Consumes: Task 1 的三张表
- Produces:
  - 5 个真实 PNG 文件，`avatar_key` 形如 `avatars/<64位小写sha256>.png`；键名 = **该文件字节的 SHA-256**
  - `fraud_scenario` 三行（`TASK_REBATE`/`FAKE_INVESTMENT`/`IMPERSONATION_LOAN`），name/description 逐字取自 PRD §11.1 PC-P01
  - `scammer_profile` 两行（陈启明、梁悦）、`victim_profile` 三行（林晓、赵诚、周桂兰），全部字段逐字取自 PRD §11.3 PC-P02，`is_enabled = 1`
  - `ContentSeedIT` 断言种子内容与 PRD 逐字一致、且 `avatar_key` 的 SHA-256 与磁盘文件字节相符

- [ ] **Step 1：编写并运行 `tools/generate-avatars.py`**

脚本用 Pillow 生成 512×512 的 RGB PNG，每张一个可辨识的纯色背景 + 姓氏首字 + 一行身份说明（使用 `C:\Windows\Fonts\msyh.ttc`，缺失时回退 `simhei.ttf`），随后计算每张图片字节的 SHA-256，把文件写入 `backend/var/avatars/<sha256>.png`，并在标准输出打印「角色 | 姓名 | avatar_key」三列表格。五个角色的画面必须与 PRD 头像描述一致：陈启明（短发、深色衬衫、神态沉稳的成年男性）、梁悦（齐肩发、简洁便装、佩戴耳麦的成年女性）、林晓（马尾、休闲上衣的年轻女性）、赵诚（短发、日常衬衫的中年男性）、周桂兰（短灰发、戴眼镜的老年女性）。

Run: `& "E:\DeepSeek Harness\DSH Desktop\resources\office-runtime\primary-runtime\dependencies\python\python.exe" tools/generate-avatars.py`
Expected: 输出 5 行 `avatars/<sha256>.png`，每个文件大小在 5KB—1MB 之间；重复执行脚本时文件名不变（同输入同输出）。

脚本只用于本次初始化，不加入运行期依赖、不加入 npm/pytest 流水线；其输出表格即是 Step 4 的输入。

- [ ] **Step 2：写失败测试 `ContentSeedIT`**

```java
@Test void seedsMatchPrdInitialCards()      // 逐字段断言 Step 4 表格全部值；两骗子在前、三受害者在后（按 id 升序）
@Test void scenariosMatchPrdPcP01()         // 三行 code/name/description 与 PRD §11.1 逐字一致，顺序按 id 升序
@Test void avatarKeysPointToRealReadablePngs() {
    // 对每张头像：按 app.content.avatar-store 下的路径读文件
    // 断言 SHA-256(bytes) 等于键中的 64 位十六进制；断言前 8 字节是 PNG 签名 \x89PNG\r\n\x1a\n
}
@Test void seedsAreInsertedOnlyOnce()       // 迁移历史中 V3 恰好一条记录，且三表行数分别为 3/2/3
```

本步骤只写测试；断言比对的是 Step 1 已落盘的文件与 Step 4 将写入的种子行，因此失败信息必须能区分「行不存在」与「哈希不符」两种情况。

- [ ] **Step 3：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd -q verify "-Dit.test=ContentSeedIT" -DfailIfNoTests=false`
Expected: FAIL（`fraud_scenario`、`scammer_profile`、`victim_profile` 三表为空，种子数据尚未写入）。

- [ ] **Step 4：编写迁移文件 `V3__seed_content_data.sql`**

三场景（name/description 逐字）：

```sql
INSERT INTO fraud_scenario (scenario_code, name, description) VALUES
  ('TASK_REBATE',        '刷单返利诈骗',     '骗子以完成任务获得返利为由建立信任，逐步诱导受害者垫付资金、继续投入'),
  ('FAKE_INVESTMENT',    '虚假投资诈骗',     '骗子以投资收益和专业指导建立信任，诱导投入资金，并以提现等理由继续索取费用'),
  ('IMPERSONATION_LOAN', '冒充亲友借款诈骗', '骗子冒充亲友，以紧急借款等理由要求转账，利用关系信任和时间压力阻碍核实');
```

五张人物卡（值逐字取自 PRD §11.3，`avatar_key` 用 Step 1 打印的实际值）：

| 表 | 字段 | 陈启明 | 梁悦 | 林晓 | 赵诚 | 周桂兰 |
|---|---|---|---|---|---|---|
| — | age / gender | 35 / 男 | 28 / 女 | 22 / 女 | 41 / 男 | 66 / 女 |
| — | identity | 诈骗团伙话务人员 | 诈骗团伙话务人员 | 正在求职的应届毕业生 | 小型商店经营者 | 退休会计 |
| — | description | 耐心、善于观察反应，通过持续交流建立信任 | 表达直接，倾向利用紧迫感和情感关系推动对方行动 | 希望增加收入，了解部分反诈常识，但容易被即时收益吸引 | 对自己的社会经验较有信心，忙碌时容易省略核实 | 重视家庭、做事谨慎，通常会主动核实，但亲友情境可能带来压力 |
| — | context_time | 工作日晚上 | 工作日晚上 | 工作日晚上 | 工作日晚上 | 工作日晚上 |
| — | context_place | 安静的工作室 | 独立房间 | 租住的房间 | 家中书桌旁 | 家中客厅 |
| — | current_state | 正准备与目标对象进行线上交流 | 正通过线上消息联系目标对象 | 正使用手机浏览信息和处理消息 | 正整理账目，并查看手机消息 | 晚饭后休息，正查看手机消息 |
| scammer | fraud_experience / speech_skill / manipulation_skill | 高→`HIGH` / `HIGH` / `HIGH` | 中→`MEDIUM` / `MEDIUM` / `MEDIUM` | — | — | — |
| scammer | behavior_traits | 受质疑时调整表达和推进节奏 | 面对连续核实时容易急于推进 | — | — | — |
| victim | anti_fraud_awareness / risk_tendency | — | — | `MEDIUM` / `HIGH` | `LOW` / `MEDIUM` | `HIGH` / `LOW` |
| victim | personality_traits | — | — | 好奇、愿意尝试，犹豫时可能咨询朋友 | 果断、自信，对熟悉口吻容易放松警惕 | 谨慎、有耐心，关心亲人 |

每个 INSERT 显式写 `is_enabled = 1`；迁移只插入、不做 `ON DUPLICATE KEY UPDATE`（首次初始化后管理员后续编辑不得被启动覆盖）。

- [ ] **Step 5：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd -q verify "-Dit.test=ContentSeedIT" -DfailIfNoTests=false`
Expected: PASS。若本机无 Docker，按 Task 2 Step 6 后的说明改用本地 MySQL 并如实备注。

- [ ] **Step 6：提交**

```bash
git add tools/generate-avatars.py backend/var backend/src/main/resources/db/migration/V3__seed_content_data.sql backend/src/test
git commit -m "feat(content): five initial avatar assets and PRD seed data (V3)"
```

> **本阶段所有 `*IT` 的运行方式（先读这一条，后续任务不再重复）：** 既有 `backend/pom.xml` 已绑定 `maven-failsafe-plugin`，`.\mvnw.cmd verify` 会执行 `*IT`；`MySqlTestBase` 通过 `@DynamicPropertySource` + Testcontainers 提供 `mysql:8.0.45`，这是**首选**方式，但需要 Docker daemon 运行。
>
> 若本机没有 Docker（2026-10-16 检查时 Docker daemon 未运行），按下述方式对**本地 MySQL 8.0.45** 运行同一批 IT，并如实记录「未使用 Testcontainers」：
> 1. `MySqlTestBase` 在 `@DynamicPropertySource` 方法最前面判断 `Boolean.getBoolean("anti.scam.localDb")`，为真时**直接 return**（不注册任何数据源属性），让 `application.yml` 的 `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` 生效；为假时才注册容器属性。
> 2. 用一个独立的空库验证 Flyway 从 V1 连续迁移到 V3：`.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.arguments=--spring.datasource.url=jdbc:mysql://localhost:3306/anti_scam_phase2?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"`，确认日志出现 `Migrating schema ... to version "2"` 与 `"3"`。
> 3. 执行 IT：`.\mvnw.cmd verify "-Danti.scam.localDb=true" "-DDB_URL=jdbc:mysql://localhost:3306/anti_scam_phase2?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true" "-DDB_USERNAME=..." "-DDB_PASSWORD=..."`。
>
> 无论走哪条路，都**不得**改用 H2／SQLite 宣称通过（数据库设计 §12.1 明确禁止用替代库证明生成列、排序规则与约束行为）。

---

### Task 3：不透明游标编解码与分页参数规则（纯单元）

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/common/api/ErrorCodes.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/content/page/ContentCursor.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/content/page/PageQuery.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/common/error/ApiException.java`
- Test: `backend/src/test/java/com/aiscam/antiscam/content/ContentCursorTest.java`

**Interfaces:**
- Consumes: 既有 `ApiException`（`int status, String code, String message, Object detail`）与 `ErrorDetail(List<FieldError>, String activeSessionId, Integer currentVersion)`
- Produces:
  - `ErrorCodes`（`public static final String` 常量）：`VALIDATION_FAILED`、`INVALID_CURSOR`、`UNAUTHENTICATED`、`FORBIDDEN`、`NOT_FOUND`、`DATA_INCONSISTENT`、`INTERNAL_ERROR`
  - `ApiException.invalidCursor()` → 400 / `INVALID_CURSOR` / 「分页游标无效」/ `detail = null`
  - `ApiException.notFound()` → 404 / `NOT_FOUND` / 「资源不存在或不可访问」/ `detail = null`
  - `ApiException.dataInconsistent(String message)` → 500 / `DATA_INCONSISTENT`
  - `ContentCursor.encode(String scope, long lastId) -> String`、`ContentCursor.decode(String scope, String cursor) -> long lastId`
  - 作用域常量（`public static final String`，位于 `ContentCursor`，后续任务的调用方只能引用它们，不得自写字面量）：`ContentCursor.SCOPE_SCAMMERS = "SCAMMER|enabled=true|idAsc"`、`ContentCursor.SCOPE_VICTIMS = "VICTIM|enabled=true|idAsc"`
  - `PageQuery(String cursor, Long limit)`：`PageQuery.normalizeSize(Long limit) -> int`（默认 20、1—100，越界抛 `VALIDATION_FAILED`，字段 `limit`）；`PageQuery.decodeLastId(String scope) -> Long`（`null` 或空串表示首屏，其余委托 `ContentCursor.decode`）

- [ ] **Step 1：写失败测试 `ContentCursorTest`**

```java
@Test void roundTrip_returnsTheSameLastId()         // encode("SCAMMER",11) → decode("SCAMMER", token) == 11
@Test void cursorIsNotPlainReadableText()           // token 中不含 "SCAMMER"、不含 "11"，且匹配 ^[A-Za-z0-9_-]+$
@Test void crossScopeReuse_isRejected()             // encode("SCAMMER",11) 交给 decode("VICTIM", …) 抛 INVALID_CURSOR
@Test void tamperedOrTruncatedCursor_isRejected()   // 改一位字符、截断一半、非 base64、空 JSON、缺字段 → 全部 INVALID_CURSOR
@Test void limitDefaultsTo20AndAccepts1To100()      // null→20、1→1、100→100
@Test void limitOutsideRange_isValidationFailed()   // 0、101、-1、1000 → VALIDATION_FAILED，fieldErrors[0].field == "limit"
@Test void emptyCursorMeansFirstPage()              // PageQuery(cursor="", limit=null).decodeLastId(scope) == null；cursor=null 同样为 null
```

- [ ] **Step 2：运行单元测试确认失败**

Run: `cd backend && .\mvnw.cmd -q test "-Dtest=ContentCursorTest"`
Expected: FAIL（类不存在，编译失败）。

- [ ] **Step 3：实现游标、分页参数与错误码**

要点：`ContentCursor` 内部结构为 JSON `{"s":"<scope>","i":<lastId>}`，经 `Base64.getUrlEncoder().withoutPadding()` 编码；`decode` 先做 `^[A-Za-z0-9_-]{1,256}$` 形状校验，再 base64 解码、解析 JSON、比对 `s` 与传入 `scope` 是否逐字相等，任何一步失败一律抛 `ApiException.invalidCursor()`；**不得**把解码异常或 JSON 文本放进 `message`。两个作用域常量的字面量就是上面 `SCOPE_SCAMMERS` / `SCOPE_VICTIMS` 的值。`limit` 用 `Long` 接收以便区分「未传」与「传了非法值」，越界抛字段错误。`PageQuery` 不解析 `enabled`（该参数在 Controller 层用 `Boolean` + 显式校验处理）。

- [ ] **Step 4：运行单元测试确认通过**

Run: `cd backend && .\mvnw.cmd -q test "-Dtest=ContentCursorTest"`
Expected: PASS。

- [ ] **Step 5：提交**

```bash
git add backend/src/main/java/com/aiscam/antiscam/common backend/src/main/java/com/aiscam/antiscam/content/page backend/src/test/java/com/aiscam/antiscam/content/ContentCursorTest.java
git commit -m "feat(content): opaque scoped cursor and page-size rules"
```

---

### Task 4：头像受控存储与 API-13 用户头像读取

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/config/ContentProperties.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/content/avatar/AvatarKey.java`、`AvatarResource.java`、`AvatarStore.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/content/web/AvatarController.java`
- Modify: `backend/src/main/resources/application.yml`（新增 `app.content.avatar-store: var` 与 `app.content.startup-check.enabled: true`）
- Test: `backend/src/test/java/com/aiscam/antiscam/content/AvatarKeyTest.java`（surefire）、`backend/src/test/java/com/aiscam/antiscam/content/AvatarApiIT.java`（failsafe）

**Interfaces:**
- Consumes: Task 2 的 `backend/var/avatars/*.png`；Task 3 的 `ErrorCodes`/`ApiException`
- Produces:
  - `ContentProperties`（`@ConfigurationProperties("app.content")`）：`Path avatarStore`（相对 JVM 工作目录，默认 `var`）、`boolean startupCheckEnabled`（默认 `true`）
  - `AvatarKey.isValid(String key) -> boolean`：正则 `^avatars/[0-9a-f]{64}\.(png|jpg)$`
  - `AvatarStore.load(String key) -> AvatarResource`：键非法抛 400；文件不存在或内容签名与扩展名不符抛 404
  - `AvatarResource(byte[] bytes, String mediaType)`，`mediaType ∈ {image/png, image/jpeg}`
  - `GET /anti.scam/v1/avatars/{key}` → 200 二进制，响应头 `Content-Type`、`Cache-Control: private, no-store`、`X-Content-Type-Options: nosniff`

- [ ] **Step 1：写失败测试**

`AvatarKeyTest`（不依赖 Spring、不依赖数据库）：

```java
@Test void acceptsLowercaseSha256WithPngOrJpg()   // avatars/ + 64 位小写 hex + .png/.jpg → true
@Test void rejectsPathTraversalAndMalformedKeys() // "../application.yml"、"avatars/../../application.yml"、
                                                  // "avatars/ABC.png"、64 位大写 hex、"avatars/x.png"、
                                                  // "avatars/<64hex>.gif"、"/avatars/<64hex>.png"、"avatars//<64hex>.png" → false
@Test void rejectsControlCharactersAndSpaces()    // "avatars/<64hex>.png "、"avatars/\t<64hex>.png"、"avatars/<64hex>\u0000.png" → false
```

`AvatarApiIT`：

```java
@Test void avatar_returnsPngBytesWithRequiredHeadersForUser()   // 200；Content-Type=image/png；Cache-Control 恰含 "private" 与 "no-store"；
                                                               // X-Content-Type-Options=nosniff；响应体前 8 字节为 PNG 签名
@Test void avatar_keyFormatViolation_returns400FieldError()    // /avatars/not-a-key → 400 VALIDATION_FAILED，fieldErrors[0].field == "key"
@Test void avatar_missingFile_returnsIdenticalNotFound()       // 合法格式但文件不存在 → 404，message 恰为「资源不存在或不可访问」，data 为 null，
                                                               // 与 /scammer-profiles/999999 的 404 响应体逐字段一致
@Test void avatar_withMismatchedSignature_returns404()         // 把 PNG 字节复制成 .jpg 键 → 404（以真实内容判定，不看扩展名）
@Test void avatar_anonymouslyIs401_andAdminIs403()
@Test void avatar_pathTraversalCannotReadBusinessFiles()       // "../application.yml" 与编码形式 "%2e%2e%2fapplication.yml" 均不得返回 200，
                                                               // 也不得返回任何 YAML 内容（Status 为 400 或由防火墙拒绝，二者取其一）
@Test void avatar_addedQueryParameterIsRejected()              // ?download=1 → 400（本接口不接受查询参数）
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd -q test "-Dtest=AvatarKeyTest"` 与 `cd backend && .\mvnw.cmd -q verify "-Dit.test=AvatarApiIT" -DfailIfNoTests=false`
Expected: FAIL（`AvatarStore`、`AvatarController` 不存在 / API 404）。

- [ ] **Step 3：实现存储、键校验与控制器**

要点：`AvatarStore` 只做 `avatarStoreDir.resolve(key).normalize()`，并再次断言结果仍在 `avatarStoreDir` 之内（防穿越的第二道保险），任何越界抛 400；用 `java.nio.file.Files.readAllBytes` 读取，读取失败统一转 `ApiException.notFound()`（不得把 `IOException` 的 message 透出）；媒体类型由**前 8 字节**判定（`\x89PNG\r\n\x1a\n` → `image/png`，`\xFF\xD8\xFF` → `image/jpeg`），与扩展名不符即 404。Controller 的 `@PathVariable` 用 `{key:.+}` 接收并在方法内先调用 `AvatarKey.isValid`（否则带 `/` 的路径会被 Spring 截断成 404 而无法区分 400/404），用 `ResponseEntity<byte[]>` 返回三个响应头；查询参数处理与 Task 7 的详情接口一致（显式接收并拒绝未声明参数，返回 400）。

`SecurityConfig` 的两处改动：①新增显式规则，`"/anti.scam/v1/avatars/*"`、`"/anti.scam/v1/scenarios"`、`"/anti.scam/v1/scammer-profiles/**"`、`"/anti.scam/v1/victim-profiles/**"` 必须 `hasRole("USER")`（ADMIN 得到 403，不能落入 `anyRequest().authenticated()`）；②在既有 `authorizeHttpRequests` 链的**最前面**加一条拒绝规则，作为穿越请求的第二道保险（`StrictHttpFirewall` 仍保持默认开启）：

```java
.requestMatchers(req -> {
    String uri = req.getRequestURI();
    return uri.contains("..") || uri.contains("\\") || uri.indexOf('\0') >= 0;
}).denyAll()
```

不要为了通过测试而关闭 `StrictHttpFirewall` 或放宽 URL 解码规则。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd -q test "-Dtest=AvatarKeyTest"` 与 `cd backend && .\mvnw.cmd -q verify "-Dit.test=AvatarApiIT" -DfailIfNoTests=false`
Expected: 均 PASS。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(content): avatar controlled store and user avatar read (API-13)"
```

---

### Task 5：基础内容实体与启动校验

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/content/domain/Level.java`、`ScenarioCode.java`、`FraudScenario.java`、`FraudScenarioRepository.java`、`ScammerProfile.java`、`ScammerProfileRepository.java`、`VictimProfile.java`、`VictimProfileRepository.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/content/startup/ContentStartupCheck.java`
- Test: `backend/src/test/java/com/aiscam/antiscam/content/ContentStartupCheckIT.java`

**Interfaces:**
- Consumes: Task 1 的三张表、Task 2 的种子数据、Task 4 的 `AvatarStore` 与 `ContentProperties`
- Produces:
  - `enum Level { LOW, MEDIUM, HIGH }`
  - `enum ScenarioCode { TASK_REBATE, FAKE_INVESTMENT, IMPERSONATION_LOAN }`，带 `String focus()`：分别返回 `"青年群体"`、`"中年群体"`、`"老年群体"`
  - 三个 `@Entity`：`FraudScenario`（含 `@Version int rowVersion`，`updatedAt` 以 `insertable=false, updatable=false` 映射，由数据库维护）、`ScammerProfile`、`VictimProfile`（后两者含 `isEnabled` 与 `@Version rowVersion`）
  - `FraudScenarioRepository extends JpaRepository<FraudScenario, Long>`：`List<FraudScenario> findAllByOrderByIdAsc()`
  - `ScammerProfileRepository extends JpaRepository<ScammerProfile, Long>`：`List<ScammerProfile> findByIdGreaterThanAndEnabledTrueOrderByIdAsc(long cursor, Pageable pageable)`、`Optional<ScammerProfile> findByIdAndEnabledTrue(long id)`
  - `VictimProfileRepository`：与骗子仓库同名同签名（`findByIdGreaterThanAndEnabledTrueOrderByIdAsc`、`findByIdAndEnabledTrue`）
  - `ContentStartupCheck implements ApplicationRunner`：当 `app.content.startup-check.enabled = true` 时调用公开方法 `verify()`；`verify()` 校验①`fraud_scenario` 恰好 3 行且 code 集合齐全；②骗子、受害者各至少 1 个 `is_enabled = 1`；③所有 `is_enabled = 1` 的 `avatar_key` 均可被 `AvatarStore.load` 读出；任一条不满足时**抛 `IndexOutOfBoundsException`**（不要用 `IllegalStateException`——Spring 上下文启动失败时会包装它并附带属性来源，容易掩盖真正原因），日志只输出违规类别与数量，不输出绝对路径或 SQL

- [ ] **Step 1：写失败测试 `ContentStartupCheckIT`**

```java
@Test void startupCheck_passesOnSeededDatabase()                      // 上下文正常启动（默认校验开启）；contentStartupCheck.verify() 不抛异常
@Test void scenarios_areThreeWithFixedFocus()                         // findAllByOrderByIdAsc 得到 3 条；
                                                                     // ScenarioCode.TASK_REBATE.focus()=="青年群体"，三者顺序 TASK_REBATE/FAKE_INVESTMENT/IMPERSONATION_LOAN
@Test void levelMapping_coversAllThreeLevels()                        // 库里五张卡的行为参数取值为合法 Level 枚举
@Test void enabledQueries_onlyReturnEnabledProfiles() {
    // jdbc 把陈启明置为 is_enabled=0 → findByIdAndEnabledTrue 返回空、findByIdGreaterThanAndEnabledTrueOrderByIdAsc 不再包含它
    // 断言后恢复 is_enabled=1（测试自身不留脏数据给同一容器内的其他 IT）
}
@Test void verify_failsFastWhenAFamilyHasNoEnabledProfile()           // 与上一个测试同一上下文内：
                                                                     // 把两类画像全部置 0 → assertThrows(IndexOutOfBoundsException.class, check::verify)；
                                                                     // finally 中恢复全部 is_enabled=1；不新建 Spring 上下文，避免"启动失败"污染共享容器
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd -q verify "-Dit.test=ContentStartupCheckIT" -DfailIfNoTests=false`
Expected: FAIL（实体与启动校验类不存在）。

- [ ] **Step 3：实现实体、仓库与启动校验**

要点：`fraud_scenario` 无 `is_enabled`，因此场景查询不过滤启用状态；`ScenarioCode` 与库里 `scenario_code` 双向 `@Enumerated(EnumType.STRING)` 映射；`focus` **不落库**，由 `ScenarioCode` 派生（数据库设计 §5.2 明确要求）；实体不暴露 setter（本阶段只读）；`ContentStartupCheck.verify()` 是公开方法、`run(ApplicationArguments)` 只在 `startupCheckEnabled` 为真时调用它，测试直接调 `verify()`。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd -q verify "-Dit.test=ContentStartupCheckIT" -DfailIfNoTests=false`
Expected: PASS。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(content): read-only entities, repositories and startup content check"
```

---

### Task 6：API-08 固定三场景 + API-09/API-11 画像列表

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/content/web/dto/ScenarioView.java`、`AvatarRef.java`、`ScammerView.java`、`VictimView.java`、`PageView.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/content/service/ContentService.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/content/web/ScenarioController.java`、`ScammerProfileController.java`、`VictimProfileController.java`（本任务只实现两个 list 方法，`{id}` 详情留给 Task 7）
- Test: `backend/src/test/java/com/aiscam/antiscam/content/ScenarioApiIT.java`、`backend/src/test/java/com/aiscam/antiscam/content/ProfileApiIT.java`

**Interfaces:**
- Consumes: Task 3 的 `PageQuery`/`ContentCursor`/`ErrorCodes`、Task 4 的 `ContentProperties`、Task 5 的实体与仓库
- Produces:
  - `AvatarRef(String key, String url)`，`url = "/anti.scam/v1/avatars/" + key 的末段`（即 `/anti.scam/v1/avatars/<sha256>.png`），不读取任何请求上下文
  - `ScenarioView(String id, ScenarioCode code, String name, String description, String focus)`；`id` 用 `String.valueOf(id)` 序列化为字符串
  - `ScammerView` / `VictimView`：字段与顺序完全按全局约束 5，`avatar` 为 `AvatarRef`
  - `PageView<T>(List<T> items, String nextCursor, boolean hasMore)`
  - `ContentService.listScenarios() -> List<ScenarioView>`：行数不为 3 时抛 `ApiException.dataInconsistent("基础场景数据不完整")`
  - `ContentService.listScammers(String cursor, Long limit) -> PageView<ScammerView>`、`ContentService.listVictims(String cursor, Long limit) -> PageView<VictimView>`
  - Controller 方法签名（三个 list 方法一律使用这套形状，后续任务的详情方法沿用同样的参数风格）：

```java
// ScenarioController：@RestController @RequestMapping("/anti.scam/v1")
@GetMapping("/scenarios")
public ApiResponse<List<ScenarioView>> scenarios(@RequestParam(required = false) String cursor,
                                                @RequestParam(required = false) Long limit,
                                                @RequestParam Map<String, String> query)

// ScammerProfileController
@GetMapping("/scammer-profiles")
public ApiResponse<PageView<ScammerView>> list(@RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Long limit,
                                               @RequestParam(required = false) Boolean enabled,
                                               @RequestParam Map<String, String> query)
```

`query` 用于兜住未声明的参数（`/scenarios` 不接受任何参数），非空即抛指向 `query.keySet()` 中第一个名字的 400 字段错误。
  - `GET /anti.scam/v1/scenarios` → 200 `ScenarioView[]`；`GET /anti.scam/v1/scammer-profiles`、`GET /anti.scam/v1/victim-profiles` → 200 `PageView`；三者响应头 `Cache-Control: no-store`（已由 `RequestIdFilter` 统一设置，测试需断言）

- [ ] **Step 1：写失败测试 `ScenarioApiIT` 与 `ProfileApiIT`**

```java
// ScenarioApiIT
@Test void scenarios_returnsExactlyThreeOrderedItemsForUser()   // data.length()==3；data[0].code=="TASK_REBATE"、focus=="青年群体"；
                                                               // 每项恰好 5 个字段（id/code/name/description/focus）
@Test void scenarios_areIdenticalToSeededContent()             // name/description 与 PRD 逐字一致
@Test void scenarios_rejectPagingParameters()                  // ?cursor=x → 400 VALIDATION_FAILED（字段 cursor）；?limit=5 → 400
@Test void scenarios_anonymouslyIs401_andAdminIs403()
@Test void scenarios_returnsNoStoreCacheHeader()

// ProfileApiIT
@Test void list_onlyReturnsEnabledProfilesInIdOrder()          // 停用梁悦后列表只含陈启明，且顺序为 id 升序
@Test void list_defaultPageHasThreeVictimsAndNoNextPage()      // 三张受害者卡一次返回；nextCursor==null；hasMore==false
@Test void list_paginationWithLimit1_walksAllPagesWithoutDuplicatesOrGaps() {
    // limit=1 逐页取，直到 hasMore==false；收集到的 id 序列等于 [11,12]（骗子）或 [21,22,23]（受害者），无重复无缺漏
}
@Test void list_responseContainsExactlyAllowedFields()          // items[0] 允许字段集合 == 全局约束 5；
                                                               // 断言 enabled/version/createdAt/updatedAt/avatarKey/rowVersion 均 doesNotExist
@Test void list_enabledFalseOrNonBoolean_isRejected()           // ?enabled=false、?enabled=0、?enabled=1、?enabled=yes、?enabled= → 400，fieldErrors[0].field=="enabled"
@Test void list_enabledTrueAndOmitted_areEquivalent()           // 两种调用 data 完全相同
@Test void list_limitBoundaries()                              // ?limit=1 与 ?limit=100 → 200；?limit=0、?limit=101、?limit=abc、?limit=10.5 → 400
@Test void list_crossEndpointCursor_isInvalidCursor()           // API-09 的 nextCursor 传给 API-11 → 400 INVALID_CURSOR，data 为 null
@Test void list_tamperedCursor_isInvalidCursor()                // 改动游标一个字符 → 400 INVALID_CURSOR
@Test void list_emptyCursorParameter_meansFirstPage()           // ?cursor= → 200 且与不传 cursor 结果相同
@Test void list_anonymouslyIs401_andAdminIs403()                // 两个接口、两种身份共 4 个断言，403 时 code=="FORBIDDEN"
@Test void list_emptyResultIsEmptyItemsNot404()                 // 把所有骗子画像置 is_enabled=0 → 200 且 items==[]、hasMore==false（断言后恢复）
@Test void list_returnsNoStoreCacheHeader()
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd -q verify "-Dit.test=ScenarioApiIT+ProfileApiIT" -DfailIfNoTests=false`
Expected: FAIL（404，接口未实现）。

- [ ] **Step 3：实现服务、DTO 与三个 Controller 的 list 方法**

要点：分页取数用 `PageRequest.of(0, limit + 1)` 的「多取一条」法：取回 `limit + 1` 条时 `hasMore = true` 并截断到 `limit`，`nextCursor = ContentCursor.encode(scope, 最后一条的 id)`；`hasMore = false` 时 `nextCursor = null`。scope 必须直接引用 `ContentCursor.SCOPE_SCAMMERS` / `ContentCursor.SCOPE_VICTIMS`，**不得**另写字面量。`enabled` 参数在 Controller 用 `Boolean` 接收，`null` 或 `Boolean.TRUE` 放行，`Boolean.FALSE` 转成指向 `enabled` 的 400 字段错误（`VALUE_INVALID`）；非布尔值会让 Spring 在绑定阶段抛 `MethodArgumentTypeMismatchException`，需在既有 `GlobalExceptionHandler` 中新增该异常的处理方法（字段名取 `ex.getName()`，code 用 `TYPE_INVALID`，message 形如「字段值不符合接口约定」），使 `?enabled=0`、`?limit=abc`、`?limit=10.5` 一并落到 400 而不是 500。`PageView` 用 record，禁止返回 `Map`。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd -q verify "-Dit.test=ScenarioApiIT+ProfileApiIT" -DfailIfNoTests=false`
Expected: PASS。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(content): scenario list and available profile pages (API-08/09/11)"
```

---

### Task 7：API-10 / API-12 画像详情

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/service/ContentService.java`（新增 `getScammer(long id)`、`getVictim(long id)`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/web/ScammerProfileController.java`、`VictimProfileController.java`（新增 `GET /{id}`）
- Test: `backend/src/test/java/com/aiscam/antiscam/content/ProfileDetailApiIT.java`

**Interfaces:**
- Consumes: Task 5 的 `findByIdAndEnabledTrue`、Task 6 的 `ScammerView`/`VictimView`/`AvatarRef`
- Produces:
  - `ContentService.getScammer(long id) -> ScammerView`、`ContentService.getVictim(long id) -> VictimView`（查不到或未启用一律抛 `ApiException.notFound()`）
  - `GET /anti.scam/v1/scammer-profiles/{id}`、`GET /anti.scam/v1/victim-profiles/{id}` → 200 单对象；`id` 必须匹配 `^[1-9][0-9]{0,19}$`

- [ ] **Step 1：写失败测试 `ProfileDetailApiIT`**

```java
@Test void detail_returnsFullProfileForEnabledRecord()    // 陈启明：13 个字段全部存在且与种子数据逐字一致；id 为字符串；
                                                          // avatar.url 匹配 ^/anti\.scam/v1/avatars/[A-Za-z0-9_.-]+$ 且该地址可 200 取回 PNG
@Test void detail_disabledProfile_returns404()             // 停用后 → 404 NOT_FOUND
@Test void detail_unknownIdAndDisabledId_areIndistinguishable()  // 两次 404 响应体逐字段相同（含 message 与 data），不泄露存在性
@Test void detail_malformedId_returns400WithFieldError()  // "abc"、"007"、"0"、"-1"、20 位以上数字 → 400 VALIDATION_FAILED，fieldErrors[0].field=="id"
@Test void detail_unsupportedQueryParameters_areRejected() // ?enabled=true、?limit=1 → 400（详情接口不接受这些参数，不能被静默忽略）
@Test void detail_anonymouslyIs401_andAdminIs403()
@Test void detail_returnsNoStoreCacheHeader()
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd -q verify "-Dit.test=ProfileDetailApiIT" -DfailIfNoTests=false`
Expected: FAIL（404，详情未实现）。

- [ ] **Step 3：实现详情接口**

要点：`@PathVariable` 用 `String` 接收，先按 `^[1-9][0-9]{0,19}$` 校验（失败抛 `ApiException.field("id", "VALUE_INVALID", "资源标识格式不正确")`），再用 `Long.parseLong` 转换（20 位以内不会溢出 `Long`）；查询走 `findByIdAndEnabledTrue`，`Optional.empty()` 与「id 不存在」走同一个 `ApiException.notFound()`，**两个分支必须共用同一处抛出点**以保证响应体逐字一致。为了让「详情不接受 `enabled`/`limit`」成立，Controller 方法签名不得声明任何 `@RequestParam`，并由既有 `GlobalExceptionHandler` 的 `Exception` 兜底之外显式处理：Spring 默认忽略未声明的查询参数，因此需要在这两个方法上显式接收 `@RequestParam Map<String, String> query` 并在非空时抛指向 `enabled`/`limit` 的 400 字段错误。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd -q verify "-Dit.test=ProfileDetailApiIT" -DfailIfNoTests=false`
Expected: PASS。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(content): scammer and victim profile details (API-10/12)"
```

---

### Task 8：前端配置页（选择 → 详情 → 确认 → 开始校验）

**Files:**
- Modify: `frontend/src/api/types.ts`（追加 `Level`、`Scenario`、`AvatarRef`、`ScammerProfilePayload`、`VictimProfilePayload`、`ScammerPagePayload`、`VictimPagePayload`）
- Create: `frontend/src/api/content.ts`
- Create: `frontend/src/components/ScenarioPicker.vue`、`ProfileCard.vue`、`ProfileDetail.vue`、`ConfigurationSummary.vue`
- Create: `frontend/src/views/ConfigurationView.vue`
- Modify: `frontend/src/router/index.ts`（`/configuration` 指向 `ConfigurationView`，`meta.role` 仍为 `USER`）
- Test: `frontend/src/__tests__/content-api.spec.ts`、`configuration-view.spec.ts`、`configuration-flow.spec.ts`

**Interfaces:**
- Consumes: Task 6/7 的四个 JSON 接口与 Task 4 的头像地址；既有 `frontend/src/api/http.ts`（`http.get`）、`stores/auth.ts`、`router/index.ts`、`AppNav.vue`
- Produces:
  - `contentApi.listScenarios(): Promise<Scenario[]>`
  - `contentApi.listScammerProfiles(params?: { cursor?: string; limit?: number }): Promise<ScammerPagePayload>`
  - `contentApi.getScammerProfile(id: string): Promise<ScammerProfilePayload>`
  - `contentApi.listVictimProfiles(params?: { cursor?: string; limit?: number }): Promise<VictimPagePayload>`
  - `contentApi.getVictimProfile(id: string): Promise<VictimProfilePayload>`
  - `contentApi.avatarUrl(ref: AvatarRef): string`（原样返回服务端 `url`，不改写、不自行拼接）
  - `ConfigurationView` 的 `data-testid`：`configuration-loading`、`configuration-error`、`configuration-retry`、`scenario-list`、`scenario-option`、`profile-list-scammer`、`profile-list-victim`、`profile-card`、`profile-detail`、`profile-avatar`、`profile-avatar-error`、`configuration-summary`、`configuration-start`、`configuration-reselect-hint`、`profiles-empty`
  - 选中状态：`selectedScenarioId` / `selectedScammerId` / `selectedVictimId`（页面 `ref`，不落任何 storage）

- [ ] **Step 1：写失败测试（三个 spec）**

```ts
// content-api.spec.ts
it('requests the five read endpoints under /anti.scam/v1')          // 断言 fetch 路径，且列表接口按需拼接 ?cursor=&limit=（首屏不带参数）
it('returns the server-provided avatar url untouched')              // avatarUrl({key,url}) === url
// configuration-view.spec.ts
it('renders scenarios and both profile lists from the server payload')   // 页面文本等于 mock 返回的 name/description/画像字段；
                                                                        // 断言页面不含硬编码的「刷单返利诈骗」等种子文案
it('shows an empty state when a profile list is empty')                 // items:[] → profiles-empty 出现，且不渲染示例人物
it('shows a retryable failure when a list request fails')               // 500 → configuration-error + configuration-retry；
                                                                        // 断言不出现假的空状态、不出现任何画像卡
// configuration-flow.spec.ts
it('selecting a scenario and both profiles enables the start button and shows the full configuration') {
  // 断言 configuration-summary 中出现所选场景名称 + 两个画像姓名 + 画像四项时间/地点/状态
}
it('start is blocked until all three selections exist')                 // 只选场景 → configuration-start 禁用且给出说明
it('shows the detailed profile fields for the selected profile')         // profile-detail 含全部 13/12 个字段的展示值，
                                                                        // 且 LOW/MEDIUM/HIGH 映射为「低/中/高」展示
it('rejects a profile that became unavailable with a reselect prompt and never silently replaces it') {
  // 先正常加载并选中陈启明；再让 getScammerProfile('11') 抛 ApiError(404,'NOT_FOUND')
  // 点开始 → configuration-reselect-hint 文本包含「所选画像已不可用，请重新选择」；
  // 断言 selectedScammerId 变为 null、未发起任何 POST、未自动选中梁悦
}
it('blocks start when the selected profile avatar fails to load')        // profile-avatar 触发 error 事件 →
                                                                        // profile-avatar-error 出现且 configuration-start 禁用
})
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd frontend && npx vitest run src/__tests__/content-api.spec.ts src/__tests__/configuration-view.spec.ts src/__tests__/configuration-flow.spec.ts`
Expected: FAIL（模块与组件不存在）。

- [ ] **Step 3：实现 API 层、组件与配置页**

要点：加载顺序为 `Promise.all([listScenarios, listScammerProfiles, listVictimProfiles])`，任一失败即进入失败态并可重试（不显示部分内容的「半成功」页面）；三个列表全部来自服务端，页面上**不得**出现任何硬编码场景/画像文案；画像卡展示头像 + 姓名 + 身份摘要，点击后右侧/下方 `ProfileDetail` 展示完整字段（含行为参数的「低/中/高」映射与时间、地点、当前状态）；点击「开始演绎」时**先重新校验**：对已选的两个画像各调用一次详情接口，任一返回 404 即清除该选中项、显示 `configuration-reselect-hint`（文案「所选画像已不可用，请重新选择」）并保持按钮禁用——绝不自动替换为其他画像；校验通过后，由于开始演绎接口属于后续阶段，按钮变为提示「开始演绎将在下一阶段开放」且**不发起任何 POST 请求**（`content-api.spec.ts` 中显式断言本页面不存在写请求）。头像 `<img>` 绑定 `@error` 设置失败标记，失败时该画像卡显示 `profile-avatar-error` 并禁止被选为开始依据。

- [ ] **Step 4：运行测试与类型检查确认通过**

Run: `cd frontend && npm.cmd test` 与 `cd frontend && npx vue-tsc --noEmit`
Expected: 两条命令退出码均为 0，且既有 `phase1.spec.ts` 仍然通过。

- [ ] **Step 5：提交**

```bash
git add frontend/src
git commit -m "feat(frontend): configuration page with scenario and profile selection"
```

---

### Task 9：契约校验脚本、端到端验收与文档更新

**Files:**
- Modify: `tools/validate-openapi.mjs`（追加 6 条路径断言与输出文案）
- Create: `backend/src/test/java/com/aiscam/antiscam/content/ContentAcceptanceIT.java`
- Modify: `README.md`（已实现范围、数据库表清单、头像资源说明、验收记录）

**Interfaces:**
- Consumes: Task 1—8 的全部产物
- Produces: 可复现的验收记录（命令与输出）与更新后的运行说明

- [ ] **Step 1：扩展 `tools/validate-openapi.mjs`**

在既有 7 条断言之后追加：`['/scenarios','get']`、`['/scammer-profiles','get']`、`['/scammer-profiles/{id}','get']`、`['/victim-profiles','get']`、`['/victim-profiles/{id}','get']`、`['/avatars/{key}','get']`；输出文案改为 `OpenAPI valid; phase 1 (7) + phase 2 (6) operations present.`；断言 `components.schemas.Scenario.additionalProperties === false`、`components.schemas.AvatarKey.pattern === '^[A-Za-z0-9_-]+$'`。

Run: `node tools/validate-openapi.mjs`
Expected: 退出码 0 并打印上述文案。

- [ ] **Step 2：写 `ContentAcceptanceIT`**

```java
@Test void ac09_initialContentMatchesThreeScenariosAndFiveCards() {
    // GET /scenarios 三项 code/name/description/focus 与 PRD §11.1 逐字一致；
    // GET /scammer-profiles 与 /victim-profiles 共 5 张卡与 PRD §11.3 逐字一致
}
@Test void ac10_profileDetailShowsAllFieldsIncludingContextAndLevels() {
    // 详情包含基础信息、定性参数与 contextTime/contextPlace/currentState，且参数值只在 LOW/MEDIUM/HIGH 中
}
@Test void ac11_everyVictimCombinesWithEveryScenario() {
    // 三个场景 × 三个受害者共 9 种组合：逐一取场景与受害者详情，断言"可组合"体现在
    // 接口层不存在任何组合限制（场景响应无画像约束字段、受害者响应不含 scene 字段、三者年龄/身份/意识在不同组合下不变）
}
@Test void ac05_userCanReadButCannotMaintainContent() {
    // USER 读取 6 个接口全部 200；
    // 不存在的写路径（POST /scenarios、PUT /scammer-profiles/{id}、POST /victim-profiles/{id}/disable）
    // 对 USER 返回 403 或 404（二者取其一并在测试中固定下来），绝不返回 2xx，也绝不产生数据库变化（前后行数一致）；
    // 发写请求时必须带 .with(csrf()) 与已登录的 USER 会话，使断言检验的是"路径不存在/角色不足"而不是 CSRF 失败
}
@Test void dbv11_disablingAProfileHidesItFromUserReadsWithoutDeletingIt() {
    // 停用梁悦：列表不含它、详情 404；jdbc 确认该行仍存在且 is_enabled=0；断言后恢复
}
@Test void noPhase3TablesExist() {
    // information_schema 中不存在 simulation_session/simulation_round/simulation_message/
    // simulation_report/agent_prompt —— 本阶段不得提前建表
}
@Test void avatarFilesAreReferencedByEnabledProfilesAndReadableForUser() {
    // 遍历 5 张卡片的 avatar.url，逐一 GET 断言 200 + image/png + 内容签名
}
```

- [ ] **Step 3：运行验收测试与全量回归**

Run:
```
cd backend && .\mvnw.cmd -q verify
cd frontend && npm.cmd test
node tools/validate-openapi.mjs
```
Expected: 后端 `*Test` 与 `*IT` 全部通过、前端全部通过、脚本退出码 0。若本机 Docker 未运行，`*IT` 需改为对本地 MySQL 8.0.45 库执行（先 `.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev` 让 Flyway 迁移，再以 Maven 参数覆盖数据源执行 IT），并在 README 中如实记录哪些断言未通过 Testcontainers 执行。

- [ ] **Step 4：人工联调冒烟（记录实际观察结果）**

1. `cd backend && .\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'`（使用 `backend/.env` 指向的本地库）
2. `cd frontend && npm.cmd run dev`
3. 浏览器登录 USER → 首页「开始体验」→ 配置页：三个场景可见并可选；骗子两张、受害者三张卡带头像；点开详情看到全部字段与「低/中/高」；三项选齐后确认区显示完整配置与开始入口
4. DevTools 检查：`/scenarios`、`/scammer-profiles`、`/victim-profiles` 响应为统一封套且 `Cache-Control: no-store`；`/avatars/<sha256>.png` 为 `image/png` + `private, no-store` + `nosniff`；直接访问 `/anti.scam/v1/avatars/does-not-exist.png` 得到 404 封套
5. 用管理员账号访问 `/configuration`：被重定向且直接请求上述接口返回 403
6. 把 `scammer_profile` 中陈启明置 `is_enabled=0`，回到页面点开始：出现「所选画像已不可用，请重新选择」，且未自动改成梁悦；恢复 `is_enabled=1`

将实际观察结果（含失败项）写入 `README.md` 的「验收记录」小节。**任何未执行的验证都如实写明，不得声称全部通过。**

- [ ] **Step 5：更新 `README.md`**

必须包含：本阶段新增的 6 个接口表；三张新表与「数据库现有 4 张业务表 + `flyway_schema_history`」的准确表述；头像资源目录 `backend/var/avatars/` 的说明（键 = 内容 SHA-256、不可覆盖、换图产生新键）与 `app.content.avatar-store` 配置项；`app.content.startup-check.enabled` 的作用；测试命令；本阶段已实现／未实现清单（明确写出「后台管理接口与开始演绎仍未实现」）；并在文首设计文档列表中补上本计划 `coding plan/2026-10-17-phase2-scenario-profile-configuration.md` 的链接；以及本次的验收记录。

- [ ] **Step 6：提交**

```bash
git add tools/validate-openapi.mjs backend/src/test README.md
git commit -m "test(content): contract check, AC-05/09/10/11 acceptance and docs"
```

---

## 六、完成判据（Definition of Done）

1. `.\mvnw.cmd verify` 的 `*Test` 与 `*IT` 全部通过；`npm.cmd test` 全部通过；`npx vue-tsc --noEmit` 退出码 0；`node tools/validate-openapi.mjs` 退出码 0。
2. API-08～API-13 六个接口的响应字段、状态码、错误码、`Cache-Control` 头与 `openapi.yaml` 逐项一致，`ContentAcceptanceIT` 全绿。
3. 「复审重点」五条各自至少有一个通过的测试，且测试名能直接读出它守的是什么行为。
4. `git log` 中每个 Task 恰好一次提交，提交信息与 Task 编号可追溯。
5. 数据库业务表为 `sys_user`、`fraud_scenario`、`scammer_profile`、`victim_profile`（外加 `flyway_schema_history`）；不存在任何为后续模块预留的空表、占位字段或空 Controller。
6. 三场景与五张人物卡与 PRD §11.1／§11.3 逐字一致，五张头像为可解码的真实图片且键名与内容 SHA-256 相符。
7. 前端 `/configuration` 不含任何硬编码场景/画像文案，不写入任何浏览器存储，不发起写请求；头像加载失败与选中项被停用两种情况都有可见提示且阻止开始。
8. `README.md` 的说明可被未参与开发的人在干净环境复现；未执行的验证项如实列出。

## 七、风险与阻塞项（执行者必须回报而非自行决定）

- **Docker 不可用**：本阶段 `*IT` 默认依赖 Testcontainers 的 `mysql:8.0.45`，而 2026-10-16 检查时本机 Docker daemon 未运行。处理方式见 Task 2 Step 6 后的「本阶段所有 `*IT` 的运行方式」：改用独立空库 `anti_scam_phase2` 承载同一批 IT，并在 README 记录。**不得**改用 H2／SQLite 宣称通过（数据库设计 §12.1 明确禁止）。
- **本地模式下的测试会直接改库**：`-Danti.scam.localDb=true` 时 IT 连的是真实库，其中若干测试会把画像置为 `is_enabled = 0` 再恢复。必须使用**专用库**（`anti_scam_phase2`），不要指向 `anti_scam_phase1` 或其他正在使用的库；所有此类测试都要用 `try/finally` 恢复数据，避免断言失败后留下脏数据。
- **头像位图与种子数据的一致性**：`V3` 的 `avatar_key` 依赖 Task 2 Step 1 生成的文件。若生成脚本被改动导致文件字节变化，SHA-256 随之变化，`V3` 与 `ContentSeedIT` 中已写下的值必须同步更新，否则 `avatarKeysPointToRealReadablePngs` 会失败——这是**预期**的失败信号，不要通过放宽断言绕过。
- **初始头像的素材性质**：PRD §11.3 的「头像」列是插画描述，PRD 未附带已生成图片。Task 2 用 Pillow 生成与描述相符的静态 PNG 作为**驱动开发的初始素材**；若需求方已有正式插画，应替换文件并按新的 SHA-256 更新 `V3` 的 `avatar_key` 后再执行本计划，**不要**用文字或坏路径冒充完成初始化。
- **`updated_at ... ON UPDATE CURRENT_TIMESTAMP(3)` 与 JPA `@Version` 的组合**：属于需要在 MySQL 8.0.45 实测的行为。若 `@Version` 与数据库自动更新时间列冲突导致 `validate` 失败，回报实际报错再决定映射方式（可改为数据库维护、实体只读），**不得**改成 `ddl-auto: update` 绕过。
- **`enabled=false` 的 400 归类**：接口文档 §2.4 与 openapi 明确要求 400，但未指明错误码是 `VALIDATION_FAILED` 还是 `INVALID_CURSOR`。本计划采用 `VALIDATION_FAILED` + 指向 `enabled` 的字段错误（与 openapi 的 VALIDATION_FAILED 示例一致）。若执行时发现契约另有依据，回报后再改，不要静默选一个。
- **详情接口与 `/scenarios` 对未声明查询参数的处理**：Spring 默认忽略未知查询参数，而本计划要求 `/scenarios?cursor=x`、`/scammer-profiles/{id}?enabled=true` 返回 400。这是本计划为了提高契约严格性而增加的行为，openapi **没有**把这两个参数列为这些接口的失败原因。若执行时认为该要求过强（例如担心影响未来新增可选参数），**先回报再决定**；若保留，则必须让 400 的 `fieldErrors[0].field` 精确等于实际传入的参数名，并在 `README.md` 中记下这条约定。
- **`row_version` 与停用竞态**：本阶段无写接口，`row_version` 仅作为列存在、不在响应中暴露。DBV-12 的「两管理员同时停用最后两个画像」属于管理后台模块的验收项，**不在本阶段执行**，不要提前实现启停写操作来「顺便」满足它。
- **AP-05／AP-07 的部分条目**：涉及「开始校验」与「管理员修改后影响」的条目只有在开始演绎与管理后台接口存在后才能完整验收。本阶段只验收「读到的可用性与实际启停一致」「选中项被停用后要求重选」两条，其余在 README 中明确标注为后续阶段。
