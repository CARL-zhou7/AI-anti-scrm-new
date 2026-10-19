# 第五阶段（复盘分析模块）开发计划

> **面向 AI 执行者：** 本计划的每一步均使用复选框（`- [ ]`）语法，请按顺序逐任务（Task）执行。每个 Task 结束都必须通过其验证步骤，并完成一次 git 提交后再进入下一个 Task。本计划建立在已完成的「登录注册 + 首页 + 场景画像配置 + 管理后台 + AI情景演绎」代码之上，请在现有文件与既有约定上增量开发，**不要**重写既有模块，也不要改变前四阶段已交付的对外契约。

**目标（Goal）：** 交付可运行的「复盘分析模块」垂直切片——正式结束的演绎自动在后台生成复盘报告（不依赖浏览器连接、页面离开不取消）；用户进入 `/simulations/:id/report` 查看过程总结、逐轮分析、骗子话术分析、受害者行为分析、结局分析与实际风险折线；生成中显示「生成中」、失败显示「生成失败」并提供手动重试，重试仍基于同一次演绎的原始事实、不重跑角色互动；中断演绎明确「不生成正式复盘」；成功报告一经保存不再改写。对应《接口文档 V1.1》API-22、API-23 与 `report.updated` SSE 事件。

**架构（Architecture）：** 新增与 `simulation` 并列的 `report` 包（报告任务的状态转换、上下文装配、执行器、重启收敛、HTTP 入口）；报告行 `simulation_report` 在第四阶段已随正式结束同事务建为 `PENDING`，本阶段只负责**领取、生成、校验、原子保存、超时/重启收敛与手动重试**。并发正确性依赖三条既有能力：行级悲观锁领取（`SELECT … FOR UPDATE`）、`attempt_no + task_token` 条件更新栅栏、以及持久队列扫描（`idx_report_queue` / `idx_report_deadline`），内存通知只用于加速发现。任务执行与演绎编排**执行资源隔离**（独立调度线程 + 受控并发信号量 + 虚拟线程调用模型），慢报告不会挤占演绎推进。前端新增报告页替换第四阶段的说明页占位，状态以服务端返回为准，未成功保存的报告绝不显示为已完成。

**技术栈（Tech Stack）：** 沿用既有锁定版本：Java 21（Corretto 21.0.8）+ Spring Boot 3.5.16（web / data-jpa / security / validation / flyway-mysql）+ MySQL 8.0.45 + Flyway；前端 Vue 3.5.44 + Vue Router 4.5.1 + Pinia 3.0.3 + Vite 6.4.4 + TypeScript 5.9.3 + Vitest 5.0.5 + Vue Test Utils 2.4.6 + jsdom 26.1.0；契约校验 `@apidevtools/swagger-parser` 12.0.0。**不引入**新依赖（不新增图表库、不使用 Lombok/MapStruct；折线图用内联 SVG）。

**Spec（本计划实现的规格文档，执行者必须同时阅读）：**
- `docs/AI反诈情景推演平台_PRD_V3_2_正式开发基线版.md`（§8.5 分析复盘模块 FR-REV-01～05 与报告规则 BR-P07、§6.2 结束流程第 5 条、§7 页面结构「分析复盘」行、§11.4/§11.5 过程·结果·状态对应与保存规则、§12.1 UX-02/UX-03/UX-05、§13.1 AC-17～AC-20、§13.2 AP-06）
- `docs/AI反诈情景推演平台_系统架构设计文档_V1_1.md`（§12.1 复盘输入与输出责任、§12.2 报告任务的可靠触发、§12.3 历史读取、§6.3 终态与迟到结果、§15.2 异常处理矩阵）
- `docs/AI反诈情景推演平台_数据库设计文档_V1_1.md`（§5.8 `simulation_report` 字段、§6.3「报告字段组合」、§9 全章：状态机、领取、成功提交与迟到隔离、手动重试、重启/超时与「仅一次」的实际含义、§10.1 权限）
- `docs/AI反诈情景推演平台_接口文档_V1_1.md`（§2.3 权限矩阵、§2.4 缓存、§5 SSE 的 `report.updated`、§6 的 API-22 与 API-23、§7 的 `ReportStatus`/`ReportError`/`ReportSummary`/`ReportContent`/`ReportView`/`RiskPoint`/`Outcome`、§8 错误码）
- `openapi.yaml`（机器可读契约，**字段名、约束、状态码与错误码的最终依据**）
- `README.md` 与 `coding plan/2026-10-18-phase4-ai-simulation.md`（第四阶段的实际交付：报告行只建不处理、`report` 字段在 `session.state` 中已返回摘要、`/simulations/:id/report` 目前是说明页、`tools/smoke-simulation.mjs` 现有断言）

---

## 一、本阶段范围与模块边界

### 1.1 本阶段实现的接口与能力（唯一交付范围）

| 编号 | 方法 | 完整路径 | 用途 | 权限 |
|---|---|---|---|---|
| API-22 | GET | `/anti.scam/v1/simulations/{id}/report` | 报告状态与内容 | USER，本人 |
| API-23 | POST | `/anti.scam/v1/simulations/{id}/report/retry` | 报告失败重试 | USER，本人 + CSRF |

同时交付：后台报告执行器（持久队列领取、单次尝试、校验、原子保存、失败分类）、超时清扫、重启收敛（`RUNNING→FAILED(SERVICE_RESTARTED)`、`FINISHED` 缺行修复）、`report.updated` SSE 发布、前端 `/simulations/:id/report` 真实报告页（状态、内容、风险折线、失败重试）、`tools/smoke-report.mjs`（或扩展现有仿真冒烟）与 README 更新。

### 1.2 本阶段明确不做（禁止提前实现、禁止预留占位接口）

- **API-24、API-25（本人历史列表与详情）** 与 `/history` 页面：属于第六阶段「历史记录」；`/history` 仍为既有说明页。
- **不新增数据库迁移**：`simulation_report` 表、索引、CHECK 约束在 `V5__create_simulation_tables.sql` 中已存在且与本计划逐条对应，**不得**修改 V1～V6 任何已执行文件，也不得新增列或表（不加报告重试日志表、不加任务表、不加通知表）。
- 不实现自动重试（PRD 明确本期不要求）、不实现报告编辑/改写、不实现报告导出或分享、不实现雷达图或用户能力评价、不实现音视频回放。
- 不实现「第二个模型评审报告」类的新 Agent（PRD 只有四个模板用途：SCAMMER/VICTIM/NARRATOR/REPORT）；语义判断只由 REPORT 模板承担，程序只做形式与硬约束校验。
- 不把 REPORT 模板改成自由文本输出，也不新增 Prompt 版本迁移（模板已由 `V6` 播种且校验和受保护）。
- 不允许为了「让报告更容易成功」而放宽校验器、替模型改写正文、或用预设文案代替真实生成。

---

## 二、全局约束（Global Constraints，所有任务默认包含）

以下数值来自规格文档与既有实现，**必须逐字实现，不得「优化」**：

1. 接口前缀固定 `/anti.scam/v1`；统一封套四字段 `code`/`message`/`data`/`requestId` 始终存在（沿用 `ApiResponse` 与 `RequestIdFilter`），私有响应 `Cache-Control: no-store`。
2. 权限：匿名 → **401 `UNAUTHENTICATED`**；ADMIN 访问 `/simulations/**` → **403 `FORBIDDEN`**（`SecurityConfig` 已有 `hasRole("USER")`，不得放宽）；他人记录与不存在记录一律 **404 `NOT_FOUND`**，`message` 恰为「资源不存在或不可访问」，两者响应体逐字段一致。
3. `ReportStatus` 四态与用户展示严格对应：`PENDING`/`RUNNING` → 「生成中」、`SUCCEEDED` → 「已完成」、`FAILED` → 「生成失败」；`canRetry` 仅 `FAILED` 为 `true`；`PENDING`/`RUNNING`/`SUCCEEDED` 的 `error` 必须为 `null`。
4. `ReportError.code` 只允许 `GENERATION_FAILED`、`VALIDATION_FAILED`、`PERSISTENCE_FAILED`、`TIMEOUT`、`SERVICE_RESTARTED`（与数据库 CHECK 完全一致）；中文说明固定为：`GENERATION_FAILED`→「报告生成失败，可手动重试。」、`VALIDATION_FAILED`→「报告内容未通过校验，可手动重试。」、`PERSISTENCE_FAILED`→「报告保存失败，可手动重试。」、`TIMEOUT`→「报告生成超时，可手动重试。」、`SERVICE_RESTARTED`→「服务重启导致报告生成中断，可手动重试。」。
5. `ReportView` 七字段恒存在：`sessionId`（字符串 ID）、`simulationStatus`、`report`（可 null）、`unavailableReason`（`SIMULATION_RUNNING` / `SIMULATION_INTERRUPTED` / null）、`outcome`（可 null）、`content`（仅 `SUCCEEDED` 非 null）、`riskSeries`。
6. `unavailableReason` 与状态严格绑定：`RUNNING` 演绎 → `SIMULATION_RUNNING` 且 `report`/`outcome` 为 null；`INTERRUPTED` → `SIMULATION_INTERRUPTED` 且 `report`/`outcome` 为 null；`FINISHED` → `null`。
7. **`FINISHED` 必须有报告行**：缺行一律 **500 `DATA_INCONSISTENT`**（`message` =「记录状态异常，请稍后重试」），不得包装成「中断无报告」或空报告。
8. `riskSeries` 只取**已评分轮次**的 `{roundNo, score}`，按 `roundNo` 升序，**缺失不补 0**，不重算、不插值；仅正式结束（`FINISHED`）返回，`RUNNING`/`INTERRUPTED` 为空数组。
9. `content` 仅来自已保存的 `content_json`；`GET` 不触发生成、不重试、不调用模型。
10. `report.updated` 事件在报告状态每次真正变化后发布（领取→`RUNNING`、成功→`SUCCEEDED`、失败→`FAILED`、重试入队→`PENDING`），载荷为该场完整 `ReportView`；同一事务内用 `publishAfterCommit`，**提交后才推送**。
11. 重试的四个分支固定：演绎 `RUNNING`/`INTERRUPTED` → **409 `REPORT_NOT_APPLICABLE`**（`message` =「该演绎不具备报告重试条件」）；`SUCCEEDED`/`PENDING`/`RUNNING` 报告 → **200** 返回当前视图且**不增加 `attempt_no`**；`FAILED` → 原子转 `PENDING` 并返回 **202**；`FINISHED` 但缺行 → **500 `DATA_INCONSISTENT`**。
12. `attempt_no` 只在**真正领取**时 +1（首次结束建行为 0）；重试入队不增加，`queued_at` 刷新；重试入队时清空 `task_token`、`started_at`、`attempt_deadline_at`、`finished_at`、`error_code`、`content_json`。
13. 领取、保存、失败三条路径都必须满足数据库 §6.3 的报告字段组合 CHECK（`PENDING` 六空；`RUNNING` 需 `attempt_no≥1`、token、`started_at`、`attempt_deadline_at > started_at` 且三空；`SUCCEEDED` 需 `content_json` 为 OBJECT 且 `error_code` 为空；`FAILED` 需 `error_code` 非空且 `content_json` 为空）。
14. 成功保存必须带栅栏条件：`session_id` 匹配、`status='RUNNING'`、`attempt_no` 与 `task_token` 与领取时一致、`now ≤ attempt_deadline_at`；**受影响行数必须为 1** 才可宣告成功；为 0 时读取当前状态并放弃，**禁止无条件覆盖**。
15. 已成功报告不可改写；迟到结果、被超时收敛的旧尝试、被手动重试取代的旧尝试，其写入必须全部落空。
16. 报告失败**不改变**演绎的 `FINISHED`、正式结局、原风险与原消息；不把已结束演绎回退为中断。
17. `attempt_deadline_at = started_at + app.simulation.report-attempt-timeout-ms`（默认 120000，正值）；模型调用超时取 `min(该尝试剩余预算, app.simulation.llm.call-timeout-ms)`。
18. 报告生成**不占用演绎的 600 秒**、不受演绎 `deadline_at` 影响；页面离开、SSE 断开都不取消生成。
19. 同一场演绎同一时刻只有一个有效尝试；跨进程竞争由数据库行锁与条件更新保证，应用内只做加速与去重。
20. 日志只记 `sessionId`、`attempt_no`、错误类别、耗时与结果，**不得**记录报告正文、Prompt 渲染结果、供应商原文、凭据或异常堆栈。
21. 响应与 SSE 中不得出现 `content_json` 原始串、`task_token`、`error_code` 之外的内部分类、Prompt 正文或模型原始输出。
22. 前端不得本地拼装报告内容、不得在非 `SUCCEEDED` 时渲染报告正文、不得用「暂无报告」掩盖 `FAILED`；重试按钮只在 `canRetry === true` 时出现，提交中禁用，重复点击不得产生第二次有效尝试。
23. 本阶段不修改 `backend/src/main/resources/prompts/**` 与 V1～V6 迁移；`spring.jpa.hibernate.ddl-auto` 保持 `validate`。

---

## 三、复审重点（Review Focus）

以下五类输入或失败模式是本阶段最可能伤害真实使用者的位置，规格文档暗示了但常规任务不会自然覆盖。**每条已在括号中注明其测试落在哪个任务的步骤，实现这些任务时必须写出对应测试，不得省略：**

1. **尝试卡死与超时收敛**——领取后模型无响应、进程被强杀、数据库短暂不可用：报告行可能长期停在 `RUNNING`。合理预期：超过 `attempt_deadline_at` 后由清扫把它原子收敛为 `FAILED/TIMEOUT` 并可手动重试；进程重启后旧 `RUNNING` 收敛为 `FAILED/SERVICE_RESTARTED`；**任何情况下都不能永久停在 `RUNNING`**，也不能在收敛后又被迟到的模型响应改写。（Task 1 步骤 1/3、Task 3 步骤 1/3/5）
2. **迟到、重复与被取代的写入**——同一行被两个执行器同时领取；或一次尝试失败后用户已手动重试并进入新一次尝试，旧调用这时才返回。合理预期：领取只有一个成功（`attempt_no` 恰好 +1），旧令牌的保存/失败写入**影响 0 行且不报成功**，新尝试不被污染，成功报告不被旧结果覆盖。（Task 1 步骤 1/3、Task 5 步骤 1/3，后者含 `concurrentRetriesProduceExactlyOneNewAttempt`）
3. **把不合格输出保存成「已完成」**——模型返回合法 JSON 但轮次对不上（缺轮、多轮、重复 `roundNo`、顺序错乱）、字段为空、正文含 HTML 标签、`outcomeAnalysis` 未点明唯一正式结局（尤其把「结局未定」写成「未受骗」）。合理预期：一律判 `VALIDATION_FAILED`，行保持 `FAILED` 且可重试；`GET` 在任何非 `SUCCEEDED` 状态下都返回 `content: null`，绝不展示半成品或伪造正文。（Task 2 步骤 1/3、Task 4 步骤 1/3）
4. **重试语义的边界**——`SUCCEEDED` 再点重试（必须 200、正文不变、`attempt_no` 不变）、`PENDING`/`RUNNING` 连点（200，不建第二次尝试）、中断演绎或仍在演绎（409 `REPORT_NOT_APPLICABLE`）、`FINISHED` 缺行（500 `DATA_INCONSISTENT`）、无 CSRF（403 `CSRF_INVALID`）、带请求体（400）。合理预期：每个分支的状态码与 `data` 都与契约一致，且数据库状态不被这些被拒请求改动。（Task 5 步骤 1/3）
5. **归属与角色穿透**——他人报告、ADMIN、匿名、以及「存在但属于别人」的 `sessionId`。合理预期：他人与不存在都返回**完全相同**的 404（不含报告正文、结局、错误分类或诊断），ADMIN 一律 403，匿名 401；`content_json` 与 `task_token` 绝不出现在任何错误响应中。（Task 4 步骤 1/3、Task 5 步骤 1/3、Task 7 步骤 1/3）

补充要求（同样必须在对应任务中测试）：`riskSeries` 必须能被识别为「不补 0」——某轮无分析时该轮**不出现**在序列中，且序列与库中 `risk_score` 逐项相等（Task 4 步骤 1/3）；`session.state` 的 `reportView` 必须与 `GET /report` **同源同值**（同一装配方法），避免两条路径给出不同状态（Task 4 步骤 1/3）。

---

## 四、文件结构（先定边界，再拆任务）

```
D:\AI-anti-scam\
├── coding plan\
│   └── 2026-10-19-phase5-analysis-report.md                  ← 本计划
├── tools\
│   ├── validate-openapi.mjs                                  ← 修改：增加 API-22/23 断言
│   ├── smoke-report.mjs                                      ← 新增：隔离实例报告全流程验收
│   └── smoke-simulation.mjs                                  ← 修改：S01 报告断言随执行器改变
├── backend\src\
│   ├── main\java\com\aiscam\antiscam\
│   │   ├── agent\
│   │   │   ├── AgentModels.java                              ← 修改：AgentRole 增加 REPORT；新增 ReportContent/RoundAnalysis/ReportPrompt
│   │   │   ├── AgentCallException.java                       ← 修改：Category 增加 REPORT
│   │   │   ├── AgentOutputParser.java                        ← 修改：新增 report(String raw)
│   │   │   ├── AgentOutputValidator.java                     ← 修改：新增 report(…) 硬约束校验
│   │   │   ├── AgentRunner.java                              ← 修改：新增 report(…) 
│   │   │   ├── DeepSeekLlmClient.java                        ← 修改：REPORT 归入 Category.REPORT
│   │   │   └── ScriptedLlmClient.java                        ← 修改：REPORT 归入 Category.REPORT
│   │   ├── simulation\
│   │   │   ├── domain\
│   │   │   │   ├── ReportErrorCode.java                      ← 新增：五类失败码与固定中文说明
│   │   │   │   ├── ReportStatus.java                         ← 修改：新增 canRetry()
│   │   │   │   ├── SimulationReport.java                     ← 修改：claim/succeed/fail/requeue 与只读访问器
│   │   │   │   ├── SimulationReportRepository.java           ← 修改：悲观锁、队列扫描、条件更新
│   │   │   │   └── SimulationSession.java                    ← 修改：补 getReportPromptId()
│   │   │   ├── service\SimulationStateAssembler.java         ← 修改：reportView 统一装配（内容、错误、风险序列）
│   │   │   └── web\dto\SimulationViews.java                  ← 修改：ReportError/ReportContent/RoundAnalysis；收紧 ReportSummary/ReportView 类型
│   │   ├── report\
│   │   │   ├── service\
│   │   │   │   ├── ReportContext.java                        ← 新增：模板 + 变量 + 脚本键 + 轮数 + 结局文案
│   │   │   │   ├── ReportContextAssembler.java               ← 新增：纯函数，只读快照与已存事实
│   │   │   │   ├── ReportContextLoader.java                  ← 新增：只读事务装载上下文与钉住模板
│   │   │   │   ├── ReportTransitions.java                    ← 新增：领取/成功/失败/重试入队/清扫/收敛/修复
│   │   │   │   ├── ReportRetryRules.java                     ← 新增：纯函数，重试分支判定
│   │   │   │   ├── ReportService.java                        ← 新增：API-22 读取与 API-23 重试入口（归属校验）
│   │   │   │   └── ReportViewMapper.java                     ← 新增：ReportContent 内部类型↔DTO、错误码映射
│   │   │   ├── engine\
│   │   │   │   ├── ReportExecutor.java                       ← 新增：调度、领取、并发信号量、虚拟线程、超时清扫
│   │   │   │   └── ReportRecovery.java                       ← 新增：重启收敛与缺行修复
│   │   │   └── web\ReportController.java                     ← 新增：API-22 / API-23
│   │   └── config\SimulationProperties.java                  ← 修改：新增 app.simulation.report.* 并发与轮询参数
│   ├── main\resources\
│   │   ├── application.yml                                   ← 修改：报告执行器参数
│   │   └── simulation\scripted\*.json                        ← 修改：补充 defaults.report 脚本输出
│   └── test\java\com\aiscam\antiscam\
│       ├── report\ReportRulesTest.java                       ← 纯单元：重试分支、状态映射、错误文案
│       ├── report\ReportContentRulesTest.java                ← 纯单元：解析与硬约束校验
│       ├── report\ReportContextRulesTest.java                ← 纯单元：Prompt 变量只含允许事实
│       ├── report\ReportViewAssemblyTest.java                ← 单元 + Mockito：ReportView 装配（含风险序列）
│       ├── report\ReportEventTest.java                       ← 单元 + Mockito：report.updated 发布时机与载荷
│       ├── report\ReportApiTest.java                         ← MockMvc：API-22/23 契约
│       └── report\ReportLifecycleIT.java                     ← 真实 MySQL：领取/栅栏/超时/收敛/重试/并发
└── frontend\src\
    ├── api\
    │   ├── types.ts                                          ← 修改：ReportError/ReportContent/ReportView 等
    │   └── report.ts                                         ← 新增：reportApi
    ├── components\report\RiskChart.vue                       ← 新增：内联 SVG 风险折线
    ├── views\ReportView.vue                                  ← 新增：替换 ReportEntryView
    ├── views\ReportEntryView.vue                             ← 删除
    ├── router\index.ts                                       ← 修改：/simulations/:id/report 指向 ReportView
    └── __tests__\report.spec.ts                              ← 新增：状态机、内容、重试、风险折线
```

**结构约束：** 一个文件一个职责；依赖方向固定为 `report → simulation（domain、DTO、assembler）` 与 `report → agent`，**`simulation` 不得依赖 `report`**（`SimulationStateAssembler` 负责装配，`report` 只调用它）；`agent` 产出**唯一**的报告内容类型 `SimulationViews.ReportContent`（JSON 契约即模型输出契约，不复制第二套同名类型），`ReportViewMapper` 只做错误码与空值映射；`ReportContextAssembler`、`ReportContentValidator`、`ReportRetryRules` 必须是**纯函数/无状态**，不得注入仓库；只有 `ReportTransitions` 与 `ReportService` 写库；`ReportExecutor` 不直接写 SQL、不拼 DTO；前端 `api/types.ts` 是唯一类型来源，组件不得自行声明响应类型，`api/report.ts` 是唯一请求入口（组件不得直接 `fetch`）。

---

## 五、任务清单

### Task 1：报告行状态机、原子栅栏与重试判定（纯逻辑 + 真实库）

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/simulation/domain/ReportErrorCode.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/domain/ReportStatus.java`（新增 `boolean canRetry()`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/domain/SimulationReport.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/domain/SimulationReportRepository.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/domain/SimulationSession.java`（新增 `long getReportPromptId()`）
- Create: `backend/src/main/java/com/aiscam/antiscam/report/service/ReportRetryRules.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/report/service/ReportTransitions.java`
- Test: `backend/src/test/java/com/aiscam/antiscam/report/ReportRulesTest.java`（surefire）、`backend/src/test/java/com/aiscam/antiscam/report/ReportLifecycleIT.java`（failsafe）

**Interfaces:**
- Consumes: 既有 `SimulationReport.pending(long sessionId, Instant now)`、`SimulationSession.getStatus()/getId()/getUserId()`、`SimulationReportRepository extends JpaRepository<SimulationReport, Long>`、`ApiException.dataInconsistent(String)`、`SimulationProperties.getReportAttemptTimeoutMs()`
- Produces:
  - `ReportErrorCode`：`GENERATION_FAILED`、`VALIDATION_FAILED`、`PERSISTENCE_FAILED`、`TIMEOUT`、`SERVICE_RESTARTED`，各带 `String message()`（文案见全局约束 4）
  - `ReportStatus.canRetry()` → `this == FAILED`
  - `SimulationReport`：只读 `getTaskToken()`、`getQueuedAt()`、`getStartedAt()`、`getAttemptDeadlineAt()`、`getContentJson()`、`getContentSchemaVersion()`；写方法 `void claim(String taskToken, Instant startedAt, Instant deadlineAt)`（要求当前为 `PENDING`，否则抛 `IllegalStateException`）、`void requeue(Instant queuedAt)`（要求当前为 `FAILED`，清空 token/三时间/错误/正文，**不改** `attemptNo`）
  - `SimulationReportRepository`：
    - `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from SimulationReport r where r.sessionId = :sessionId") Optional<SimulationReport> lockById(long sessionId)`
    - `List<SimulationReport> findTop5ByStatusOrderByQueuedAtAscSessionIdAsc(ReportStatus status)`
    - `List<SimulationReport> findTop5ByStatusAndAttemptDeadlineAtBeforeOrderByAttemptDeadlineAtAsc(ReportStatus status, Instant at)`
    - `@Modifying(clearAutomatically = true, flushAutomatically = true)` 四条条件更新：`int markSucceeded(long sessionId, int attemptNo, String taskToken, String contentJson, Instant now)`、`int markFailed(long sessionId, int attemptNo, String taskToken, String errorCode, Instant now)`（两者都带 `status = RUNNING and attemptDeadlineAt >= :now` 条件）、`int failExpired(Instant now)`、`int failRunning(Instant now, String errorCode)`
  - `ReportRetryRules.Action { RETURN_EXISTING, REQUEUE, NOT_APPLICABLE, INCONSISTENT }`
  - `ReportRetryRules.decide(SimulationStatus simulation, ReportStatus reportOrNull) -> Action`（纯函数；判定表见 Step 1 测试）
  - `ReportTransitions`（`@Service`，方法均 `@Transactional(isolation = Isolation.READ_COMMITTED)`）：
    - `record Claimed(long sessionId, int attemptNo, String taskToken, Instant startedAt, Instant attemptDeadlineAt)`
    - `Claimed claim(long sessionId, Instant now)`：返回 `null` 表示不可领取
    - `boolean markSucceeded(Claimed attempt, String contentJson, Instant now)`
    - `boolean markFailed(Claimed attempt, ReportErrorCode code, Instant now)`
    - `int failExpired(Instant now)`、`int failRunningOnRestart(Instant now)`、`boolean repairMissing(long sessionId, Instant now)`
    - **本任务不发布 SSE**：`report.updated` 依赖统一装配后的 `ReportView`，在 Task 4 接入（见该任务），避免先写一版空载荷再返工。

- [ ] **Step 1：写失败测试**

`ReportRulesTest`（纯单元，不启动 Spring）：

```java
@Test void retryDecisionTableMatchesTheContract()
    // RUNNING/RUNNING+null -> NOT_APPLICABLE；INTERRUPTED/* -> NOT_APPLICABLE；
    // FINISHED+PENDING -> RETURN_EXISTING；FINISHED+RUNNING -> RETURN_EXISTING；
    // FINISHED+SUCCEEDED -> RETURN_EXISTING；FINISHED+FAILED -> REQUEUE；FINISHED+null -> INCONSISTENT
@Test void reportStatusDisplayAndRetryFlags()      // PENDING/RUNNING->"生成中", SUCCEEDED->"已完成", FAILED->"生成失败"；
                                                   // canRetry() 仅 FAILED 为 true
@Test void errorCodeMessagesAreTheFixedChineseTexts()  // 五条逐字断言（全局约束 4）
@Test void requeueKeepsAttemptNumberAndClearsFenceFields()
    // SimulationReport.pending(...)+claim(...)+fail 语义后 requeue(now)：attemptNo 不变；
    // taskToken/startedAt/attemptDeadlineAt/finishedAt/errorCode/contentJson 全为 null；status==PENDING、queuedAt==now
@Test void claimRefusesANonPendingRow()            // 对 SUCCEEDED 行调用 claim(...) 抛 IllegalStateException，且字段未被改动
```

`ReportLifecycleIT`（继承既有 `MySqlSmokeIT` 的 Testcontainers 写法；无 Docker 时按 README 的隔离实例方式运行）：

```java
@Test void claimIncrementsAttemptAndSetsFence()          // PENDING/0 -> claim 后 RUNNING/1，token/startedAt/deadlineAt 非空且 deadlineAt>startedAt
@Test void secondClaimOfTheSameRowReturnsNull()          // 连续两次 claim：第二次返回 null，attempt_no 仍为 1
@Test void claimRefusesAnUnfinishedParentSession()       // 父会话仍 RUNNING 时 claim 返回 null，行保持 PENDING
@Test void successFenceRejectsStaleTokenAndExpiredAttempt()
    // 用旧 token、错误 attemptNo、以及 now>attemptDeadlineAt 三种条件分别 markSucceeded：均返回 false，
    // SELECT 断言 status 仍 RUNNING、content_json 仍 NULL
@Test void successFenceAcceptsTheCurrentAttemptOnce()
    // 正确 token：第一次 true 且 SUCCEEDED/content_json 非空；第二次 false，content_json 不被第二次内容覆盖
@Test void failExpiredOnlyTouchesOverdueRunningRows()  // 到期的 RUNNING -> FAILED/TIMEOUT/finished_at 非空；未到期与 PENDING 不受影响
@Test void failRunningOnRestartConvergesSurvivors()   // 两条 RUNNING -> FAILED/SERVICE_RESTARTED；PENDING/SUCCEEDED/FAILED 不变
@Test void repairInsertsAPendingRowOnlyForFinishedSessionsMissingOne()
    // 删除 FINISHED 会话的报告行后 repairMissing：插入 PENDING/0；对 INTERRUPTED 会话返回 false 且不插入
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd test "-Dtest=ReportRulesTest"` 与（Docker 可用时）`cd backend && .\mvnw.cmd verify "-Dit.test=ReportLifecycleIT" -DfailIfNoTests=false`
Expected: FAIL（`ReportErrorCode`/`ReportRetryRules`/`ReportTransitions` 不存在，编译失败）。

- [ ] **Step 3：实现状态机、栅栏与重试判定**

要点：`claim` 在**同一事务**内先 `reports.lockById(sessionId)`（行锁），再校验 `status == PENDING` 与父会话 `status == FINISHED`，然后 `row.claim(UUID.randomUUID().toString(), now, now.plusMillis(properties.getReportAttemptTimeoutMs()))` 并 flush；父会话未结束或行不存在时返回 `null`，**不改变任何字段**（这是「父会话非 FINISHED 属数据异常，跳过而非改写」的处理）。四条条件更新用 JPQL 批量更新并返回受影响行数，条件必须包含 `status = RUNNING`、`attemptNo`、`taskToken`、`attemptDeadlineAt >= now`；`clearAutomatically = true` 避免同一持久化上下文读到旧实体。实体 `requeue` 只能从 `FAILED` 迁移且保留 `attemptNo`（数据库 §9.4），本任务只实现该实体方法，重试编排在 Task 5 完成。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd test "-Dtest=ReportRulesTest"` 与 `cd backend && .\mvnw.cmd verify "-Dit.test=ReportLifecycleIT" -DfailIfNoTests=false`
Expected: 均 PASS。若本机没有 Docker daemon（2026-10-16 起未运行），按 README 中 `tools/smoke-content.ps1` 的方式用隔离 MySQL 实例运行 `ReportLifecycleIT`，并在提交信息与 README 中如实记录「未使用 Testcontainers」，**不得**改用 H2 宣称栅栏行为已验证。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(report): report row state machine, attempt fence and retry rules (API-22/23 core)"
```

---

### Task 2：报告上下文、严格解析与硬约束校验

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/agent/ReportValidationException.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/AgentModels.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/AgentCallException.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/AgentOutputParser.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/AgentOutputValidator.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/AgentRunner.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/DeepSeekLlmClient.java`、`ScriptedLlmClient.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/report/service/ReportContext.java`、`ReportContextAssembler.java`、`ReportContextLoader.java`
- Modify: `backend/src/main/resources/simulation/scripted/default.json`、`round-limit-20.json`（各补 `defaults.report`）
- Test: `backend/src/test/java/com/aiscam/antiscam/report/ReportContentRulesTest.java`、`ReportContextRulesTest.java`

**Interfaces:**
- Consumes: 既有 `PromptRenderer.render(String, Map<String,String>)`（要求提供**恰好**被模板使用的变量，多一个少一个都失败）、`AgentPromptRepository.findById`、`ConfigurationSnapshots.scammerText/victimText`、`SimulationRoundRepository.findBySessionIdOrderByRoundNoAsc`、`SimulationMessageRepository.findByRoundId`、`SimulationSession.getScenarioSnapshot()/getScammerSnapshot()/getVictimSnapshot()/getReportPromptId()/getOutcomeCode()/getOutcomeReason()/getLimitReason()`
- Produces:
  - `AgentCallException.Category` 增加 `REPORT`
  - `AgentModels.AgentRole` 增加 `REPORT`
  - `AgentModels.ReportContent(String summary, List<RoundAnalysis> roundAnalyses, String speechAnalysis, String victimBehaviorAnalysis, String outcomeAnalysis)`（内部值类型；映射到 `SimulationViews.ReportContent` 由 Task 4 的 mapper 负责）
  - `AgentModels.RoundAnalysis(int roundNo, String analysis)`
  - `AgentOutputParser.report(String raw) -> ReportContent`：严格 JSON，字段集**恰好**为 `summary`、`roundAnalyses`、`speechAnalysis`、`victimBehaviorAnalysis`、`outcomeAnalysis`；`roundAnalyses` 每项恰好 `roundNo`（整数）与 `analysis`（字符串）；类型/字段集/JSON 语法不符抛 `ReportValidationException`
  - `AgentOutputValidator.report(ReportContent content, int expectedRounds, String outcomeDisplayText) -> ReportContent`：命中任一规则抛 `ReportValidationException`
  - `AgentRunner.report(ReportContext context, long timeoutMs) -> ReportContent`
  - `ReportContext(String template, Map<String,String> variables, String scriptKey, int roundCount, String outcomeDisplayText)`
  - `ReportContextAssembler.variables(SimulationSession session, List<SimulationRound> rounds, Map<Long, List<SimulationMessage>> messagesByRound, ConfigurationSnapshots snapshots) -> Map<String,String>`：**恰好**四个键 `scenario`、`profiles`、`outcome`、`rounds`（与 `prompts/v1/report.txt` 的占位符一致）
  - `ReportContextLoader.load(long sessionId) -> ReportContext`（`@Transactional(readOnly = true)`）：装载会话、轮次、消息、快照文本与**钉住的 REPORT 模板**（`agent_prompt` 中 `reportPromptId` 对应的行）；会话不存在或模板缺失抛 `IllegalStateException`
  - `scripted` 客户端：`REPORT` 调用使用 `roundNo = 0`，从脚本 `defaults.report` 取输出（`extends` 链生效）

- [ ] **Step 1：写失败测试**

`ReportContextRulesTest`（纯单元）：

```java
@Test void variablesContainExactlyTheTemplatePlaceholders()
    // 断言 keySet 恰为 {"scenario","profiles","outcome","rounds"}；
    // 用 prompts/v1/report.txt 的真实内容调用 PromptRenderer.render，必须成功（不多不少）
@Test void roundsTextKeepsOrderSpeakersActionsAndAnalyses()
    // 3 轮构造：文本按轮号升序出现；每轮含骗子台词、动作说明（若有）、受害者台词；
    // 含该轮风险分与依据；内部意图/内部心理出现且只在对应角色的段落
@Test void outcomeTextUsesTheAuthoritativeOutcome()
    // 断言包含 outcomeCode.displayText()、outcomeReason；UNDETERMINED 时含 limitReason
@Test void profilesTextComesFromFrozenSnapshots()   // 改名后的当前画像不影响文本：文本取自 scenariO/scammer/victim snapshot
```

`ReportContentRulesTest`（纯单元）：

```java
@Test void parserRejectsExtraMissingOrWronglyTypedFields()
    // 多字段、缺字段、roundAnalyses 非数组、roundNo 为字符串、analysis 为 null -> ReportValidationException
@Test void validatorRequiresRoundsOneToOneWithSavedRounds()
    // expectedRounds=3：3 项且 roundNo 为 1,2,3 -> 通过；2 项、4 项、roundNo 乱序、重复 2 -> 拒绝
@Test void validatorRejectsBlankTextAndHtml()
    // summary 空串、analysis 全空白 -> 拒绝；任一文本包含 '<' 或 '>' -> 拒绝（保证"不含可执行HTML"）
@Test void validatorRequiresOutcomeNameInOutcomeAnalysis()
    // outcomeDisplayText="结局未定" 而 outcomeAnalysis 不含该串 -> 拒绝；包含则通过
@Test void validatorAcceptsAWellFormedReport()      // 3 轮正常内容通过，且返回对象与输入等值
@Test void validatorRejectsOversizedText()          // summary 601 码点、analysis 301 码点 -> 拒绝（防御上限，见 Step 3）
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd test "-Dtest=ReportContentRulesTest+ReportContextRulesTest"`
Expected: FAIL（`AgentOutputParser.report` 等不存在，编译失败）。

- [ ] **Step 3：实现上下文装配、解析与校验**

要点：`ReportContextAssembler` 只读**已保存事实**与**冻结快照**，不查当前画像表；每轮的消息取自 `SimulationMessage.getContent()`、`getActionText()` 与 `internalNoteForAgents()`（后者按数据库 §5.7「只供解说与报告读取」使用，且**只**进入报告上下文，不得进入任何用户 DTO/SSE/日志）；`rounds` 文本按轮号升序，每轮格式固定为「第N轮｜骗子：台词（行动：…；内部意图：…）／受害者：台词（行动：…；内部心理：…）／解说：阶段、话术、手段、心理、风险分、依据」；某轮缺分析时照实写「本轮无解说」，**不得补造**。校验上限为**防御性**（不是产品上限，注释中注明）：`summary/speechAnalysis/victimBehaviorAnalysis/outcomeAnalysis` ≤ 600 码点，单条 `analysis` ≤ 300 码点，按 `codePointCount` 计数；`expectedRounds` 必须 ≥ 1。`ReportValidationException` 携带 `String reason`（只进日志，不进响应）。这两个客户端把 `AgentRole.REPORT` 归入 `Category.REPORT`（`DeepSeek` 与 `Scripted` 各改一处 switch）。`AgentRunner.report` 的超时取 `Math.max(1000, Math.min(timeoutMs, config.getCallTimeoutMs()))`，`roundNo` 传 0、`attempt` 传 1、`scriptKey` 取 `context.scriptKey()`。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd test "-Dtest=ReportContentRulesTest+ReportContextRulesTest"`
Expected: PASS；并确认既有 `AgentRulesTest`、`SimulationRulesTest` 仍通过。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(report): report prompt context, strict parser and hard-constraint validator"
```

---

### Task 3：后台报告执行器与重启收敛

**Files:**
- Create: `backend/src/main/java/com/aiscam/antiscam/report/engine/ReportExecutor.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/report/engine/ReportRecovery.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/config/SimulationProperties.java`（新增 `Report` 组）
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/aiscam/antiscam/report/ReportLifecycleIT.java`（追加执行器用例）

**Interfaces:**
- Consumes: Task 1 的 `ReportTransitions`（`claim`/`markSucceeded`/`markFailed`/`failExpired`/`failRunningOnRestart`/`repairMissing`）、Task 2 的 `ReportContextLoader`/`AgentRunner.report`/`ReportValidationException`、既有 `SimulationSessionRepository.findByStatus`、`AgentCallException`、`ThreadPoolTaskScheduler`（既有 bean `simulationScheduler`）、`ApplicationRunner`
- Produces:
  - `SimulationProperties.Report`（`@ConfigurationProperties` 的一部分）：`boolean enabled = true`、`long pollMs = 1000`、`int maxConcurrent = 4`、`long initialDelayMs = 2000`；绑定 `app.simulation.report.*`
  - `ReportExecutor`（`@Component`）：`@Scheduled(fixedDelayString = "${app.simulation.report.poll-ms:1000}", initialDelayString = "${app.simulation.report.initial-delay-ms:2000}") void poll()`；`public void markReady()`；`public void wake()`（仅用于在重试入队后本进程内立即触发一次 `poll`，等价语义下允许被忽略）；内部 `dispatch()` 与 `attempt(Claimed)`
  - `ReportRecovery implements ApplicationRunner @Order(Ordered.HIGHEST_PRECEDENCE + 10)`：先 `transitions.failRunningOnRestart(now)`，再为 `FINISHED` 缺行会话 `repairMissing(...)`，最后 `executor.markReady()`；`app.simulation.report.enabled=false` 时跳过执行器就绪（只做收敛）

- [ ] **Step 1：写失败测试（在 `ReportLifecycleIT` 追加）**

```java
@Test void pendingRowIsClaimedAndBecomesSucceededWithoutAnySseSubscriber()
    // 直接造一条 PENDING 行 + 父会话 FINISHED，脚本模型（LLM_PROVIDER=scripted 或注入 scripted LlmClient）输出合法报告；
    // 调用 executor.poll() 两次（或用 Awaitility 风格的轮询等待，最多 10 秒），断言：
    // status=SUCCEEDED、attempt_no=1、content_json 非空且结构合法、finished_at 非空；
    // 期间没有任何 SSE 订阅者（证明生成不依赖连接）
@Test void invalidModelOutputFailsWithValidationCode()
    // 注入返回 roundNo 与实存轮次不匹配的 LlmClient -> FAILED/VALIDATION_FAILED，content_json 为 NULL
@Test void modelCallFailureFailsWithGenerationCode()          // LlmClient 抛 AgentCallException -> FAILED/GENERATION_FAILED
@Test void expiredAttemptIsSweptToTimeout()                   // 直接造一条 RUNNING 且 attempt_deadline_at 已过的行，poll 一次 -> FAILED/TIMEOUT
@Test void lateResultAfterTimeoutCannotOverwrite()            // 先让行被清扫为 FAILED/TIMEOUT，再用原 token 调 markSucceeded -> false 且行仍 FAILED
@Test void recoveryConvergesRunningRowsBeforeDispatch()
    // 启动前造一条 RUNNING 行；执行 ReportRecovery.run(...) -> FAILED/SERVICE_RESTARTED，且 executor 未就绪前 poll() 不领取任何行
@Test void recoveryRepairsFinishedSessionWithoutReport()
    // 删除 FINISHED 会话的报告行；Recovery 后该场存在 PENDING 行（attempt_no=0），下一次 poll 能正常领取
@Test void previousPhaseLeftoverPendingRowIsPickedUp()        // attempt_no=0 的遗留 PENDING 行可被领取并成功（阶段边界兼容）
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd verify "-Dit.test=ReportLifecycleIT" -DfailIfNoTests=false`
Expected: FAIL（`ReportExecutor`/`ReportRecovery` 不存在）。

- [ ] **Step 3：实现执行器与收敛**

要点：`poll()` 顺序固定为「未就绪直接返回 → `failExpired(now)` → `dispatch()`」。`dispatch()` 先尝试获取信号量许可（`tryAcquire`），**拿到许可后**才 `claim`；`claim` 返回 `null` 时立即释放许可，避免「领取成功却无执行资源」导致行空等超时。每次尝试在虚拟线程执行：装载上下文 → `runner.report(...)` → `markSucceeded`；异常映射固定为 `ReportValidationException → VALIDATION_FAILED`、`AgentCallException → GENERATION_FAILED`、`DataAccessException → PERSISTENCE_FAILED`、其它 `RuntimeException → GENERATION_FAILED`（日志只记异常类名与 `sessionId`/`attemptNo`）。`markSucceeded`/`markFailed` 自身抛 `DataAccessException` 时只记日志并释放许可——超时清扫会兜底，**不得**在 catch 中再尝试第二次写入。`ReportRecovery` 的缺行修复只针对 `FINISHED`，逐条 `repairMissing` 并记一条 `WARN`（含 `sessionId`，不含正文）；`INTERRUPTED`/`RUNNING` 一律不建行。`poll()` 的 `Scheduled` 线程不得阻塞在模型调用上（必须委派）。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd verify "-Dit.test=ReportLifecycleIT" -DfailIfNoTests=false`
Expected: PASS（无 Docker 时按 Task 1 Step 4 的隔离实例方式执行并如实记录）。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(report): background report executor, timeout sweep and restart convergence"
```

---

### Task 4：API-22 报告读取与 ReportView 统一装配

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/web/dto/SimulationViews.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/report/service/ReportViewMapper.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/service/SimulationStateAssembler.java`
- Create: `backend/src/main/java/com/aiscam/antiscam/report/service/ReportService.java`（本任务只实现 `view`）
- Create: `backend/src/main/java/com/aiscam/antiscam/report/web/ReportController.java`（本任务只实现 `GET`）
- Test: `backend/src/test/java/com/aiscam/antiscam/report/ReportViewAssemblyTest.java`、`ReportEventTest.java`、`ReportApiTest.java`

**Interfaces:**
- Consumes: Task 1 的 `ReportErrorCode`、`ReportStatus.canRetry()`、`ReportTransitions`（本任务为其补发布）；Task 2 的 `AgentModels.ReportContent`；既有 `SimulationRoundRepository`、`SimulationSessionRepository`、`SimulationReportRepository`、`ConfigurationSnapshots`、`TempAudioStore`、`SimulationEventHub`
- Produces:
  - `SimulationViews.ReportError(String code, String message)`；`SimulationViews.RoundAnalysis(int roundNo, String analysis)`；`SimulationViews.ReportContent(String summary, List<RoundAnalysis> roundAnalyses, String speechAnalysis, String victimBehaviorAnalysis, String outcomeAnalysis)`
  - `SimulationViews.ReportSummary` 的 `error` 字段类型由 `Object` 收紧为 `ReportError`；`SimulationViews.ReportView` 的 `content` 由 `Object` 收紧为 `ReportContent`
  - `SimulationStateAssembler.reportView(SimulationSession s) -> ReportView`（**唯一**装配点）：`unavailableReason`、`report`（含 `error`）、`outcome`、`content`、`riskSeries` 全部在此计算；`sessionState(s)` 与 `State.report` 改为复用它的结果
  - `ReportViewMapper`：`AgentModels.ReportContent -> SimulationViews.ReportContent`、`ReportErrorCode -> ReportError`、`errorCode(String) -> ReportError`（未知值抛 `ApiException.dataInconsistent`）
  - `ReportService.view(long userId, String id) -> ReportView`（`@Transactional(readOnly = true)`）：`id` 格式 400、不存在或非本人 404、其余委托 assembler
  - `GET /anti.scam/v1/simulations/{id}/report` → 200 `ReportView`（`Cache-Control: no-store`；拒绝任何查询参数）

- [ ] **Step 1：写失败测试**

`ReportViewAssemblyTest`（Mockito 单元测试，构造 `SimulationStateAssembler`）：

```java
@Test void finishedSucceededReportsContentAndErrorIsNull()
    // content_json 含 2 轮分析 -> content 非空且逐字段相等；report.error 为 null；canRetry=false
@Test void finishedFailedReportsTypedErrorAndNoContent()
    // error_code="TIMEOUT" -> report.error=={TIMEOUT,"报告生成超时，可手动重试。"}、content==null、canRetry=true
@Test void pendingAndRunningReportNoContentNoError()      // 两态均 content==null、error==null、canRetry=false、displayText=="生成中"
@Test void runningSimulationHasUnavailableReasonAndEmptySeries()
    // RUNNING -> report==null、outcome==null、unavailableReason=="SIMULATION_RUNNING"、riskSeries 为空
@Test void interruptedSimulationHasUnavailableReason()
@Test void finishedWithoutReportRowThrowsDataInconsistent()   // 500 DATA_INCONSISTENT
@Test void riskSeriesSkipsUnanalysedRoundsAndNeverFillsZero()
    // 3 轮中第 2 轮无分析 -> 序列为 [{1,25},{3,75}]，不含第 2 轮，也不含 0；
    // 顺序按 roundNo 升序，与库中 risk_score 逐项相等
@Test void unknownStoredErrorCodeIsDataInconsistent()          // error_code="WHATEVER" -> 500，不返回错误分类
```

`ReportEventTest`（Mockito 单元测试，验证 `report.updated` 只在提交后发布一次且载荷为完整视图）：

```java
@Test void claimPublishesRunningViewAfterCommit()        // claim 成功后 publishAfterCommit 恰好一次，事件名 "report.updated"、
                                                         // sessionId 与 stateVersion 正确、载荷 report.status=="RUNNING"
@Test void successPublishesSucceededViewWithContent()   // 成功后可取到 content 非空、error 为 null
@Test void failurePublishesFailedViewWithTypedError()   // FAILED 载荷 report.error.code=="TIMEOUT"
@Test void rejectedFenceDoesNotPublish()                // markSucceeded 返回 false 时不得发布（迟到结果不产生事件）
```

`ReportApiTest`（`@WebMvcTest(controllers = ReportController.class)` + `@MockitoBean ReportService`，沿用 `AuthHomeSmokeTest` 的导入与 `authentication(...)` 写法）：

```java
@Test void getReport_returnsEnvelopeWithNoStore()          // 200；data 恰含 7 个字段；Cache-Control 含 no-store
@Test void getReport_returns404ForForeignOrUnknown()       // service 抛 notFound -> 404，message=="资源不存在或不可访问"，data 为 null
@Test void getReport_returns500ForInconsistentRecord()     // service 抛 dataInconsistent -> 500 DATA_INCONSISTENT
@Test void getReport_rejectsQueryParameters()              // ?x=1 -> 400 VALIDATION_FAILED（与本项目其他 GET 端点一致）
@Test void getReport_requiresUserRole()                    // 匿名 401；ADMIN 403
@Test void getReport_malformedIdIs400()                    // "abc"/"007"/"0" -> 400，fieldErrors[0].field=="id"
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd test "-Dtest=ReportViewAssemblyTest+ReportEventTest+ReportApiTest"`
Expected: FAIL（`ReportView` 新字段、`reportView` 方法、控制器不存在，编译失败）。

- [ ] **Step 3：实现统一装配、读取服务、SSE 发布与 GET 接口**

要点：`SimulationStateAssembler.reportView` 先算 `riskSeries`（仅 `FINISHED`，`hasAnalysis()` 的轮次，升序，不补 0），再按状态分支：`RUNNING`/`INTERRUPTED` → `report=null`、`outcome=null`、`unavailableReason` 对应值；`FINISHED` → 必须取到行，否则抛 `dataInconsistent`，`content` 仅在 `SUCCEEDED` 时由 `ReportViewMapper` 解析 `content_json`（解析失败同样 `dataInconsistent`），`error` 仅在 `FAILED` 时映射。`State.report` 改为 `reportView(s).report()`，保证 `session.state` 与 `GET /report` 同源。

同时在本任务为 Task 1 的 `ReportTransitions` 四处状态变化补上 `report.updated` 发布：`claim`（→`RUNNING`）、`markSucceeded`（→`SUCCEEDED`，仅当返回 `true`）、`markFailed`（→`FAILED`，仅当返回 `true`）、`failExpired`/`failRunningOnRestart` 逐条收敛后各发布一次；载荷统一为 `assembler.reportView(session)`，`stateVersion` 取会话当前值，使用 `hub.publishAfterCommit(new SimulationEvent("report.updated", sessionId, stateVersion, view))`。拒绝的栅栏写入（返回 `false`）**不得**发布。

`ReportController` 用 `@RequestMapping("/anti.scam/v1/simulations")` + `@GetMapping("/{id}/report")`，参数用 `@PathVariable String id` 与 `@RequestParam MultiValueMap<String,String> query`，先 `PageQuery.validate(query, Set.of())`；`ReportService.view` 用 `PageQuery.id(id)` 校验格式并用 `sessions.findById` + `getUserId()` 做归属校验（两者失败都抛 `ApiException.notFound()`）。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd test "-Dtest=ReportViewAssemblyTest+ReportEventTest+ReportApiTest"`
Expected: PASS；并确认既有 `SimulationRulesTest`、`AdminRulesTest` 仍通过（`ReportView`/`ReportSummary` 类型收紧后编译通过）。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(report): unified report view assembly and report read API (API-22)"
```

---

### Task 5：API-23 报告失败重试

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/common/error/ApiException.java`（新增 `reportNotApplicable()`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/report/service/ReportService.java`（新增 `retry`）
- Modify: `backend/src/main/java/com/aiscam/antiscam/report/service/ReportTransitions.java`（新增 `retry`：锁行 → 判定 → 迁移 → 发布）
- Modify: `backend/src/main/java/com/aiscam/antiscam/report/web/ReportController.java`（新增 `POST`）
- Test: `backend/src/test/java/com/aiscam/antiscam/report/ReportApiTest.java`（追加）、`ReportLifecycleIT.java`（追加）

**Interfaces:**
- Consumes: Task 1 的 `ReportRetryRules.decide(...)`、`SimulationReport.requeue(...)`、`reports.lockById(...)`；Task 3 的 `ReportExecutor.wake()`；Task 4 的 `SimulationStateAssembler.reportView(...)` 与 `report.updated` 发布约定
- Produces:
  - `ApiException.reportNotApplicable()` → 409 / `REPORT_NOT_APPLICABLE` / 「该演绎不具备报告重试条件」/ `detail = null`
  - `record ReportRetryResult(int httpStatus, ReportView view)`
  - `ReportTransitions.retry(SimulationSession session, Instant now) -> ReportRetryResult`：**同一事务内**先 `reports.lockById(sessionId)` 得到当前行（不存在的 `FINISHED` → 抛 `dataInconsistent`），再用 `ReportRetryRules.decide(session.getStatus(), row.getStatus())` 判定；`REQUEUE` 分支对**已锁定的同一实体**调用 `row.requeue(now)`，发布 `report.updated(PENDING)` 后返回 202；其余分支返回 200 或抛出对应异常
  - `ReportService.retry(long userId, String id) -> ReportRetryResult`：`PageQuery.id` 校验 → `sessions.findById` + `getUserId()` 归属校验（失败 404）→ 委托 `transitions.retry(...)`；提交成功后调用 `executor.wake()`
  - `POST /anti.scam/v1/simulations/{id}/report/retry` → 202 或 200 `ReportView`；409 `REPORT_NOT_APPLICABLE`；500 `DATA_INCONSISTENT`；无请求体（带非空请求体 → 400 `VALIDATION_FAILED`，字段 `body`）

- [ ] **Step 1：写失败测试**

`ReportApiTest` 追加：

```java
@Test void retryReturns202WhenRequeued()               // service 返回 (202, view) -> HTTP 202 + 统一封套
@Test void retryReturns200ForExistingAttemptOrReport() // (200, view) -> HTTP 200
@Test void retryReturns409WhenNotApplicable()          // service 抛 reportNotApplicable -> 409 REPORT_NOT_APPLICABLE，
                                                       // message=="该演绎不具备报告重试条件"，data 为 null
@Test void retryRequiresCsrf()                         // 无 X-CSRF-TOKEN -> 403 CSRF_INVALID
@Test void retryRejectsARequestBody()                  // 带 "{}" -> 400 VALIDATION_FAILED，fieldErrors[0].field=="body"
@Test void retryRequiresUserRole()                     // 匿名 401；ADMIN 403
@Test void retryMalformedIdIs400()                     // "abc" -> 400，fieldErrors[0].field=="id"
```

`ReportLifecycleIT` 追加（真实库，走 `ReportService.retry`）：

```java
@Test void retryOnFailedMovesToPendingAndKeepsAttemptNumber()
    // FAILED/1 -> 202、PENDING、queued_at 刷新、attempt_no 仍为 1、taskToken/startedAt/attemptDeadlineAt/finishedAt/errorCode/contentJson 全为 NULL；
    // 随后一次 poll 领取后 attempt_no==2
@Test void retryFromFailedIsIdempotent()                 // 已是 PENDING 时再次 retry -> 200，queued_at 不被重写
@Test void retryOnSucceededReturnsOriginalContent()
    // 200；content 与首次成功完全一致；attempt_no 不变；SELECT 断言 content_json 未被改写
@Test void retryOnPendingOrRunningDoesNotCreateASecondAttempt()
    // PENDING/0 -> 200 且 attempt_no 仍为 0；RUNNING/1 -> 200 且 attempt_no 仍为 1
@Test void retryOnInterruptedOrRunningSessionIsNotApplicable()
    // INTERRUPTED 会话 -> 409 REPORT_NOT_APPLICABLE 且未插入/未修改任何报告行
@Test void retryOnFinishedSessionWithoutRowIsDataInconsistent()   // 500 DATA_INCONSISTENT，不插入行
@Test void concurrentRetriesProduceExactlyOneNewAttempt()
    // 两个线程同时对同一 FAILED 行进 retry：恰好一个 202、一个 200；attempt_no 只由后续领取增加一次
@Test void reportFailureDoesNotRevertTheSession()      // markFailed 后会话仍 FINISHED、outcome 与 ended_at 不变
```

- [ ] **Step 2：运行测试确认失败**

Run: `cd backend && .\mvnw.cmd test "-Dtest=ReportApiTest"` 与 `cd backend && .\mvnw.cmd verify "-Dit.test=ReportLifecycleIT" -DfailIfNoTests=false`
Expected: FAIL（`POST` 未实现 / `ReportService.retry` 不存在）。

- [ ] **Step 3：实现重试**

要点：`retry` 先按 `PageQuery.id` 校验格式、再 `sessions.findById` + 归属校验（失败 404），全程**不锁、不写会话**；随后在**同一事务**内 `reports.lockById(sessionId)` 拿到当前行再判定（`ReportRetryRules.decide(session.getStatus(), row.getStatus())`），这样并发重试的第二个请求会阻塞在行锁上，读到已变为 `PENDING` 的状态并返回 200，`attempt_no` 只可能被后续领取增加一次。`REQUEUE` 分支直接对已锁定的实体调用 `row.requeue(now)`（不要另起一次 `lockById`，避免重复加锁与判定漂移），提交后发布 `report.updated(PENDING)` 并在控制器返回 202 之后由 `ReportService` 调 `executor.wake()`。`NOT_APPLICABLE` 抛 `ApiException.reportNotApplicable()`；`INCONSISTENT` 抛 `dataInconsistent`。控制器用 `@RequestBody(required = false) String body` + 与 `SimulationController.rejectBody` 等价的判定（把该静态方法抽到 `common.web` 复用，**不要**在两个控制器里各写一份规则）；HTTP 状态取 `ReportRetryResult.httpStatus()`（202/200），响应体统一为 `ApiResponse.ok(view)`。

- [ ] **Step 4：运行测试确认通过**

Run: `cd backend && .\mvnw.cmd test "-Dtest=ReportApiTest"` 与 `cd backend && .\mvnw.cmd verify "-Dit.test=ReportLifecycleIT" -DfailIfNoTests=false`
Expected: 均 PASS。

- [ ] **Step 5：提交**

```bash
git add backend/src
git commit -m "feat(report): idempotent report retry with attempt fencing (API-23)"
```

---

### Task 6：前端复盘报告页

**Files:**
- Modify: `frontend/src/api/types.ts`
- Create: `frontend/src/api/report.ts`
- Create: `frontend/src/components/report/RiskChart.vue`
- Create: `frontend/src/views/ReportView.vue`
- Delete: `frontend/src/views/ReportEntryView.vue`
- Modify: `frontend/src/router/index.ts`
- Test: `frontend/src/__tests__/report.spec.ts`

**Interfaces:**
- Consumes: 既有 `http.get`/`http.post`、`ApiError`、`apiBase`；`simulationApi.state(id)`（复用，用于页头场景名与轮数）；`useAuthStore`
- Produces:
  - `types.ts` 追加：`ReportError`、`ReportContent`、`ReportView`、`ReportSummary`（`error: ReportError | null`）；`SessionStateData.reportView` 类型由 `unknown` 收紧为 `ReportView`
  - `reportApi.view(id): Promise<ReportView>`（`GET /simulations/{id}/report`）、`reportApi.retry(id): Promise<ReportView>`（`POST .../report/retry`）
  - `RiskChart`：props `{ points: { roundNo: number; score: number }[] }`；`data-testid="report-risk-chart"`、`report-risk-empty`、`report-risk-point`；内联 SVG（每个点 `score` 映射到 0—100 的纵轴，横轴按 `roundNo` 等距），不引入图表库
  - `ReportView.vue`：`data-testid="report"`、`report-outcome`、`report-outcome-reason`、`report-status`、`report-generating`、`report-summary`、`report-round-analyses`、`report-speech-analysis`、`report-victim-behavior`、`report-outcome-analysis`、`report-retry`、`report-error`、`report-interrupted`、`report-unavailable`、`report-load-error`
  - 路由：`/simulations/:id/report` → `ReportView`（`meta.role: 'USER'`，隐藏 `AppNav` 或沿用第四阶段布局约定）

- [ ] **Step 1：写失败测试 `report.spec.ts`**

```ts
it('shows generating while pending or running and polls until a terminal state', async () => {})
   // 第一次 GET 返回 PENDING，第二次返回 SUCCEEDED：断言 report-generating 先出现、随后 report-summary 出现，
   // 且轮询在终态后停止（fetch 次数稳定）
it('renders every content section from the server payload verbatim', async () => {})
   // summary、逐轮 analysis（按 roundNo 顺序且显示轮号）、speechAnalysis、victimBehaviorAnalysis、outcomeAnalysis
it('renders the authoritative outcome and its reason', async () => {})
it('shows the retry button only when canRetry is true and sends exactly one request while in flight', async () => {})
   // FAILED -> report-retry 可见；点击后按钮禁用；retry 返回 PENDING -> 回到生成中；双击不产生第二次 retry 调用
it('shows the failure message and never renders report body for a failed report', async () => {})
it('shows "不生成正式复盘" for an interrupted simulation and offers no retry', async () => {})
   // unavailableReason=SIMULATION_INTERRUPTED -> report-interrupted，无 report-retry、无 content 区块
it('shows a notice when the simulation is still running', async () => {})
it('shows a unified not-found page for a foreign or unknown record', async () => {})
   // 404 -> report-load-error，文本含「记录不存在或不可访问」
it('plots only the reported risk points and never fills missing rounds with zero', async () => {})
   // riskSeries [{1,25},{3,75}] -> 两个点、两个标签 1/3，不存在 0 分点，不存在 2 号点
it('renders a single point without a line when only one score exists', async () => {})
it('shows no radar chart and no user-ability evaluation anywhere on the page', async () => {})
   // 断言页面文本不含「雷达」「能力评分」「观看者能力」「得分榜」等字样，
   // 且不存在 svg[data-chart="radar"] / [data-testid="report-radar"] 元素（AC-18 的硬性要求）
it('keeps the risk chart values identical to the API payload', async () => {})
   // 从渲染出的 SVG 点坐标反解分数不现实，改为断言每个点的 aria-label / title 文本含「第1轮 25分」「第3轮 75分」
```

测试用 `vi.spyOn(reportApi, 'view'|'retry')` 与 `vi.spyOn(simulationApi, 'state')`，并用 `vi.useFakeTimers()` 驱动 2 秒轮询。

- [ ] **Step 2：运行测试确认失败**

Run: `cd frontend && npx vitest run src/__tests__/report.spec.ts`
Expected: FAIL（`reportApi`、`RiskChart`、`ReportView` 不存在）。

- [ ] **Step 3：实现类型、API、风险折线与报告页**

要点：进入页面先 `Promise.all([reportApi.view(id), simulationApi.state(id)])`，`state` 仅用于页头（场景名、轮数、双方姓名）与中断原因，失败不阻塞报告主体（404 时整页显示 `report-load-error`）。轮询：`PENDING`/`RUNNING` 时每 2 秒 `reportApi.view`，终态或 `unavailableReason` 非空后停止，`onUnmounted` 清理定时器，最多 60 次后停止并提示「报告仍在生成，可稍后刷新」。`FAILED` 必须整块显示 `report-error.message` 且**不渲染** `content` 区域。重试：提交中禁用按钮，`202`/`200` 都用返回视图替换本地状态，`409 REPORT_NOT_APPLICABLE` 显示提示并停止轮询。风险折线数据只来自 `riskSeries`，不得用 `state.currentRoundNo` 补齐；每个点带 `aria-label="第{roundNo}轮 {score}分"` 以便断言与无障碍。页面**不得**出现雷达图、用户能力评分或排名（AC-18），也不得出现「下载」「分享」「再生成一份」等入口；中断页提供「重新开始体验」链接到 `/configuration`。

- [ ] **Step 4：运行测试与类型检查确认通过**

Run: `cd frontend && npm.cmd test` 与 `cd frontend && npx vue-tsc --noEmit` 与 `cd frontend && npm.cmd run build`
Expected: 三条命令退出码 0；既有 `phase1.spec.ts`、`configuration.spec.ts`、`admin.spec.ts`、`simulation.spec.ts` 仍通过。

- [ ] **Step 5：提交**

```bash
git add frontend/src
git commit -m "feat(frontend): analysis report page with status polling, risk chart and retry"
```

---

### Task 7：契约校验、端到端验收与文档

**Files:**
- Modify: `tools/validate-openapi.mjs`
- Create: `tools/smoke-report.mjs`
- Modify: `tools/smoke-simulation.mjs`（S01 的报告断言随执行器变更）
- Modify: `README.md`
- Test: 复用 Task 1～6 的测试 + 冒烟脚本

**Interfaces:**
- Consumes: 全部前序产物
- Produces: 可复现的验收记录（命令与输出）、报告端到端冒烟脚本、更新后的运行说明

- [ ] **Step 1：扩展 `tools/validate-openapi.mjs`**

在既有断言后追加：`['/simulations/{id}/report','get']`、`['/simulations/{id}/report/retry','post']`；并断言 `components.schemas.ReportContent.required` 恰含 `summary`/`roundAnalyses`/`speechAnalysis`/`victimBehaviorAnalysis`/`outcomeAnalysis`、`components.schemas.ReportError.properties.code.enum` 恰为五个失败码、`components.schemas.ReportView.properties.content` 允许 null。输出文案追加 `phase 5 (2) operations present`。

Run: `node tools/validate-openapi.mjs`
Expected: 退出码 0 并打印更新后的文案。

- [ ] **Step 2：编写 `tools/smoke-report.mjs`**

以 `tools/smoke-simulation.mjs` 为模板（隔离 MySQL 8.0.45、随机管理员口令、`LLM_PROVIDER=scripted`、`TTS_PROVIDER=silent` 仅限 dev/test、独立端口如 `13311/18084`、结束清理自有进程）。断言至少覆盖：

1. 登录 USER 后开始一场演绎并推进到 `FINISHED`（沿用仿真的 SSE 驱动方式），随后 `GET /report` 最终变为 `SUCCEEDED`（轮询上限 30 秒），`content` 五段齐全、`riskSeries` 与库中 `risk_score` 一致。
2. 直接改库把该场报告置为 `FAILED/VALIDATION_FAILED`，`GET /report` 返回 `canRetry=true` 与中文说明；`POST retry` 返回 202（`report.status=PENDING`），再次轮询最终 `SUCCEEDED` 且 `attempt_no` 从 1 变 2。
3. 对 `SUCCEEDED` 报告再次 `POST retry` → 200，`attempt_no` 不变、内容不变。
4. `INTERRUPTED` 会话：`GET /report` 返回 `unavailableReason=SIMULATION_INTERRUPTED` 且 `report=null`；`POST retry` → 409 `REPORT_NOT_APPLICABLE`。
5. 另一个 USER 访问他人报告 → 404（与不存在记录的响应体逐字段一致）；ADMIN 访问 → 403；匿名 → 401；不带 CSRF 的 retry → 403 `CSRF_INVALID`。
6. 全流程断言 `Cache-Control: no-store`，且响应中不出现 `content_json`、`task_token`、`PASSWORD`、SQL 或绝对路径。

Run: `node tools/smoke-report.mjs`
Expected: 脚本无抛错退出，逐条打印通过标记；若本机缺少 MySQL 8.0.45 或 Java 21，明确报错并说明未执行，**不得**声称通过。

- [ ] **Step 3：更新 `tools/smoke-simulation.mjs` 并跑通既有回归**

把 S01 中「`report.status === 'PENDING'`」与「`PENDING/0`」的断言改为：正式结束后报告行**要么** `PENDING`、**要么**已被执行器领取为 `RUNNING`/`SUCCEEDED`；轮询最多 30 秒等待 `SUCCEEDED`，并断言 `attempt_no >= 1` 且 `content_json` 非空。其余场景（中断无报告行）断言保持不变。

Run: `node tools/smoke-simulation.mjs`
Expected: 全部断言通过（无 Docker 时同样使用其内置的隔离 MySQL）。

- [ ] **Step 4：运行全量回归**

Run:
```
cd backend && .\mvnw.cmd package
cd frontend && npm.cmd test
cd frontend && npm.cmd run build
cd .. && node tools/validate-openapi.mjs
```
Expected: 后端打包成功且全部单元/HTTP 冒烟测试通过；前端测试与生产构建通过；契约脚本退出码 0。Docker 可用时另跑 `.\mvnw.cmd verify` 覆盖 `ReportLifecycleIT` 与 `MySqlSmokeIT`。

- [ ] **Step 5：更新 `README.md`**

必须包含：本阶段两个接口与报告状态机说明（`PENDING/RUNNING→生成中`、`FAILED→生成失败且可重试一次`、成功不改写）；「页面离开不取消生成、报告不计入 600 秒」；重试的幂等规则与 409 条件；`app.simulation.report.*` 四个参数；报告页入口与中断演绎的处理；**已知限制**：单实例内存唤醒、多实例部署前需重审（数据库 DBD-10）、报告生成为单次尝试不自动重试、模型输出不合规会显示生成失败需手动重试；测试与冒烟命令（含 `node tools/smoke-report.mjs`）；本次验收的实际结果与未执行项；并在文首文档清单补上本计划链接。

- [ ] **Step 6：提交**

```bash
git add tools README.md
git commit -m "test(report): openapi checks, end-to-end report smoke and docs"
```

---

## 六、完成判据（Definition of Done）

1. `.\mvnw.cmd package` 与 `.\mvnw.cmd test` 通过；`npm.cmd test`、`npm.cmd run build`、`npx vue-tsc --noEmit` 退出码 0；`node tools/validate-openapi.mjs` 退出码 0。
2. API-22、API-23 的字段、状态码、错误码、`Cache-Control` 与 `openapi.yaml` 逐项一致；`tools/smoke-report.mjs` 全部断言通过；`tools/smoke-simulation.mjs` 更新后仍全部通过。
3. 「复审重点」五条各自至少有一个通过的测试，且测试名能直接读出它守的是什么行为。
4. `git log` 中每个 Task 恰好一次提交，提交信息可追溯到 Task。
5. 数据库结构与第四阶段完全一致（仍为 9 张业务表）：**没有**新增迁移、没有新增表或列，`simulation_report` 的 CHECK 在每条写入路径上都被满足。
6. 前四阶段契约未回归：`GET /simulations/{id}` 的既有字段不变（仅 `report.error` 由 null 变为按状态填充）、`session.state` 的 `reportView` 与 `GET /report` 同值、USER/ADMIN/匿名边界不变。
7. 报告页在「生成中／已完成／生成失败／中断」四种情形下显示真实状态；未成功保存的报告从不显示为已完成，也不展示任何正文。
8. `README.md` 的说明可被未参与开发的人在干净环境复现；未执行的验证项如实列出。

## 七、风险与阻塞项（执行者必须回报而非自行决定）

- **Docker 不可用**：`ReportLifecycleIT` 是本阶段唯一能证明「领取互斥、栅栏拒绝迟到写入、超时收敛、重启收敛、并发重试只产生一次尝试」的测试，默认依赖 Testcontainers 的 `mysql:8.0.45`；本机 Docker daemon 自 2026-10-16 起未运行。处理方式是按 README 记录的隔离 MySQL 方式运行（Task 1 Step 4），并在 README 记录「未使用 Testcontainers」；**不得**改用 H2/SQLite 宣称并发与 CHECK 行为已验证（数据库设计 §12.1 明确禁止）。
- **模型输出合规率（V-01 关联项）**：REPORT 模板要求「roundAnalyses 与实际轮次一一对应」，真实模型可能漏轮或改写结局。校验失败是**预期行为**，不得为了降低失败率而放宽 `ReportContentValidator` 或允许把不合规内容保存为 `SUCCEEDED`；若失败率明显，只允许通过新增 `agent_prompt` 版本迁移修正模板（不得修改已执行 V6 或 `prompts/v1/*.txt` 的校验和）。
- **`max_tokens` 与长报告**：`app.simulation.llm.max-tokens` 现为 800，20 轮报告可能被截断（`finish_reason=length` 已按可重试失败处理）。需实测后决定是否提高；**不得**通过裁剪轮次或省略分析来换取通过。
- **超时与调用超时的关系**：尝试预算 120 秒、单次调用超时 20 秒。正常情况下调用超时先触发（→`GENERATION_FAILED`），`attempt_deadline_at` 只是崩溃/挂起时的兜底（→`TIMEOUT`）。若实测发现模型稳定超过 20 秒，先报告并调整配置，不要改栅栏条件或延长到掩盖超时。
- **`report.updated` 载荷体积**：成功事件包含完整 `content`，单帧可达数十 KB。若代理或浏览器表现异常，先回报实测，**不要**私自改成只发摘要（那会与 `ReportView` 契约不一致）。
- **`session.state` 中 `reportView` 的变化**：本阶段后它会带上 `content` 与 `error`。这是契约要求，但会改变第四阶段那条 SSE 帧的大小与内容；若前端演绎页对 `reportView` 有假设（例如只读 `report.status`），先核对再改，**不要**为兼容旧前端而让两条路径返回不同形状。
- **遗留 `PENDING` 行**：第四阶段产生的 `PENDING/0` 行会被本阶段执行器正常领取（阶段边界，不是缺陷）。启动收敛不得假设「启动时一定没有遗留工作」。
- **单实例假设**：并发许可、唤醒、`recovered` 就绪位都是进程内状态；跨进程只依赖数据库锁与条件更新。多实例部署前必须重审（数据库 DBD-10），本阶段不做。
- **中断演绎的报告入口**：`API-23` 对中断返回 409 是正确的业务结论，不得为了「统一体验」而在中断记录上提供重试入口。
