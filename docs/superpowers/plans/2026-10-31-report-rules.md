# 报告与规则展示优化计划

目标：短报告、真实信任变化、账本资金摘要和可扫读规则，保持旧数据兼容。
技术：现有 Java/Spring、Vue/TypeScript，不增加依赖。
约束：不修改行为机制、计分、胜负和已执行迁移，不覆盖现有工作区改动。

1. AgentModels/Parser/Validator、ReportViewMapper：增加可选节点标题，取消长摘要下限，保留结构、结果、金额、逐字证据验证。新提示词通过 V51 新迁移保存。验证短摘要、旧格式及错误证据。
2. ReportView.vue、api/types.ts：合并结果，资金先于节点；程序组织金额句，明细用 details；显示标题和红升绿降中性不变、缺失。验证六种资金情况和正负零信任。
3. SceneRuleService、AdminSceneRulesView.vue：新归纳收紧标题及条件/作用；列表直接复用正文中完整结构字段，不截断、不写回，非结构旧规则诚实提示查看全文。编辑即时依正文重算。验证编辑、启停、来源、冲突和快照现有测试。
4. 同步接口文档与 OpenAPI；运行相关后端测试、前端测试、构建及可用页面检查。未完成的检查如实记录。

重点边界：没有资金往来、只有报酬、返款与报酬并存、零信任变化、无标题/摘要旧数据。使用原生 details/summary 保证键盘展开；窄屏标题和信任独立换行。


验证结果：
- 后端 `Report*Test,SceneRuleRulesTest,SceneRuleApiTest,SnapshotRulesTest`：53项通过，包括新模板经 AgentRunner 生成解析校验及旧节点无 title 兼容。
- 前端 report/admin/simulation/ruleOverview：56项通过；`npm run build` 和 `npm run validate:api` 通过。
- Chrome 无头浏览器使用模拟接口加载实际构建页面：1280px、390px均无横向溢出；标题与信任同字号，正负零颜色/文字正确；资金明细、完整规则可通过Enter展开。截图和结构证据在 var/report-rules-browser/。
- 本次所改文件的 diff whitespace 检查通过；仓库其他既有改动未清理。
- 未连接真实模型、未对运行数据库执行V51迁移；未运行全仓测试。Maven首次编译遇到本机编译器资源关闭错误，重试后相关测试成功。
