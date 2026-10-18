# 第四阶段（AI情景演绎模块）开发计划

> **面向 AI 执行者：** 本计划的每一步均使用复选框（`- [ ]`）语法，请按顺序逐任务（Task）执行。每个 Task 结束都必须通过其验证步骤，并完成一次 git 提交后再进入下一个 Task。本计划建立在已完成的「登录注册模块 + 首页模块 + 场景画像配置模块 + 管理后台模块」代码之上，请在现有文件与现有约定上增量开发，不要重写既有模块，不要改变既有对外契约（Task 1、Task 2 中明确列出的 V1.1 基线对齐改动除外）。
>
> **开工前必须先完成「Task 0：开工检查」。第七章决策 D-1～D-4 已于 2026-10-18 由需求方确认（见 7.1），Task 0 中将其写入 README。** 任何与本计划冲突的需求文档条文，以四份 V1.1/V3.2 文档为准，并回报差异，不得自行补定业务规则。

**目标（Goal）：** 交付可运行的「AI情景演绎模块」垂直切片——普通用户在配置页点击「进入演绎」后进入演绎页；资源加载完成后点击「开始演绎」开始计时；依次观看场景介绍动画（≤15秒）与双方人物卡（合计≤8秒）；随后左右舞台逐轮演出：骗子发言→受害者回应→底部解说与五档风险节点条→5秒阅读停留→下一轮；最少5轮、最多20轮、有效时长600秒；可打开设置暂停（停表，单场累计计入≤300秒）、切换声音、继续或退出；正式结束时同事务写入唯一结局与 PENDING 报告记录并进入复盘入口；退出、刷新、生成/语音/媒体/保存故障与半轮超时按中断收敛。对应《接口文档 V1.1》API-14～API-21、API-42～API-44 共 11 个接口、11 类 SSE 事件，以及 API-13 的快照头像授权扩展。

**架构（Architecture）：** 延续 Spring Boot 模块化单体，新增与 `content`、`admin` 并列的五个包：`simulation`（会话、轮次、消息、报告记录的领域与短事务，开始/状态/回执/暂停/退出服务，编排引擎、预备区、计时与收敛）、`agent`（三角色上下文装配、Prompt 渲染、模型调用、输出解析与业务校验）、`media`（音色映射、分句、CosyVoice 网关客户端、临时音频）、`timing`（点击计时采样与校准）、`sse`（事件中心）。编排原则为**落库与发布严格顺序、计算允许流水线提前**：候选内容只在服务端内存预备区中提前生成，轮到其阶段才以短事务保存，提交后才经 SSE 发布；前端以单一「演出控制器」驱动开场、立绘两帧嘴部开合、气泡、分句逐字、动作说明、解说与风险、阅读停留和暂停，三类呈现回执是唯一能推进演绎的写操作。

**技术栈（Tech Stack）：** 沿用既有锁定版本：Java 21（Corretto 21.0.8）+ Spring Boot 3.5.16（web / data-jpa / security / validation / flyway-mysql）+ MySQL 8.0.45 + Flyway；前端 Vue 3.5.44 + Vue Router 4.5.1 + Pinia 3.0.3 + Vite 6.4.4 + TypeScript 5.9.3 + Vitest 5.0.5 + Vue Test Utils 2.4.6 + jsdom 26.1.0；契约校验 `@apidevtools/swagger-parser` 12.0.0。模型调用使用 Spring 自带 `RestClient`（OpenAI 兼容 Chat Completions，优先验证 DeepSeek Flash，JSON 输出模式）；SSE 使用 Spring MVC `SseEmitter`；JSON 用既有 Jackson；Prompt 渲染用自写 `{{var}}` 替换。**后端与前端均不引入新的运行期依赖**（不使用 Lombok、MapStruct、模板引擎、音频库、WebSocket、Redis、消息中间件、前端动画/状态库）。语音合成为**独立部署**的 CosyVoice + 本计划新增的 Python 网关 `tools/tts-gateway/`（只在 TTS 主机上安装其 Python 依赖与 ffmpeg，不进入 Java/前端依赖树）。

**Spec（本计划实现的规格文档，执行者必须同时阅读）：**
- `docs/AI反诈情景推演平台_PRD_V3_2_正式开发基线版.md`（§3.2～3.4、§6.1～6.2 流程与结局表、§8.4 FR-SIM-01～08、§9 AGT-01～04、§10 全章：BR-C01～04、BR-P01～06、BR-P12、BR-P13、§11.3 音色映射与共用约束、§11.4～11.5 BR-P08/BR-P10、§12.2 PC-P04 媒体最低要求与量化指标、§13.1 AC-12～16、AC-28～30、§13.2 AP-01～05、AP-10、AP-11、§14 全章演出层）
- `docs/AI反诈情景推演平台_系统架构设计文档_V1.1.md`（§2.3 不变量、§5 前端与媒体、§6 会话与任务、§7 三Agent、§8 风险与结局、§9.1～9.4 流程、§10 保存一致性、§11 状态与时间、§13.2～13.4 权限与临时媒体、§15.2 异常矩阵、§15.3 V-01～V-11、§16 全章）
- `docs/AI反诈情景推演平台_数据库设计文档_V1.1.md`（§1.3 不变量、§5.3～5.9 字段、§6 约束与索引、§7 全章（7.1 开始事务、7.2 阶段、7.3 短事务、7.4 证据、7.5 正式结束、7.6 中断、7.7 计时、7.9 暂停、7.10 最低轮数）、§8 Prompt 版本与 runtime_snapshot、§9.1～9.2 报告行创建、§10.1～10.3 权限快照资源、§12.1 DBV-02～09、DBV-13、DBV-16、DBV-18～20）
- `docs/AI反诈情景推演平台_接口文档_V1.1.md`（§1.2、§2 全章、§3 状态不变量、§4 回执矩阵与时序、§5 SSE 全章、§6 的 API-13～API-21、API-42～API-44、§7 字段字典、§8 错误码、§9.1 计时协议与 `CONNECTION_LOST` 规则、§9.2 TBD 台账、§10.2 联调场景 API-02～API-12、API-17～API-20）
- `openapi.yaml`（Task 1 同步到 V1.1 后作为**字段名、约束、状态码与错误码的最终依据**）
- `README.md` 与 `coding plan/2026-10-17-phase3-admin-console.md`（前三阶段的实际交付、测试命令、隔离冒烟脚本写法、`AVATAR_STORE` 约定）

---

## 一、本阶段范围与模块边界

### 1.1 本阶段实现的接口与事件（唯一交付范围）

| 编号 | 方法 | 完整路径 | 用途 | 权限 |
|---|---|---|---|---|
| API-42 | GET | `/anti.scam/v1/timing/samples` | 点击计时采样（演绎页加载期间） | USER |
| API-14 | POST | `/anti.scam/v1/simulations` | 开始演绎（点击「开始演绎」时） | USER |
| API-15 | GET | `/anti.scam/v1/simulations/{id}` | 当前演绎状态 | USER（本人） |
| API-16 | GET | `/anti.scam/v1/simulations/{id}/events` | 订阅演绎事件（SSE） | USER（本人） |
| API-17 | POST | `/anti.scam/v1/simulations/{id}/opening/presented` | 开场呈现回执 | USER（本人） |
| API-18 | POST | `/anti.scam/v1/simulations/{id}/messages/{messageId}/presentation` | 消息呈现回执 | USER（本人） |
| API-19 | POST | `/anti.scam/v1/simulations/{id}/rounds/{roundNo}/analysis/presented` | 分析呈现回执 | USER（本人） |
| API-20 | POST | `/anti.scam/v1/simulations/{id}/exit` | 主动退出 | USER（本人） |
| API-21 | GET | `/anti.scam/v1/simulations/{id}/messages/{messageId}/audio` | 临时语音（`audio/mpeg`） | USER（本人） |
| API-43 | POST | `/anti.scam/v1/simulations/{id}/pause` | 设置暂停 | USER（本人） |
| API-44 | POST | `/anti.scam/v1/simulations/{id}/resume` | 继续演绎 | USER（本人） |
| API-13（扩展） | GET | `/anti.scam/v1/avatars/{key}` | 增加「被本人任一演绎快照引用」的授权分支 | USER |

SSE 事件（全部 11 类）：`session.started`、`round.started`、`message.saved`、`analysis.saved`、`round.reading`、`session.finished`、`session.interrupted`、`session.state`、`session.paused`、`session.resumed`、`report.updated`。其中 `report.updated` 本阶段**只定义发布通道与载荷类型，不会被触发**（报告任务属于下一阶段）；`session.state` 中的 `reportView` 按 ReportView 结构返回 `report` 摘要（FINISHED 时为 PENDING「生成中」）、`content=null` 与真实 `riskSeries`。

同时交付：
- V1.1 基线对齐：`openapi.yaml` 同步到接口文档 V1.1（44 项操作、11 类事件）；**移除第三阶段新增的画像永久删除功能**（D-1，已确认）；画像张嘴立绘 `speakingAvatar` / `speakingAvatarKey`（迁移、初始素材、用户端与后台 DTO、后台上传表单）。
- 数据库迁移 V4（张嘴立绘列）、V5（演绎五表：`simulation_session`、`simulation_round`、`simulation_message`、`simulation_report`、`agent_prompt`）、V6（四类 Prompt 第 1 版种子）。
- 前端：配置页「进入演绎」改造、`/simulation` 演绎页（加载→开始→场景介绍→人物卡→舞台→设置→终态）、正式结束后跳转的 `/simulations/:id/report` 复盘入口说明页（显示真实结局与「报告生成中」，报告内容由下一阶段实现）。
- `tools/tts-gateway/`（CosyVoice 网关）、`tools/smoke-simulation.ps1` + `tools/simulation-driver.mjs`（隔离实例端到端验收）、README 更新与真实模型/语音联调记录。

### 1.2 本阶段明确不做（禁止提前实现、禁止预留占位接口）

- **API-22～API-25**（报告读取、报告重试、历史列表、历史详情）与报告生成后台任务、报告领取/超时/迟到隔离：属于第五阶段「分析复盘 + 历史记录」。本阶段只在正式结束事务中**创建 PENDING 报告行**（数据库设计 §1.3 不变量 7 要求同事务），不领取、不调用 REPORT Prompt。`/history` 仍为既有说明页。
- 中断后续演、倍速、回放、音视频保存、用户参与对话的任何输入框；顶部除设置按钮外不放任何控制项（不显示倒计时、轮数控制、跳过按钮）。
- 不新增第 10 张业务表：不建事件日志表、Outbox 表、回执表、暂停历史表、模型调用日志表、任务表。预备区只存在于内存。
- 不提供 Prompt 管理页面或接口；Prompt 切换只走迁移/受控脚本。
- 不实现情绪表情、骨骼动画、音素级口型、字级强制对齐、多模型路由、摘要记忆服务、RAG。
- 不为「满足 600 秒」新增产品规则：不得提前提交「结局未定」、不得附加宽限、不得强制每场 20 轮、不得用固定倍速或更短的固定停留。
- 不在生产配置中启用 `scripted`（脚本模型）或 `silent`（静音合成）提供者；二者只用于 `dev`/`test` 配置与隔离冒烟，生产启动检查必须拒绝。

---

## 二、全局约束（Global Constraints，所有任务默认包含）

以下数值与规则来自四份规格文档，**必须逐字实现，不得「优化」**：

1. **轮数**：一轮 =「骗子一次发言或行动 + 受害者一次回应」，解说不计轮；最少 5 轮、最多 20 轮；禁止建立第 21 轮。第 1～4 轮不得形成 `GOAL_ACHIEVED`/`LOSS_STOPPED`/`NOT_DEFRAUDED`（BR-P12）；第 1～4 轮只可能因时限（完整轮位置 `UNDETERMINED`/`TIME_LIMIT`，或半轮 `TIME_LIMIT` 中断）、故障或退出而终止。
2. **最低轮数预检**：第 1～4 轮，受害者候选回应必须先在预备区由解说 Agent 生成候选分析；候选分析给出明确结局（`candidateOutcome≠null`）时，丢弃候选回应与候选分析，带强化约束重新生成受害者回应；**单轮重新生成不超过 2 次**，仍不合规按 `DIALOGUE_FAILED` 中断；重新生成耗时计入有效时长；被接受回应对应的候选分析即为该轮保存的分析（解说如实判定，不为凑轮改判）。
3. **时长**：`startedAt` = 校准后的点击「开始演绎」时刻（不是请求到达时刻，不是建行时刻）；初始 `deadline_at = started_at + 600 秒`；只在「继续演绎」事务中按本次计入时长顺延；`本次计入 = max(0, min(now − paused_at, 300000 − paused_total_ms))` 毫秒；`paused_total_ms ∈ [0, 300000]`；暂停中的有效截止 = `deadline_at + min(now − paused_at, 300000 − paused_total_ms)`；累计计入达到 300 秒后即使仍在暂停也按实时到期。切换标签页不暂停、不中断。点击前的资源加载不计时；点击后的开场、补充加载、生成、保存、语音、动画、解说、停留、等待全部计时。
4. **阅读停留**：分析呈现确认后 `reading_until = min(确认时间 + 5 秒, 有效截止时间)`；继续演绎时若 `reading_until` 晚于 `paused_at` 则同步顺延。停留到期后由**服务端**建立下一轮并发布 `round.started`，客户端不发任何推进请求。
5. **完整轮结束位置**：当前轮 `phase=COMPLETE` 且双方消息 `COMPLETED`、分析 `analysis_presented_at` 非空。只有在此位置才检查正式结束。到达上限时：已有明确候选结局 → 采用该结局；第 20 轮无结局 → `UNDETERMINED`+`ROUND_LIMIT`（同时到时记 `BOTH`）；未到 20 轮但到时且位于完整轮位置 → `UNDETERMINED`+`TIME_LIMIT`；不在完整轮位置到时 → `INTERRUPTED`+`TIME_LIMIT`。
6. **结局优先级**：`GOAL_ACHIEVED`（同轮随后停止也保留）→ `LOSS_STOPPED`（已实际受骗且明确停止）→ `NOT_DEFRAUDED`（无实际受骗且明确停止）→ 无。风险 0/100 不是结束阈值；「考虑一下/先等等/去核实」不是停止；骗子单方面宣称不能作为受害者行为证据。
7. **三场景关键目标（固定，不落库、不可后台编辑）**：`TASK_REBATE` = 已完成首笔垫付后又实际完成一次追加投入；`FAKE_INVESTMENT` = 已投入投资款后又实际支付一次提现/解冻等额外费用；`IMPERSONATION_LOAN` = 未通过独立渠道核实身份就实际完成借款转账。无金额门槛；承诺/准备不算完成。`focus` 映射：青年群体/中年群体/老年群体。
8. **风险**：只允许 0/25/50/75/100；开场与未评分为 `null`（前端显示「尚未评分」），不补 0；每轮重新定档，不累加、不插值、不平滑；非法分值按 `ANALYSIS_FAILED` 路径（先在预算内透明重试），**不得就近取整**。
9. **保存与发布**：先短事务提交业务事实，再经 SSE 发布；`message.saved` 只在正文已保存、音频已就绪且 `PRESENTING` 已保存后发布；预备区内容在轮到前**不落库、不下发**；正式结束/中断/版本不符/重启时丢弃。外部调用（模型、TTS）不得放在数据库事务内；所有写入先锁会话行，按「会话 → 轮次 → 消息」顺序访问；外部调用前捕获 `state_version`，返回后重新核对，不符或已终态则丢弃结果。
10. **终态**：只允许 `RUNNING→FINISHED` 与 `RUNNING→INTERRUPTED`，不可逆；正式结束与 `simulation_report`（`status=PENDING`、`attempt_no=0`、`queued_at=now`）同事务；中断不建报告行；暂停中发生终态转换时同事务结算计入时长并清空 `paused_at`。
11. **中断码（服务端核定）**：`USER_EXIT`、`DIALOGUE_FAILED`、`ANALYSIS_FAILED`、`TTS_FAILED`、`MEDIA_FAILED`、`PERSISTENCE_FAILED`、`TIME_LIMIT`、`CONNECTION_LOST`、`SERVICE_RESTARTED`；`interruption_detail` 由服务端按码填充固定中文文案，不接受客户端文本，不含堆栈与供应商原始错误。
12. **回执错误码作用域**：开场 FAILED 仅 `TEXT_RENDER_FAILED`/`AVATAR_LOAD_FAILED`/`ANIMATION_FAILED`；消息 FAILED 仅 `TEXT_RENDER_FAILED`/`ANIMATION_FAILED`/`AUDIO_LOAD_FAILED`/`AUDIO_PLAYBACK_FAILED`；分析 FAILED 仅 `TEXT_RENDER_FAILED`；越界 400。COMPLETED 不得带 `errorCode`，FAILED 必须带。回执结果 `effect ∈ {APPLIED, ALREADY_APPLIED, IGNORED_TERMINAL}`；相反结果 409 `PRESENTATION_CONFLICT`；阶段不符/未来轮/非当前消息 409 `STAGE_CONFLICT`；错误归属或父子错配 404；终态新回执 200 `IGNORED_TERMINAL` 且不补写证据。
13. **消息文本**：`content` 只放台词（气泡与 TTS 同源）；`action_text` 为动作说明（舞台展示、不朗读，可作证据）；`internal_note`（骗子内部意图/受害者内部心理）只供解说与报告读取，**不进入对方角色上下文，不出现在任何用户 DTO、SSE、日志**。台词目标约 50 字以内、解说每项与风险依据约 40 字以内（生成约束，不截断已保存内容）。
14. **音色**：按画像性别与年龄段（青年 18—34、中年 35—59、老年 60+）映射预设音色；每类主选+备选；双方映射到同一音色时受害者改用备选；性别文案不是「男」「女」时按年龄段取中性音色；开始时固定在 `runtime_snapshot.media_config`，同场不变；用户与管理员都不选音色。
15. **音频与分句**：服务端按句末与逗号类标点切分分句，逐句合成后拼接为单个 MP3（`audio/mpeg`），记录每句 `startMs/endMs/charStart/charEnd`（字符下标按 Unicode 码点，`charEnd` 不含）与总时长；分句须按顺序覆盖 `content` 全部字符且时间递增；分句时间与音频只存临时目录，终态释放，不入库。`audioUrl/audioDurationMs/segments` 仅对 RUNNING 中当前允许呈现（`PRESENTING`）的消息非空，其余为 `null`。
16. **SSE**：同源 Cookie、原生 EventSource，不带 URL token、Authorization 或 CSRF 头；帧含 `id:`、`event:`、`data:`；`data` 为 `{eventId, sessionId, stateVersion(字符串), data}`；连接建立/重连先发 `session.state`；心跳用注释帧；响应 `Cache-Control: no-store`、`X-Accel-Buffering: no`；不承诺按 `Last-Event-ID` 补发；登出/Session 失效立即关闭对应流；最后一轮先发 `session.finished` 再发 `round.reading(nextAction=REPORT)`；暂停期间不发布 `round.started` 与新的 `message.saved`。
17. **传输约定**：前缀 `/anti.scam/v1`；JSON `lowerCamelCase`；成功 `code="OK"`；封套四字段；ID 与 `stateVersion` 为十进制字符串；时间为 UTC `yyyy-MM-ddTHH:mm:ss.SSSZ`；请求未知字段 400 `UNKNOWN_FIELD`（尤其 `outcome`、`riskScore`、`userId`、`role` 等越权字段）；未登录 401、角色不符 403、跨用户或不存在统一 404（正文一致）；写操作需 Cookie + `X-CSRF-TOKEN`；私有 JSON、SSE、音频 `Cache-Control: no-store`。
18. **开始请求**：同一 `startRequestId` 同含义重发返回原场 200（即使已终态），不同配置 409 `IDEMPOTENCY_CONFLICT`；已有 RUNNING 返回 409 `ACTIVE_SIMULATION_EXISTS` 且 `data.activeSessionId` 为本人活动会话；所选画像停用 409 `PROFILE_UNAVAILABLE`（不自动替换）；`timing` 不可接受 400 `VALIDATION_FAILED`（字段级说明），**不得回退为按接收时间起算**；无法生成有效样本 503 `TIMING_PROTOCOL_NOT_READY`；新建成功 201。
19. **权限**：ADMIN 访问 `/simulations/**`、`/timing/**` 一律 403（`SecurityConfig` 已有 `hasRole("USER")` 规则，保持不变）；归属校验沿 `message→round→session→user`，路径中的 `id`、`messageId`、`roundNo` 必须指向同一演绎。
20. **画像立绘（V1.1）**：每个画像两张同像素尺寸静态 JPG/PNG：闭嘴图 `avatar_key`（兼卡片头像）与张嘴图 `speaking_avatar_key`；单张 ≤5MB；两键均为不可覆盖的内容寻址资源；画像保存时两张都必须存在且尺寸一致，否则 400 `VALIDATION_FAILED`（字段 `speakingAvatarKey`）。

---

## 三、复审重点（Review Focus）

代码复审时逐条核对，每条至少有一个测试名能直接读出它守护的行为：

1. **先保存再发布**：任何 SSE 事件发布前，对应事实已提交；`message.saved` 前 `presentation_status=PRESENTING` 已提交；预备区对象永远不会出现在 DTO、SSE 或数据库中（`PreparedContentNeverPublishedTest`）。
2. **唯一终态与迟到隔离**：正式结束与中断竞争只有一个成功；外部调用返回时 `state_version` 不符或已终态，结果被丢弃且不写库（`LateAgentResultIsDiscardedTest`、`FinishAndInterruptRaceIT`）。
3. **时间口径**：`startedAt` 来自校准点击值；重发不重置；暂停计入上限 300 秒；完整轮到时 → `UNDETERMINED/TIME_LIMIT`，半轮到时 → `INTERRUPTED/TIME_LIMIT`；第 21 轮永不建立（`DeadlineCalculatorTest`、`RoundLimitTest`）。
4. **BR-P12**：第 1～4 轮无正式结局；不合规候选不保存不展示；第 3 次仍不合规 → `DIALOGUE_FAILED`；数据库 CHECK 兜底（`MinimumRoundsPrecheckTest`、DBV-19 冒烟断言）。
5. **信息隔离**：`internal_note` 不出现在任何用户响应；受害者上下文不含骗子内部意图与解说结论，骗子上下文不含受害者内部心理与解说结论（`AgentContextIsolationTest`、`InternalNoteNeverExposedTest`）。
6. **权限**：他人会话的状态、SSE、回执、音频、暂停、退出均 404；ADMIN 全部 403；快照头像只对本人开放（`SimulationPermissionTest`）。
7. **前端单一时钟**：说话期间逐字进度只由 `audio.currentTime` 与分句时间计算；静音不改变时间轴；暂停冻结一切并在继续后从原位置恢复、不重播（`performance-director.spec.ts`）。

---

## 四、文件结构（先定边界，再拆任务）

```
AI-anti-scrm-Carl\
├── coding plan\2026-10-18-phase4-ai-simulation.md               ← 本计划
├── openapi.yaml                                                 ← 修改（Task 1）：同步接口文档 V1.1
├── tools\
│   ├── validate-openapi.mjs                                     ← 修改：44 项操作 + V1.1 Schema 断言
│   ├── generate-avatars.py                                      ← 修改：为五个初始画像生成张嘴帧
│   ├── tts-gateway\                                             ← 新增：CosyVoice 网关（Python，仅 TTS 主机）
│   │   ├── app.py / requirements.txt / voices.example.json / README.md
│   ├── smoke-simulation.ps1                                     ← 新增：隔离 MySQL + 脚本模型 + 静音合成的端到端验收
│   └── simulation-driver.mjs                                    ← 新增：Node 22 驱动 HTTP + SSE 场景
├── backend\src\main\
│   ├── java\com\aiscam\antiscam\
│   │   ├── common\error\ApiException.java                       ← 修改：演绎相关错误工厂
│   │   ├── config\SecurityConfig.java                           ← 修改：仅放行 ASYNC/ERROR 派发（SSE），规则不变
│   │   ├── config\SimulationProperties.java                     ← 新增：app.simulation.* 绑定与生产防护
│   │   ├── config\SimulationExecutorsConfig.java                ← 新增：编排/外部调用/调度三类执行资源
│   │   ├── content\…                                            ← 修改（Task 2）：speakingAvatarKey、尺寸校验、快照头像授权
│   │   ├── admin\…                                              ← 修改（Task 2）：写入请求与后台视图增加张嘴立绘
│   │   ├── timing\                                              ← 新增（Task 4）
│   │   │   ├── TimingSampleStore.java / TimingCalibrator.java / CalibratedStart.java
│   │   │   └── web\TimingController.java / web\dto\TimingSampleView.java
│   │   ├── simulation\
│   │   │   ├── domain\                                          ← 新增（Task 3）：五个实体、枚举、仓库
│   │   │   ├── snapshot\ConfigurationSnapshots.java / RuntimeSnapshot.java
│   │   │   ├── rules\FactCode.java / ScenarioGoalRules.java / OutcomeRules.java / DeadlineCalculator.java
│   │   │   ├── service\SimulationStartService.java              ← API-14
│   │   │   ├── service\SimulationQueryService.java / SimulationStateAssembler.java   ← API-15、SSE 快照
│   │   │   ├── service\SimulationTransitions.java               ← 全部短事务（建轮、存消息、媒体就绪、回执、存分析、结束、中断、暂停、继续）
│   │   │   ├── service\PresentationReceiptService.java          ← API-17/18/19
│   │   │   ├── service\PauseService.java / ExitService.java     ← API-43/44、API-20
│   │   │   ├── engine\SimulationOrchestrator.java               ← 编排入口（每场串行）
│   │   │   ├── engine\SessionLanes.java                         ← 每场串行执行通道
│   │   │   ├── engine\PreparationArea.java / PreparedTurn.java / PreparedAnalysis.java
│   │   │   ├── engine\RoundScheduler.java                       ← 阅读停留到期、有效截止到期
│   │   │   ├── engine\ConnectionMonitor.java                    ← CONNECTION_LOST 三条件
│   │   │   ├── engine\SimulationRecovery.java                   ← 启动收敛 SERVICE_RESTARTED
│   │   │   └── web\SimulationController.java / AudioController.java / web\dto\*.java
│   │   ├── agent\
│   │   │   ├── LlmClient.java / DeepSeekLlmClient.java / ScriptedLlmClient.java
│   │   │   ├── PromptRenderer.java / AgentContextAssembler.java / AgentRunner.java
│   │   │   ├── ScammerTurn.java / VictimTurn.java / NarratorJudgement.java / EvidenceItem.java
│   │   │   └── AgentOutputParser.java / AgentOutputValidator.java / EvidenceValidator.java
│   │   ├── media\
│   │   │   ├── VoiceCatalog.java / VoiceMapper.java / SentenceSegmenter.java
│   │   │   ├── TtsClient.java / CosyVoiceGatewayClient.java / SilentTtsClient.java / SynthesizedAudio.java
│   │   │   └── TempAudioStore.java
│   │   └── sse\SimulationEventHub.java / SimulationEvent.java / AfterCommitPublisher.java
│   └── resources\
│       ├── application.yml / application-dev.yml                ← 修改：app.simulation.*、mvc 异步超时
│       ├── db\migration\V4__add_speaking_avatar_key.sql         ← 新增（Task 2）
│       ├── db\migration\V5__create_simulation_tables.sql        ← 新增（Task 3）
│       ├── prompts\v1\scammer.txt / victim.txt / narrator.txt / report.txt   ← 新增（Task 3/6）
│       └── simulation\scripted\*.json                           ← 新增：脚本模型剧本（仅 scripted 提供者读取）
├── backend\src\main\java\db\migration\V6__seed_agent_prompts.java ← 新增（Task 3）：Flyway Java 迁移，读取 prompts\v1\*.txt
├── backend\src\test\java\com\aiscam\antiscam\
│   ├── timing\TimingCalibratorTest.java
│   ├── simulation\DeadlineCalculatorTest.java / OutcomeRulesTest.java / ScenarioGoalRulesTest.java
│   ├── simulation\SimulationStartApiTest.java / SimulationStateAssemblerTest.java / PresentationReceiptRulesTest.java
│   ├── simulation\OrchestratorFlowTest.java / MinimumRoundsPrecheckTest.java / LateAgentResultIsDiscardedTest.java
│   ├── simulation\PauseResumeTest.java / SimulationPermissionTest.java / InternalNoteNeverExposedTest.java
│   ├── agent\AgentOutputValidatorTest.java / EvidenceValidatorTest.java / AgentContextIsolationTest.java / PromptRendererTest.java
│   ├── media\SentenceSegmenterTest.java / VoiceMapperTest.java / SilentTtsClientTest.java
│   └── sse\SimulationEventHubTest.java
└── frontend\src\
    ├── api\types.ts / http.ts                                   ← 修改：演绎类型、keepalive 退出
    ├── api\simulation.ts / timing.ts                            ← 新增：唯一演绎请求入口
    ├── simulation\                                              ← 新增：纯 TS 逻辑（可单测）
    │   ├── performanceDirector.ts      单一时钟演出状态机
    │   ├── eventInbox.ts               SSE 去重、按对象合并、版本防回退
    │   ├── segmentReveal.ts            分句逐字进度计算
    │   ├── timingSampler.ts            API-42 采样与过期前刷新
    │   ├── audioTrack.ts               HTMLAudioElement 封装（静音同时间轴、暂停/恢复）
    │   ├── assetPreloader.ts           四张立绘预加载与校验
    │   └── liveGuard.ts                刷新识别、pagehide keepalive 退出、路由离开确认
    ├── components\simulation\
    │   ├── OpeningScene.vue + scenes\TaskRebateScene.vue / FakeInvestmentScene.vue / ImpersonationLoanScene.vue
    │   ├── CharacterCards.vue / SimulationStage.vue / StageCharacter.vue / SpeechBubble.vue
    │   ├── NarrationPanel.vue / RiskNodeBar.vue / SettingsOverlay.vue / WaitingHint.vue / TerminalNotice.vue
    ├── views\SimulationView.vue                                  ← 新增：/simulation
    ├── views\ReportEntryView.vue                                 ← 新增：/simulations/:id/report（本阶段说明页）
    ├── views\ConfigurationView.vue                               ← 修改：「进入演绎」
    ├── components\ProfileFormFields.vue / AvatarUploader.vue     ← 修改（Task 2）：两张立绘
    ├── router\index.ts                                           ← 修改：两条 USER 路由
    └── __tests__\simulation-api.spec.ts / timing-sampler.spec.ts / segment-reveal.spec.ts /
                  event-inbox.spec.ts / performance-director.spec.ts / simulation-view.spec.ts /
                  settings-overlay.spec.ts / configuration.spec.ts（修改）/ admin.spec.ts（修改）
```

**结构约束：**
- 依赖方向：`simulation` → `agent`、`media`、`timing`、`sse`、`content`（只读实体与 `AvatarStore`）；`agent`、`media`、`timing`、`sse` 不得依赖 `simulation.service`/`engine`；`content`、`admin` 不得依赖 `simulation`（Task 2 的快照头像授权通过 `content` 定义的 `SnapshotAvatarReferences` 接口反向注入实现，实现类放在 `simulation`）。
- **所有对数据库演绎表的写操作只出现在 `SimulationTransitions`**（及 Task 3 的迁移）；编排、回执、暂停、退出服务只调用它；它的每个公共方法就是数据库设计 §7.3 表中的一个短事务，方法内禁止任何外部调用或等待。
- 编排引擎与预备区是**唯一**调用 `agent`、`media` 的地方；控制器不得直接调用模型或 TTS。
- DTO 只做映射：任何 DTO 构造器都不接收 `internal_note`；`SimulationStateAssembler` 是 SimulationState 的唯一组装点（HTTP 响应与 SSE 共用）。
- 前端：`api/types.ts` 是唯一类型来源，`api/simulation.ts` 是唯一演绎请求入口，组件不得直接 `fetch`/`new EventSource`；`simulation/*.ts` 不引用 Vue，可在 Vitest 中纯逻辑测试；演绎页状态留在视图与其 composable 内，不新增 Pinia store。

---

## 五、任务清单

### Task 0：开工检查（不提交代码）

- [ ] **Step 1：确认基线可构建。** `cd backend && .\mvnw.cmd package`；`cd frontend && npm.cmd ci && npm.cmd test && npm.cmd run build`；`node tools/validate-openapi.mjs`。全部通过后再开始；失败则先回报，不在本阶段顺手修。
- [ ] **Step 2：确认文档版本。** `docs/` 下四份 V1.1/V3.2 文档即本计划依据；README 顶部链接仍指向 V1.0/V3.1，Task 15 统一更新。
- [ ] **Step 3：把第七章 7.1 中已确认的决策 D-1～D-4**（2026-10-18 需求方确认）原样写入 README「第四阶段决策记录」小节。
- [ ] **Step 4：准备外部依赖。** 申请模型 API Key（环境变量 `LLM_API_KEY`，只放 `.env`，已在 `.gitignore`）；确认 CosyVoice 运行主机（开发期本机或 GPU 云主机）与参考音色素材来源（D-3：可自行生成或从网上获取，记录来源）。外部依赖未就绪不阻塞 Task 1～14（全部可用 `scripted`/`silent` 提供者完成），只阻塞 Task 15。
- [ ] **Step 5：新建分支** `git checkout -b phase4-simulation`。

---

### Task 1：OpenAPI 同步到接口文档 V1.1（契约先行）

**Files:**
- Modify: `openapi.yaml`
- Modify: `tools/validate-openapi.mjs`

**Interfaces:**
- Consumes: `docs/AI反诈情景推演平台_接口文档_V1.1.md` 第 5～8 章（以第 7 章字典为准；接口文档 §10.1 已声明 V1.1 新增内容尚未同步到 yaml）
- Produces（yaml 中必须新增/修改）：
  - 路径：`POST /simulations/{id}/pause`（`pauseSimulation`）、`POST /simulations/{id}/resume`（`resumeSimulation`），响应 `PauseResultResponse`，错误码按 API-43/44 表。
  - Schema 新增：`AudioSegment`、`PauseResult`、`PauseResultResponse`、`SessionPausedEvent`、`SessionResumedEvent`（加入 `x-event-schemas`，合计 11 类）。
  - Schema 修改：`SimulationState` 增加必需字段 `nextAction`（若 V1.0 yaml 缺失）、`paused`、`pausedAt`、`pausedCreditMs`（0～300000），`deadlineAt` 描述改为有效截止；`LiveMessage` 增加 `actionText`、`audioDurationMs`（≥1 或 null）、`segments`（`AudioSegment[]` 或 null）；`HistoryMessage` 增加 `actionText`（历史不含音频与内部意图）；`ScammerProfile`/`VictimProfile`/`AdminScammerProfile`/`AdminVictimProfile` 增加必需 `speakingAvatar: AvatarRef`；`ScammerWrite`/`VictimWrite`/`ScammerUpdate`/`VictimUpdate` 增加必需 `speakingAvatarKey: AvatarKey`。
  - 描述修改：API-14（演绎页点击时发出）、API-17（开场回执覆盖场景介绍与人物卡）、API-18（分句逐字、动作说明、嘴部动画）、API-42（演绎页加载期间采样）。
  - **删除**第三阶段新增的 `DELETE /admin/scammer-profiles/{id}` 与 `DELETE /admin/victim-profiles/{id}` 两条路径及仅被它们使用的 Schema/示例（D-1 已确认去掉删除功能，与 PRD §5.3、BR-P09「不永久删除」一致）。
  - `info.version` 改为 `1.1.0`，`info.description` 改为「44项正式HTTP操作、11类SSE事件」。

- [ ] **Step 1：先写失败的契约断言。** 在 `validate-openapi.mjs` 路径列表追加 API-14～API-25、API-42～API-44 全部路径/方法；并追加：

```js
const s = document.components.schemas
for (const f of ['paused', 'pausedAt', 'pausedCreditMs', 'nextAction']) assert.ok(s.SimulationState.required.includes(f), `SimulationState.${f}`)
assert.equal(s.SimulationState.properties.pausedCreditMs.maximum, 300000)
for (const f of ['actionText', 'audioDurationMs', 'segments']) assert.ok(s.LiveMessage.required.includes(f), `LiveMessage.${f}`)
for (const f of ['startMs', 'endMs', 'charStart', 'charEnd']) assert.ok(s.AudioSegment.required.includes(f))
for (const n of ['ScammerProfile', 'VictimProfile', 'AdminScammerProfile', 'AdminVictimProfile']) assert.ok(s[n].required.includes('speakingAvatar'), n)
for (const n of ['ScammerWrite', 'VictimWrite']) assert.ok(s[n].required.includes('speakingAvatarKey'), n)
assert.ok(!('internalNote' in s.LiveMessage.properties) && !('internalNote' in s.HistoryMessage.properties))
assert.equal(Object.keys(document['x-event-schemas'] ?? {}).length, 11)
for (const p of ['/admin/scammer-profiles/{id}', '/admin/victim-profiles/{id}']) assert.ok(!document.paths[p]?.delete, `DELETE ${p} must be removed (D-1)`)
```

（`x-event-schemas` 的实际位置以现有 yaml 为准，先 `grep -n x-event-schemas openapi.yaml` 确认后再写断言。）
- [ ] **Step 2：运行确认失败** `node tools/validate-openapi.mjs` → FAIL（缺 pause 路径与新字段）。
- [ ] **Step 3：修改 yaml。** 逐项对照接口文档第 7 章字典，所有新对象 `additionalProperties: false`；为 API-43/44 与 `message.saved` 补齐示例（示例须通过 Schema 校验）；不改动与 V1.1 无关的既有定义。
- [ ] **Step 4：运行确认通过**，并执行 `cd frontend && npm.cmd run validate:api`。
- [ ] **Step 5：提交** `git commit -m "docs(api): sync openapi.yaml to interface spec V1.1 (pause/resume, segments, speaking avatar)"`

---

### Task 2：V1.1 基线对齐——张嘴立绘、快照头像授权、移除画像永久删除

> 第二、三阶段按 V1.0 实现，画像只有一张头像。演出层要求闭嘴/张嘴两帧（PRD BR-P09、§14.3；数据库 §5.3～5.4、§10.3 第 6 条；DBV-20），必须先补齐，否则演绎页无法实现嘴部开合。

**Files:**
- Modify: `tools/generate-avatars.py`（新增 `portrait(index, speaking=False)`：张嘴帧把嘴部 `arc` 换成同位置的深色椭圆，其余像素一致，尺寸同为 512×512；输出两组键）
- Create: `backend/var/avatars/<sha256>.png` × 5（张嘴帧）
- Create: `backend/src/main/resources/db/migration/V4__add_speaking_avatar_key.sql`
- Modify: `content/domain/ScammerProfile.java`、`VictimProfile.java`（`speakingAvatarKey` 字段与只读访问器、写方法参数）
- Modify: `content/avatar/AvatarStore.java`（新增 `Dimension dimensions(String storedKey)`，复用既有解码校验）
- Modify: `content/web/dto/ScammerView.java`、`VictimView.java`（增加 `AvatarRef speakingAvatar`）
- Modify: `content/service/ContentService.java`（`avatar(key)` 授权：闭嘴或张嘴键被启用画像引用 **或** `SnapshotAvatarReferences.referencedByUser(userId, storedKey)` 为真）
- Create: `content/avatar/SnapshotAvatarReferences.java`（接口；本任务提供「恒为 false」的默认实现 `NoSnapshotAvatarReferences`，Task 3 由 `simulation` 提供真实实现并以 `@Primary` 替换）
- Modify: `content/startup/ContentStartupCheck.java`（校验两张立绘可读且尺寸一致；对两键相同的非种子画像只记 WARN，见下）
- Modify: `admin/web/dto/ScammerWriteRequest.java`、`ScammerUpdateRequest.java`、`VictimWriteRequest.java`、`VictimUpdateRequest.java`（必需 `speakingAvatarKey`）、`AdminScammerView.java`、`AdminVictimView.java`（`speakingAvatar`）
- Modify: `admin/service/AdminProfileService.java`（保存前：两键都能 `storedKey` 解析、都可解码、像素尺寸一致，否则 `ApiException.field("speakingAvatarKey","VALUE_INVALID","两张立绘尺寸必须一致")`）
- Modify: `frontend/src/api/types.ts`（`speakingAvatar`、`speakingAvatarKey`）、`components/ProfileFormFields.vue`、`components/AvatarUploader.vue`（两个上传位：「闭嘴立绘（兼头像）」「张嘴立绘」，各自预览；两张都成功才允许保存）
- Modify（D-1 移除删除功能）：`admin/web/AdminProfileController.java`（删除两个 `@DeleteMapping`）、`admin/service/AdminProfileService.java`（删除 `deleteScammer`/`deleteVictim` 及仅为其服务的仓库方法）、`frontend/src/api/admin.ts`（删除删除调用）、`frontend/src/api/http.ts`（若 `http.delete` 已无调用方则一并删除）、`frontend/src/views/AdminProfilesView.vue` 及相关组件（删除「删除」按钮与确认框，停用/启用保留）、`tools/smoke-admin.ps1`（删除删除用例，改为断言 `DELETE` 不可用且记录仍在）、`README.md` 第三阶段小节（删去删除接口行与说明）
- Test: 修改 `ContentRulesTest`、`AdminRulesTest`、`frontend/src/__tests__/admin.spec.ts`、`configuration.spec.ts`；修改 `tools/smoke-admin.ps1`、`tools/smoke-content.ps1` 的写入载荷与字段断言

**V4 迁移内容（逐字顺序）：**

```sql
ALTER TABLE scammer_profile ADD COLUMN speaking_avatar_key VARCHAR(255) NULL AFTER avatar_key;
ALTER TABLE victim_profile  ADD COLUMN speaking_avatar_key VARCHAR(255) NULL AFTER avatar_key;
-- 五个种子画像：按 avatar_key 精确匹配回填张嘴帧（键值取自 generate-avatars.py 的实际输出）
UPDATE scammer_profile SET speaking_avatar_key = 'avatars/<陈启明张嘴sha>.png' WHERE avatar_key = 'avatars/3e1d7c56…becc.png';
-- ……其余四条同理……
-- 管理员在第三阶段新增的画像没有张嘴帧：临时回填为闭嘴帧本身（尺寸天然一致），由启动检查 WARN 列出，需管理员补传
UPDATE scammer_profile SET speaking_avatar_key = avatar_key WHERE speaking_avatar_key IS NULL;
UPDATE victim_profile  SET speaking_avatar_key = avatar_key WHERE speaking_avatar_key IS NULL;
ALTER TABLE scammer_profile MODIFY speaking_avatar_key VARCHAR(255) NOT NULL;
ALTER TABLE victim_profile  MODIFY speaking_avatar_key VARCHAR(255) NOT NULL;
```

- [ ] **Step 1：写失败测试。**
  - `ContentRulesTest`：用户端画像 JSON 恰好包含 `avatar` 与 `speakingAvatar` 两个 `AvatarRef`，`url` 前缀 `/anti.scam/v1/avatars/`；张嘴键被启用画像引用时 `GET /avatars/{key}` 200；既非启用画像引用又非本人快照引用 404。
  - `AdminRulesTest`：新增/编辑缺 `speakingAvatarKey` → 400 `REQUIRED`；两张尺寸不同 → 400 字段 `speakingAvatarKey`；尺寸相同 → 201/200 且后台视图含 `speakingAvatar`（`/admin/avatars/` 前缀）。
  - `admin.spec.ts`：表单出现两个上传位；只上传一张时保存按钮禁用；保存载荷含两个键；画像列表与编辑区**不再出现「删除」按钮**，停用/启用仍可用。
  - `AdminRulesTest`（D-1）：`DELETE /admin/scammer-profiles/{id}`、`DELETE /admin/victim-profiles/{id}` 不再成功（非 2xx，实际状态码以框架对未映射方法的处理为准并写入测试注释），请求后该画像仍可通过详情接口读取。
- [ ] **Step 2：运行确认失败** `.\mvnw.cmd test "-Dtest=ContentRulesTest,AdminRulesTest"`；`npm.cmd test`。
- [ ] **Step 3：生成素材并实现。** 先运行 `python tools/generate-avatars.py`（需 Pillow，仅开发机）得到五个张嘴键，填入 V4；再改实体、DTO、服务、前端。实体 `speakingAvatarKey` 只加 getter 与既有写方法参数，不加 setter。
- [ ] **Step 4：验证。** 单元测试通过；`pwsh -NoProfile -File tools/smoke-content.ps1` 与 `tools/smoke-admin.ps1`（已同步载荷）在隔离库上通过，确认 V1→V4 在空库上顺序执行成功（如实记录这是首次在空库上跑通全部迁移）。
- [ ] **Step 5：提交** `git commit -m "feat(content): V1.1 speaking avatar frame, snapshot avatar access; remove permanent profile deletion (D-1)"`

---

### Task 3：演绎数据层——V5 五表、V6 Prompt 种子、实体与仓库

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__create_simulation_tables.sql`
- Create: `backend/src/main/java/db/migration/V6__seed_agent_prompts.java`（Flyway `BaseJavaMigration`：读取 `prompts/v1/{scammer,victim,narrator,report}.txt`，插入四行 `version_no=1, is_active=1`；覆写 `getChecksum()` 返回四个文件内容的 CRC32 组合，使文本被改动后 Flyway 校验失败而不是静默漂移）
- Create: `backend/src/main/resources/prompts/v1/*.txt`（内容要求见 Task 6；本任务先放入通过渲染变量检查的完整初版）
- Create: `simulation/domain/`：`SimulationSession`、`SimulationRound`、`SimulationMessage`、`SimulationReport`、`AgentPrompt` 五个实体；枚举 `SimulationStatus`、`RoundPhase`、`SpeakerRole`、`PresentationStatus`、`OutcomeCode`、`LimitReason`、`InterruptionCode`（含固定中文文案）、`ReportStatus`、`PromptType`；仓库五个
- Create: `simulation/snapshot/SnapshotAvatarReferencesImpl.java`（`@Primary` 实现 Task 2 接口）
- Test: `simulation/SimulationSchemaRulesTest.java`（纯单元：枚举与文案映射）；DB 约束在 Task 14 冒烟中以真实 MySQL 断言（DBV-03、DBV-19）

**V5 建表要点（字段、类型、默认值逐字取自数据库设计 §5.5～5.9，下列为必须出现的约束；约束名按 `ck_/uq_/idx_/fk_` 前缀）：**

| 表 | 必须实现的约束/索引 |
|---|---|
| `agent_prompt` | `uq_prompt_type_version(prompt_type,version_no)`；生成列 `active_type = CASE WHEN is_active=1 THEN prompt_type ELSE NULL END` + `uq_prompt_active_type`；`prompt_type IN ('SCAMMER','VICTIM','NARRATOR','REPORT')` |
| `simulation_session` | 外键 7 条（user、scenario、两画像、四 Prompt）全部 `RESTRICT`；`uq_session_start_request(user_id,start_request_id)`；生成列 `active_user_id = CASE WHEN status='RUNNING' THEN user_id ELSE NULL END` + `uq_session_active_user`；`status IN (...)`；`current_round_no BETWEEN 0 AND 20`；`paused_total_ms BETWEEN 0 AND 300000`；`deadline_at = started_at + INTERVAL (600000 + paused_total_ms) * 1000 MICROSECOND`；三种状态的空值组合（数据库 §6.3 逐条）；终态 `paused_at IS NULL`；`status<>'FINISHED' OR outcome_code='UNDETERMINED' OR current_round_no>=5`；`limit_reason` 仅 UNDETERMINED 非空；索引 `idx_session_user_history(user_id, started_at DESC, id DESC)`、`idx_session_status_deadline(status, deadline_at, id)` 及 7 个外键支撑索引 |
| `simulation_round` | `uq_round_session_no(session_id,round_no)`；`round_no BETWEEN 1 AND 20`；`risk_score IS NULL OR risk_score IN (0,25,50,75,100)`；分析字段组「全 NULL」或「全部必填」两种合法组合（含 `evidence_json` 为 ARRAY、`analysis_saved_at`）；`goal_achieved=1 → actual_fraud_occurred=1`；`candidate_outcome` 与 `candidate_reason` 同有同无且只允许三类明确结局；`round_no>=5 OR (candidate_outcome IS NULL AND candidate_reason IS NULL)`；`analysis_presented_at` 非空 → 分析已保存且 `phase='COMPLETE'`；`phase` 白名单 7 值 |
| `simulation_message` | `uq_message_round_role(round_id,speaker_role)`；`speaker_role IN ('SCAMMER','VICTIM')`；`content`/`action_text`/`internal_note` 为 TEXT；`presentation_status` 四值；COMPLETED ⇔ 有完成时间且无错误码；FAILED ⇔ 有错误码且无完成时间；PENDING/PRESENTING 两者皆空 |
| `simulation_report` | 主键即 `session_id`（外键）；四种状态的字段组合（数据库 §6.3「报告字段组合」逐条）；`idx_report_queue(status,queued_at,session_id)`、`idx_report_deadline(status,attempt_deadline_at,session_id)` |

所有 `CHECK` 的条件必填必须写成显式 `IS NOT NULL` 的两种合法组合（数据库 §6.3 末段：`UNKNOWN` 会通过 CHECK）。JSON 列加 `JSON_TYPE(x)='OBJECT'/'ARRAY'` 检查。字符集 `utf8mb4`、排序 `utf8mb4_0900_bin`、引擎 InnoDB。

**实体映射约定：**
- JSON 列映射为 `String`，由 `ConfigurationSnapshots`/`RuntimeSnapshot` 用 Jackson 读写（不使用 Hibernate JSON 类型，避免序列化差异）。
- 生成列 `active_user_id`、`active_type` 用 `@Column(insertable=false, updatable=false)`。
- `state_version` 是应用层版本闸门，**不要**用 JPA `@Version`（会话行已通过悲观锁串行化）。
- 仓库提供 `@Lock(PESSIMISTIC_WRITE) Optional<SimulationSession> lockById(long id)`、`lockByUserAndStartRequest(...)`；`SysUser` 行锁复用 `UserRepository` 新增的 `lockById`；画像共享锁 `@Lock(PESSIMISTIC_READ)` 的 `shareLockById`。
- `SimulationMessage` 不提供 `content`/`actionText`/`internalNote` 的 setter；构造后不可变。`internalNote` 的 getter 包级可见或命名为 `internalNoteForAgents()`，便于复审时检索使用点。

- [ ] **Step 1：写失败测试** `SimulationSchemaRulesTest`：`InterruptionCode` 九个值各有非空中文文案且不含技术词；`OutcomeCode.displayText` 为「诈骗目标达成/受骗后止损/未受骗结束/结局未定」；`ReportStatus` → 「生成中/生成中/已完成/生成失败」。
- [ ] **Step 2：运行确认失败。**
- [ ] **Step 3：实现迁移、实体、仓库。** 启动应用（`dev` 配置连本地库）确认 Flyway V5、V6 执行成功、`ddl-auto: validate` 通过；用 `SHOW CREATE TABLE` 人工核对约束齐全。
- [ ] **Step 4：在空的隔离库上执行一次 V1→V6**（可临时复用 `smoke-admin.ps1` 的启动段，或直接等 Task 14 脚本），并手工执行三条应被拒绝的 SQL：同用户第二条 RUNNING 会话、`round_no=3` 写 `candidate_outcome`、`risk_score=10`，确认均报约束错误。把结果记录到 Task 15 的验收记录草稿。
- [ ] **Step 5：提交** `git commit -m "feat(simulation): schema V5/V6, entities and repositories for simulation tables"`

---

### Task 4：点击计时采样与开始演绎（API-42、API-14）

**Files:**
- Create: `config/SimulationProperties.java`（`@ConfigurationProperties("app.simulation")`，见下方配置表）
- Create: `timing/TimingSampleStore.java`、`timing/TimingCalibrator.java`、`timing/CalibratedStart.java`、`timing/web/TimingController.java`、`timing/web/dto/TimingSampleView.java`
- Create: `media/VoiceCatalog.java`、`media/VoiceMapper.java`（开始时需要固定音色，因此在本任务实现）
- Create: `simulation/snapshot/ConfigurationSnapshots.java`、`simulation/snapshot/RuntimeSnapshot.java`
- Create: `simulation/service/SimulationStartService.java`、`simulation/service/SimulationTransitions.java`（本任务只实现 `createSession`）
- Create: `simulation/web/SimulationController.java`（本任务只有 `POST /simulations`）、`simulation/web/dto/StartRequest.java`、`TimingEvidence.java`
- Modify: `common/error/ApiException.java`（新增 `activeSimulationExists(String activeSessionId)`、`idempotencyConflict()`、`profileUnavailable()`、`timingNotReady()`、`stageConflict()`、`presentationConflict()`、`audioNotReady()`、`audioExpired()`；错误码与 HTTP 状态逐字取自接口文档 §8）
- Modify: `application.yml`、`application-dev.yml`
- Test: `timing/TimingCalibratorTest.java`、`media/VoiceMapperTest.java`、`simulation/SimulationStartApiTest.java`

**配置项（`application.yml`，括号内为默认值；标注 TBD 的为待实测参数，只能改值不能改语义）：**

| 键 | 默认 | 说明 |
|---|---|---|
| `app.simulation.timing.sample-ttl-ms` | 60000 | 样本有效期（TBD-01a） |
| `app.simulation.timing.max-uncertainty-ms` | 1500 | `(receive−send)/2` 上限（TBD-01a） |
| `app.simulation.timing.max-click-to-arrival-ms` | 30000 | 点击到请求到达的最大间隔（TBD-01a） |
| `app.simulation.llm.provider` | `deepseek` | `deepseek` 或 `scripted`（后者仅 dev/test） |
| `app.simulation.llm.base-url` / `model` / `api-key` | `https://api.deepseek.com` / `${LLM_MODEL}` / `${LLM_API_KEY}` | 模型标识部署时按官方文档记录（V-05） |
| `app.simulation.llm.call-timeout-ms` | 20000 | 单次调用上限，实际取 `min(该值, 剩余有效预算)` |
| `app.simulation.llm.transient-retries` | 2 | 未发布内容的透明重试次数（与 BR-P12 的 2 次重新生成分开计数） |
| `app.simulation.tts.provider` | `cosyvoice` | `cosyvoice` 或 `silent`（后者仅 dev/test） |
| `app.simulation.tts.gateway-url` | `${TTS_GATEWAY_URL:http://127.0.0.1:50080}` | 内网地址，不对公网开放 |
| `app.simulation.tts.voices` | 列表 | `{id, ageGroup: YOUTH/MIDDLE/SENIOR, gender: MALE/FEMALE/NEUTRAL, rank: PRIMARY/BACKUP}`，每类至少主选+备选（V-09） |
| `app.simulation.audio-temp-dir` | `var/tmp-audio` | 临时语音目录，终态释放 |
| `app.simulation.sse.heartbeat-ms` / `emitter-timeout-ms` | 15000 / 1800000 | TBD-04 |
| `app.simulation.connection.lost-grace-ms` | 30000 | 心跳容忍窗口（TBD-05） |
| `app.simulation.executors.lanes` / `io` | 8 / 32 | 编排通道线程与外部调用并发（3 场并行实测后锁定） |
| `app.simulation.report-attempt-timeout-ms` | 120000 | 只写入 runtime_snapshot，供下一阶段使用 |

`SimulationProperties` 在 `@PostConstruct` 中校验：非 `dev`/`test` profile 下 `provider` 为 `scripted`/`silent` 时**启动失败**；`deepseek` 下 `api-key` 为空时启动失败（`dev` 下允许并在首次调用时报 `DIALOGUE_FAILED`）。

**接口与算法：**
- `TimingSampleStore.issue(httpSessionId, userId) -> TimingSample(sampleId, serverSampledAt, expiresAt, methodVersion="1.0")`：`sampleId` 为 128 位随机数的 base64url；内存 `ConcurrentHashMap`，过期清理；**服务重启后样本全部失效**（开始请求 400，前端重新采样并请用户再次点击）。
- `TimingCalibrator.calibrate(sample, evidence, receivedAt) -> CalibratedStart(startedAt, receivedAt, timingEvidenceJson)`，校验顺序（接口文档 §7 TimingEvidence）：样本存在且未过期 → 属于当前 HTTP Session 与当前用户 → `send ≤ receive ≤ click` → `uncertainty = (receive − send)/2 ≤ max-uncertainty-ms` → `startedAt = serverSampledAt + (click − (send + receive)/2)` → `startedAt ≤ receivedAt + uncertainty` → `receivedAt − startedAt ≤ max-click-to-arrival-ms` → `startedAt + 600s > receivedAt`。任一失败 `ApiException.field("timing.<字段>", "VALUE_INVALID", 说明)`；**禁止**任何回退到 `receivedAt` 的分支。`timingEvidenceJson` 保存 `method_version`、`sample_id`、三个客户端单调值、`server_sampled_at`、`estimated_offset_ms`、`uncertainty_ms`（数据库 §7.7）。
- `VoiceMapper.map(scammerSnapshot, victimSnapshot) -> VoicePair(scammerVoiceId, victimVoiceId, mappingVersion="1")`：年龄 <35 → YOUTH（含 18 岁以下，见 D-4）、35—59 → MIDDLE、≥60 → SENIOR；`gender` 精确等于「男」→ MALE、「女」→ FEMALE，否则 NEUTRAL；取 PRIMARY；双方相同则受害者改取同类 BACKUP；缺少所需音色时启动检查已失败，不在运行时降级。
- `SimulationStartService.start(userId, httpSessionId, StartRequest) -> StartOutcome(created: boolean, state)`，严格按数据库 §7.1：

```text
tx(READ COMMITTED) {
  lock sys_user(userId)                                   // 串行入口
  existing = session by (userId, startRequestId)
  if existing: 三个 ID 与原快照来源一致 → return (false, state(existing))  // 200，即使已终态
               否则 → 409 IDEMPOTENCY_CONFLICT
  if RUNNING session of user exists → 409 ACTIVE_SIMULATION_EXISTS(activeSessionId)
  calibrated = TimingCalibrator.calibrate(...)            // 400 字段错误
  share-lock scenario, scammer, victim；任一画像 is_enabled=0 或不存在 → 409 PROFILE_UNAVAILABLE
  校验四张立绘可读（AvatarStore.load）
  prompts = 一次查询取四类 is_active=1；每类恰好一个，否则 500 DATA_INCONSISTENT
  insert simulation_session(RUNNING, current_round_no=0, state_version=0,
         started_at, received_at, timing_evidence, deadline_at=started_at+600s,
         三份完整快照, 四个 prompt_id, runtime_snapshot(含 VoicePair、pacing_config))
}
afterCommit: 发布 session.started；orchestrator.onSessionStarted(sessionId)   // Task 8 接入，本任务先留空实现
```

  唯一约束冲突（并发双击）不转成未知错误：回滚后按 `startRequestId` 或活动会话回查并返回对应 200/409。
- `ConfigurationSnapshots`：三份 JSON 按数据库 §10.2 完整字段（含 `speaking_avatar_key`、`row_version`、开始时 `is_enabled`、场景 `focus`），ID 用十进制字符串；提供 `toConfigurationView()` 映射为与 `ScenarioView/ScammerView/VictimView` 相同的用户端结构（头像 URL 用 `/anti.scam/v1/avatars/` 前缀）。
- `runtime_snapshot` 结构：`{schema_version:1, app_build, ruleset_version:"PRD-V3.2", model_config:{provider, model, temperature:{scammer,victim,narrator,report}, max_tokens}, media_config:{scammer_voice, victim_voice, voice_mapping_version, segmentation_version:"1", animation_version:"1", scene_intro_version:"1"}, pacing_config:{min_rounds:5, max_rounds:20, max_regenerations:2, line_target_chars:50, analysis_target_chars:40, scene_intro_max_ms:15000, cards_max_ms:8000, reading_ms:5000}, report_attempt_timeout_ms}`；不含密钥；**不经任何用户接口返回**。
- 控制器：`POST /anti.scam/v1/simulations`，`@RequestBody StartRequest`（Jackson 已 `fail-on-unknown-properties`，未知字段 400 `UNKNOWN_FIELD`），新建 201、幂等 200；`GET /anti.scam/v1/timing/samples` 拒绝任何查询参数，`Cache-Control: no-store`。

- [ ] **Step 1：写失败测试。**

```java
// TimingCalibratorTest（纯单元，注入固定 Clock）
@Test void startedAtIsMappedFromClickNotFromArrival()        // sample@T, send=1000, recv=1200, click=4100 → startedAt = T+3000ms
@Test void rejectsExpiredSample_otherSessionsSample_andOtherUsersSample()
@Test void rejectsOutOfOrderMonotonicValues()                 // send>recv、recv>click → 400 字段 timing.clientReceiveMonotonicMs/clientClickMonotonicMs
@Test void rejectsLargeUncertaintyAndFutureClickAndStaleClick()
@Test void neverFallsBackToReceivedAt()                       // 所有失败路径均抛异常，没有返回值
// VoiceMapperTest
@Test void mapsByAgeBandAndGender()                           // 35岁男→MIDDLE_MALE；66岁女→SENIOR_FEMALE；22岁女→YOUTH_FEMALE
@Test void victimFallsBackToBackupWhenSameVoice()            // 两个 28 岁女性 → 受害者用 BACKUP
@Test void nonBinaryGenderTextUsesNeutralVoiceOfAgeBand()
// SimulationStartApiTest（@WebMvcTest，服务层 mock）+ 服务层单元测试（仓库 mock）
@Test void start_returns201WithRunningStateAndNullRiskAndReport()   // currentRoundNo=0、latestRiskScore=null、outcome/interruption/report=null、paused=false、pausedCreditMs=0
@Test void sameStartRequestId_returns200SameSession_evenWhenFinished()
@Test void sameStartRequestId_differentProfiles_returns409IdempotencyConflict()
@Test void activeRunning_returns409WithActiveSessionId()
@Test void disabledProfile_returns409ProfileUnavailable_noReplacement()
@Test void unknownField_outcomeCode_returns400UnknownField()
@Test void admin_gets403_anonymous_gets401_missingCsrf_gets403()
@Test void runtimeSnapshotAndTimingEvidenceAreNeverInResponse()
```

- [ ] **Step 2：运行确认失败** `.\mvnw.cmd test "-Dtest=TimingCalibratorTest,VoiceMapperTest,SimulationStartApiTest"`。
- [ ] **Step 3：实现。** 注意：`createSession` 是 `SimulationTransitions` 的第一个方法；服务层只编排校验与调用；`Clock` 以 Bean 注入（默认 `Clock.systemUTC()`），后续所有时间判断都用它，测试可替换。
- [ ] **Step 4：运行确认通过**，既有测试全部仍通过。
- [ ] **Step 5：提交** `git commit -m "feat(simulation): timing samples and start simulation with snapshot, voice mapping (API-42, API-14)"`

---

### Task 5：状态读取与 SSE 事件中心（API-15、API-16）

**Files:**
- Create: `simulation/service/SimulationStateAssembler.java`、`SimulationQueryService.java`
- Create: `simulation/web/dto/`：`SimulationStateView`、`LiveRoundView`、`LiveMessageView`、`AudioSegmentView`、`AnalysisView`、`OutcomeView`、`InterruptionView`、`ReportSummaryView`、`ReportViewView`（仅供 `session.state` 使用）、`RiskPointView`
- Create: `sse/SimulationEventHub.java`、`sse/SimulationEvent.java`、`sse/AfterCommitPublisher.java`
- Modify: `simulation/web/SimulationController.java`（`GET /simulations/{id}`、`GET /simulations/{id}/events`）
- Modify: `config/SecurityConfig.java`：在 `authorizeHttpRequests` 最前面加入 `.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()`（SSE 异步派发需要；**不改动**任何 URL 规则）
- Modify: `application.yml`：`spring.mvc.async.request-timeout` 与 `emitter-timeout-ms` 一致
- Test: `simulation/SimulationStateAssemblerTest.java`、`sse/SimulationEventHubTest.java`、`simulation/SimulationPermissionTest.java`（本任务覆盖 GET 与 SSE，后续任务继续追加）、`simulation/InternalNoteNeverExposedTest.java`

**组装规则（`SimulationStateAssembler.assemble(session, currentRound, messages, report, now)`，唯一组装点）：**

| 字段 | 规则 |
|---|---|
| `stateVersion` | `state_version` 十进制字符串 |
| `deadlineAt` | 存储的 `deadline_at`（不把进行中的暂停时长预先加进去；前端暂停时自行冻结） |
| `paused` / `pausedAt` / `pausedCreditMs` | `paused_at != null` / `paused_at` / `paused_total_ms` |
| `configuration` | `ConfigurationSnapshots.toConfigurationView()`，**永不**回查当前画像表 |
| `currentRound` | `current_round_no=0` 时 `null`；否则当前轮：`interactionComplete` = 两条消息都存在；`presentationComplete` = `phase=COMPLETE`；`messages` 按 SCAMMER、VICTIM 排序；`analysis` 仅 `analysis_saved_at` 非空时给出 |
| `LiveMessage.audioUrl/audioDurationMs/segments` | 仅当 `status=RUNNING` 且消息 `PRESENTING` 且临时音频元数据存在时为 `/anti.scam/v1/simulations/{sid}/messages/{mid}/audio`、时长、分句；否则三者均 `null` |
| `latestRiskScore` | 本场 `round_no` 最大且已有分析的那一轮的 `risk_score`；无则 `null` |
| `readingUntil` | 当前轮 `reading_until` |
| `nextAction` | `FINISHED` 且当前轮有 `reading_until` → `REPORT`；`RUNNING` 且当前轮 `COMPLETE` 且有 `reading_until` → `NEXT_ROUND`；其余 `null` |
| `outcome` | 仅 FINISHED：`{code, displayText, reason=outcome_reason, limitReason}` |
| `interruption` | 仅 INTERRUPTED：`{code, message=interruption_detail}` |
| `report` | 仅 FINISHED 且报告行存在：`{status, displayText, canRetry=(status==FAILED), attemptNo, updatedAt, error}`；FINISHED 但缺行 → 500 `DATA_INCONSISTENT` |

`session.state` 的 `data = {state, reportView}`；`reportView = {sessionId, simulationStatus, report, unavailableReason, outcome, content:null, riskSeries}`，`unavailableReason`：RUNNING → `SIMULATION_RUNNING`、INTERRUPTED → `SIMULATION_INTERRUPTED`、FINISHED → `null`；`riskSeries`：仅 FINISHED 时为已评分轮次 `{roundNo, score}` 升序（**缺失不补 0**），RUNNING/INTERRUPTED 为空数组（接口文档 §7 ReportView：无正式报告资格时为空）。

**事件中心：**
- `subscribe(sessionId, userId, httpSessionId) -> SseEmitter`：先在只读事务中校验归属（他人/不存在 404，在发送响应头之前抛出），再创建 emitter、立即发送 `session.state`；响应头 `Content-Type: text/event-stream`、`Cache-Control: no-store`、`X-Accel-Buffering: no`。
- 帧格式：`id: <eventId>`、`event: <type>`、`data: <单行 JSON>`、空行；`eventId` 为 UUID。
- `AfterCommitPublisher.publish(sessionId, type, Supplier<Object> dataSupplier)`：在当前事务注册 `afterCommit`，提交后在**新的只读事务**中构造载荷并广播；无事务时直接发布。`SessionStarted/Finished/Interrupted/Paused/Resumed` 的 `data` 均为完整 `SimulationState`。
- 心跳：每 `heartbeat-ms` 向所有 emitter 发送注释帧 `: ping`。
- 关闭：订阅 `HttpSessionDestroyedEvent`（登出、过期、被新登录挤掉）→ 关闭该 HTTP Session 的全部 emitter；会话进入 INTERRUPTED 后发布完事件即关闭；FINISHED 保持（报告变化留给下一阶段）。
- 连接记录：维护 `sessionId → {activeEmitters, lastDisconnectedAt}`，供 Task 9 的 `ConnectionMonitor` 读取。

- [ ] **Step 1：写失败测试。**
  - `SimulationStateAssemblerTest`：开场（`currentRound=null`、`latestRiskScore=null`）；半轮（只有骗子消息，`interactionComplete=false`，`analysis=null`）；消息 PENDING 时 `audioUrl=null`；PRESENTING 时三项齐全；终态时音频三项全为 `null`；第 3 轮无分析时 `latestRiskScore` 取第 2 轮；`nextAction` 三种取值；FINISHED 缺报告行抛 `DATA_INCONSISTENT`。
  - `InternalNoteNeverExposedTest`：构造 `internal_note="SECRET-INTENT-XYZ"` 的消息，断言 `GET /simulations/{id}` 响应体与 `session.state` 帧文本都不包含该字符串。
  - `SimulationEventHubTest`：订阅后第一帧是 `session.state`；`afterCommit` 前不发送、回滚不发送；同一事件 `eventId` 唯一；心跳是 `:` 开头注释；HTTP Session 销毁后 emitter 完成。
  - `SimulationPermissionTest`（追加）：用户 B 读取用户 A 的状态与 SSE → 404 且正文与不存在一致；ADMIN → 403；匿名 → 401；`/simulations/007` → 400、`/simulations/99999999999999999999` → 404。
- [ ] **Step 2：运行确认失败。**
- [ ] **Step 3：实现。** 用 `MockMvc` 的 `asyncDispatch` 验证 SSE；`SecurityConfig` 只加派发类型放行一行。
- [ ] **Step 4：运行确认通过**；手工用浏览器开发者工具确认 SSE 首帧（可在 Task 8 后复验）。
- [ ] **Step 5：提交** `git commit -m "feat(simulation): state assembler and SSE event hub (API-15, API-16)"`

---

### Task 6：Agent 执行层——上下文、Prompt、模型调用、输出校验

**Files:**
- Create: `agent/LlmClient.java`（`String completeJson(AgentRole role, String systemPrompt, String userPrompt, Duration timeout)`）、`DeepSeekLlmClient.java`、`ScriptedLlmClient.java`
- Create: `agent/PromptRenderer.java`、`agent/AgentContextAssembler.java`、`agent/AgentRunner.java`
- Create: `agent/ScammerTurn.java`、`VictimTurn.java`、`NarratorJudgement.java`、`EvidenceItem.java`、`ToneHint.java`
- Create: `agent/AgentOutputParser.java`、`AgentOutputValidator.java`、`EvidenceValidator.java`、`AgentCallException.java`（`retryable` 标志 + 归类 `DIALOGUE`/`ANALYSIS`）
- Create: `simulation/rules/FactCode.java`、`ScenarioGoalRules.java`、`OutcomeRules.java`
- Modify: `backend/src/main/resources/prompts/v1/*.txt`（定稿初版）
- Create: `backend/src/main/resources/simulation/scripted/{not-defrauded-r5,goal-achieved-r6,round-limit-20,precheck-regen-r3,precheck-exceeded-r2,invalid-risk,tts-fail-r2}.json`
- Test: `agent/AgentOutputValidatorTest.java`、`EvidenceValidatorTest.java`、`AgentContextIsolationTest.java`、`PromptRendererTest.java`、`simulation/OutcomeRulesTest.java`、`ScenarioGoalRulesTest.java`

**模型输出 JSON（Prompt 中给出完全相同的示例；解析器拒绝缺字段、多字段、类型错误）：**

```json
// SCAMMER
{"line":"台词，约50字以内","action":"模拟行动说明或null","intent":"本轮内部意图","tone":{"speed":"SLOW|NORMAL|FAST","style":"简短语气提示"}}
// VICTIM
{"line":"台词","action":"已完成/正在进行的模拟行动或null","psychology":"本轮内部心理","tone":{"speed":"SLOW|NORMAL|FAST","style":"简短语气提示"}}
// NARRATOR
{"fraudStage":"","speechAnalysis":"","methodAnalysis":"","victimPsychology":"","riskScore":0,"riskReason":"",
 "actualFraudOccurred":false,"goalAchieved":false,"cooperationStopped":false,
 "evidence":[{"factCode":"INITIAL_PAYMENT_COMPLETED","speaker":"VICTIM","source":"ACTION","roundNo":2,"quote":"原文片段","eventOrder":1}],
 "candidateOutcome":null,"candidateReason":null}
```

**`FactCode`（固定枚举，数据库 §7.4）：** `INITIAL_PAYMENT_COMPLETED`（刷单首笔垫付）、`ADDITIONAL_PAYMENT_COMPLETED`（追加投入）、`INVESTMENT_COMPLETED`（投资款投入）、`EXTRA_FEE_PAID`（提现/解冻等额外费用）、`LOAN_TRANSFER_COMPLETED`（借款转账）、`VERIFICATION_CODE_GIVEN`、`CREDENTIALS_GIVEN`、`ACCOUNT_CONTROL_GRANTED`、`OTHER_DANGEROUS_ACTION_EXECUTED`、`INDEPENDENT_VERIFICATION_COMPLETED`、`COOPERATION_STOPPED`。前九个中除最后两类外均属「实际受骗行为」。

**校验规则（`AgentOutputValidator` + `EvidenceValidator`，失败一律抛 `AgentCallException(retryable=true)`，由预备区在预算内透明重试，耗尽后按 `DIALOGUE_FAILED`/`ANALYSIS_FAILED` 中断）：**
1. 角色台词：`line` 去首尾空白后非空；码点数 ≤ 120（硬上限，见 D-4；目标 50 由 Prompt 约束）；`action` 为 null 或非空且 ≤ 120；`line` 中不得包含括号内的动作叙述（正则 `[（(【\[].*?(转账|付款|点击|输入|发送|打开).*?[）)】\]]` 命中即视为动作混入台词，要求重生）；`intent`/`psychology` 非空。
2. 解说：四项分析与 `riskReason` 非空且各 ≤ 120 码点（目标 40）；`riskScore ∈ {0,25,50,75,100}`，否则失败（**不取整**）；`goalAchieved → actualFraudOccurred`；累计事实单调：上一轮已保存 `actual_fraud_occurred=1` 或 `goal_achieved=1` 时本轮不得为 false。
3. 证据：每项 `quote` 必须是被引用消息 `content` 或 `action_text`（由 `source` 指定）的子串；`roundNo ≤` 当前轮；实际受骗、核实、停止类事实只能引用 `speaker=VICTIM`；`actualFraudOccurred=true` 时至少一条实际受骗事实证据（可引用历史轮）；同一条消息内多个行为用 `eventOrder` 递增区分；同一 `quote` 不得同时计为首笔与追加两次。
4. 场景目标（`ScenarioGoalRules.goalAchieved(code, evidence)`）：`TASK_REBATE` 需要 `INITIAL_PAYMENT_COMPLETED` 先于 `ADDITIONAL_PAYMENT_COMPLETED`（先比 `roundNo` 再比 `eventOrder`）；`FAKE_INVESTMENT` 需要 `INVESTMENT_COMPLETED` 先于 `EXTRA_FEE_PAID`；`IMPERSONATION_LOAN` 需要 `LOAN_TRANSFER_COMPLETED` 且其之前不存在 `INDEPENDENT_VERIFICATION_COMPLETED`。模型给出的 `goalAchieved` 与规则计算结果不一致 → 校验失败（程序不替模型改判，也不静默覆盖）。
5. 候选结局（`OutcomeRules.expected(goal, actualFraud, stopped)`）：`goal→GOAL_ACHIEVED`；`actual∧stopped→LOSS_STOPPED`；`¬actual∧stopped→NOT_DEFRAUDED`；否则 `null`。模型 `candidateOutcome` 与之不一致 → 校验失败；`candidateOutcome≠null` 时 `candidateReason` 必须非空；**不得出现 `UNDETERMINED`**。
6. 第 1～4 轮：校验仍按上述规则如实执行；`candidateOutcome≠null` 不是校验失败，而是交给 Task 8 的 BR-P12 预检处理。

**上下文隔离（`AgentContextAssembler`，三个方法各自只拿需要的数据）：**

| 角色 | 可以看到 | 绝不能看到 |
|---|---|---|
| 骗子 | 场景快照（名称、简介、`scenario_code` 对应的推进目标说明）、本人画像快照、双方已保存的 `content`+`action_text`、当前轮号、剩余有效时长（秒）、`pacing_config`（第 1～4 轮不急于收网的节奏提示、台词长度目标） | 受害者 `internal_note`、任何解说字段与风险、候选结局、其他会话 |
| 受害者 | 本人画像快照（含防骗意识与风险倾向）、本人情境、双方已保存 `content`+`action_text`、本轮骗子 `content`+`action_text`、当前轮号、剩余时长；第 1～4 轮附加 BR-P12 行为约束；重生时附加「上一候选因形成决定性行为被拒绝」的强化约束（不透露解说内容） | 骗子 `internal_note`、诈骗类型标签与关键目标定义、任何解说与风险、其他会话 |
| 解说 | 全部快照、本场全部已保存消息（含双方 `internal_note`）、本轮候选受害者回应、历史各轮已保存的累计事实与证据、PRD 五档与结局判据、场景关键目标、FactCode 定义 | 其他会话、用户账号信息 |

**Prompt 文件要求（`prompts/v1/*.txt`，变量 `{{name}}`；`PromptRenderer` 遇到未提供的变量或多余变量抛异常）：** 每个文件包含：角色职责与禁止行为（AGT-01～03 原文要点）、输入变量块、输出 JSON 示例与「只输出 JSON」声明（DeepSeek JSON 模式要求提示词包含 json 字样）、长度目标；`narrator.txt` 逐字写入 PRD §10.2 五档表与 6 条统一判断规则、§10.3 关键目标表、结局判据与优先关系；`victim.txt` 写入 BR-P12 允许/禁止行为；`report.txt` 写入第五阶段需要的结构骨架（本阶段不调用，但必须有效，因为会话要绑定其 ID）。

**`DeepSeekLlmClient`：** `RestClient` POST `{base-url}/chat/completions`，载荷 `{model, messages:[system,user], response_format:{type:"json_object"}, temperature, max_tokens, stream:false}`，非思考模式参数按部署时官方文档设置（记录在 README）；超时 = `min(call-timeout-ms, 剩余预算)`；HTTP 非 2xx、`content` 为空、`finish_reason=length`、JSON 解析失败 → `AgentCallException(retryable=true)`；日志只记角色、轮号、耗时、用量、错误类别，**不记录提示词与输出正文**。

**`ScriptedLlmClient`：** 按 `simulation/scripted/<剧本>.json` 返回固定输出；剧本键 `{role, roundNo, attempt}`，支持 `"error":"EMPTY"|"TIMEOUT"` 与 `"delayMs"`；剧本名通过会话所选骗子画像姓名映射（例如在冒烟中新增名为「剧本:goal-achieved-r6」的骗子画像），映射规则写在类注释与 README。

- [ ] **Step 1：写失败测试。**

```java
// AgentOutputValidatorTest
@Test void riskScore10_isRejectedNotRounded()
@Test void goalAchievedRequiresActualFraud()
@Test void cumulativeFactsCannotRevertToFalse()
@Test void actionNarrationInsideLineIsRejected()
@Test void undeterminedIsNeverAcceptedAsCandidate()
@Test void candidateMustMatchOutcomeRules_goalBeatsStopInSameRound()
// EvidenceValidatorTest
@Test void quoteMustBeSubstringOfReferencedContentOrAction()
@Test void victimActionEvidenceCannotCiteScammerClaim()
@Test void taskRebateNeedsTwoOrderedPayments_sameMessageUsesEventOrder()
@Test void impersonationLoanFailsGoalWhenVerificationPrecedesTransfer()
// AgentContextIsolationTest
@Test void victimPromptNeverContainsScammerIntentOrNarration()
@Test void scammerPromptNeverContainsVictimPsychologyOrRisk()
@Test void victimPromptOmitsScenarioGoalDefinition()
@Test void rounds1to4VictimPromptContainsMinimumRoundConstraint_round5DoesNot()
// PromptRendererTest
@Test void missingOrExtraVariableFailsFast()
@Test void allFourV1TemplatesRenderWithFullVariableSet()
// OutcomeRulesTest / ScenarioGoalRulesTest：PRD §6.2 结局表与 §10.3 判据逐行一个用例
```

- [ ] **Step 2：运行确认失败。**
- [ ] **Step 3：实现。** 校验器与规则类为纯函数，不访问数据库；`AgentRunner` 组合「装配上下文 → 渲染 → 调用 → 解析 → 校验」，不做重试（重试归预备区）。
- [ ] **Step 4：运行确认通过。** 可选：有 API Key 时运行一次 `DeepSeekLlmClientManualIT`（`@Tag("manual")`，默认不执行），只验证连通与 JSON 模式，不作质量结论。
- [ ] **Step 5：提交** `git commit -m "feat(agent): role contexts, prompt v1, LLM client and output/evidence validation"`

---

### Task 7：媒体层——分句、TTS 网关、临时音频（API-21）

**Files:**
- Create: `media/SentenceSegmenter.java`、`media/TtsClient.java`、`media/CosyVoiceGatewayClient.java`、`media/SilentTtsClient.java`、`media/SynthesizedAudio.java`、`media/TempAudioStore.java`
- Create: `simulation/web/AudioController.java`
- Create: `tools/tts-gateway/app.py`、`requirements.txt`、`voices.example.json`、`README.md`
- Test: `media/SentenceSegmenterTest.java`、`media/SilentTtsClientTest.java`、`simulation/AudioAccessTest.java`

**分句（`SentenceSegmenter.split(String content) -> List<Segment(text, charStart, charEnd)>`）：** 按码点遍历；在句末标点 `。！？!?；;…` 与逗号类 `，,、：:` 之后断开，标点归属前一分句；连续标点合并；仅由标点/空白组成的分句并入前一分句；结果按顺序无缝覆盖 `[0, codePointCount)`。`segmentation_version="1"`。

**网关契约（Java ↔ `tools/tts-gateway`，内网 HTTP）：**

```
POST /v1/synthesize   Content-Type: application/json
{"voiceId":"senior_female_a","speed":1.0,"segments":["妈，是我。","我手机坏了，","先用同事的号。"]}
200 {"format":"mp3","audioBase64":"...","segmentDurationsMs":[820,1040,1310],"totalDurationMs":3170}
4xx/5xx {"error":"..."}   → TtsException
GET  /healthz          → 200 {"model":"<CosyVoice版本>","voices":[...]}   （启动检查与 V-09 记录用）
```

- 网关（`app.py`，FastAPI）：启动时加载 CosyVoice 模型与 `voices.json`（每个音色 = 参考音频 + 参考文本，零样本克隆）；逐分句调用 CosyVoice 推理得到 PCM，**按实际采样点数计算每句时长**，拼接后用 ffmpeg（libmp3lame，单声道）转码为 MP3；`speed` 限制在 0.8～1.2；只监听内网地址，不对公网暴露。模型版本、采样率与硬件由 V-09 实测后写入 README，不在代码里假设。
- Java 侧 `CosyVoiceGatewayClient.synthesize(voiceId, ToneHint, segments, timeout) -> SynthesizedAudio(mp3Bytes, List<AudioSegment>, totalMs)`：按 `segmentDurationsMs` 累加得到 `startMs/endMs`，结合分句码点区间生成 `segments`；校验返回分句数与请求一致、时长均 ≥1，否则 `TtsException`。语气提示只映射到 `speed`（SLOW=0.9、NORMAL=1.0、FAST=1.1），不改动文本。
- `SilentTtsClient`（dev/test）：每分句时长 `max(600, 码点数 × 200) ms`；用程序生成的 MPEG-1 Layer III 静音帧拼接（帧头 `FF FB 10 C0`：32kbps、44.1kHz、单声道、无填充，帧长 104 字节，其余字节为 0，每帧 1152 采样 ≈ 26.122ms），帧数 = `ceil(总时长 / 26.122)`；返回真实可解码 MP3。支持剧本注入的失败（`tts-fail-r2`）。
- `TempAudioStore`：`{audio-temp-dir}/{sessionId}/{messageId}.mp3` 与同名 `.json`（`durationMs`、`segments`）；先写临时文件再原子移动；`release(sessionId)` 删除整个会话目录；`releaseAll()` 供启动清理；**不在数据库保存任何音频路径**。

**API-21 判定顺序：** 校验 `id`/`messageId` 格式 → 加载消息并沿 `message→round→session→user` 校验本人（否则 404）→ 会话非 RUNNING → 410 `AUDIO_EXPIRED` → 消息不属于当前轮 → 410 → `PENDING` → 409 `AUDIO_NOT_READY` → `COMPLETED`/`FAILED` → 410 → `PRESENTING` 且文件存在 → 200 `audio/mpeg`、`Cache-Control: no-store`、`X-Content-Type-Options: nosniff`，支持同一条消息的重复 GET（网络重取）；文件缺失 → 410。ADMIN 403。

- [ ] **Step 1：写失败测试。**
  - `SentenceSegmenterTest`：「妈，是我。我手机坏了！」→ 3 段且区间无缝；含 emoji（代理对）时按码点计数；纯标点结尾并入前段；无标点整句一段。
  - `SilentTtsClientTest`：输出以 `FF FB` 开头、长度为 104 的整数倍；`segments` 时间递增、末段 `endMs == totalDurationMs`；剧本失败抛 `TtsException`。
  - `AudioAccessTest`：PRESENTING 200 且头部正确；PENDING 409；COMPLETED 410；他人 404；ADMIN 403；终态 410。
- [ ] **Step 2：运行确认失败。**
- [ ] **Step 3：实现 Java 侧与网关。** 网关在没有 GPU 的开发机上可只做 `--dry-run`（返回正弦提示音）用于联调格式；真实合成留到 Task 15。
- [ ] **Step 4：运行确认通过**；若本机已部署 CosyVoice，手工 `curl` 网关一次并把 `healthz` 输出贴到 README 草稿。
- [ ] **Step 5：提交** `git commit -m "feat(media): sentence segmentation, CosyVoice gateway client, temp audio (API-21)"`

---

### Task 8：编排引擎（一）——预备区流水线、三类回执与正常推进（API-17、API-18、API-19）

**Files:**
- Create: `config/SimulationExecutorsConfig.java`（`lanes`：N 个单线程执行器按 `sessionId % N` 分配，保证同场串行、异场并行；`io`：虚拟线程执行器，只跑模型/TTS 调用；`scheduler`：`ThreadPoolTaskScheduler(2)` 跑停留与截止定时）
- Create: `simulation/engine/SessionLanes.java`、`SimulationOrchestrator.java`、`PreparationArea.java`、`PreparedTurn.java`（台词、动作、内部意图/心理、`SynthesizedAudio`）、`PreparedAnalysis.java`、`RoundScheduler.java`（本任务实现阅读停留到期；截止到期在 Task 9）
- Create: `simulation/service/PresentationReceiptService.java`；`simulation/web/dto/PresentationReceipt.java`、`PresentationResultView.java`
- Modify: `simulation/service/SimulationTransitions.java`（新增：`createFirstRoundOnOpening`、`createNextRound`、`saveMessage`、`markMessagePresenting`、`completeMessage`、`failMessage`、`saveAnalysis`、`completeAnalysisAndCheckFinish`）
- Modify: `simulation/web/SimulationController.java`（三个回执端点）
- Test: `simulation/PresentationReceiptRulesTest.java`、`simulation/OrchestratorFlowTest.java`、`simulation/MinimumRoundsPrecheckTest.java`、`simulation/PreparedContentNeverPublishedTest.java`

**短事务（每个方法：锁会话 → 核对 `status=RUNNING`、期望轮号/阶段、调用方捕获的 `state_version` → 写入 → `state_version+1` → 注册 afterCommit 发布）：**

| 方法 | 前置条件 | 写入 | 提交后发布 |
|---|---|---|---|
| `createFirstRoundOnOpening` | `current_round_no=0` | 插入第 1 轮 `SCAMMER_GENERATING`，`current_round_no=1` | `round.started` → `orchestrator.onRoundStarted` |
| `createNextRound(n)` | 第 n 轮 `COMPLETE`、`reading_until ≤ now`、未暂停、`n<20`、`now <` 有效截止 | 插入第 n+1 轮，`current_round_no=n+1` | `round.started` |
| `saveMessage(round, role, turn)` | 阶段 `SCAMMER_GENERATING`/`VICTIM_GENERATING` 且该角色无消息 | 插入消息 `PENDING`（`content`、`action_text`、`internal_note`），阶段 → `*_PRESENTING` | 无（等媒体就绪） |
| `markMessagePresenting(msgId)` | 消息 `PENDING`、临时音频已写入 | `PRESENTING` | `message.saved`（`LiveMessage` 含 `audioUrl/segments`） |
| `completeMessage(msgId)` | 当前轮当前消息 `PRESENTING` | `COMPLETED`、完成时间；骗子 → 阶段 `VICTIM_GENERATING`，受害者 → `ANALYZING` | 无（编排继续） |
| `saveAnalysis(round, analysis)` | 阶段 `ANALYZING`、双方 `COMPLETED` | 一次写入完整分析字段组、累计事实、`evidence_json`（候选证据中的受害者引用此时映射为真实 `message_id`）、候选结局、`analysis_saved_at`；阶段 → `ANALYSIS_PRESENTING` | `analysis.saved` |
| `completeAnalysisAndCheckFinish(round)` | 阶段 `ANALYSIS_PRESENTING` | `analysis_presented_at`、`reading_until=min(now+5s, 有效截止)`、阶段 `COMPLETE`；**同事务**判定正式结束（全局约束 5、6）：结束则写 `FINISHED`、`outcome_code/reason/limit_reason`、`ended_at`，并插入 `simulation_report(PENDING, attempt_no=0, queued_at=now)` | 结束：`session.finished` 再 `round.reading(REPORT)`；否则 `round.reading(NEXT_ROUND)` 并按 `reading_until` 定时 |

**编排流水线（`SimulationOrchestrator`，所有回调都投递回该场的 lane 执行；每个预备任务携带 `epoch`，会话终态或丢弃时 `epoch` 失效）：**

```text
onSessionStarted        → prepareScammer(1)
onRoundStarted(n)       → 等 scammer(n) 就绪（暂停中则挂起）→ saveMessage → 写临时音频 → markMessagePresenting
                          → prepareVictim(n)                                   // 骗子呈现期间
prepareVictim(n):
   for attempt in 0..2:                                                        // BR-P12：最多重新生成 2 次
       victim  = AgentRunner.victim(context(n, attempt>0 ? 强化约束 : 无))     // 透明重试另计
       judged  = AgentRunner.narrator(context(n, victim))                      // 对候选回应做候选分析
       if n < 5 and judged.candidateOutcome != null: discard(victim, judged); continue
       accept(victim, judged); break
   if 未接受: 标记 victim(n) 失败(DIALOGUE_FAILED)                             // 到阶段时中断
   audio = TTS(victim.line)                                                    // 与下一项并行
   if n < 20 and judged.candidateOutcome == null: prepareScammer(n+1)          // 基于被接受的回应
onScammerCompleted(n)   → 等 victim(n) 就绪 → saveMessage(VICTIM) → 写音频 → markMessagePresenting
onVictimCompleted(n)    → 等 analysis(n) 就绪 → saveAnalysis
onAnalysisCompleted(n)  → （事务已在回执中完成）结束则 discardAll + 释放临时音频；否则 RoundScheduler.at(reading_until, onReadingExpired(n))
onReadingExpired(n)     → 暂停中则等待继续；否则 createNextRound(n)
```

- **透明重试**：模型/TTS 调用失败且 `retryable` 时，在 `transient-retries` 次数与剩余有效预算内重试；每次调用前后都核对 `epoch` 与会话状态；结果只进预备区。
- **轮到时未就绪**：阶段已到但预备未完成 → 不做任何事（前端已因 `round.started`/上一回执显示等待态），完成后立即保存发布；**不得**因等待而跳过台词或缩短语音。
- **轮到时已失败**：按类别中断（Task 9 实现 `interrupt`，本任务先调用接口）：骗子/受害者生成失败 → `DIALOGUE_FAILED`；分析失败 → `ANALYSIS_FAILED`；TTS 失败 → 先 `saveMessage` 保存已通过校验的文字（历史可见），再中断 `TTS_FAILED`。
- **第 20 轮**：永不调用 `prepareScammer(21)`；`createNextRound(20)` 直接拒绝。
- **暂停**：预备计算继续，但 `saveMessage`/`markMessagePresenting`/`saveAnalysis`/`createNextRound` 全部挂起到继续后执行（Task 10 接入暂停标志，本任务预留 `isPaused` 判断点）。

**回执服务（`PresentationReceiptService`，判定顺序固定）：**
1. 请求体校验：`status` 必填；`COMPLETED` 带 `errorCode` 或 `FAILED` 缺 `errorCode` → 400；`errorCode` 超出该回执作用域（全局约束 12）→ 400；未知字段 400。
2. 锁会话并校验归属（404）；路径中的 `messageId`/`roundNo` 必须属于该会话（404）。
3. 会话已终态：若该对象已有相同的已完成事实 → `ALREADY_APPLIED`，否则 `IGNORED_TERMINAL`；均返回 200 + 当前状态，**不写任何字段**。
4. 对象已有相同事实 → `ALREADY_APPLIED`；已有相反事实 → 409 `PRESENTATION_CONFLICT`。
5. 阶段不符、未来轮、非当前消息、消息仍 `PENDING` → 409 `STAGE_CONFLICT`。
6. `COMPLETED` → 对应事务，`APPLIED`；`FAILED` → 消息置 `FAILED`（开场/分析不改消息）并中断 `MEDIA_FAILED`，`APPLIED`。
7. 返回 `{effect, state}`；提交后通知编排器。

- [ ] **Step 1：写失败测试。**

```java
// PresentationReceiptRulesTest（服务层，仓库与编排器 mock）
@Test void openingCompleted_createsRound1_onceOnly_secondIsAlreadyApplied()
@Test void openingFailed_withAudioErrorCode_is400_outOfScope()
@Test void messageCompleted_forNonCurrentMessage_is409StageConflict()
@Test void messageCompleted_whilePending_is409StageConflict()
@Test void completedThenFailed_sameMessage_is409PresentationConflict()
@Test void receiptAfterTerminal_isIgnoredTerminal_andWritesNothing()
@Test void messageFailed_marksFailedAndInterruptsMediaFailed()
@Test void receiptBody_withOutcomeCode_is400UnknownField()
@Test void otherUsersMessageId_is404_mismatchedParent_is404()
// OrchestratorFlowTest（ScriptedLlmClient + SilentTtsClient + 内存假仓库或 Mockito；固定 Clock）
@Test void fiveRoundsThenNotDefrauded_finishesWithPendingReportInSameTransaction()
@Test void goalAchievedAndStopInSameRound_outcomeIsGoalAchieved()
@Test void round20WithoutOutcome_finishesUndeterminedRoundLimit_neverCreatesRound21()
@Test void nextScammerIsPreparedDuringVictimPresentation_butSavedOnlyAfterReading()
@Test void eventsOrder_finishedBeforeRoundReadingReport()
// MinimumRoundsPrecheckTest
@Test void round3CandidateWithOutcome_isDiscardedAndRegenerated_neverSavedNorPublished()
@Test void thirdNonCompliantCandidate_interruptsDialogueFailed()
@Test void round5RefusalFinishesNotDefrauded()
@Test void round2InitialPaymentIsRecordedAsFactWithoutOutcome()
// PreparedContentNeverPublishedTest
@Test void preparedNextRoundIsDiscardedOnFinish_andNeverAppearsInDbOrSse()
```

- [ ] **Step 2：运行确认失败。**
- [ ] **Step 3：实现。** 先实现 `SimulationTransitions` 的事务方法（每个都有「版本不符 → 返回 `Stale`」分支，调用方据此丢弃），再实现编排器；所有 `CompletableFuture` 回调用 `lanes.execute(sessionId, ...)` 回到串行通道，禁止在 io 线程直接写库。
- [ ] **Step 4：运行确认通过**；`dev` 配置下以 `scripted`+`silent` 启动后端，用浏览器开发者工具或 `curl -N` 观察一场 5 轮演绎的事件顺序（前端尚未完成，可用 Task 14 的驱动脚本雏形手动发送回执）。
- [ ] **Step 5：提交** `git commit -m "feat(simulation): orchestrator pipeline, preparation area, presentation receipts (API-17/18/19)"`

---

### Task 9：编排引擎（二）——退出、时限、故障、失联与重启收敛（API-20）

**Files:**
- Create: `simulation/service/ExitService.java`、`simulation/web/dto/ExitRequest.java`
- Create: `simulation/engine/ConnectionMonitor.java`、`simulation/engine/SimulationRecovery.java`
- Create: `simulation/rules/DeadlineCalculator.java`
- Modify: `simulation/engine/RoundScheduler.java`（截止定时 + 每秒兜底扫描 `idx_session_status_deadline`）
- Modify: `simulation/service/SimulationTransitions.java`（新增 `interrupt(sessionId, code, expectedVersion|ANY)`、`finishOnDeadline(sessionId)`）
- Modify: `simulation/web/SimulationController.java`（`POST /simulations/{id}/exit`）
- Test: `simulation/DeadlineCalculatorTest.java`、`simulation/LateAgentResultIsDiscardedTest.java`、`simulation/ExitAndDeadlineTest.java`、`simulation/RecoveryTest.java`

**规则：**
- `DeadlineCalculator.effectiveDeadline(session, now)`：未暂停 → `deadline_at`；暂停中 → `deadline_at + min(now − paused_at, 300000 − paused_total_ms)`。`isExpired(session, now) = now ≥ effectiveDeadline`。所有「剩余预算」都由它计算。
- **到时处理 `finishOnDeadline`**（锁会话后重新计算，未到则重新定时并返回）：
  - 当前轮 `phase=COMPLETE`（完整轮位置，含其阅读停留期间）→ `FINISHED`：有候选结局用候选（正常情况下回执时已结束，这里是兜底）；第 20 轮记 `BOTH`；否则 `UNDETERMINED`+`TIME_LIMIT`；同事务建 PENDING 报告；发布 `session.finished` 与 `round.reading(REPORT)`（`reading_until` 截到截止时刻）。
  - 其他任何位置（含 `current_round_no=0` 开场中、新建但尚无消息的轮次，见 D-2）→ `INTERRUPTED`+`TIME_LIMIT`。
  - 第 1～4 轮的完整轮位置到时也是 `UNDETERMINED`/`TIME_LIMIT`（数据库 CHECK 允许）。
- **退出 `ExitService.exit(userId, sessionId, reason)`**：`reason ∈ {NAVIGATION, REFRESH, CLOSE, LOGOUT}`；RUNNING → `interrupt(USER_EXIT)`；FINISHED/INTERRUPTED → 原样返回 200（正式结束不回退）。登出（`HttpSessionDestroyedEvent` 中该用户 RUNNING 会话）按 `USER_EXIT` 收敛；收敛失败不阻止登出。
- **中断 `interrupt`**：原子 RUNNING→INTERRUPTED，写 `interruption_code`、服务端文案、`ended_at=now`；暂停中先结算计入时长再清空 `paused_at`；当前处于 `PRESENTING` 的消息**保持真实状态**（只有收到 FAILED 回执的才置 FAILED）；提交后：丢弃预备区（`epoch` 失效、尝试取消在途请求）、释放临时音频、发布 `session.interrupted`、关闭该场 SSE。
- **迟到结果**：模型/TTS 回调中 `epoch` 失效或会话非 RUNNING → 直接丢弃，不写库、不发布；事务方法版本不符返回 `Stale` → 丢弃；这条路径要有 INFO 级日志（只记 ID 与类别）。
- **保存失败**：任一事务抛出数据库异常 → 停止该场推进 → 尝试 `interrupt(PERSISTENCE_FAILED)`；若仍失败，不发布任何「已中断」事件，标记该场为「待收敛」，由每 10 秒的收敛任务在数据库恢复后核对真实状态再收敛；前端此时只会因心跳/事件缺失显示「状态未知」。
- **失联 `ConnectionMonitor`**（每 5 秒）：仅当同时满足接口文档 §9.1 三条件才 `interrupt(CONNECTION_LOST)`：① 该场无活动 SSE 连接且断开已超过 `lost-grace-ms`；② 断开以来没有收到该场任何回执/暂停/继续/退出请求；③ 已过有效截止，或剩余有效预算小于当前待呈现内容所需时长（当前 `PRESENTING` 消息的 `durationMs`，分析阶段按 1 秒计）。切标签、单次心跳延迟、页面隐藏都不触发。
- **重启收敛 `SimulationRecovery`**（`ApplicationRunner`，最高优先级）：在接受开始请求与启动 lanes 之前：所有 RUNNING 会话 → `INTERRUPTED`+`SERVICE_RESTARTED`（`ended_at=now`，暂停中先结算），即使已有 COMPLETE 轮次也**不**补写正式未定；`TempAudioStore.releaseAll()`；完成前开始请求返回 503 `SERVICE_UNAVAILABLE`。FINISHED 缺报告行的修复属于下一阶段，本阶段只记录 ERROR 日志。

- [ ] **Step 1：写失败测试。**

```java
// DeadlineCalculatorTest
@Test void notPaused_effectiveIsStoredDeadline()
@Test void paused_addsElapsedUpToRemainingCredit()            // 已计入 200s，暂停 150s → 只加 100s
@Test void creditExhausted_pausedSessionExpiresInRealTime()
// ExitAndDeadlineTest（固定可推进 Clock）
@Test void deadlineAtCompleteRound_finishesUndeterminedTimeLimit_withPendingReport()
@Test void deadlineDuringVictimSpeech_interruptsTimeLimit_noReport_halfRoundKept()
@Test void deadlineDuringOpening_interruptsTimeLimit()
@Test void deadlineAtCompleteRound4_isUndeterminedNotInterrupted()
@Test void exitWhileRunning_interruptsUserExit_releasesAudio_closesStream()
@Test void exitAfterFinished_returnsFinishedUnchanged()
@Test void finishAndExitRace_onlyOneTerminalTransitionCommits()
// LateAgentResultIsDiscardedTest
@Test void victimArrivesAfterInterrupt_isNotSavedNorPublished()
@Test void staleStateVersion_transitionReturnsStale_andNothingChanges()
// RecoveryTest
@Test void runningSessionsBecomeServiceRestarted_beforeStartsAreAccepted()
@Test void connectionLost_requiresAllThreeConditions()       // 缺任一条件均保持 RUNNING
```

- [ ] **Step 2：运行确认失败。**
- [ ] **Step 3：实现。** 截止定时在会话开始、继续演绎时（Task 10）注册；兜底扫描只查 `status='RUNNING' AND deadline_at <= now`（未暂停会话）和 `paused_at IS NOT NULL` 的少量会话。
- [ ] **Step 4：运行确认通过**；全部既有测试仍通过。
- [ ] **Step 5：提交** `git commit -m "feat(simulation): exit, deadline convergence, failure mapping, connection-lost and restart recovery (API-20)"`

---

### Task 10：设置暂停与继续（API-43、API-44）

**Files:**
- Create: `simulation/service/PauseService.java`、`simulation/web/dto/PauseResultView.java`
- Modify: `simulation/service/SimulationTransitions.java`（`pause`、`resume`）、`simulation/engine/SimulationOrchestrator.java`（挂起/放行发布）、`RoundScheduler.java`（暂停时取消停留与截止定时；继续时按新值重排）
- Modify: `simulation/web/SimulationController.java`（两个端点，无请求体；带请求体 → 400）
- Test: `simulation/PauseResumeTest.java`

**规则（数据库 §7.9、接口 API-43/44）：**
- `pause`：锁会话；终态 → `IGNORED_TERMINAL`（正式结束后的最后停留中打开设置属于此情形）；已暂停 → `ALREADY_APPLIED`；否则写 `paused_at=now`、`state_version+1` → `APPLIED`，提交后发布 `session.paused`，编排器进入挂起，`RoundScheduler` 取消该场停留定时并把截止定时改为 `paused_at + (300000 − paused_total_ms)`（计入额度耗尽时刻，到时按实时截止判断）。
- `resume`：锁会话；终态 → `IGNORED_TERMINAL`；未暂停 → `ALREADY_APPLIED`；否则 `credit = max(0, min(now − paused_at, 300000 − paused_total_ms))`；`paused_total_ms += credit`、`deadline_at += credit`；当前轮 `reading_until` 非空且晚于 `paused_at` 时 `+= credit`；清空 `paused_at`；`state_version+1` → `APPLIED`；提交后发布 `session.resumed`，按新的 `reading_until`、`deadline_at` 重排定时，并依次放行挂起的保存/发布动作。
- 暂停中到达「计入额度耗尽后的实时截止」→ 走 Task 9 的 `finishOnDeadline`（同事务结算并清空 `paused_at`）。
- 暂停中收到的回执照常落库（前端规则是暂停中不发，但服务端不能拒绝合法事实），只是其后的推进动作挂起。
- 暂停不改变消息、分析、风险、结局与 `started_at`；不写暂停历史。

- [ ] **Step 1：写失败测试。**

```java
@Test void pause_then_resumeAfter120s_extendsDeadlineAndReadingUntilBy120s()
@Test void repeatedPause_isAlreadyApplied_versionUnchanged()
@Test void resumeWithoutPause_isAlreadyApplied()
@Test void cumulativeCreditCapsAt300s_acrossMultiplePauses()
@Test void pausedLongerThanRemainingCredit_convergesByDeadlineWhilePaused_pausedAtCleared()
@Test void noRoundStartedOrMessageSavedIsPublishedWhilePaused()
@Test void pauseAfterFinished_isIgnoredTerminal()
@Test void pauseOtherUsersSession_is404_admin403()
```

- [ ] **Step 2：运行确认失败。**
- [ ] **Step 3：实现。**
- [ ] **Step 4：运行确认通过。**
- [ ] **Step 5：提交** `git commit -m "feat(simulation): settings pause and resume with capped deadline credit (API-43, API-44)"`

---

### Task 11：前端（一）——演绎接口、「进入演绎」、演绎页加载与开始

**Files:**
- Modify: `frontend/src/api/types.ts`（`SimulationState`、`LiveRound`、`LiveMessage`、`AudioSegment`、`Analysis`、`Outcome`、`Interruption`、`ReportSummary`、`StartRequest`、`TimingEvidence`、`TimingSample`、`PresentationReceipt`、`PresentationResult`、`PauseResult`、`ExitReason`、11 类 SSE 事件联合类型，字段与 yaml 逐一对应）
- Modify: `frontend/src/api/http.ts`（新增 `postKeepalive(path, body)`：`fetch(..., {keepalive:true})`，带内存中的 CSRF 令牌，不等待结果、不抛错）
- Create: `frontend/src/api/simulation.ts`（`start`、`state`、`openStream`（返回封装后的 EventSource）、`presentOpening`、`presentMessage`、`presentAnalysis`、`exit`、`exitKeepalive`、`pause`、`resume`、`audioUrl`）、`frontend/src/api/timing.ts`
- Create: `frontend/src/simulation/timingSampler.ts`、`assetPreloader.ts`、`liveGuard.ts`
- Create: `frontend/src/views/SimulationView.vue`（本任务完成：加载 → 就绪 → 开始 → 订阅 → 进入开场的入口）、`frontend/src/views/ReportEntryView.vue`
- Modify: `frontend/src/views/ConfigurationView.vue`、`frontend/src/router/index.ts`
- Test: `simulation-api.spec.ts`、`timing-sampler.spec.ts`、`simulation-view.spec.ts`（加载与开始部分）、`configuration.spec.ts`（修改）

**行为要求：**
- **配置页**：按钮文案改为「进入演绎」，说明文字改为「进入演绎页后，资源加载完成即可点击开始演绎」；点击时仍按第二阶段逻辑重新核验双方详情（停用项清除并要求重选）；成功后 `router.push({ path: '/simulation', query: { scenarioId, scammerProfileId, victimProfileId } })`；**不发送 POST、不建立记录、不计时**；删除「开始演绎将在下一阶段开放」提示。
- **路由**：`/simulation`（`role: 'USER'`，隐藏 `AppNav`，全屏舞台布局）；`/simulations/:id/report`（`role: 'USER'`，`ReportEntryView`）。缺少或非法 query → 回 `/configuration`。
- **加载阶段（不计时）**：并行执行 ①`GET /scenarios` 取所选场景、`GET /scammer-profiles/{id}`、`GET /victim-profiles/{id}`（任一 404 → 「所选画像已不可用，请返回重新选择」+ 返回配置页按钮）；②`assetPreloader` 预加载四张立绘（`Image` + `decode()`，任一失败显示「人物形象加载失败」+ 重试）；③`timingSampler.sample()`。全部完成后显示「开始演绎」按钮；加载中显示清晰等待态。
- **`timingSampler`**：记录 `performance.now()` 发送/接收值；在 `expiresAt` 前 15 秒（按服务端时间换算）自动重新采样，保证用户长时间停留后点击仍有有效样本；提供 `serverNow()`：`serverSampledAt + (performance.now() − (send+receive)/2)`，供阅读停留与截止的本地换算。
- **开始**：点击即记录 `clientClickMonotonicMs = performance.now()` 与 `startRequestId = crypto.randomUUID()`（该次点击固定，网络重发沿用）；按钮立即禁用；请求结果：
  - 201/200 → 写 `sessionStorage['anti-scam.live-session'] = sessionId`（刷新识别用）、打开 SSE、比对快照四个立绘键与预加载键（不一致则补充加载，计时已开始）、进入开场（Task 12）。
  - 409 `ACTIVE_SIMULATION_EXISTS` → 「你已有一场进行中的演绎（可能在其他窗口或标签页），请先在原页面完成或退出」，提供返回首页；不提供接管或续演。
  - 409 `PROFILE_UNAVAILABLE` → 返回配置页并提示重选。
  - 400 且字段以 `timing.` 开头 → 重新采样，提示「计时校准失败，请再次点击开始演绎」（新点击生成新的 `startRequestId`）。
  - 网络失败/结果未知 → 用**同一** `startRequestId` 与同一 `timing` 重发一次；仍失败提示并停留在就绪态。
  - 503 `TIMING_PROTOCOL_NOT_READY`/`SERVICE_UNAVAILABLE` → 提示稍后再试。
- **`liveGuard`**：
  - 挂载时若 `sessionStorage` 有标记 → 视为刷新：`GET state`，RUNNING 则 `POST exit {reason:'REFRESH'}`；显示终态说明（Task 13 的 `TerminalNotice`）；清除标记。**不恢复演出。**
  - `pagehide` → `exitKeepalive(id, 'CLOSE')`（仅在 RUNNING 且未进入终态时）。
  - `onBeforeRouteLeave`（浏览器后退等站内导航）：RUNNING 时弹出页内确认「离开将中断本次演绎，确定离开吗？」，确认后 `POST exit {reason:'NAVIGATION'}` 再离开；取消则留在页面（计时继续）。
  - 进入终态或正式进入复盘入口时清除标记。
  - 不使用 `visibilitychange` 推断退出；不使用无法携带 CSRF 头的 `sendBeacon`。
- **`ReportEntryView`**：`GET /simulations/{id}`，展示正式结局与依据、报告状态「生成中」，并说明「复盘报告页面将在下一阶段开放」；INTERRUPTED 显示「本次演绎已中断，不生成正式复盘」；非本人或不存在 → 统一「记录不存在或不可访问」。

- [ ] **Step 1：写失败测试。**
  - `configuration.spec.ts`：按钮为「进入演绎」；核验通过后跳转 `/simulation?...` 且**没有**任何 POST 请求。
  - `timing-sampler.spec.ts`（假 `performance.now` 与假定时器）：证据三值满足 `send ≤ receive ≤ click`；临近过期自动重采样；`serverNow()` 换算正确。
  - `simulation-api.spec.ts`：开始请求体恰为五个字段；回执请求体形状；`exitKeepalive` 使用 `keepalive:true` 且带 `X-CSRF-TOKEN`；暂停/继续无请求体。
  - `simulation-view.spec.ts`：资源未就绪时无「开始演绎」按钮；就绪后出现；点击后按钮禁用且只发一次 POST；409 ACTIVE 文案；刷新标记存在时调用 `exit(REFRESH)` 且不播放任何内容。
- [ ] **Step 2：运行确认失败** `npm.cmd test`。
- [ ] **Step 3：实现。**
- [ ] **Step 4：运行确认通过**；`npm.cmd run build`（含 `vue-tsc`）通过。
- [ ] **Step 5：提交** `git commit -m "feat(web): enter-simulation flow, loading, timing sampler and start (API-42, API-14)"`

---

### Task 12：前端（二）——演出控制器、开场与舞台

**Files:**
- Create: `frontend/src/simulation/performanceDirector.ts`、`eventInbox.ts`、`segmentReveal.ts`、`audioTrack.ts`
- Create: `frontend/src/components/simulation/OpeningScene.vue`、`scenes/TaskRebateScene.vue`、`scenes/FakeInvestmentScene.vue`、`scenes/ImpersonationLoanScene.vue`、`CharacterCards.vue`、`SimulationStage.vue`、`StageCharacter.vue`、`SpeechBubble.vue`、`NarrationPanel.vue`、`RiskNodeBar.vue`、`WaitingHint.vue`
- Modify: `frontend/src/views/SimulationView.vue`
- Test: `segment-reveal.spec.ts`、`event-inbox.spec.ts`、`performance-director.spec.ts`

**演出控制器（`performanceDirector.ts`，纯 TS，唯一时钟）：**

| 状态 | 进入条件 | 时钟 | 退出动作 |
|---|---|---|---|
| `OPENING_SCENE` | 开始成功、补充加载完成 | 单调时钟；场景动画自身 8～12 秒，硬上限 15 秒 | → `OPENING_CARDS` |
| `OPENING_CARDS` | 场景结束 | 骗子卡 3.5 秒 + 受害者卡 3.5 秒（合计 ≤8 秒） | 提交开场 COMPLETED → `WAITING` |
| `WAITING` | 收到 `round.started` 或回执后等待下一条内容 | 无 | 收到 `message.saved`/`analysis.saved` |
| `SPEAKING(msg)` | `message.saved`（`PRESENTING`） | `audio.currentTime` | 音频 `ended` 且文字完整、嘴部回闭嘴帧 → 提交消息 COMPLETED → `WAITING` |
| `NARRATING(round)` | `analysis.saved` | 单调时钟 | 解说与风险渲染完成 → 提交分析 COMPLETED → `READING` |
| `READING(until)` | `round.reading` | `serverNow()` 对比 `readingUntil` | `NEXT_ROUND`：到期后转 `WAITING`（等服务端 `round.started`，到期 3 秒仍未收到则 `GET state` 核对）；`REPORT`：到期后跳转 `/simulations/:id/report` |
| `FROZEN(prev)` | 打开设置 | 全部冻结 | 继续演绎后回到 `prev` 原位置 |
| `TERMINAL` | `session.interrupted`，或 FINISHED 且无停留 | — | — |

- 控制器只消费服务端已保存事实形成的事件，确定性地转换为演出指令；不自行推进轮次、不调用模型。
- **暂停期间产生的回执**（例如冻结前刚好完成）排队，继续后再提交；冻结中不提交任何回执。
- **本地截止**：`serverNow() ≥ deadlineAt` 且未暂停 → 立即停止播放与推进，显示「已达到时长上限，正在确认结果…」，每 2 秒 `GET state` 直到收到终态；**不自行显示结局**。

**舞台与呈现（PRD 第 14 章、PC-P04、PC-P06）：**
- 布局：顶部右上角仅一个设置按钮；中部舞台左骗子、右受害者（位置固定、中间视觉分隔）；下方独立解说区 + 五节点风险条。PC 浏览器优先（C-01），最小宽度 1280 下不出现横向滚动。
- `StageCharacter`：显示闭嘴帧；待机为 CSS 轻微上下浮动（约 3 秒周期）；说话时每 120 ms 在闭嘴/张嘴帧间切换并伴随轻微身体位移，当前发言者放大 4% 并提高亮度，非发言者保持待机；说完回到闭嘴帧。两帧都用已预加载的 `<img>` 叠放切换 `opacity`，避免切换时重新解码；任一图片 `error` → `AVATAR_LOAD_FAILED`（开场）或 `ANIMATION_FAILED`（消息）回执。
- `SpeechBubble`：骗子气泡在左、受害者在右；台词按 `segmentReveal` 逐字显示；动作说明以不同样式（斜体、灰色、前缀「动作：」）显示在气泡下方，**不朗读**；超出高度时气泡内滚动、不截断。
- `segmentReveal(currentMs, segments, content) -> visibleCodePoints`：在当前分句内按 `(currentMs − startMs)/(endMs − startMs)` 比例显示该分句码点，之前分句全部显示；`ended` 时强制全部显示。按码点切片（`Array.from`），避免截断代理对。
- `audioTrack`：每条消息一个 `HTMLAudioElement`，`src = audioUrl`，`preload='auto'`；静音用 `muted=true`（同一时间轴，仍以 `ended` 判定完成）；`error` → `AUDIO_LOAD_FAILED`；`play()` 被拒（含自动播放限制）→ `AUDIO_PLAYBACK_FAILED`；播放中 `currentTime` 连续 10 秒不前进且未暂停 → `AUDIO_PLAYBACK_FAILED`。逐字进度监听 `timeupdate` 并在页面可见时叠加 `requestAnimationFrame` 平滑，后台标签不依赖动画帧。「开始演绎」「继续演绎」两次点击都是用户手势，用来解锁播放。
- **气泡保留与清理**：骗子说完保留左气泡；受害者说话时左右同时显示；解说与停留期间都保留；收到下一轮 `round.started` 时只清空前端两个气泡（不调用任何接口）。
- `NarrationPanel`：受害者说完后显示本轮「诈骗阶段 / 手段分析 / 话术分析 / 受害者心理 / 风险依据」五项与「第 N 轮」标签，纯文字；下一轮演出期间保留上一轮内容与轮次标签，直到被新分析替换；不提前展示。
- `RiskNodeBar`：0/25/50/75/100 五节点，高亮当前档位并显示依据；开场显示「尚未评分」（不是 0）；下一轮演出期间保持上一轮档位。
- **开场**：`OpeningScene` 按 `configuration.scenario.code` 选择三段预制场景动画之一（纯 CSS/SVG 关键帧，不依赖外部资源；画面不随名称简介编辑而变），叠加显示快照中的场景名称与简介；动画 `animationend` 未在上限 + 1 秒内触发 → `ANIMATION_FAILED`。`CharacterCards` 依次展示双方半身形象（闭嘴帧）、姓名、性别、年龄、身份（全部取快照）。
- `WaitingHint`：轻量「对方正在组织语言…」等待态，只在 `WAITING` 且超过 600 ms 未收到内容时出现。
- `eventInbox`：按 `eventId` 去重；消息与分析按对象 ID/轮号合并；`stateVersion` 用 `BigInt` 比较，较旧的会话级快照不回退本地状态；`session.state`/`session.paused`/`session.resumed` 以服务端状态覆盖本地暂停态、`deadlineAt`、`readingUntil`。

- [ ] **Step 1：写失败测试。**
  - `segment-reveal.spec.ts`：分句边界严格对齐；中途进度只显示到当前分句内比例位置；emoji 不被截半；`ended` 全显示。
  - `event-inbox.spec.ts`：重复 `eventId` 只处理一次；`stateVersion` "10" 大于 "9"（不按字典序）；旧快照不覆盖新状态。
  - `performance-director.spec.ts`（假音频、假时钟）：开场两段总时长不超过 15+8 秒并恰好提交一次开场回执；骗子 `ended` 前不提交回执；静音时仍以 `ended` 完成且逐字进度与非静音一致；受害者说话时左气泡仍在；`analysis.saved` 后提交分析回执；`round.started`(n+1) 才清空气泡且解说保留并标注第 n 轮；`REPORT` 停留到期跳转；`play()` 被拒 → 提交 `AUDIO_PLAYBACK_FAILED`；本地到时停止推进且不显示结局。
- [ ] **Step 2：运行确认失败。**
- [ ] **Step 3：实现。** 组件只读控制器状态并上报 DOM 事件（`ended`、`animationend`、`error`），所有决策在控制器内。
- [ ] **Step 4：运行确认通过**；在 `dev`（`scripted`+`silent`）下人工完整看一场，核对 AC-13、AC-14、AC-28、AC-29 的可见效果并截图存入 `.tools/`（不提交）。
- [ ] **Step 5：提交** `git commit -m "feat(web): performance director, opening sequence, stage, bubbles, narration and risk bar"`

---

### Task 13：前端（三）——设置界面、终态与重连核对

**Files:**
- Create: `frontend/src/components/simulation/SettingsOverlay.vue`、`TerminalNotice.vue`
- Modify: `frontend/src/simulation/performanceDirector.ts`、`frontend/src/views/SimulationView.vue`
- Test: `settings-overlay.spec.ts`、`performance-director.spec.ts`（追加）、`simulation-view.spec.ts`（追加）

**行为要求：**
- **打开设置**：先本地冻结（暂停音频、逐字、嘴部切换、动画、停留倒计时），再 `POST pause`；结果未知时保持冻结并 `GET state` 核对 `paused` 后再决定是否重发；`IGNORED_TERMINAL`（正式结束后的最后停留）只冻结本地跳转倒计时。
- **设置界面**（覆盖在舞台之上）：「声音」开关（只改 `muted`，不调用接口，不算故障）、「继续演绎」、「退出演绎」。
- **继续演绎**：`POST resume` → 用返回的 `deadlineAt`、`readingUntil` 更新本地 → 从冻结位置恢复（`audio.play()` 从原 `currentTime` 继续，不重播已完成台词，不重新开始本轮）→ 提交冻结期间排队的回执。返回 `IGNORED_TERMINAL` → 关闭设置并按终态处理。
- **退出演绎**：页内二次确认「退出后本次演绎将中断且不生成正式复盘」（正式结束后的最后停留中文案改为「将直接进入复盘」）；确认后 `POST exit {reason:'NAVIGATION'}`：返回 INTERRUPTED → 显示 `TerminalNotice`；返回 FINISHED → 跳转复盘入口。
- **服务端在暂停中收敛**（累计计入超过 300 秒后到时）：收到 `session.finished`/`session.interrupted` 时关闭设置界面并按终态处理。
- **`TerminalNotice`**：显示 `interruption.message`；保留舞台上已显示的内容但停止一切播放；提供「重新开始体验」（→ `/configuration`，再次体验将创建新记录）与「返回首页」；不提供续演、重播、下载。
- **SSE 断线**：EventSource 自动重连；`onerror` 时调用 `GET /auth/me`，401 则关闭流并引导登录（不无限重连）；重连后的 `session.state` 用于核对：已 `COMPLETED` 的本轮消息直接完整显示、不重播；当前 `PRESENTING` 且本地正在播放的消息继续；本地未播放的 `PRESENTING` 消息开始播放；会话已终态则显示终态。**刷新/关闭不在此列**（由 `liveGuard` 按退出处理）。
- **登出**：演绎页不提供登出入口；若 Session 过期（401），停止演出并引导登录（服务端按登出收敛为 USER_EXIT）。

- [ ] **Step 1：写失败测试。**
  - `settings-overlay.spec.ts`：三个控件齐全；退出需二次确认；声音开关不发请求。
  - `performance-director.spec.ts`（追加）：说话中打开设置 → 音频暂停、逐字停止、不提交回执；继续后从原 `currentTime` 恢复且回执只提交一次；停留中暂停 → 倒计时冻结、继续后按新的 `readingUntil` 继续；暂停中收到 `session.interrupted` → 关闭设置并显示终态。
  - `simulation-view.spec.ts`（追加）：重连 `session.state` 中已完成消息不重复播放；401 后不再重连。
- [ ] **Step 2：运行确认失败。**
- [ ] **Step 3：实现。**
- [ ] **Step 4：运行确认通过**；`npm.cmd test`、`npm.cmd run build` 全部通过。
- [ ] **Step 5：提交** `git commit -m "feat(web): settings pause/resume, mute, exit confirmation, terminal states and reconnect reconciliation"`

---

### Task 14：隔离实例端到端验收与 3 场并行（脚本模型 + 静音合成）

**Files:**
- Create: `tools/smoke-simulation.ps1`（沿用 `smoke-admin.ps1` 写法：PowerShell 7.5+、MySQL 8.0.45、Java 21；端口 **13310/18083**；`.tools/simulation-smoke-<随机值>` 下创建隔离库、头像目录与临时音频目录；以 `dev` profile + `APP_SIMULATION_LLM_PROVIDER=scripted` + `APP_SIMULATION_TTS_PROVIDER=silent` 启动打包后的 jar；结束关闭自己启动的进程并保留日志；参数 `-IncludeLongCases` 打开 600 秒级用例）
- Create: `tools/simulation-driver.mjs`（Node 22 原生 `fetch`；自写最小 SSE 解析器读取 `text/event-stream`；维护 Cookie 与 CSRF；按剧本发送回执，模拟前端的「收到事件 → 等待音频时长 → 回执」节奏）
- Modify: `README.md`（仅追加运行方式，验收记录在 Task 15 统一写）

**准备：** 脚本先用管理员接口上传立绘并新增若干名为「剧本:<剧本名>」的骗子画像（`ScriptedLlmClient` 据此选择剧本），再注册 4 个普通用户。

**必须覆盖的场景与断言（每条在驱动脚本中是一个具名用例，失败即退出非 0）：**

| 用例 | 断言 | 对应 |
|---|---|---|
| S01 正常 5 轮未受骗 | 事件顺序 `session.state → round.started → message.saved×2 → analysis.saved → round.reading` ×5，末轮 `session.finished` 先于 `round.reading(REPORT)`；库中 5 轮 10 条消息、`outcome=NOT_DEFRAUDED`、报告行 `PENDING/attempt_no=0` | AC-16、AP-02、BR-P07 |
| S02 第 6 轮目标达成后同轮停止 | `GOAL_ACHIEVED`；无第 7 轮；第 7 轮骗子预备内容未入库 | AP-03 |
| S03 20 轮无结局 | `UNDETERMINED`+`ROUND_LIMIT`；`max(round_no)=20` | AP-04、DBV-06 |
| S04 第 3 轮候选形成结局后重新生成 | 被丢弃候选的台词从未出现在任何事件、响应与数据库中；第 3 轮无候选结局 | AP-10、BR-P12 |
| S05 第 2 轮连续 3 次不合规 | `INTERRUPTED/DIALOGUE_FAILED`，无报告行 | AP-10 |
| S06 非法风险分 10 | 透明重试后仍非法 → `ANALYSIS_FAILED`；库中无 10 分 | AP-01、DBV-03 |
| S07 第 2 轮受害者 TTS 失败 | `TTS_FAILED`；该条文字已保存，`presentation_status=PENDING` | AP-05 |
| S08 消息 FAILED 回执 | `MEDIA_FAILED`；该消息 `FAILED` 且有错误码 | AP-05 |
| S09 主动退出 / 刷新退出 | `USER_EXIT`；退出后音频 410、回执 `IGNORED_TERMINAL` | AP-05、API-07 |
| S10 开始幂等与竞争 | 同 `startRequestId` 重发 200 同 ID 且 `startedAt` 不变；换配置 409 `IDEMPOTENCY_CONFLICT`；两个不同请求并发只成功一个、另一个 409 `ACTIVE_SIMULATION_EXISTS` 带 `activeSessionId`；停用所选画像后开始 409 `PROFILE_UNAVAILABLE`；开始与停用并发只出现「拒绝」或「快照包含该画像」两种结果 | AP-05、AP-07、DBV-02、DBV-12 |
| S11 计时证据 | 过期样本、他人样本、乱序单调值 → 400 `timing.*`；成功时 `startedAt` 早于请求到达时刻 | AP-04、V-02 |
| S12 权限 | 用户 B 对 A 的状态、SSE、三类回执、音频、暂停、继续、退出全部 404 且正文一致；ADMIN 403；匿名 401；停用画像后 A 仍能读取其快照立绘、B 404 | AC-03、AC-04、DBV-13 |
| S13 暂停继续 | 暂停 20 秒后继续：`deadlineAt` 与 `readingUntil` 恰好顺延约 20 秒、`pausedCreditMs≈20000`；重复暂停/继续 `ALREADY_APPLIED`；暂停期间无 `round.started`/`message.saved` | AP-11、DBV-18 |
| S14 三场并行 | 3 个用户同时演绎各 6 轮，全部正式结束，事件不串场，任何一场的截止控制未被拖延 | C-04、V-04、DBV-16 |
| S15 重启收敛 | 演绎中结束后端进程并重启：该场 `SERVICE_RESTARTED`、临时音频目录已清空、同用户可开始新演绎 | DBV-08 |
| S16 信息隔离 | 所有 HTTP 响应与 SSE 帧中搜索剧本里的内部意图/心理标记字符串，结果为 0 | 复审重点 5、DBV-20 |
| S17 约束兜底 | 直接 SQL：同用户第二条 RUNNING、第 3 轮写候选结局、第 4 轮 FINISHED 非未定、风险 10、`paused_total_ms=300001` 全部被拒 | DBV-03、DBV-19 |
| L01（长）完整轮到时 | 剧本放慢节奏使 600 秒到达时处于阅读停留：`UNDETERMINED`+`TIME_LIMIT`，`endedAt` 与截止差值记录到日志 | AP-04 |
| L02（长）半轮到时 | 到达 600 秒时受害者正在呈现：`INTERRUPTED/TIME_LIMIT`，半轮保留，无报告 | AP-04 |
| L03（长）暂停超过上限 | 第 2 轮暂停 330 秒不继续：在 `startedAt+600s+300s` 附近按实时到期收敛，`paused_total_ms=300000`、`paused_at=NULL` | AP-11 |
| L04（长）失联 | 断开 SSE 且不再回执：仅在剩余预算不足以完成当前呈现时才 `CONNECTION_LOST`；断开后 30 秒内重连则保持 RUNNING | V-03、TBD-05 |

- [ ] **Step 1：先写驱动脚本与 S01，运行确认在当前代码上可执行**（若发现缺陷，回到对应 Task 修复并单独提交 `fix(...)`，不要在冒烟脚本里绕过）。
- [ ] **Step 2：逐条补齐 S02～S17，全部通过。**
- [ ] **Step 3：`-IncludeLongCases` 并行运行 L01～L04（总耗时约 16 分钟），全部通过。**
- [ ] **Step 4：记录** 实际耗时、`endedAt − 有效截止` 差值、3 场并行时各阶段耗时到 `.tools/` 日志，供 Task 15 填写验收记录。
- [ ] **Step 5：提交** `git commit -m "test(simulation): isolated end-to-end smoke for simulation module incl. long time-limit cases"`

---

### Task 15：真实模型与 CosyVoice 联调验证、文档更新

> 本任务关闭或推进架构 V-01、V-02、V-03、V-05、V-09、V-10、V-11 与接口 TBD-01a、TBD-04、TBD-05、TBD-07、TBD-10、TBD-11。**只记录真实发生的结果**；未执行或未通过的项如实写「未执行/未通过 + 原因」，不得写成已验证。

**Files:**
- Modify: `README.md`（标题改为「第一至四阶段」；设计文档链接改为 `docs/` 下四份 V1.1/V3.2；新增「第四阶段：AI情景演绎」小节：功能、接口表、环境变量、`tts-gateway` 部署、`scripted`/`silent` 仅限开发、冒烟命令、决策记录、验收记录、已知限制）
- Modify: `tools/tts-gateway/README.md`（实测的 CosyVoice 版本、模型、采样率、GPU 型号、显存占用、合成实时率）
- Modify（仅当实测要求调整运行参数）：`application.yml` 中标注 TBD 的配置项值（**只改值，不改语义**；每个改动在 README 写依据）

- [ ] **Step 1：部署 CosyVoice 与网关（V-09/TBD-10）。** 准备音色库（青年/中年/老年 × 男/女，每类主选+备选，另备中性音色）；参考音频可自行生成（例如用现有语音合成工具生成后作为克隆参考）或从网上获取（优先选择明确允许使用的公开素材），在 `tools/tts-gateway/README.md` 逐条记录来源（D-3）；测量 40 字台词的合成耗时与实时率（目标 < 1），以及「生成+合成」能否被上一段播放时间覆盖。
- [ ] **Step 2：真实模型联调（V-01/V-05/V-11）。** 以 `deepseek`+`cosyvoice` 运行：三场景 × 三受害者至少各 1 场（两位骗子交替），共 ≥9 场；逐场记录：轮数、结局、风险序列、是否有违反五档/结局判据/证据规则、第 1～4 轮重新生成次数与是否超限中断、模型调用耗时与用量。发现 Prompt 问题时**新增** `prompts/v2/*.txt` 与迁移 `V7__agent_prompts_v2`（不修改 V6），重新验证。
- [ ] **Step 3：时间预算（V-02/TBD-01a）。** 统计平均每轮时长、开场时长、首轮等待、最后停留；判断 20 轮能否在 600 秒内以完整轮收尾；若不能，只按 PRD §10.4 / 架构 §16.9 调整生成长度目标与节奏提示（例如后期轮次缩短台词），**不得**修改 5 秒停留、600 秒上限或提前未定；锁定计时参数（样本有效期、可接受误差）。
- [ ] **Step 4：浏览器验证（V-03/V-10/TBD-07/TBD-11）。** Chrome 与 Edge 当前版本各完整走一遍：开始后音频正常播放、静音切换、后台标签 2 分钟后回来（不暂停、不错位）、说话中/停留中/结束后停留中暂停与继续、累计暂停超过 300 秒、刷新、关闭标签、浏览器后退；逐字进度误差不超过当前分句；嘴部帧随说话切换；结果逐项记录。
- [ ] **Step 5：反向代理验证（TBD-04）。** 若已有云主机：经 HTTPS 反向代理验证 SSE 不被缓冲、心跳生效、长连接超过 10 分钟不断开；记录代理配置要点。无云主机则写「未执行，阻塞于 C-06」。
- [ ] **Step 6：全量回归。** `.\mvnw.cmd package`、`npm.cmd test`、`npm.cmd run build`、`node tools/validate-openapi.mjs`、`smoke-content.ps1`、`smoke-admin.ps1`、`smoke-simulation.ps1 -IncludeLongCases` 全部通过。
- [ ] **Step 7：更新 README 并提交** `git commit -m "docs: phase 4 simulation module run guide, decisions and acceptance record"`

**README 验收记录模板（Task 15 填写）：**

| 检查 | 结果 | 证据/备注 |
|---|---|---|
| 后端单元与 MockMvc 测试 | 通过 N 项 / 未通过 | 命令与日期 |
| 前端测试与构建 | | |
| OpenAPI 静态校验（44 项，画像 DELETE 已移除） | | |
| 隔离冒烟 S01～S17、L01～L04 | | 耗时、截止差值 |
| 真实模型 9 场（结局分布、规则违反数、重新生成率、超限中断数） | | V-01、V-11 |
| CosyVoice 实时率、音色可辨性 | | V-09 |
| 端到端时间预算（平均每轮秒数、20 轮可行性） | | V-02 |
| Chrome / Edge 媒体与生命周期 | | V-03、V-10 |
| HTTPS 代理 SSE | | TBD-04 |

---

## 六、完成判据（Definition of Done）

1. `.\mvnw.cmd package` 全部测试通过；`npm.cmd test`、`npm.cmd run build`（含 `vue-tsc`）退出码 0；`node tools/validate-openapi.mjs` 退出码 0，且 `openapi.yaml` 已与接口文档 V1.1 一致。
2. API-14～API-21、API-42～API-44 的字段、状态码、错误码、`Cache-Control` 与 11 类 SSE 事件载荷与 `openapi.yaml` 逐项一致；API-13 快照授权分支生效。
3. `tools/smoke-simulation.ps1 -IncludeLongCases` 的 S01～S17、L01～L04 全部通过；`smoke-content.ps1`、`smoke-admin.ps1` 未回归。
4. 「复审重点」七条各至少有一个通过的测试，测试名能读出其守护的行为。
5. 数据库为 9 张业务表（`sys_user`、`fraud_scenario`、`scammer_profile`、`victim_profile`、`simulation_session`、`simulation_round`、`simulation_message`、`simulation_report`、`agent_prompt`）+ `flyway_schema_history`；V1～V3 未被修改；新增仅 V4、V5、V6（以及 Task 15 若产生的 V7 Prompt 版本）。
6. 生产配置下无法启用 `scripted`/`silent`；任何响应、SSE、日志中都不出现 `internal_note`、`runtime_snapshot`、`timing_evidence`、提示词正文、API Key。
7. 后台不再提供画像永久删除（接口、前端按钮、yaml 均已移除，D-1）；只有停用/启用。
8. 前端演绎页：顶部只有设置按钮；无对话输入、倍速、回放、续演入口；开场「加载等待 → 开始演绎 → 场景介绍 → 人物卡 → 正式演绎」；正式结束后进入复盘入口页，中断显示原因。
9. `git log` 中 Task 1～15 各恰好一次提交（冒烟中发现的缺陷修复允许额外 `fix(...)` 提交），提交信息可追溯 Task 编号。
10. README 可让未参与开发的人在干净环境复现；第七章决策结论与 Task 15 实测结果如实记录。

---

## 七、已确认决策与风险（风险项执行者必须回报而非自行决定）

### 7.1 已确认决策（2026-10-18 需求方确认，Task 0 Step 3 写入 README）

| 编号 | 问题 | 背景 | 确认结论 |
|---|---|---|---|
| D-1 | 第三阶段新增的画像**永久删除**接口如何处理 | PRD §5.3、§8.8 与 BR-P09 明确「不永久删除」；V5 建立 `simulation_session → 画像` 的 `RESTRICT` 外键后，被演绎引用的画像删除会触发外键错误 | **去掉删除功能**：移除两个 DELETE 接口、服务方法、前端删除按钮、yaml 路径与冒烟用例（Task 1、Task 2 执行）；画像只保留停用/重新启用 |
| D-2 | 阅读停留结束、服务端已建立第 n+1 轮但尚未保存任何消息时到达 600 秒，是「完整轮位置」还是「未完成轮」 | 数据库 §7.2/§7.5 以 `phase=COMPLETE` 定义完整轮位置；新轮 `SCAMMER_GENERATING` 不满足 | 按数据库设计执行：`INTERRUPTED/TIME_LIMIT`、无报告（Task 9 实现并由 L02 类用例覆盖）。不得改为「结局未定」或提前不建新轮 |
| D-3 | CosyVoice 音色参考音频的来源、TTS 运行硬件 | 零样本音色依赖参考录音；架构 C-05、V-09 | 参考音频**可自行生成，也可从网上获取**；优先使用明确允许使用的公开素材，每条在 `tools/tts-gateway/README.md` 记录来源；参考音频文件只放在 TTS 主机的网关配置目录，不提交到仓库。硬件按 V-09 实测选型 |
| D-4 | 若干实现上限是否接受 | 接口 TBD-09 未统一文本长度；PRD 只给目标长度 | 台词、动作说明、解说每项硬上限 120 个码点（超出视为输出不合格并在预算内重生，不截断已保存内容）；18 岁以下画像按青年音色映射 |

### 7.2 风险与阻塞项

- **600 秒预算是否足够 20 轮**：架构 §16.9 估算约 525 秒，前提是生成与合成被播放时间完全隐藏。若 V-09 实测合成实时率 ≥1 或模型耗时过长，正常演绎会频繁停在半轮超时。处理顺序：提升 TTS 硬件 → 缩短台词目标 → 调整节奏提示；**不得**修改时限、停留或提前未定。数据不足以达成时提交证据走 PRD 变更。
- **模型语义判定可靠性（V-01）**：结构化 JSON 只保证形式；「承诺/准备/实际执行/停止」的区分必须用真实样本评估。校验器拒绝不一致输出会增加重试与中断率，这是预期行为，不得为降低中断率而放宽校验或让程序替模型改判。
- **BR-P12 重新生成率（V-11）**：高防骗意识受害者（周桂兰）在前 4 轮可能频繁触发拒绝；若超限中断率明显，优化 `victim.txt` 的前 4 轮行为约束（新增 Prompt 版本），不得改为「强制继续已终止的互动」或篡改解说判定。
- **DeepSeek JSON 模式偶发空内容**：已按可重试失败处理；若频率过高，记录并评估调整 `max_tokens` 或提示词，不切换为无校验的自由文本。模型标识与非思考模式参数以部署当日官方文档为准，写入 README。
- **SSE 与 Spring Security 异步派发**：若 SSE 在首帧后报 403/空响应，检查 `dispatcherTypeMatchers(ASYNC, ERROR)` 是否生效，**不要**为此放开 `/simulations/**` 的 URL 规则。
- **反向代理缓冲与超时（TBD-04）**：未验证前不得宣称云端 SSE 可用；`X-Accel-Buffering: no` 只对部分代理有效，需按实际代理配置。
- **浏览器自动播放与后台节流（V-03）**：依赖「开始演绎」「继续演绎」两次用户手势解锁；Edge/Chrome 策略差异导致 `play()` 被拒时按 `AUDIO_PLAYBACK_FAILED` 中断（PRD：媒体故障不静默降级），并记录复现条件。
- **刷新识别（TBD-05）**：`sessionStorage` 标记 + `pagehide` keepalive 是尽力而为；关闭标签后服务端靠 `CONNECTION_LOST` 三条件兜底，可能在一段时间内仍显示 RUNNING（期间同账号新开始会收到 409 ACTIVE）。这是接口文档已接受的边界，需在 README 已知限制中写明。
- **Flyway Java 迁移**：V6 为 Java 迁移，校验和来自 `getChecksum()`；已执行后修改 `prompts/v1/*.txt` 会导致启动校验失败——这是期望行为，修正 Prompt 必须新增版本迁移。
- **报告行只建不处理**：本阶段正式结束的记录报告会一直显示「生成中」，直到第五阶段实现报告任务；这是阶段边界，不是缺陷，README 需说明。下一阶段启动收敛逻辑需处理本阶段遗留的 PENDING 行（正常领取即可）。
- **Docker 不可用**：沿用前两阶段的隔离 MySQL 方式（`smoke-*.ps1`），**不得**用 H2/SQLite 宣称并发、生成列、CHECK、行锁行为已验证（数据库 §12.1）。
- **单实例约束**：编排 lanes、预备区、计时样本、SSE 连接、临时音频均为单进程内存/本地状态；多实例部署前必须重审（数据库 DBD-10），本阶段不做。
- **登录 Session 超时**：现为 Tomcat 默认 30 分钟不活动；演绎期间回执请求会续期，但设置界面长时间停留（最多 5 分钟计入 + 之后实时）不会超时；若 TBD-02 改变策略需复核演绎页 401 处理。

---

## 附：需求追踪（本阶段条目 → 任务 / 验证）

| 需求或验收 | 实现任务 | 主要验证 |
|---|---|---|
| FR-SIM-01、AC-28、PC-P06（开场、计时起点） | Task 4、11、12 | `TimingCalibratorTest`、`simulation-view.spec.ts`、S11 |
| FR-SIM-02、AC-29（左右舞台、气泡保留与清理、动作说明） | Task 8、12 | `performance-director.spec.ts`、S01、Task 15 浏览器验证 |
| FR-SIM-03（立绘两帧动画）、BR-P09 立绘 | Task 2、12 | `AdminRulesTest`、DBV-20、浏览器验证 |
| FR-SIM-04、AC-13、PC-P04（语音、分句同步、静音） | Task 4、7、12、13 | `SentenceSegmenterTest`、`segment-reveal.spec.ts`、V-09、V-10 |
| FR-SIM-05、FR-SIM-06、AC-14、AC-15、BR-P01、BR-P02 | Task 6、8、12 | `AgentOutputValidatorTest`、S01、S06 |
| FR-SIM-07、AC-16、BR-P03～P06、AP-03～AP-05 | Task 6、8、9 | `OrchestratorFlowTest`、`ExitAndDeadlineTest`、S02～S09、L01、L02 |
| BR-P12、AP-10 | Task 6、8 | `MinimumRoundsPrecheckTest`、S04、S05、S17 |
| FR-SIM-08、BR-P13、AC-30、AP-11 | Task 10、13 | `PauseResumeTest`、`settings-overlay.spec.ts`、S13、L03 |
| BR-P08（先保存再展示）、AGT-04 | Task 5、8 | `PreparedContentNeverPublishedTest`、`SimulationEventHubTest` |
| BR-P10、DATA-03（当时完整配置、快照头像） | Task 2、4、5 | `SimulationStateAssemblerTest`、S12 |
| PER-01～04、UX-04 | Task 4、5、7、9、10 | `SimulationPermissionTest`、S12 |
| AGT-01～03 上下文隔离 | Task 6 | `AgentContextIsolationTest`、S16 |
| C-04 约 3 场并行 | Task 8、14 | S14 |
