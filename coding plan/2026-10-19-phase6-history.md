# 第六阶段（历史记录模块）开发计划

> **面向 AI 执行者：** 本计划的每一步均使用复选框（`- [ ]`）语法，请按顺序逐任务（Task）执行。每个 Task 结束都必须通过其验证步骤，并完成一次 git 提交后再进入下一个 Task。本计划建立在已完成的「登录注册 + 首页 + 场景画像配置 + 管理后台 + AI情景演绎 + 复盘分析」代码之上，请在现有文件与既有约定上增量开发，**不要**重写既有模块，也不要改变前五阶段已交付的对外契约。

**目标（Goal）：** 交付可运行的「历史记录模块」垂直切片——普通用户在 `/history` 看到本人全部已保存演绎（进行中、已结束、已中断），按开始时间倒序稳定分页，列表显示开始时间、快照场景名、双方画像名、演绎状态与报告状态；进入 `/history/:id` 查看当时完整配置、逐轮对话（台词与动作说明分开）、逐轮解说与风险、正式结局或中断原因、报告入口；未完成轮明确标注、无评分不补 0、无音视频回放、不续演、不删除不导出；基础内容后续编辑或停用不改变历史内容。对应《接口文档 V1.1》API-24、API-25。

**架构（Architecture）：** 新增与 `simulation`、`report` 并列的 `history` 包（分页游标、列表与详情装配、只读服务、HTTP 入口），依赖方向为 `history → simulation（实体、快照、DTO、组装器）` 与 `history → report（报告摘要映射）`，**`simulation` 与 `report` 不得依赖 `history`**。分页采用 `(started_at DESC, id DESC)` 复合键集游标：从既有 `ContentCursor` 抽出通用签名逻辑为 `SignedCursor`，历史游标以 `"<epochMilli>:<id>"` 为载荷并绑定本人账号，命中数据库既有索引 `idx_session_user_history`。名称为**快照**读取（不 Join 当前画像表），报告状态复用第五阶段的摘要映射，风险序列只取已保存评分，详情最多 20 轮 / 40 条消息一次返回。前端把 `/history` 占位页替换为真实列表与详情，复用第五阶段的 `RiskChart` 与 `ProfileDetail`。

**技术栈（Tech Stack）：** 沿用既有锁定版本：Java 21（Corretto 21.0.8）+ Spring Boot 3.5.16（web / data-jpa / security / validation / flyway-mysql）+ MySQL 8.0.45 + Flyway；前端 Vue 3.5.44 + Vue Router 4.5.1 + Pinia 3.0.3 + Vite 6.4.4 + TypeScript 5.9.3 + Vitest 5.0.5 + Vue Test Utils 2.4.6 + jsdom 26.1.0；契约校验 `@apidevtools/swagger-parser` 12.0.0。**不引入**新依赖（不新增日期库、表格库或图表库；时间用 `Intl.DateTimeFormat` 按 Asia/Shanghai 展示，风险折线复用既有内联 SVG 组件）。

**Spec（本计划实现的规格文档，执行者必须同时阅读）：**
- `docs/AI反诈情景推演平台_PRD_V3_2_正式开发基线版.md`（§8.6 历史记录模块 FR-HIS-01～03 与 MVP 边界、§6.1 主流程「历史记录」行、§7 页面结构「历史列表与详情」行、§11.4/§11.5 保存与历史一致性 BR-P08/BR-P10、§12.1 UX-02/UX-03/UX-04、§13.1 AC-19/AC-20、§13.2 AP-06）
- `docs/AI反诈情景推演平台_系统架构设计文档_V1_1.md`（§12.3 历史读取、§10.4 快照与历史不变性、§13.2 数据访问控制）
- `docs/AI反诈情景推演平台_数据库设计文档_V1_1.md`（§5.5～5.8 表字段、§6.4 索引 `idx_session_user_history`、§7.5/§7.6 终态与中断、§10.1 数据访问规则、§10.2 完整快照结构、§12.1 DBV-15）
- `docs/AI反诈情景推演平台_接口文档_V1_1.md`（§2.3 权限矩阵、§2.4 分页与缓存、§6 的 API-24 与 API-25、§7 的 `HistoryItem`/`HistoryPage`/`HistoryRound`/`HistoryMessage`/`HistoryDetail` 与复用的 `ReportSummary`/`Outcome`/`Interruption`/`Analysis`/`Configuration`、§8 错误码）
- `openapi.yaml`（机器可读契约，**字段名、约束、状态码与错误码的最终依据**；注意 `GET /simulations` 的 500 只列 `INTERNAL_ERROR`，而 `GET /simulations/{id}/detail` 的 500 列 `DATA_INCONSISTENT`）
- `README.md` 与前五阶段计划（尤其 `coding plan/2026-10-19-phase5-analysis-report.md`：`ReportSummary` 已含 `error`、`SimulationStateAssembler.reportView` 已统一装配、报告行缺失属于异常）

---

## 一、本阶段范围与模块边界

### 1.1 本阶段实现的接口与能力（唯一交付范围）

| 编号 | 方法 | 完整路径 | 用途 | 权限 |
|---|---|---|---|---|
| API-24 | GET | `/anti.scam/v1/simulations` | 本人历史列表（复合键集分页） | USER，仅本人 |
| API-25 | GET | `/anti.scam/v1/simulations/{id}/detail` | 本人历史详情 | USER，仅本人 |

同时交付：`history` 后端包（游标、装配器、只读服务、控制器）、前端 `/history` 列表页与 `/history/:id` 详情页（替换现有占位页）、`/history` 空状态与加载失败重试、半轮标注、风险折线（复用 `RiskChart`）、报告入口四态、既有冒烟脚本中的历史端到端断言、契约脚本与 README 更新。

### 1.2 本阶段明确不做（禁止提前实现、禁止预留接口）

- **不新增数据库迁移**：本阶段是纯读模块，`simulation_session`/`simulation_round`/`simulation_message`/`simulation_report` 与索引在 V5 已存在；不得改 V1～V6、不得新增列或表（不加历史归档表、不加浏览记录表、不加导出任务表）。
- 不实现历史删除、到期清理、导出、分享、音视频回放、续演、报告正文再生成（重试入口只**跳转**到既有 `/simulations/:id/report` 页面，不新增接口）。
- 不实现跨用户或管理员查看（ADMIN 访问 `/simulations/**` 仍为 403）；不实现「按场景/状态筛选」「关键词搜索」「total 总数」（契约不含这些参数，传入一律 400）。
- 不实现服务端渲染的日期格式化（接口只返回 UTC 毫秒 `Z` 字符串，展示转换在前端）。
- 不在历史读取路径调用模型、TTS 或重新计算风险/结局；不补写缺失内容、不补 0、不重新判定。
- 不允许为了「让列表更稳」而静默跳过损坏记录或伪造报告状态；前端不得在非 `SUCCEEDED` 时显示「已完成」。

---

## 二、全局约束（Global Constraints，所有任务默认包含）

以下数值来自规格文档与既有实现，**必须逐字实现，不得「优化」**：

1. 接口前缀固定 `/anti.scam/v1`；统一封套四字段 `code`/`message`/`data`/`requestId` 始终存在（沿用 `ApiResponse` 与 `RequestIdFilter`），响应 `Cache-Control: no-store`。
2. 权限：匿名 → **401 `UNAUTHENTICATED`**；ADMIN 访问 `/simulations/**` → **403 `FORBIDDEN`**（`SecurityConfig` 已有 `hasRole("USER")`，不得放宽）；他人记录与不存在记录一律 **404 `NOT_FOUND`**，`message` 恰为「资源不存在或不可访问」，两者响应体**逐字段一致**。
3. 列表只返回**本人**记录，包含 `RUNNING`、`FINISHED`、`INTERRUPTED` 三态；不接受任何 `userId` 参数（客户端传入即 400，身份只能来自服务端会话）。
4. 排序固定为 `startedAt DESC, id DESC`；分页为**键集游标**（不得用 offset/页码），`limit` 默认 20、范围 1—100，越界 400，不静默截断；响应固定 `{items, nextCursor, hasMore}`，**不返回 total**。
5. 游标是不透明值，绑定**本人账号 + 本接口 + 本排序**：损坏、被篡改、跨账号、跨接口复用一律 **400 `INVALID_CURSOR`**（`message` =「分页游标无效」，`data = null`）；游标不是授权凭据，每页独立按本人过滤。
6. 列表与详情中的场景名、骗子名、受害者名**只从冻结快照读取**，不得 Join 当前 `fraud_scenario`/`scammer_profile`/`victim_profile`；当前画像被编辑、停用或改名后，历史内容与名称不变。
7. `HistoryItem.report` 为 `ReportSummary`（可 null）：`FINISHED` 取报告行摘要；`RUNNING`/`INTERRUPTED` 为 `null`。**若 `FINISHED` 缺报告行**：列表该条 `report = null` 并记 WARN 日志（`GET /simulations` 契约的 500 只允许 `INTERNAL_ERROR`，不得抛 `DATA_INCONSISTENT`）；详情按全局约束 11 抛 500 `DATA_INCONSISTENT`。
8. `HistoryItem.outcome` 仅 `FINISHED` 非 null；`HistoryItem.interruption` 仅 `INTERRUPTED` 非 null；`RUNNING` 两者皆 null。
9. 详情中 `configuration` 由三份快照构造（与 `GET /simulations/{id}` 的 `configuration` 结构一致），`rounds` 按轮号升序、最多 20 轮，每轮 `messages` 按 `SCAMMER`→`VICTIM` 排序、最多 2 条；一次响应返回，**不分页**。
10. 详情消息**没有** `audioUrl`/`audioDurationMs`/`segments` 字段；`content` 只放台词、`actionText` 放动作说明（无行动为 null）；`analysis` 在本轮已保存分析时非 null，否则 `null`（不补造、不补 0）。
11. `HistoryDetail.report`：`FINISHED` 必须有报告行，缺行 → **500 `DATA_INCONSISTENT`**（`message` =「记录状态异常，请稍后重试」）；`RUNNING`/`INTERRUPTED` 为 `null`。
12. `interactionComplete` = 本轮双方消息均已保存（2 条）；`presentationComplete` = `phase == COMPLETE`；半轮必须如实保留并在前端标注「本轮未完成」，不得隐藏半轮、不得补造对方台词。
13. 风险只取已保存的 `risk_score`（0/25/50/75/100），按轮号升序；未评分轮次**不出现**在风险序列中，**不补 0**，不重算、不插值。
14. 时间传输为 UTC ISO 8601 含三位毫秒与 `Z`（`UtcTime.format`），可空字段为 null；`endedAt` 在 `RUNNING` 时为 null。
15. 历史读取不写库：本模块只有只读事务（`@Transactional(readOnly = true, isolation = READ_COMMITTED)`），不调用模型/TTS，不触发报告生成或重试。
16. 错误 `message` 与日志不得包含对话正文、报告正文、Prompt、SQL、堆栈或绝对路径；日志只记 `sessionId`、计数与错误类别。
17. 前端不得把 UTC 字符串直接展示为本地时间，必须按 Asia/Shanghai 格式化；且**只能展示服务端返回的内容**（不得本地拼装轮次、补造解说或改写状态文案）。
18. 前端页面不得出现删除、导出、分享、下载、音视频播放器或「继续演绎」入口；中断记录明确「不生成正式复盘」，`FINISHED` 但报告缺失时不得显示「已完成」。
19. 既有契约不得回归：`GET /simulations/{id}`、`/events`、`/report`、`/report/retry` 的行为与字段不变；`ContentCursor` 的对外行为保持不变（既有 `ContentRulesTest` 必须原样通过）。
20. `spring.jpa.hibernate.ddl-auto` 保持 `validate`；不修改 `backend/src/main/resources/prompts/**`。

---

## 三、复审重点（Review Focus）

以下五类输入或失败模式是本阶段最可能伤害真实使用者的位置，规格文档暗示了但常规任务不会自然覆盖。**每条已在括号中注明其测试落在哪个任务的步骤，实现这些任务时必须写出对应测试，不得省略：**

1. **开始时间相同的并列记录**——两条记录 `started_at` 完全相同（同一毫秒内两次开始），只用时间游标会重复或漏掉其中一条。合理预期：排序按 `startedAt DESC, id DESC`，`limit=1` 逐页取完两条时既不重复也不丢失，且较新 id 排在前面。（Task 1 步骤 1/3）
2. **游标的越权与跨用途复用**——把 A 的 `nextCursor` 交给 B 使用、把画像列表的游标交给历史列表、手工改动 token 中一位、截断字符串、传空串、传 `?userId=`。合理预期：前四种一律 400 `INVALID_CURSOR` 且绝不返回他人记录；空串等同首屏；`userId` 作为未知参数 400 `VALIDATION_FAILED`。（Task 1 步骤 1/3、Task 2 步骤 1/3）
3. **半轮与未评分轮次**——受害者消息尚未保存（只有骗子一条）、或本轮分析未保存就中断。合理预期：`interactionComplete=false`、`presentationComplete=false`、`analysis=null`，风险序列**不含**该轮且不出现 0 分；前端标「本轮未完成」，不显示虚构解说或默认分。（Task 3 步骤 1/3、Task 5 步骤 1/3）
4. **历史不变性（后台改动后回看）**——演绎结束后管理员改场景文案、改画像参数、换头像、停用画像。合理预期：列表名称、详情配置、对话、解说、风险、结局与报告状态**全部与当时一致**，头像仍可读取（快照引用授权已在第四阶段实现），不出现当前值混入。（Task 3 步骤 1/3、Task 6 步骤 2）
5. **报告入口的状态真实性**——`SUCCEEDED`/`FAILED`/`PENDING`/`RUNNING`/`INTERRUPTED`/`FINISHED 缺行` 六种情形。合理预期：只有 `SUCCEEDED` 显示「已完成」并可打开报告；`FAILED` 显示「生成失败」并可进入重试页；生成中显示「生成中」；中断显示「不生成正式复盘」且**无**重试入口；`FINISHED` 缺行在详情返回 500 `DATA_INCONSISTENT`、在列表该条 `report=null` 且**不**显示为「已完成」。（Task 2 步骤 1/3、Task 3 步骤 1/3、Task 5 步骤 1/3）

补充要求（同样必须在对应任务中测试）：他人记录与不存在记录必须返回**完全相同**的 404 响应体（Task 3 步骤 1/3）；详情响应中不得出现任何音频字段与 `internal_note`（Task 3 步骤 1/3）。

---

## 四、文件结构（先定边界，再拆任务）

```
D:\AI-anti-scam\
├── coding plan\
│   └── 2026-10-19-phase6-history.md                          ← 本计划
├── tools\
│   ├── validate-openapi.mjs                                  ← 修改：增加 GET /simulations 与 /detail 断言
│   └── smoke-simulation.mjs                                  ← 修改：在既有场景后追加历史断言（无需新脚本）
├── backend\src\
│   ├── main\java\com\aiscam\antiscam\
│   │   ├── common\web\SignedCursor.java                      ← 新增：游标签名原语（HMAC + base64url + 作用域/账号绑定）
│   │   ├── content\page\ContentCursor.java                   ← 修改：改为委托 SignedCursor，对外行为不变
│   │   ├── content\page\PageQuery.java                       ← 修改：抽出 limit(...) 供两个模块共用
│   │   ├── simulation\domain\SimulationSessionRepository.java ← 修改：新增两条按本人分页的查询
│   │   ├── simulation\domain\SimulationReportRepository.java  ← 修改：新增 findByIdIn 批量取报告行
│   │   ├── simulation\snapshot\ConfigurationSnapshots.java    ← 修改：新增 names(...) 只取三个名称
│   │   ├── simulation\service\SimulationStateAssembler.java   ← 修改：outcome/interruption 提升为公共方法；report 摘要改由 ReportViewMapper 提供
│   │   ├── report\service\ReportViewMapper.java               ← 修改：新增 summary(SimulationReport)
│   │   └── history\
│   │       ├── page\HistoryCursor.java                        ← 新增：历史键集游标（epochMilli:id）
│   │       ├── service\HistoryAssembler.java                  ← 新增：item / detail 装配（唯一 DTO 构造点）
│   │       ├── service\HistoryService.java                    ← 新增：只读列表与详情（归属校验）
│   │       ├── web\HistoryController.java                     ← 新增：API-24 / API-25
│   │       └── web\dto\HistoryViews.java                      ← 新增：HistoryItem/HistoryPage/HistoryMessage/HistoryRound/HistoryDetail
│   └── test\java\com\aiscam\antiscam\history\
│       ├── HistoryCursorTest.java                             ← 纯单元：游标编解码与绑定
│       ├── HistoryAssemblerTest.java                          ← 单元 + Mockito：item/detail 字段与半轮/缺报告行
│       ├── HistoryApiTest.java                                ← MockMvc：API-24/25 契约与权限
│       └── HistoryReadIT.java                                 ← 真实 MySQL：排序/并列时间/游标续页/快照不变性/归属
└── frontend\src\
    ├── api\
    │   ├── types.ts                                           ← 修改：HistoryItem/HistoryPage/HistoryDetail/HistoryMessage/HistoryRound
    │   └── history.ts                                         ← 新增：historyApi.list/detail
    ├── utils\time.ts                                          ← 新增：formatShanghai(iso)（Asia/Shanghai，唯一时间展示入口）
    ├── components\history\HistoryConfiguration.vue             ← 新增：三份快照的配置展示（复用 ProfileDetail）
    ├── components\history\HistoryRounds.vue                    ← 新增：逐轮对话 + 解说 + 半轮标注
    ├── views\HistoryView.vue                                   ← 新增：/history 列表
    ├── views\HistoryDetailView.vue                             ← 新增：/history/:id 详情
    ├── router\index.ts                                         ← 修改：/history 与 /history/:id
    └── __tests__\
        ├── history-list.spec.ts                                ← 列表：分页、空态、失败态、状态与报告文案
        └── history-detail.spec.ts                              ← 详情：配置、轮次、半轮、风险图、报告入口、无媒体
```

**结构约束：** 一个文件一个职责；`history` 只读，不得引入任何写库方法；`HistoryAssembler` 是历史 DTO 的**唯一**构造点（控制器与服务都不得自行拼装 DTO），`HistoryController` 只做参数校验与封套；`HistoryService` 负责归属校验与分页编排，不直接触碰 JPA 仓库以外的拼接逻辑；`history` 不修改 `simulation`/`report` 的既有语义，只复用其公共方法；前端 `api/types.ts` 是唯一类型来源，`api/history.ts` 是唯一请求入口（组件不得直接 `fetch`），时间展示只能走 `utils/time.ts`。

---

## 五、任务清单

### Task 1：历史键集游标与本人分页查询

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/common/web/SignedCursor.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/page/ContentCursor.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/history/page/HistoryCursor.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/domain/SimulationSessionRepository.java`
- Test: `backend/src/test/java/com/aiscam/antiscam/history/HistoryCursorTest.java`（surefire）、`HistoryReadIT.java`（failsafe）

**Interfaces:**
- Consumes: 既有 `ApiException.invalidCursor()`、`simulation_session` 的列 `user_id`、`started_at`、`id` 与索引 `idx_session_user_history (user_id, started_at DESC, id DESC)`
- Produces:
  - `SignedCursor`（无状态、非 Spring bean）：`String encode(String scope, String account, String payload)`、`Optional<String> decode(String scope, String account, String cursor)`。格式与既有 `ContentCursor` 完全一致：`base64urlNoPadding(payloadBytes ‖ HMAC-SHA256(scope + '\0' + account + '\0' ‖ payloadBytes))`，32 字节签名；`decode` 先校验 `^[A-Za-z0-9_-]{1,256}$`，再 base64 解码、要求解码后长度 > 32、要求**规范化再编码等于原串**、再校验 HMAC（`MessageDigest.isEqual`）、最后比对 `scope`；任何一步失败返回 `Optional.empty()`（不抛异常）
  - `ContentCursor`（`@Component`，包 `content.page`）保持现有公开 API 与行为**逐字不变**：`SCOPE_SCAMMERS`、`SCOPE_VICTIMS`、`encode(scope, account, long)`、`decode(scope, account, String) -> long`（`null`/空串返回 `0`；非法抛 `ApiException.invalidCursor()`；载荷必须是 `[1-9][0-9]{0,18}`）；内部改为组合 `SignedCursor`
  - `HistoryCursor`（`@Component`，包 `history.page`）：`public static final String SCOPE = "HISTORY|startedAtDesc,idDesc"`；构造器 `HistoryCursor(SignedCursor signing)` 注入签名原语（便于测试用同一实例构造"签名有效但载荷非法"的游标）；`record Keyset(Instant startedAt, long sessionId)`；`String encode(String account, Instant startedAt, long sessionId)`；`Keyset decode(String account, String cursor)`（`cursor` 为 null 或空串返回 `null`；非法抛 `ApiException.invalidCursor()`）。载荷为 `"<startedAt.toEpochMilli()>:<sessionId>"`，签名校验通过后仍必须匹配 `^[0-9]{1,19}:[1-9][0-9]{0,18}$`，否则同样 `INVALID_CURSOR`
  - 仓库查询（两个上界严格小于，保证并列时间不重复/不遗漏）：
    - `List<SimulationSession> findByUserIdOrderByStartedAtDescIdDesc(long userId, Pageable page)`
    - `@Query("select s from SimulationSession s where s.userId = :userId and (s.startedAt < :startedAt or (s.startedAt = :startedAt and s.id < :id)) order by s.startedAt desc, s.id desc") List<SimulationSession> findPageBefore(@Param("userId") long userId, @Param("startedAt") Instant startedAt, @Param("id") long id, Pageable page)`

- [ ] **Step 1：写失败测试**

`HistoryCursorTest`（纯单元）：

```java
@Test void roundTripKeepsTheExactKeyset()          // encode(account, t, 42) -> decode 得到同一 Instant（毫秒精度）与 42
@Test void cursorIsBoundToAccountAndScope()        // 交给另一账号 -> INVALID_CURSOR；
                                                   // 把 ContentCursor 生成的游标交给历史解码 -> INVALID_CURSOR
@Test void tamperedTruncatedAndGarbageAreRejected() // 篡改一位、去掉尾部 8 字符、非 base64、纯文字、超长 300 字符 -> INVALID_CURSOR
@Test void blankCursorMeansFirstPage()             // null 与 "" -> null（不抛异常）
@Test void payloadRejectsNonNumericOrZeroId()
    // 用与 HistoryCursor 相同的 SignedCursor 实例签出 "abc:1"、"1700000000000:0"、"-1:5" -> INVALID_CURSOR
    //（签名有效、只是载荷不合法，确保形状校验确实在签名之后执行）
@Test void cursorShapeIsUrlSafeAndOpaque()         // 匹配 ^[A-Za-z0-9_-]+$；不含 ":"、"HISTORY" 或明文时间戳
```

`HistoryReadIT`（继承既有 `MySqlSmokeIT` 的 Testcontainers 写法；无 Docker 时按 README 的隔离实例方式运行）：

```java
@Test void pagesByStartedAtThenIdDescending()
    // 同一用户 4 条会话：t3(最新)、t2、t1、t1'(与 t1 同一 startedAt 但 id 更大)
    // 断言 findByUserIdOrderByStartedAtDescIdDesc(limit 10) 顺序为 id 降序组内（t1' 在 t1 前），整体 started_at 降序
@Test void keysetContinuesAcrossATieWithoutDuplicatesOrGaps()
    // 直接调用 findPageBefore 逐页取（limit=1），把两条并列时间的记录各返回一次，顺序为较新 id 在前，无重复无缺
@Test void otherUsersRowsAreNeverReturned()
    // 两个用户各有记录：任一查询的用户过滤后只含本人；用 A 的 Keyset 查 B 的 userId 只返回 B 自己的后续数据（由调用方保证 userId 来源）
@Test void equalStartedAtIsStoredWithMillisecondPrecision()
    // 用构造器写入同一 Instant，读回逐纳秒相等（证明并列判定不因精度丢失而失效）
```

> 注意：`simulation_session` 有生成列 `active_user_id`，同一用户**只能有一条 `RUNNING`**。本任务的种子数据请使用 `FINISHED`/`INTERRUPTED` 构造（`finish(...)`/`interrupt(...)`），需要 `RUNNING` 的用例单独使用不同用户。

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd test "-Dtest=HistoryCursorTest"` 与（Docker 可用时）`cd backend && .\mvnw.cmd verify "-Dit.test=HistoryReadIT" -DfailIfNoTests=false`
Expected: FAIL（`SignedCursor`/`HistoryCursor` 与新查询不存在，编译失败）。

- [ ] **Step 3：实现游标原语、委托改造与两条查询**

要点：先把 `ContentCursor` 的签名与解析逻辑**原样**搬到 `SignedCursor`，再把 `ContentCursor` 改成「载荷校验 + 委托」——`encode` 传入 `Long.toString(id)`，`decode` 在 `SignedCursor` 返回空时抛 `invalidCursor()`、否则校验载荷并 `Long.parseLong`。改造后 `ContentRulesTest.cursorIsSignedAndBoundToAccountAndResource` 必须**原样通过**（不要改测试）。`HistoryCursor` 用同一个 `SignedCursor`（各自持有随机密钥实例，进程重启后旧游标失效，与既有约定一致），`decode` 只解析时间戳与 id 两个数。两个仓库方法都必须带 `userId` 条件，`findPageBefore` 的 `(startedAt < :startedAt or (startedAt = :startedAt and id < :id))` 括号顺序不可省，`Pageable` 由调用方传 `PageRequest.of(0, limit + 1)`。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd test "-Dtest=HistoryCursorTest+ContentRulesTest"` 与 `cd backend && .\mvnw.cmd verify "-Dit.test=HistoryReadIT" -DfailIfNoTests=false`
Expected: 均 PASS。若本机没有 Docker daemon（2026-10-16 起未运行），按 README 中 `tools/smoke-content.ps1` 的方式用隔离 MySQL 实例运行 `HistoryReadIT`，并在提交信息与 README 中如实记录「未使用 Testcontainers」，**不得**改用 H2 宣称排序与并列时间已验证。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(history): signed keyset cursor and per-user paged queries (API-24 core)"
```

---

### Task 2：API-24 本人历史列表

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/report/service/ReportViewMapper.java`（新增 `summary`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/service/SimulationStateAssembler.java`（复用 `summary`；`outcome`/`interruption` 提升为公共方法）
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/snapshot/ConfigurationSnapshots.java`（新增 `names`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/domain/SimulationReportRepository.java`（新增 `findByIdIn`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/content/page/PageQuery.java`（抽出 `limit`）
- Create: `backend/src/main/java/com/aiscam/antiscam/history/web/dto/HistoryViews.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/history/service/HistoryAssembler.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/history/service/HistoryService.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/history/web/HistoryController.java`（本任务只实现 `GET`）
- Test: `HistoryAssemblerTest.java`、`HistoryApiTest.java`（列表部分）、`HistoryReadIT.java`（追加列表用例）

**Interfaces:**
- Consumes: Task 1 的 `HistoryCursor`、`findByUserIdOrderByStartedAtDescIdDesc`、`findPageBefore`；既有 `SimulationStateAssembler.reportView`、`ReportSummary`、`Outcome`、`Interruption`、`SimulationStatus`、`UtcTime.format`、`ApiException.notFound()`
- Produces:
  - `ReportViewMapper.summary(SimulationReport row) -> ReportSummary`：`status`、`displayText`、`canRetry`（= `ReportStatus.canRetry()`）、`attemptNo`、`updatedAt`（`UtcTime.format`）、`error`（仅 `FAILED` 时由既有 `errorCode(String)` 映射）；`SimulationStateAssembler.reportView` 改为调用它，行为不变
  - `SimulationStateAssembler.outcome(SimulationSession s) -> Outcome` 与 `public Interruption interruption(SimulationSession s) -> Interruption`（`assemble()` 内部改为调用这两个方法；`RUNNING`/`FINISHED` 时 `interruption` 返回 `null`）
  - `ConfigurationSnapshots.names(String scenarioJson, String scammerJson, String victimJson) -> Names`，`record Names(String scenario, String scammer, String victim)`；只读 `path("name").asText()`，**不构造 `AvatarRef`**（避免旧键格式导致列表失败）
  - `SimulationReportRepository.findBySessionIdIn(Collection<Long> sessionIds) -> List<SimulationReport>`
  - `PageQuery.limit(MultiValueMap<String,String> query) -> int`：与现有 `from` 中的 limit 规则**完全一致**（默认 20、`[0-9]+`、1—100、字段错误文案不变），`from` 改为调用它
  - `HistoryViews.HistoryItem(String sessionId, String startedAt, String scenarioName, String scammerName, String victimName, String status, ReportSummary report, Outcome outcome, Interruption interruption)`
  - `HistoryViews.HistoryPage(List<HistoryItem> items, String nextCursor, boolean hasMore)`
  - `HistoryAssembler.item(SimulationSession s, SimulationReport reportOrNull) -> HistoryItem`：名称取自 `snapshots.names(...)`；`outcome` 仅 `FINISHED`；`interruption` 仅 `INTERRUPTED`；`report` 由调用方传入（缺行时为 `null`）。**本任务构造器只注入 `ConfigurationSnapshots` 与 `SimulationStateAssembler`**（Task 3 再补轮次/消息/报告仓库）
  - `HistoryService.list(long userId, String account, String cursor, int limit) -> HistoryPage`（`@Transactional(readOnly = true, isolation = READ_COMMITTED)`）
  - `GET /anti.scam/v1/simulations` → 200 `HistoryPage`

- [ ] **Step 1：写失败测试**

`HistoryAssemblerTest`（Mockito 单元测试，构造 `HistoryAssembler`）：

```java
@Test void finishedItemCarriesReportOutcomeAndSnapshotNames()
    // 快照名 = 三个 name；report 为传入摘要；outcome 非空；interruption 为 null；startedAt 为 UTC 毫秒 Z 字符串
@Test void runningItemHasNoOutcomeInterruptionOrReport()
@Test void interruptedItemCarriesInterruptionAndNoOutcome()
@Test void finishedItemWithoutReportRowHasNullReport()   // 缺行时 item.report == null 且不抛异常（列表降级）
@Test void namesComeFromSnapshotsNotCurrentTables()      // 快照 name 与"当前"无关：换一个不同的 JSON 直接进 assembler，输出取快照值
```

`HistoryApiTest`（`@WebMvcTest(controllers = HistoryController.class)` + `@MockitoBean HistoryService`，沿用 `AuthHomeSmokeTest` 的导入与 `authentication(...)` 写法）：

```java
@Test void listReturnsEnvelopeWithNoStore()          // 200；data 恰含 items/nextCursor/hasMore；每条恰含 9 个字段；Cache-Control 含 no-store
@Test void listRejectsUnknownParameters()            // ?userId=9、?page=1、?status=FINISHED、?enabled=true -> 400 VALIDATION_FAILED，field 为实际参数名
@Test void listValidatesLimit()                      // ?limit=0、101、abc -> 400；?limit=1、100 -> 200
@Test void listRejectsInvalidCursor()                // service 抛 invalidCursor -> 400 INVALID_CURSOR，data 为 null
@Test void listRequiresUserRole()                    // 匿名 401；ADMIN 403
@Test void listPassesAccountAndLimitToService()      // 验证控制器把 auth.getName()、未解码的 cursor、PageQuery.limit 传给服务（防止上限被绕过）
```

`HistoryReadIT` 追加（真实库，走 `HistoryService.list`）：

```java
@Test void listReturnsOnlyOwnRecordsInOrder()        // 两用户各有记录：A 的列表只含 A，按 startedAt DESC,id DESC
@Test void listWalksAllPagesWithoutDuplicates()      // limit=1 从首屏取到 hasMore=false，sessionId 集合等于本人全部记录且无重复
@Test void listNextCursorIsNullOnLastPage()          // 末页 nextCursor==null、hasMore==false
@Test void listReportsFinishedReportSummary()        // FINISHED 有行 -> report.status/displayText/attemptNo 正确；INTERRUPTED -> null
@Test void listDegradesMissingReportRowToNull()      // 删除 FINISHED 的报告行 -> 该条 report==null（不抛 DATA_INCONSISTENT），其余条目正常
@Test void crossAccountCursorIsRejected()            // A 的 nextCursor 交给 B -> INVALID_CURSOR
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd test "-Dtest=HistoryAssemblerTest+HistoryApiTest"`
Expected: FAIL（`HistoryViews`/`HistoryAssembler`/`HistoryService`/控制器与 `ReportViewMapper.summary` 不存在，编译失败）。

- [ ] **Step 3：实现摘要抽取、列表装配、服务与 GET 接口**

要点：先做两处**行为不变**的重构（`ReportViewMapper.summary` 被 `reportView` 复用、`outcome`/`interruption` 被 `assemble` 复用），确认既有 `ReportViewAssemblyTest` 仍通过，再写新代码。`HistoryService.list` 依次：`PageQuery.validate(query, Set.of("cursor", "limit"))`（在控制器）→ `PageQuery.limit(query)` → `HistoryCursor.decode(account, cursor)` → 有游标用 `findPageBefore`，否则 `findByUserIdOrderByStartedAtDescIdDesc`，都传 `PageRequest.of(0, limit + 1)` → `hasMore = rows.size() > limit` → 截断 → `findBySessionIdIn(ids)` 一次取报告行并建 `Map<Long, SimulationReport>` → 逐条 `assembler.item(...)`；`FINISHED` 且缺行时记 `WARN`（只含 sessionId）并传 `null`；`nextCursor` 用**最后一条已返回记录**的时间与 id 编码。控制器把 `Authentication#getName()` 作为账号传入（与 `ContentCursor` 的账号绑定约定一致），不得使用请求参数中的任何身份信息。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd test "-Dtest=HistoryAssemblerTest+HistoryApiTest+ReportViewAssemblyTest"` 与 `cd backend && .\mvnw.cmd verify "-Dit.test=HistoryReadIT" -DfailIfNoTests=false`
Expected: 均 PASS（重构未改变既有报告装配行为）。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(history): personal history list with report state (API-24)"
```

---

### Task 3：API-25 本人历史详情

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/history/web/dto/HistoryViews.java`（新增 `HistoryMessage`、`HistoryRound`、`HistoryDetail`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/history/service/HistoryAssembler.java`（构造器补注入 `SimulationRoundRepository`、`SimulationMessageRepository`、`SimulationReportRepository`；新增 `detail`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/history/service/HistoryService.java`（新增 `detail`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/history/web/HistoryController.java`（新增 `GET /{id}/detail`）
- Test: `HistoryAssemblerTest.java`、`HistoryApiTest.java`（详情部分）、`HistoryReadIT.java`（追加详情用例）

**Interfaces:**
- Consumes: Task 2 的 `HistoryAssembler`、`ReportViewMapper.summary`、`SimulationStateAssembler.outcome/interruption/analysis`；既有 `ConfigurationSnapshots.toConfigurationView`、`SimulationRoundRepository.findBySessionIdOrderByRoundNoAsc`、`SimulationMessageRepository.findByRoundIdIn`、`SimulationReportRepository.findById`、`PageQuery.id`、`ApiException.dataInconsistent/notFound`
- Produces:
  - `HistoryViews.HistoryMessage(String id, int roundNo, String speaker, String content, String actionText, String presentationStatus, String presentationCompletedAt, String presentationErrorCode, String savedAt)`（**无音频字段**）
  - `HistoryViews.HistoryRound(int roundNo, String phase, boolean interactionComplete, boolean presentationComplete, List<HistoryMessage> messages, Analysis analysis, String readingUntil)`
  - `HistoryViews.HistoryDetail(String sessionId, String status, String startedAt, String deadlineAt, String endedAt, ConfigurationView configuration, List<HistoryRound> rounds, Outcome outcome, Interruption interruption, ReportSummary report)`
  - `HistoryAssembler.detail(SimulationSession s) -> HistoryDetail`：一次取全部轮次与该场的报告行，消息用 `findByRoundIdIn` 批量取；`FINISHED` 缺行 → `ApiException.dataInconsistent("记录状态异常，请稍后重试")`
  - `HistoryService.detail(long userId, String id) -> HistoryDetail`：`PageQuery.id(id)` → `sessions.findById` 且 `userId` 相同，否则 `ApiException.notFound()` → `assembler.detail(...)`
  - `GET /anti.scam/v1/simulations/{id}/detail` → 200 `HistoryDetail`（拒绝任何查询参数）

- [ ] **Step 1：写失败测试**

`HistoryAssemblerTest` 追加：

```java
@Test void detailMapsConfigurationRoundsAndMessagesInOrder()
    // 配置取快照；rounds 按 roundNo 升序；每轮 messages 先 SCAMMER 后 VICTIM；
    // content 与 actionText 分别映射；presentation 三字段与 savedAt 正确；analysis 非空轮次映射四段分析与风险
@Test void halfRoundIsKeptAndMarkedIncomplete()
    // 只有骗子一条消息：messages.size()==1、interactionComplete=false、presentationComplete=false（phase != COMPLETE）
@Test void roundWithoutAnalysisHasNullAnalysisAndNoInventedScore()
    // 有消息但 analysisSavedAt 为空：analysis==null；risk 不出现（详情不单独输出风险字段）
@Test void detailHasNoAudioFields()                 // 序列化后的 JSON 不含 audioUrl/audioDurationMs/segments/internalNote 键
@Test void finishedDetailWithoutReportRowThrowsDataInconsistent()   // 500 DATA_INCONSISTENT
@Test void interruptedDetailHasInterruptionAndNullReport()
@Test void runningDetailHasNeitherOutcomeNorInterruption()
```

`HistoryApiTest` 追加：

```java
@Test void detailReturnsEnvelopeWithNoStoreAndTenFields()   // 200；data 恰含 10 个字段；no-store
@Test void detailReturns404ForForeignOrUnknown()            // service 抛 notFound -> 404，message=="资源不存在或不可访问"，data 为 null
@Test void detailReturns500ForInconsistentRecord()          // service 抛 dataInconsistent -> 500 DATA_INCONSISTENT
@Test void detailRejectsQueryParameters()                   // ?x=1 -> 400
@Test void detailMalformedIdIs400()                         // "abc"/"007"/"0" -> 400，fieldErrors[0].field=="id"
@Test void detailRequiresUserRole()                         // 匿名 401；ADMIN 403
```

`HistoryReadIT` 追加（真实库，走 `HistoryService.detail`）：

```java
@Test void detailReturnsFrozenConfigurationAndSavedProcess()
    // 开始演绎 -> 推进到 FINISHED（复用仿真脚本或构造数据）-> 详情：配置名/参数与快照一致、rounds 与库一致、outcome 一致、report 摘要一致
@Test void detailIsUnaffectedByLaterAdminEdits()
    // 结束后改 fraud_scenario.name、scammer_profile.behavior_traits、换 avatar_key -> 详情仍是旧名称/旧参数/旧头像键
@Test void snapshotAvatarsStayReadableForTheOwnerOnly()
    // 停用该画像（或把当前 avatar_key 换成新键）后：
    // ContentService.avatar(旧快照键, 本人 userId) 仍返回图片字节（走既有 SnapshotAvatarReferences 授权）；
    // 另一用户调用同一键 -> 404。证明历史头像不因后台改动而失效，也不因此对外泄露
@Test void detailOwnershipIsIdenticalNotFoundForForeignAndMissing()   // 他人与不存在：两次异常 code/status/message 完全一致
@Test void interruptedDetailKeepsHalfRoundWithoutAnalysis()
    // 中断发生在受害者消息保存前：rounds 末轮 interactionComplete=false、analysis==null、outcome==null、interruption 非空、report==null
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd test "-Dtest=HistoryAssemblerTest+HistoryApiTest"` 与 `cd backend && .\mvnw.cmd verify "-Dit.test=HistoryReadIT" -DfailIfNoTests=false`
Expected: FAIL（详情 DTO/方法与 `GET /{id}/detail` 未实现）。

- [ ] **Step 3：实现详情装配、服务与接口**

要点：`detail` 的轮次装配顺序固定为「按轮号升序 → 每轮按 `SpeakerRole.ordinal()` 升序」，`interactionComplete` 用消息数 `== 2`、`presentationComplete` 用 `phase == RoundPhase.COMPLETE`（与 `SimulationStateAssembler.liveRound` 同一判定，不要另立规则）；`analysis` 直接调用既有 `assembler.analysis(round)`（已处理 null）；`readingUntil` 可为 null。消息映射只取 `getContent/getActionText/getPresentationStatus/getPresentationCompletedAt/getPresentationErrorCode/getCreatedAt`，**不得**读取 `internalNoteForAgents()`。`HistoryService.detail` 的归属校验与列表一致：格式错误 400，其余一律 404（`findById` 为空与 `userId` 不同走同一个抛出点，保证响应体逐字一致）。控制器复用列表的 `PageQuery.validate(query, Set.of())` 拒绝查询参数。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd test "-Dtest=HistoryAssemblerTest+HistoryApiTest"` 与 `cd backend && .\mvnw.cmd verify "-Dit.test=HistoryReadIT" -DfailIfNoTests=false`
Expected: 均 PASS。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(history): read-only history detail with frozen configuration (API-25)"
```

---

### Task 4：前端历史列表页

**Files:**
- Modify: `frontend/src/api/types.ts`
- Create: `frontend/src/api/history.ts`
- Create: `frontend/src/utils/time.ts`
- Create: `frontend/src/views/HistoryView.vue`
- Modify: `frontend/src/router/index.ts`（`/history` 指向 `HistoryView`）
- Test: `frontend/src/__tests__/history-list.spec.ts`

**Interfaces:**
- Consumes: 既有 `http.get`、`ApiError`、`PageView` 风格、`useAuthStore`、`AppNav`（已链接 `/history`）、Task 2 的 `HistoryPage` 契约
- Produces:
  - `types.ts` 追加：`HistoryItem`、`HistoryPage`、`SimulationStatus`（若已有则复用）、`HistoryItem.interruption: Interruption | null`；不得新增与后端不一致的字段
  - `historyApi.list(params?: { cursor?: string; limit?: number }): Promise<HistoryPage>`（`GET /simulations` + 查询串，首屏不带参数）
  - `utils/time.ts`：`formatShanghai(iso: string): string`，用 `Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', ... })` 输出 `YYYY-MM-DD HH:mm`；非法输入返回原串
  - `HistoryView.vue` 的 `data-testid`：`history`、`history-loading`、`history-error`、`history-retry`、`history-empty`、`history-list`、`history-item`、`history-item-started`、`history-item-scenario`、`history-item-profiles`、`history-item-status`、`history-item-report`、`history-more`

- [ ] **Step 1：写失败测试 `history-list.spec.ts`**

```ts
it('renders each saved record with time, scenario, both names, status and report state', async () => {})
   // startedAt 以北京时间展示（断言 08:00Z -> 16:00）；场景名与双方姓名取服务端值；状态文案「进行中/已结束/已中断」
it('shows report state truthfully for every status', async () => {
   // SUCCEEDED -> 「已完成」；FAILED -> 「生成失败」；PENDING/RUNNING -> 「生成中」；
   // INTERRUPTED 且 report=null -> 「不生成正式复盘」；RUNNING 且 report=null -> 「—」
})
it('never claims a finished record without a report is complete', async () => {
   // status=FINISHED 且 report=null -> 显示「报告状态异常」，断言页面不含「已完成」
})
it('shows an empty state when there are no records', async () => {})
   // items: [] -> history-empty，文本含「本人暂无记录」，不渲染任何 history-item
it('loads more pages with the cursor and stops at the last page', async () => {
   // 首页 hasMore=true、nextCursor='c1' -> 点击 history-more 请求 cursor=c1；末页 hasMore=false 时按钮消失
})
it('appends without duplicating ids when a page overlaps', async () => {
   // 第二页返回与第一页重复的 sessionId -> 渲染列表不出现重复项（按 sessionId 去重）
})
it('shows a retryable failure instead of an empty state', async () => {})
   // 500 -> history-error + history-retry；断言不出现 history-empty
it('links every item to its history detail route', async () => {})
   // 断言链接为 /history/<sessionId>
it('renders no delete, export, share or media controls', async () => {})
   // 页面文本不含「删除」「导出」「分享」「下载」，且不存在 audio/video 元素
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd frontend && npx vitest run src/__tests__/history-list.spec.ts`
Expected: FAIL（`historyApi`、`HistoryView`、`utils/time` 不存在）。

- [ ] **Step 3：实现类型、API、时间工具与列表页**

要点：加载顺序为「首屏 `historyApi.list()`」→ 追加时 `historyApi.list({ cursor })` 并按 `sessionId` 去重；失败时进入可重试错误态，**不得**渲染为空态；状态文案与报告文案用表驱动映射，`FINISHED && report === null` 单独给出「报告状态异常」而绝不显示「已完成」；列表项整块可点，路由到 `/history/:id`；移动端纵向排列（沿用 `style.css` 既有类，必要时仅在本页加 scoped 样式）；页面不出现任何媒体控件或删除/导出入口。

- [ ] **Step 4：运行测试与类型检查确认通过**

Run: `cd frontend && npm.cmd test` 与 `cd frontend && npx vue-tsc --noEmit` 与 `cd frontend && npm.cmd run build`
Expected: 三条命令退出码 0；既有 `phase1/configuration/admin/simulation/report` 各 spec 仍通过。

- [ ] **Step 5：提交**

```bash
git add frontend/src
git commit -m "feat(frontend): personal history list with truthful report state"
```

---

### Task 5：前端历史详情页

**Files:**
- Modify: `frontend/src/api/types.ts`（新增 `HistoryDetail`、`HistoryRound`、`HistoryMessage`）
- Modify: `frontend/src/api/history.ts`（新增 `detail`）
- Create: `frontend/src/components/history/HistoryConfiguration.vue`
- Create: `frontend/src/components/history/HistoryRounds.vue`
- Create: `frontend/src/views/HistoryDetailView.vue`
- Modify: `frontend/src/router/index.ts`（新增 `/history/:id`）
- Test: `frontend/src/__tests__/history-detail.spec.ts`

**Interfaces:**
- Consumes: Task 4 的 `historyApi`、`formatShanghai`；既有 `ProfileDetail.vue`（展示单个画像）、`RiskChart.vue`（`points: {roundNo, score}[]`）；Task 3 的 `HistoryDetail` 契约
- Produces:
  - `historyApi.detail(id: string): Promise<HistoryDetail>`
  - `HistoryConfiguration`：props `{ configuration: ConfigurationView }`；`data-testid="history-configuration"`，用 `ProfileDetail` 渲染双方，并显示场景名/简介/内容侧重与开始时间
  - `HistoryRounds`：props `{ rounds: HistoryRound[] }`；`data-testid="history-rounds"`、`history-round`、`history-round-message`、`history-round-analysis`、`history-round-incomplete`；逐轮显示轮号、双方台词（`content`）、动作说明（`actionText`，仅非空时）、解说四段与风险分；`interactionComplete === false` 时显示「本轮未完成（对方回应未保存）」且不渲染空解说
  - `HistoryDetailView.vue`：`data-testid="history-detail"`、`history-detail-loading`、`history-detail-error`、`history-detail-outcome`、`history-detail-interruption`、`history-detail-risk`、`history-detail-report`、`history-report-link`、`history-report-retry-link`、`history-back`

- [ ] **Step 1：写失败测试 `history-detail.spec.ts`**

```ts
it('renders the frozen configuration and both profiles', async () => {})
   // 场景名/简介/内容侧重 + 双方姓名、年龄、身份、参数；断言不是从当前画像接口取数（只 mock historyApi.detail）
it('renders rounds in order with content, action text and analysis', async () => {})
   // 断言轮号顺序、台词与动作说明分别出现、四段解说与风险分出现
it('marks an incomplete round and never invents the missing rebuttal', async () => {})
   // interactionComplete=false 的轮次显示 history-round-incomplete，且该轮不出现 VICTIM 台词/解说
it('plots only saved risk scores and never fills zero', async () => {
   // 用 rounds 中 analysis 非空的分数构造 RiskChart 输入 [{1,25},{3,75}]；
   // 断言不存在 0 分点、不存在第 2 轮点（缺分析轮不补 0）
})
it('shows the outcome for a finished run and the interruption for an aborted one', async () => {})
   // FINISHED -> outcome.displayText 与 reason；INTERRUPTED -> interruption.message 且无 outcome 区块
it('offers a report link only according to the report state', async () => {
   // SUCCEEDED -> history-report-link 指向 /simulations/<id>/report；FAILED -> history-report-retry-link 同路由；
   // PENDING/RUNNING -> 文案「生成中」且无链接；INTERRUPTED -> 「不生成正式复盘」且无链接
})
it('shows a truthful anomaly notice for a finished record without report', async () => {
   // status=FINISHED 且 report=null -> 「报告状态异常」，断言不出现「已完成」也不出现「生成失败」
})
it('shows a unified not-found page for a foreign or unknown record', async () => {})
   // 404 -> history-detail-error，文本含「记录不存在或不可访问」
it('renders no audio, video, delete, export or resume controls', async () => {})
   // 断言不存在 audio/video 元素，文本不含「播放」「导出」「删除」「继续演绎」
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd frontend && npx vitest run src/__tests__/history-detail.spec.ts`
Expected: FAIL（`historyApi.detail`、两个组件与详情视图不存在）。

- [ ] **Step 3：实现配置块、轮次块与详情页**

要点：页面只调用 `historyApi.detail(id)`（不额外拉 `GET /simulations/{id}`，配置与状态全部来自详情响应）；风险折线输入由 `rounds.filter(r => r.analysis).map(r => ({ roundNo: r.roundNo, score: r.analysis!.riskScore }))` 计算——**不得**用轮号补齐或用 0 占位；报告入口按六种状态分支渲染；`readingUntil` 不展示（对用户无意义）；返回链接回 `/history`；整页只读、无媒体元素；沿用既有 `configuration-section` 等样式类保持与报告页一致。

- [ ] **Step 4：运行测试与类型检查确认通过**

Run: `cd frontend && npm.cmd test` 与 `cd frontend && npx vue-tsc --noEmit`
Expected: 退出码 0，既有各 spec 仍通过。

- [ ] **Step 5：提交**

```bash
git add frontend/src
git commit -m "feat(frontend): history detail with frozen configuration, rounds and report entry"
```

---

### Task 6：契约校验、端到端验收与文档

**Files:**
- Modify: `tools/validate-openapi.mjs`
- Modify: `tools/smoke-simulation.mjs`（在既有场景后追加历史断言，含 `historyFlow`）
- Modify: `README.md`
- Test: 复用 Task 1～5 的测试 + 冒烟脚本

**Interfaces:**
- Consumes: 全部前序产物；`tools/smoke-simulation.mjs` 既有的隔离环境与 5 个用户、S01/S07/S09 场景
- Produces: 可复现的验收记录（命令与输出）、历史端到端冒烟、更新后的运行说明

- [ ] **Step 1：扩展 `tools/validate-openapi.mjs`**

在既有断言后追加：`['/simulations','get']`（该 path 已有 `post`）、`['/simulations/{id}/detail','get']`；并断言 `components.schemas.HistoryDetail.required` 恰含 `sessionId`/`status`/`startedAt`/`deadlineAt`/`endedAt`/`configuration`/`rounds`/`outcome`/`interruption`/`report`，`components.schemas.HistoryMessage.properties` **不含** `audioUrl`，`components.schemas.HistoryPage.properties` 不含 `total`。输出文案追加 `phase 6 (2) operations present`。

Run: `node tools/validate-openapi.mjs`
Expected: 退出码 0 并打印更新后的文案。

- [ ] **Step 2：在 `tools/smoke-simulation.mjs` 中追加历史断言**

该脚本的 `main()` 在非 `--report` 模式下会并行跑完 S01/S04/S07/S13/S09 五个场景（`users` 共 5 个），随后结束。本步骤**不新增脚本、不新增 flag**：在 `await Promise.all([...])` 那个块之后调用 `await check('History list, detail, pagination, immutability and permissions', () => historyFlow(users, admin))`，让默认冒烟同时覆盖历史模块（`--report` 模式在第 63 行已提前 `return`，不受影响）。`historyFlow` 需要自行准备数据并断言：

1. 用既有 `Client` 的 start + exit 调用（参考 `s09`）为 `users[1]` 造 **3 条 `INTERRUPTED`** 记录（同一用户同时只能有一条 `RUNNING`，逐条 start→exit 即可），使分页可验证。
2. `users[1]` 取 `GET /simulations?limit=1`：`items` 只含本人记录、`hasMore=true`、`nextCursor` 非空；用 `nextCursor` 逐页取到 `hasMore=false`，收集到的 `sessionId` 集合恰为 3 条且**无重复**；每条 `status=INTERRUPTED`、`report=null`、`interruption.code` 非空。
3. `users[0]` 取 S01 会话语义上的 `GET /simulations`：能读到该 FINISHED 记录且 `report.status` 为 `SUCCEEDED`（若执行器尚未完成则允许 `PENDING`/`RUNNING`，并在该子断言中轮询最多 30 秒等待终态；超时则如实记录而非放行）。
4. 对同一 S01 会话取 `GET /simulations/{id}/detail`：`status=FINISHED`、`outcome.code` 与库中 `outcome_code` 一致、`configuration.scenario.name` 等于快照名、`rounds` 数量与 `SELECT COUNT(*) FROM simulation_round WHERE session_id=...` 一致、任一消息对象中**不存在** `audioUrl` 键。
5. **不可变性**：管理端用 `PUT /admin/scenarios/{id}` 把该场景名称改为一个明显不同的值，再取同一详情，`configuration.scenario.name` **仍是旧名**；随后把名称改回，避免影响后续断言。
6. `users[2]` 的 S07 会话（受害者 TTS 失败中断）：详情末轮 `interactionComplete=false`、该轮 `analysis=null`、`outcome=null`、`interruption` 非空、`report=null`。
7. 权限与游标：`users[0]` 的 `nextCursor` 交给 `users[1]` 使用 → 400 `INVALID_CURSOR`；`users[1]` 访问 `users[0]` 的详情 → 404，且响应体与访问 `999999` 的 404 **逐字段一致**；`GET /simulations?userId=1` → 400 `VALIDATION_FAILED`；ADMIN 访问两个接口 → 403；匿名 → 401。
8. 两个接口的响应都含 `Cache-Control: no-store`，且响应体不含 `userId`、`internalNote`、`audioUrl`、SQL 或绝对路径。

Run: `node tools/smoke-simulation.mjs` 与 `node tools/smoke-report.mjs`
Expected: 两个脚本全部通过（同一隔离实例模式，端口空闲时依次执行；`--report` 模式不运行历史断言）。

- [ ] **Step 3：运行全量回归**

Run:
```
cd backend && .\mvnw.cmd package
cd frontend && npm.cmd test
cd frontend && npm.cmd run build
cd .. && node tools/validate-openapi.mjs
```
Expected: 后端打包成功且全部单元/HTTP 冒烟测试通过；前端测试与生产构建通过；契约脚本退出码 0。Docker 可用时另跑 `.\mvnw.cmd verify` 覆盖 `HistoryReadIT` 与 `MySqlSmokeIT`。

- [ ] **Step 4：更新 `README.md`**

必须包含：本阶段两个接口与「历史只读」的定位；列表分页语义（`startedAt DESC, id DESC`、游标绑定本人、limit 1—100、无 total）；详情包含哪些内容、明确不包含音频与内部备注；半轮与未评分轮次的展示规则；**历史不变性**（快照名称/配置/头像不随后台改动）；报告入口四态与「`FINISHED` 缺报告行」的处理（列表降级为 `null`、详情 500 `DATA_INCONSISTENT`）；`/history` 与 `/history/:id` 两个路由；测试与冒烟命令（说明历史断言已并入 `node tools/smoke-simulation.mjs`，`node tools/smoke-report.mjs` 不受影响）；本次验收的实际结果与未执行项；并在文首文档清单补上本计划链接。

- [ ] **Step 5：提交**

```bash
git add tools README.md
git commit -m "test(history): openapi checks, end-to-end history smoke and docs"
```

---

## 六、完成判据（Definition of Done）

1. `.\mvnw.cmd package` 与 `.\mvnw.cmd test` 通过；`npm.cmd test`、`npm.cmd run build`、`npx vue-tsc --noEmit` 退出码 0；`node tools/validate-openapi.mjs` 退出码 0。
2. API-24、API-25 的字段、状态码、错误码、分页语义与 `Cache-Control` 与 `openapi.yaml` 逐项一致；`node tools/smoke-simulation.mjs` 中的历史断言全部通过，且 `smoke-report.mjs` 仍能通过。
3. 「复审重点」五条各自至少有一个通过的测试，且测试名能直接读出它守的是什么行为。
4. `git log` 中每个 Task 恰好一次提交，提交信息可追溯到 Task。
5. 数据库结构与第五阶段完全一致（9 张业务表）：**没有**新增迁移、表或列；本模块全部读路径为只读事务。
6. 前五阶段契约未回归：`ContentRulesTest` 原样通过（`ContentCursor` 行为不变）、`ReportViewAssemblyTest` 通过（`reportView` 行为不变）、`GET /simulations/{id}` 与 `/report` 字段不变。
7. 前端 `/history` 与 `/history/:id` 在「进行中/已结束/已中断/报告失败/报告缺失」五类数据下显示真实状态；不出现删除、导出、分享、下载、音视频播放或续演入口。
8. `README.md` 的说明可被未参与开发的人在干净环境复现；未执行的验证项如实列出。

## 七、风险与阻塞项（执行者必须回报而非自行决定）

- **Docker 不可用**：`HistoryReadIT` 是唯一能证明「并列 `startedAt` 的键集分页不重不漏」「快照不变性」「归属 404 一致」的测试，默认依赖 Testcontainers 的 `mysql:8.0.45`；本机 Docker daemon 自 2026-10-16 起未运行。处理方式是按 README 记录的隔离 MySQL 方式运行（Task 1 Step 4），并在 README 记录「未使用 Testcontainers」；**不得**改用 H2 宣称排序与游标语义已验证（数据库设计 §12.1 明确禁止）。
- **`ContentCursor` 抽取的回归风险**：`SignedCursor` 抽取会改动第二阶段的共享代码。必须保持 `encode/decode` 的载荷格式、空游标返回 `0`、非法游标抛 `INVALID_CURSOR` 三条行为不变，并以**未修改的** `ContentRulesTest` 作为门禁。若抽取后该测试需要改动，说明行为已经变了，应回退重做而不是改测试。
- **`GET /simulations` 的 500 契约较窄**：openapi 只列 `INTERNAL_ERROR`，因此 `FINISHED` 缺报告行时列表选择「该条 `report=null` + WARN」而不是 500（详情才 500 `DATA_INCONSISTENT`）。若产品方希望列表也硬失败，需要先改契约再改实现，**不要**在实现里悄悄返回未文档化的 `DATA_INCONSISTENT`。
- **并列时间的排序依赖毫秒精度**：`started_at` 为 `DATETIME(3)`，同一毫秒内的两次开始靠 `id` 区分。若未来把精度改为秒级，键集游标会出现并列歧义——修改列精度属于契约变更，不在本阶段处理，发现精度被改要立即回报。
- **快照名称可能为空串**：`names(...)` 不做非空校验（避免旧快照导致整页 500），因此极端数据下列表可能显示空名称。这是有意的降级；若要求严格非空，需要先确认错误码契约，不要私自在列表抛 `DATA_INCONSISTENT`。
- **游标进程内密钥**：`SignedCursor` 使用进程内随机密钥，重启后旧游标失效（与既有 `ContentCursor` 一致，用户刷新页面即可）。多实例部署前必须改为共享持久密钥（数据库 DBD-10），本阶段不做。
- **`RUNNING` 记录只可能有一条**：`active_user_id` 唯一约束决定同一用户同时只有一场活动演绎；IT 造数据时不要尝试为同一用户插两条 `RUNNING`（会被唯一键拒绝），需要多状态时使用不同用户。
- **报告入口只跳转、不新增接口**：历史详情页对 `FAILED` 只提供进入 `/simulations/:id/report` 的链接，重试仍由第五阶段的 API-23 完成。不要为了「在历史页直接重试」而新增接口或复制重试逻辑。
