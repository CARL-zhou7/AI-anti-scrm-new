# Trust Progression V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让有效对话推动连续信任和真实资金行为，在20轮内支持未付款识破、小额后止损、追加达到大额目标三条自然路径。

**Architecture:** 复用现有事件判定、计分、资金账本、人物快照、提示词版本和轮末结局链路。新增 trust-v2 规则标记及必要的事件进展标识，不建立第二套模拟引擎；资金执行与目标判定仍由程序校验。

**Tech Stack:** Java 21 / Spring Boot / JPA / MySQL / Flyway；Vue 3 / TypeScript；JUnit / Vitest。

**Spec:** 本文“已确认需求”来自本聊天的用户确认；现有 PRD 中冲突的旧要求以该确认覆盖。

本轮仅产出实施计划，尚未修改业务代码。上述模板中的执行技能不是产品依赖；执行时遵守用户选定方式及实际可用工具，不为套用技能增加架构、依赖或强制审批。

## 已确认需求

- 默认门槛：进一步了解40、小额70、大额85；管理员仍可修改。
- 有效推进路径中，易受影响组合第6—8轮达到门槛并完成首次小额付款；谨慎组合第10—12轮达到并完成付款。没有固定等待轮数、自动加分或必付承诺。
- 正常剧情尽量18轮内完整结束，较长博弈落在16—18轮；允许更早形成结局，20轮兜底。保留结果未定，不为凑结局编造漏洞、停止或转账。
- 未付款且有确切反证并停止：识破。小额付款后明确停止追加：止损，不要求先识破。先小额、再实际追加使未收回本金超过人物小额上限：达到大额目标。
- 未收回本金 = 累计付款 - 累计本金退款；不等于现有扣除了奖励的 actualLossYuan。报酬不冒充本金退款。
- 易受影响人物更容易延伸前次顺利体验；谨慎人物重新关注新增金额、费用和条件。对新增顾虑的有效回答才继续计分，人物不预绑定结局。
- 同一区间内分数升降影响主动追问、解释接受度和独立核验意愿；付款权限保持确定性门槛。确切反证优先，主观相信不写成客观核实。
- 合理回答/专业解释+3，缓解明确质疑/相关新材料+4，实际兑现+5，回避且加重疑虑-1，新矛盾-5，决定性反证-10，重复0。同一进展不按多个正向类别重复计分。
- 易受影响组合正/负系数1.2/1.0；谨慎组合1.0/1.2；继续 HALF_UP、分数限0—100。

| 人物 | 刷单返利初始分 | 虚假投资初始分 | 亲友借款初始分 | 易受影响场景 |
|---|---:|---:|---:|---|
| 林晓 | 50 | 40 | 45 | 刷单返利 |
| 赵诚 | 40 | 50 | 45 | 虚假投资 |
| 周国良 | 45 | 40 | 50 | 亲友借款 |

## Global Constraints

- 不覆盖已有工作区修改，不重写已执行迁移，不回填历史分数、资金、报告或结局。
- 新规则按会话运行快照冻结；旧场次继续旧规则和旧模板，不仅仅冻结数字阈值。
- 新默认值仅更新仍匹配旧种子值的配置；管理员自定义内容保留。不可因人物同名覆盖用户新建资料。
- 不向骗子暴露受害者余额、内部信任分、系数或未公开疑虑；后台事件判定器可以读取必要规则及历史。
- 不新增依赖、不建立通用规则编辑器、不自动给每轮奖励、不为完成率预设结局。
- 时间兜底调整保留既有计时校准、暂停补偿、模型调用超时和中断恢复语义。

## Review Focus

- 同一主题的真正进展与改写重复难以区分：任务1覆盖重放、跨类型重复、同主题新增事实；真实模型验收单独观察语义判定。
- 付款预检与正式入账可能得到不同目标：任务3使用同一计算，覆盖候选动作、账本事实和先后次序。
- 报酬、本金退款以及累计拆单容易混算：任务3覆盖等于/超过小额上限、全退后再投和重复同笔付款。
- 改门槛或升级时旧场次可能悄然换规则：任务0及任务4覆盖冻结版本、保留自定义和迁移升级。
- 超限或拒绝可能被误写成识破/目标完成：任务3及任务5覆盖反证缺失、已付款超限和明确止损。

## Task 0: 冻结新规则与配置，保留旧场次

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/service/SimulationTransitions.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/snapshot/ConfigurationSnapshots.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/AgentModels.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/engine/SimulationOrchestrator.java`
- Test: `backend/src/test/java/com/aiscam/antiscam/simulation/SnapshotRulesTest.java`

**Interfaces:** 新增 `ConfigurationSnapshots.trustV2(SimulationSession): boolean`，只读取 `runtime_snapshot.trust_config.trust_ruleset_version == "trust-v2"`；缺字段是旧规则。AgentScene 增加 `boolean trustV2`，旧构造入口默认false；现有 trustV1 表示信任机制已启用，v2不绕过其安全校验。

- [ ] 在 SnapshotRulesTest 增加旧快照无版本仍走旧规则、新快照保存trust-v2且管理员后续改门槛不影响当前会话的断言。
- [ ] 在backend运行 `./mvnw.cmd -Dtest=SnapshotRulesTest test`，先确认新断言失败。
- [ ] 新会话冻结规则版本、门槛、系数、小额金额上限及最终选定的时间预算；加载AgentScene时从快照传递版本。
- [ ] 重跑上述测试。检查不使用当前政策表重新解释旧会话。

## Task 1: 细化事件并修复计分去重

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/rules/TrustEventType.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/service/TrustEventService.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/domain/SimulationTrustEvent.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/AgentModels.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/AgentOutputParser.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/AgentOutputValidator.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/service/WaitingItemService.java`
- Create: `backend/src/main/resources/db/migration/V38__trust_v2_events.sql`
- Create: `backend/src/main/resources/prompts/v15/event.txt`
- Test: `backend/src/test/java/com/aiscam/antiscam/simulation/TrustEventRulesTest.java`, `TrustRulesTest.java`
- Test: `backend/src/test/java/com/aiscam/antiscam/simulation/engine/EventMaterialHandlingTest.java`

**Interfaces:** 保留 NEW_EFFECTIVE_EXPLANATION(3)及旧枚举；新增 DOUBT_RESOLVED(4)、PROFESSIONAL_EXPLANATION(3)、RELEVANT_MATERIAL(4)、EVASIVE_RESPONSE(-1)。`TrustEventCandidate` 增加 `String progressKey`，v2必填，旧输出兼容默认subjectKey。`subjectKey`仍表示材料/问题事项，`progressKey`表示该事项的具体新增事实，不使用轮号或随机序号。

- [ ] 添加测试：新有效解释3×1.2四舍五入为4；缓解质疑4×1.2为5；重复重试不加分；同主题的新进展可计分；同一进展从回答换成专业类别不再加分；负面矛盾不会被正向解释吞掉。
- [ ] 运行 `./mvnw.cmd -Dtest=TrustRulesTest,TrustEventRulesTest,EventMaterialHandlingTest test`，确认新增断言失败。
- [ ] V38扩充事件类型约束，增加progress_key；旧记录填充旧subject_key，唯一键扩为session/type/subject/progress。旧场次服务仍按原type+subject去重，v2按新增进展校验。执行前确认V38号未被其他改动占用。
- [ ] v2原文证据、事项与进展键统一校验：历史重复证据不重复计分；同一subject/progress的正向候选取主要事件（基础分最高，平分按原顺序），不叠加；不得简单删除原去重。
- [ ] 新事件提示词加入三场景具体例子、已提疑问、已计进展及来源边界。只有后台判定器读取这些内容，数值仍由程序计算。
- [ ] WaitingItemService接受新解释类事件更新已有INFORMATION事项；材料齐全、真实性、独立核验继续区分。不能把progressKey拿来创建一批重复等待事项。
- [ ] 同一解释后的“我懂了”、付款动作不再额外制造INDEPENDENT_SUPPORT；保留真正独立态度事件的兼容处理。资金兑现仍只由账本确认。
- [ ] 重跑测试，并更新受新事件集合影响的解析/纠错用例及必要scripted夹具。

## Task 2: 让分数变化影响实际行为与追加判断

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/AgentContextAssembler.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/AgentRunner.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/engine/SimulationOrchestrator.java`
- Create: `backend/src/main/resources/prompts/v15/victim.txt`, `v15/scammer.txt`, `v15/narrator.txt`
- Test: `backend/src/test/java/com/aiscam/antiscam/agent/TrustInfluenceTest.java`
- Test: `backend/src/test/java/com/aiscam/antiscam/agent/ScammerScenarioPromptTest.java`

**Interfaces:** 在AgentContextAssembler中增加生成侧 `victimCaution(int score, int previousScore, ActionGate.TrustGate gate): String`；沿用既有victim/scammer/event组装入口传递所需上下文。previousScore取本轮计分前的已保存值，无事件时等于当前值，不增加第二套信任状态。

- [ ] 添加测试：45→55态度/核验指导变化且付款预算仍0；55→45反向恢复谨慎；69不能付、70可小额、84不能大额、85可大额；数字控制不进入角色台词事实。
- [ ] 运行 `./mvnw.cmd -Dtest=TrustInfluenceTest,ScammerScenarioPromptTest test` 确认新断言失败。
- [ ] 根据当前分、相邻门槛的相对位置及升降生成具体指导，保留连续强度，不再只给两个抽象小数或固定付款档位台词。用45/55作验收锚点而非新增硬档位。
- [ ] v2将画像中的一般核验习惯作为偏好而非绝对前置条件；确切反证和资金事实仍不可覆盖。小额后根据画像与新增请求风险重新回应，不按姓名或年龄写死结局。
- [ ] 从已有对话/流水判断尚未付款、已小额、追加博弈、已停止等阶段，不额外存储易失真的剧情状态表。骗子仅收到可观察阶段、公开回应和剩余轮数，不获知内部余额/分数/系数。
- [ ] 6—8、10—12仅为校准窗口；提示词减少复读、回应当前疑问、保留场景语境，不命令角色到某轮付款。已明确停止立即进入结局，不强迫再沟通2—3轮。
- [ ] 重跑测试；同时保留既有材料来源、称呼、借款主事件和付款意愿/能力分离的边界测试。

## Task 3: 统一大额目标与三条结局，避免首次小额提前结束

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/rules/ScenarioGoalRules.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/rules/OutcomeRules.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/AgentRunner.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/agent/AgentOutputValidator.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/service/SimulationTransitions.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/service/SimulationStateAssembler.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/report/service/ReportContextAssembler.java`
- Modify: `frontend/src/components/simulation/TerminalNotice.vue`
- Test: `backend/src/test/java/com/aiscam/antiscam/simulation/OutcomeRulesTest.java`, `engine/EarlyGoalTest.java`
- Test: `frontend/src/__tests__/simulation.spec.ts`, `report.spec.ts`

**Interfaces:** 新增 `ScenarioGoalRules.goalAchieved(FundsContext before, List<FundPosting> candidatePostings): boolean` 用于v2。正式判断传空candidatePostings并检查账本，候选判断使用FundRules.apply产生的已验证postings；共享按顺序累计的判定逻辑。旧重载仅供旧规则。调用方继续对已成立的prior.goalAchieved保持不可回退。

- [ ] 测试三个场景首笔小额均不达成目标；后续付款使未收回本金从上限变为上限+1时达成；仅意向/达85分不达成；返款后再投、报酬到账、拆单、候选与正式结果一致。
- [ ] 运行 `./mvnw.cmd -Dtest=OutcomeRulesTest,EarlyGoalTest test` 确认新断言失败。
- [ ] 按账本顺序识别先小额、后追加超上限；不得用投资必须交额外费用、刷单固定1000元、亲友首笔付款等旧目标覆盖v2。明确首次请求仍以小额为剧情推进方向，达85不能凭空执行大额。
- [ ] 在预检/正式分析/提示词目标描述/程序校验统一接入新目标与小额金额上限。复用实际资金校验，不让解说模型自行裁定目标。
- [ ] 未付款识破必须有反证与停止证据；已付款停止不要求反证；暂时犹豫不是停止。无反证的单纯拒绝不能显示成“已识破”，可复用NOT_DEFRAUDED表示未受骗结束，不能伪造第一条路径。
- [ ] 优先复用现有goalAchieved、stoppedAfterFraud、actualFraudOccurred、limitReason区分展示与报告，不为三条路径新建重复状态字段。超限保留真实付款/损失，明确说明剧情结果未定，不显示成目标完成。
- [ ] 检查新旧OutcomeCode允许范围、历史装配和报告资格分支；仅调整受新v2结果影响的分支。重跑后端上述测试及前端simulation/report测试。

## Task 4: 新默认参数、提示词版本及配置可理解性

**Files:**
- Create: `backend/src/main/resources/db/migration/V39__trust_v2_defaults.sql`
- Create: `backend/src/main/java/db/migration/V40__seed_trust_v2_prompts.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/admin/web/dto/TrustPolicyViews.java`
- Modify: `frontend/src/views/AdminTrustPolicyView.vue`
- Modify: `frontend/src/api/types.ts`, `openapi.yaml`
- Test: `backend/src/test/java/com/aiscam/antiscam/admin/TrustPolicyApiTest.java`
- Test: `backend/src/test/java/com/aiscam/antiscam/simulation/V13MigrationUpgradeIT.java`
- Test: `frontend/src/__tests__/admin.spec.ts`

**Interfaces:** TrustPolicy响应保持原阈值及version字段，rulesetVersion允许trust-v1/trust-v2；利用现有admin人物接口读取当前人物初始分/系数，不新建配置中心。界面估算：`ceil(max(0, small - initial) / HALF_UP(3 * positiveFactor))`；单次增量为0时显示无法靠此类事件推进，不除零。

- [ ] 添加迁移测试：旧默认60/80/90变40/70/85；九组匹配旧种子值的初始分/系数变为本文值；已修改配置、新增同名人物、历史快照保留。无法可靠识别种子或修改来源时保留并报告，不按名字批量覆盖。
- [ ] 测试配置提示：初始分已达小额、估算超过剩余预算、正向增量四舍五入为0；估算明确是有效事件次数，不宣称保证第几轮付款。
- [ ] V39保守更新默认值并正确递增row_version；V40追加新模板版本，按各PromptType当前最大版本分配，旧迁移文件和已引用模板不改。
- [ ] 补充三个场景的画像核验偏好/新增风险反应，仅更新可确认的旧默认文本；保留管理员自由编辑内容。行为规则在v2生成侧确定优先级，不能依赖盲目重写画像。
- [ ] 配置页面用现有组件显示当前/待保存门槛对各组合的估算和警告，允许保存合法自定义；不静默重设初始分。同步rulesetVersion类型、OpenAPI和必要校验脚本。
- [ ] 运行 `./mvnw.cmd -Dtest=TrustPolicyApiTest test`；隔离MySQL环境运行 `./mvnw.cmd -Dit.test=V13MigrationUpgradeIT verify`；frontend运行 `npm test -- src/__tests__/admin.spec.ts` 与 `npm run validate:api`。

## Task 5: 剧情预算、回归与真实演绎校准

**Files:**
- Modify: `backend/src/main/java/com/aiscam/antiscam/config/SimulationProperties.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/domain/SimulationSession.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/service/SimulationTransitions.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/timing/TimingCalibrator.java`
- Modify: `backend/src/main/java/com/aiscam/antiscam/simulation/rules/OutcomeRules.java`
- Modify: `tools/smoke-simulation.mjs`
- Modify: `backend/src/main/resources/simulation/scripted/` 下受新版本影响的夹具
- Modify: `docs/v1.3-manual-acceptance.md`、现有PRD/接口/数据库/架构文档中直接冲突的段落
- Create: `docs/2026-10-26-trust-v2-calibration.md`
- Test: `backend/src/test/java/com/aiscam/antiscam/simulation/SimulationRulesTest.java`, `OutcomeRulesTest.java`

**Interfaces:** 新增 `SimulationProperties.maxActiveDurationMs`，用于新v2会话并冻结到pacing_config；已有会话仍使用保存的deadline。首轮工程试跑暂用1_800_000ms作为兜底值，此数值不是用户已确认需求，必须由端到端耗时结果校准后在交付中说明。所有时间消费者读同一预算，不在角色提示词内各写一个期限。

- [ ] 添加测试：第18轮有结局可以结束，无结局不伪造；第20轮完整结束后停止；已付小额超限不变成大额达标；新场次不会被旧600秒常量提前截断，旧deadline及暂停补偿不变。
- [ ] 搜索并处理实际与总时长关联的600_000/600000和用户文案；保留无关媒体毫秒值及单次调用超时。smoke脚本不要把新deadline改回旧600秒。
- [ ] 跑定向测试后跑一次完整回归：backend `./mvnw.cmd test`，frontend `npm test`、`npm run build`、`npm run validate:api`；迁移用隔离库，不改用户业务数据。
- [ ] 用scripted路径覆盖三结局、未付款无反证拒绝、超限兜底、重复进展与追加资金边界；记录预检和正式入账是否一致。
- [ ] 真实模型先覆盖九组组合，每组至少一次；针对窗口外、复读、无依据加分、材料误当核实等异常复跑。初轮九场只能发现问题，不能证明成功概率；不可挑选有利样本掩盖失败。
- [ ] 校准记录保存人物/场景、实际配置快照、逐轮事件与前后分、首次达70轮、首次实际小额轮、后续付款、结局轮、结束原因、总时长。优先使用现有记录/日志，不新增监控平台。
- [ ] 对有持续有效进展的路径检查易受影响6—8轮、谨慎10—12轮；记录不匹配原因。无效解释导致不付款不是窗口失败，需同时检查是否自然形成拒绝/识破或如实兜底。
- [ ] 只根据观察调整相关事件判定、人物表达或参数；不得按轮号偷偷加分。核对管理员改门槛后仍围绕当前问题推进、小额后保留追加博弈。
- [ ] 更新校准结论和直接相关文档。完成 `git diff --check` 并按文件检查没有覆盖其他工作；提交只在用户要求时进行，不打包提交工作区其他修改。

## 交付判定

规则测试/迁移/契约/资金和旧版本回归通过，真实模型观察单独报告。没有真实模型凭据或服务时可以完成代码与确定性验证，但必须明确“真实节奏尚未验收”，不能把提示词字符串测试当成自然演绎成功。

## Self-review

- 已将计分、连续行为、九组参数、三结局、去重、管理员调整、旧数据保护、18/20轮和时间约束分别映射到任务。
- 新进展与等待事项分开，新增版本与模板一起冻结；没有新增概率作弊或压力分数系统。
- 资金目标不使用actualLossYuan替代未收回本金，候选和正式计算共享规则。
- 技术边界尚需实际模型验收；30分钟兜底是待测工程起点，不是已验证的最优时长。
