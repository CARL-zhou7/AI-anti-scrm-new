# Victim scene guidance implementation plan

> **For agentic workers:** implement task by task; frontend and rule administration are independent delegated tasks, runtime integration stays in the main session.

**Goal:** 管理员按场景维护受害者规则，同一具体请求连续两轮未被接受后，第三次合法请求必须推进。

**Architecture:** 复用场景规则及会话快照，新增目标角色。语义识别只提取当前请求及历史对应关系；程序保存逐轮判断、计算连续次数、限定单事项付款许可。许可在生成与事务保存时复用，不修改信任分。沿用重试及技术中断。

**Tech Stack:** Spring Boot/JPA/MySQL/Flyway、Vue/TypeScript/Vitest，无新增依赖。

**Spec:** 本聊天已确认的Q1–Q12及后台左右分栏要求。

## Global constraints
- 同一请求的第三次连续出现，不是整场第三轮；换词不重置，实质变化/中断重置。
- 仅匹配启用的冻结受害者规则时生效；旧快照没有受害者规则不改变行为。
- 允许余额与普通预留，不能借许可动用应急预留、重复支付、伪造核实或覆盖停止/反证。
- 自动生效，无受害者逐轮编辑；后台可直接创建受害者规则。
- 不重写旧迁移、旧快照、报告及已保存对话。

## Review focus
- 生成重试和事务重放不能重复计数或付款。
- 低信任许可仅绑定当前请求，不能泄漏为全局大额权限。
- 已停止、已有明确反证或当前可靠反证出现时不强制配合。
- 同请求已执行后，保留记忆但不能再次付款。
- 未匹配、规则停用、新请求、资金不足、引用错误与模型输出错误可解释且不造事实。

## Task 1: Rules and administration
- [x] 在SceneRule/DTO/Repository/Snapshot中增加targetRole，旧数据默认SCAMMER；V52新增字段、逐轮JSON列、初始受害者规则。
- [x] 受害者创建不需要来源；既有权限、版本检查及骗子来源链不变。
- [x] AdminSceneRulesView左右分栏，共用场景选择；短标题、条件、指导，保留完整内容。
- [x] 定向服务/API/快照与UI检查，前端build。

## Task 2: Persisted request decisions
- [x] 新增VictimGuidance纯规则及严格识别输出契约，使用原文证据/冻结规则ID，程序计算次数。
- [x] SimulationRound保存victim_guidance_json；SimulationTransitions在正式骗子消息之后记录，重复调用幂等。
- [x] 保存已接受事项和已完成付款信息，给下一轮提供记忆。
- [x] 测试跨全局轮号、改写连续、变化重置、已完成与反证边界。

## Task 3: Generation and funds integration
- [x] Orchestrator在受害者生成前识别并保存判断；无规则不额外请求。
- [x] Runner注入具体指导，非资金执行必须有明确action；付款必须精确对应本次金额及用途。
- [x] FundsContext带本次许可；FundRules统一校验生成和入账，拒绝应急/额外动作/重复入账。
- [x] 失败走现有首次+两次重试及DIALOGUE_FAILED，不伪造动作。
- [x] 定向行为/资金/重试测试、后端检查、API契约同步。

## Completion
- [x] 记录实际测试与限制，不将真实模型或迁移未运行的检查记为通过。

## Verification evidence
- 后端完整单元/接口回归、前端测试/构建、OpenAPI检查及桌面/390px浏览器验证。
- 独立MySQL的V52迁移2项通过；业务库未迁移，真实模型未调用。
