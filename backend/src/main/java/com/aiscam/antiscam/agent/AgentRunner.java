package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.agent.LlmClient.LlmRequest;
import com.aiscam.antiscam.config.SimulationProperties;
import com.aiscam.antiscam.report.service.ReportContext;
import java.time.Duration;
import com.aiscam.antiscam.simulation.domain.*;
import java.util.List;

/**
 * Assemble context → render the pinned prompt → call the model → parse → validate. One attempt per call; retry
 * and regeneration policy belongs to the preparation area of the orchestrator.
 */
public class AgentRunner {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentRunner.class);
    private final LlmClient llm;
    private final SimulationProperties.Llm config;

    public AgentRunner(LlmClient llm, SimulationProperties.Llm config) {
        this.llm = llm;
        this.config = config;
    }

    public ReportData report(ReportContext context, long timeoutMs) {
        long timeout = Math.max(1, timeoutMs);
        String prompt = PromptRenderer.render(context.template(), context.variables());
        // Multi-paragraph reports need more output space than a single dialogue turn.
        String raw = llm.completeJson(new LlmRequest(AgentRole.REPORT, prompt, config.getReportTemperature(),
                Math.max(4096, config.getMaxTokens()), Duration.ofMillis(timeout), 0, Math.max(0,context.attemptNo()-1), context.scriptKey()));
        if (context.contentSchemaVersion() == 4) {
            // Schema 4 stores the v3 structure plus the program verdict injected by the read-side mapper.
            var parsed = AgentOutputValidator.reportV4(AgentOutputParser.reportV3(raw),
                    context.outcomeDisplayText(), context.fundsV3(), context.quotes());
            return new ReportContentV3(3, parsed.result(), parsed.summary(), parsed.keyNodes(),
                    parsed.preventionAdvice(), parsed.fundsSummary());
        }
        if (context.contentSchemaVersion() == 3) return AgentOutputValidator.reportV3(AgentOutputParser.reportV3(raw),
                context.outcomeDisplayText(),context.fundsV3(),context.quotes());
        return context.contentSchemaVersion() == 2
                ? AgentOutputValidator.reportV2(AgentOutputParser.reportV2(raw),context.outcomeDisplayText(),context.funds(),context.quotes())
                : AgentOutputValidator.report(AgentOutputParser.report(raw), context.roundCount(), context.outcomeDisplayText());
    }

    public ScammerTurn scammer(AgentScene scene, List<DialogueLine> history, int roundNo, long remainingMs, int attempt, FundsContext funds) {
        return scammer(scene,history,roundNo,remainingMs,attempt,funds,"");
    }
    public ScammerTurn scammer(AgentScene scene, List<DialogueLine> history, int roundNo, long remainingMs, int attempt,
            FundsContext funds, String waitingFacts) {
        return scammer(scene, history, roundNo, remainingMs, attempt, funds, waitingFacts, "");
    }
    public ScammerTurn scammer(AgentScene scene, List<DialogueLine> history, int roundNo, long remainingMs, int attempt,
            FundsContext funds, String waitingFacts, String validationFeedback) {
        return scammer(scene, history, roundNo, remainingMs, attempt, funds, waitingFacts, validationFeedback, "");
    }
    public ScammerTurn scammer(AgentScene scene, List<DialogueLine> history, int roundNo, long remainingMs, int attempt,
            FundsContext funds, String waitingFacts, String validationFeedback, String regenerationGuidance) {
        var variables = AgentContextAssembler.scammer(scene, history, roundNo, remainingMs / 1000);
        if (funds != null) {
            variables.put("funds_context", AgentContextAssembler.scammerFunds(funds));
            variables.put("victim_public_profile", variables.getOrDefault("victim_public_profile", scene.victimPublicProfile())+"\n"+waitingFacts);
        }
        String prompt = PromptRenderer.render(scene.scammerTemplate(), variables);
        prompt += fundEvidenceInstructions();
        prompt += "\n【逐轮推进】本轮先回应受害者最后的实际问题，在已发生内容上补充新信息或下一步。"
                + "不要重复上一轮的邀请、夸赞或截图说明；已经邀请过体验时，解释体验步骤或回答疑问。";
        if (!regenerationGuidance.isBlank()) prompt += "\n【用户指定的本轮重新生成方向】\n" + regenerationGuidance
                + "\n按此方向重新生成本轮完整话术line和行为action，两者保持一致；方向可以指导说什么，也可以指导做什么。"
                + "仅改变本轮骗子的台词与动作，遵守本场情景、既定事实、资金约束和输出结构；不把用户方向当作已经发生的事实。";
        if (!validationFeedback.isBlank()) {
            prompt += "\n【上次输出未通过校验，请重新输出完整JSON】\n校验原因：" + validationFeedback
                    + "\n保持本场诈骗类型及已发生事实，不要照抄上一版错误资金动作。";
            if (funds != null && funds.refundCapYuan() > 0) prompt += "未来承诺、待操作后才支付、提醒查收及确认先前到账都不是本轮新出款，fundActions=[]；claimedRefundYuan按本轮声称金额填写，无声称则null。"
                    + "若本轮确实新发放报酬，quote必须逐字引用本轮line/action中同时包含阿拉伯数字金额和已实际支付、已转账或已发放等完成描述的连续原文；只有发送材料不算发放。"
                    + "已入账的同一笔报酬不能再次记账，另一次同额新发放还须在执行原文明确又、再次或另外支付/转账/发放。"
                    + "承诺不能靠改写成已付款来凑通过；保留正确资金事实、关联付款ID及出款上限，不凭空编造执行动作。";
        }
        prompt += "\n【提交前结构自检】无论本轮是否出款、是否正在纠错，都只输出完整六字段对象："
                + "{\"line\":\"本轮台词\",\"action\":null,\"intent\":\"本轮意图\",\"tone\":{\"speed\":\"NORMAL\",\"style\":\"自然\"},\"claimedRefundYuan\":null,\"fundActions\":[]}。"
                + "这是字段结构，不是替你决定本轮事实；按本轮真实内容填写。action无动作时为null，但字段必须保留；intent不能省略或改成psychology。"
                + "claimedRefundYuan无声称时为null，fundActions无实际新出款时为[]，不能只输出修正字段，也不要添加解释字段。"
                + "确有新出款时，先在action写明含阿拉伯数字金额的已完成执行，再把该执行原文逐字填入quote；"
                + "仅承诺、金额表达不完整或只是提醒旧到账时，不为凑格式虚构执行。每项fundActions保留type、purpose、amountYuan、roundNo、speaker、quote、eventOrder、reason、relatedPaymentId、reserveTier。"
                + "reserveTier和relatedPaymentId只能放在fundActions数组内的动作对象中，根对象禁止这些字段。fundActions=[]时不输出任何资金动作子字段。";
        String raw = llm.completeJson(request(AgentRole.SCAMMER, prompt, config.getScammerTemperature(), remainingMs, roundNo, attempt, scene));
        try {
            var turn = AgentOutputValidator.validate(AgentOutputParser.scammer(raw));
            var previous = history.stream().filter(l -> l.speaker() == SpeakerRole.SCAMMER && l.roundNo() < roundNo)
                    .reduce((a, b) -> b);
            if (previous.isPresent() && previous.get().content().strip().equals(turn.line().strip()))
                throw new AgentCallException(AgentCallException.Category.DIALOGUE, true,
                        "scammer line repeats the previous round; 回应受害者最新问题并补充新信息，不要重复上一轮台词");
            AgentOutputValidator.funds(SpeakerRole.SCAMMER, turn.line(), turn.action(), turn.claimedRefundYuan(), turn.fundActions(), roundNo, funds, history, scene.v12());
            return turn;
        } catch (AgentCallException e) {
            log.info("agent validation role=SCAMMER round={} attempt={} reason={}", roundNo, attempt, e.getMessage());
            throw e.withRejectedOutput(raw);
        }
    }

    public List<TrustEventCandidate> event(AgentScene scene, List<DialogueLine> history, int roundNo,
            long remainingMs, int attempt, FundsContext funds) {
        return event(scene,history,roundNo,remainingMs,attempt,funds,"");
    }
    public List<TrustEventCandidate> event(AgentScene scene, List<DialogueLine> history, int roundNo,
            long remainingMs, int attempt, FundsContext funds, String waitingFacts) {
        return event(scene, history, roundNo, remainingMs, attempt, funds, waitingFacts, "");
    }
    public List<TrustEventCandidate> event(AgentScene scene, List<DialogueLine> history, int roundNo,
            long remainingMs, int attempt, FundsContext funds, String waitingFacts, String validationFeedback) {
        var variables=AgentContextAssembler.event(scene,history,roundNo,funds);
        variables.put("funds_context",variables.get("funds_context")+"\n"+waitingFacts);
        String prompt = PromptRenderer.render(scene.eventTemplate(), variables);
        if (scene.trustV2()) prompt += "\n【正负事件采用相同证据标准】输出示例中的NEW_CONTRADICTION不是默认判定。先检查是否回答了具体疑问或提供了相关新信息。"
                + "对‘是否先垫钱’明确回答‘首单我垫付、不用你垫钱’，是可记录的NEW_EFFECTIVE_EXPLANATION或DOUBT_RESOLVED，即使尚未付款。"
                + "同一进展只选一种正向类型，不同时计分；承诺不是已兑现，不能记PROMISE_FULFILLED。"
                + "备用链接、标题加‘备用’、普通加载故障本身不构成NEW_CONTRADICTION；必须有与既有说法冲突的具体事实。"
                + "动作中的‘伪装’‘伪造’是教学叙述，不代表受害者已识别，不能单凭这些词扣分。";
        if (scene.trustV3()) prompt += "\n【整轮证据标准】按本轮完整往来提取事件：联系人发言与本人回应都可以是证据，不能只看联系人一方。"
                + "每条事件必须声明speaker为SCAMMER或VICTIM，eventOrder从1按原文顺序递增；quote逐字来自该speaker本轮CONTENT或ACTION。"
                + "INDEPENDENT_SUPPORT/INDEPENDENT_DOUBT只能来自VICTIM；DECISIVE_COUNTEREVIDENCE必须给出PARTIAL或CONFIRMED档位，其他类型不得带档位。"
                + "COUNTEREVIDENCE_RESOLVED只在既有反证主题被本轮原文明确解释时使用，subjectKey须与原反证同主题；没有新进展不要重复计分。"
                + "同一主题沿用subjectKey，新进展使用新的progressKey；不要输出任何分数、阈值或系数。";
        if(scene.scenarioCode()==com.aiscam.antiscam.content.domain.ScenarioCode.IMPERSONATION_LOAN) {
            prompt+="\n【亲属身份矛盾自检】父亲姓名不等于儿子姓名；骗子画像真名不是被冒充亲属的姓名。"
                    +"儿子全名未设定时，自称小周、材料写周小周且符合既定周姓，本身不构成NEW_CONTRADICTION或DECISIVE_COUNTEREVIDENCE。"
                    +"姓氏与冻结家庭事实确实冲突时仍须记录反证；不得用昵称、怪异姓名、紧急借款或未独立认证本身判定矛盾。"
                    +"提交前核对矛盾双方：本轮原文与哪条已知事实/既往说法具体冲突，两者必须都有依据；无依据时不输出矛盾事件。";
        }
        if (!validationFeedback.isBlank()) prompt += "\n【上次输出未通过校验，请重新输出完整JSON】\n校验原因：" + validationFeedback
                + "\n只提取原文已经给出的事实；材料日期、户名或用途不明确时，materials=[]，不得补写日期、户名或用途。"
                + "仍可保留有原文证据的新事件及真实存在的requirement；不能把材料不齐全写成已经满足要求。"
                + "材料四项必须在同一quote中逐字出现，缺失则不提取摘要；原文不足以支持任何新事件时明确返回events空数组。";
        prompt += "\n【本轮EVENT可引用原文】quote只能逐字复制下列对应source的连续原文，不能改写、拼接或引用历史、另一角色发言及内部意图。"
                + "CONTENT引用台词，ACTION引用动作；保留原文标点和空格。没有原文依据的事件不输出。";
        if (scene.trustV3()) {
            for (var line : history) if (line.roundNo() == roundNo) {
                prompt += "\n[" + line.speaker() + "] CONTENT：" + line.content()
                        + "\n[" + line.speaker() + "] ACTION：" + (line.action() == null ? "（无动作，不可引用）" : line.action());
            }
        } else {
            for (var line : history) if (line.roundNo() == roundNo && line.speaker() == SpeakerRole.SCAMMER) {
                prompt += "\nCONTENT：" + line.content() + "\nACTION：" + (line.action() == null ? "（无动作，不可引用）" : line.action());
            }
        }
        String raw = llm.completeJson(request(AgentRole.EVENT, prompt, config.getNarratorTemperature(), remainingMs, roundNo, attempt, scene));
        try {
            var events = AgentOutputParser.event(raw, scene.trustV3());
            if (scene.trustV3()) {
                for (var event : events) {
                    var cited = history.stream().filter(line -> line.roundNo() == roundNo && line.speaker() == event.speaker())
                            .findFirst();
                    if (cited.isEmpty() || !AgentOutputValidator.eventQuoteMatches(event, cited.get().content(), cited.get().action()))
                        throw AgentOutputParser.fail(AgentCallException.Category.ANALYSIS,
                                "event quote is not in the declared speaker's current message (speaker=" + event.speaker()
                                + "); quote必须逐字引用该角色本轮台词或动作，不能概括、改写或引用另一角色与历史轮次");
                }
                return events;
            }
            for (var event : events) {
                if (scene.trustV2() && event.progressKey() == null) throw AgentOutputParser.fail(AgentCallException.Category.ANALYSIS, "trust-v2 event requires progressKey");
                if (!scene.trustV2() && (event.type().v2Only() || event.progressKey() != null))
                    throw AgentOutputParser.fail(AgentCallException.Category.ANALYSIS, "legacy event task cannot use v2 events");
                if (history.stream().noneMatch(line -> line.roundNo() == roundNo && line.speaker() == SpeakerRole.SCAMMER
                        && AgentOutputValidator.eventQuoteMatches(event,line.content(),line.action())))
                    throw AgentOutputParser.fail(AgentCallException.Category.ANALYSIS,
                            "event quote is not in the current scammer message (source=" + event.source()
                            + "); quote必须逐字引用本轮对应source的台词或动作，不能概括或改写；核对source与原文，删除无依据的事件并输出完整JSON");
            }
            return events;
        } catch (AgentCallException e) {
            log.info("agent validation role=EVENT round={} attempt={} reason={}", roundNo, attempt, e.getMessage());
            throw e.withRejectedOutput(raw);
        }
    }

    public VictimTurn victim(AgentScene scene, List<DialogueLine> history, DialogueLine scammerLine, int roundNo,
            long remainingMs, boolean regeneration, int attempt, FundsContext funds) {
        return victim(scene, history, scammerLine, roundNo, remainingMs, regeneration, attempt, funds, "");
    }

    public VictimTurn victim(AgentScene scene, List<DialogueLine> history, DialogueLine scammerLine, int roundNo,
            long remainingMs, boolean regeneration, int attempt, FundsContext funds, String receivedFacts) {
        return victim(scene, history, scammerLine, roundNo, remainingMs, regeneration, attempt, funds, receivedFacts, "");
    }
    public VictimTurn victim(AgentScene scene, List<DialogueLine> history, DialogueLine scammerLine, int roundNo,
            long remainingMs, boolean regeneration, int attempt, FundsContext funds, String receivedFacts, String validationFeedback) {
        return victim(scene, history, scammerLine, roundNo, remainingMs, regeneration, attempt, funds, receivedFacts,
                validationFeedback, funds == null || funds.gate() == null ? 0 : funds.gate().score());
    }
    public VictimTurn victim(AgentScene scene, List<DialogueLine> history, DialogueLine scammerLine, int roundNo,
            long remainingMs, boolean regeneration, int attempt, FundsContext funds, String receivedFacts,
            String validationFeedback, int previousScore) {
        var variables = AgentContextAssembler.victim(scene, history, scammerLine, roundNo, remainingMs / 1000, regeneration);
        if (scene.trustV1()) variables.put("received_facts", receivedFacts);
        if (funds != null) variables.put("funds_context", AgentContextAssembler.victimFunds(funds, scene.trustV2(), previousScore));
        String prompt = PromptRenderer.render(scene.victimTemplate(), variables);
        prompt += fundEvidenceInstructions();
        prompt += "\n【回应当前请求】先判断本轮对方实际邀请你做什么，再决定接受、询问或拒绝。"
                + "信任和付款意愿只约束涉及本人真实资金的请求，不是每轮必须说出口的拒绝理由。"
                + "对方只邀请模拟盘、模拟资金或免费体验、没有要求真实入金时，回应是否体验或询问具体操作，"
                + "不要把模拟体验改读为实盘投资，不要无缘由转向拒绝真金白银投入。"
                + "历史已有未解决的真实付款要求时仍可回应，不因出现模拟字样忽略实际收费。";
        if (!validationFeedback.isBlank()) prompt += "\n【上次输出未通过校验，请重新输出完整JSON】\n校验原因：" + validationFeedback
                + "\n保持本人背景与真实收到的信息，不编造付款或核实。未执行资金动作时fundActions=[]；"
                + "先核对本轮付款意愿和资金权限，再修正金额格式。若本笔付款或预留释放不被允许，fundActions=[]，"
                + "台词和动作同步改为未执行，可以继续询问或暂缓；被拒绝的输出尚未发生，不得为了补金额而编造转账，不能只清空数组却保留已付款的说法。"
                + "实际执行时引用本轮line/action中的逐字执行原文，保持金额、余额、预留限制和动作顺序，不通过篡改事实凑校验。";
        String raw = llm.completeJson(request(AgentRole.VICTIM, prompt, config.getVictimTemperature(), remainingMs, roundNo, attempt, scene));
        try {
            var turn = AgentOutputValidator.validate(AgentOutputParser.victim(raw));
            AgentOutputValidator.funds(SpeakerRole.VICTIM, turn.line(), turn.action(), null, turn.fundActions(), roundNo, funds, history, scene.v12());
            return turn;
        } catch (AgentCallException e) {
            log.info("agent validation role=VICTIM round={} attempt={} reason={}", roundNo, attempt, e.getMessage());
            throw e.withRejectedOutput(raw);
        }
    }

    public ValidatedJudgement narrator(AgentScene scene, List<DialogueLine> lines, int roundNo, PriorFacts prior,
            long remainingMs, int attempt, FundsContext funds, Integer claimed, boolean candidate, java.util.List<com.aiscam.antiscam.simulation.domain.FundAction> candidateActions) {
        return narrator(scene,lines,roundNo,prior,remainingMs,attempt,funds,claimed,candidate,candidateActions,"");
    }
    public ValidatedJudgement narrator(AgentScene scene, List<DialogueLine> lines, int roundNo, PriorFacts prior,
            long remainingMs, int attempt, FundsContext funds, Integer claimed, boolean candidate, List<FundAction> candidateActions,
            String waitingFacts) {
        return narrator(scene, lines, roundNo, prior, remainingMs, attempt, funds, claimed, candidate,
                candidateActions, waitingFacts, "");
    }
    public ValidatedJudgement narrator(AgentScene scene, List<DialogueLine> lines, int roundNo, PriorFacts prior,
            long remainingMs, int attempt, FundsContext funds, Integer claimed, boolean candidate, List<FundAction> candidateActions,
            String waitingFacts, String validationFeedback) {
        var variables = AgentContextAssembler.narrator(scene, lines, roundNo, prior);
        variables.put("previous_facts",variables.get("previous_facts")+"\n"+waitingFacts);
        if (funds != null) variables.put("funds_context", AgentContextAssembler.narratorFunds(funds, claimed, candidate));
        var summary = funds == null ? null : funds.summary();
        List<com.aiscam.antiscam.simulation.domain.FundPosting> candidatePostings = List.of();
        if (scene.v12() && candidate) {
            var application = com.aiscam.antiscam.simulation.rules.FundRules.apply(summary, funds, roundNo,
                    funds.existingFlows().size() + 1, candidateActions);
            summary = application.after();
            candidatePostings = application.postings();
        }
        Boolean v2Goal = scene.trustV2() ? com.aiscam.antiscam.simulation.rules.ScenarioGoalRules.goalAchieved(funds, candidatePostings) : null;
        if (scene.trustV2()) variables.put("scenario_goal", variables.get("scenario_goal")
                + "。本场小额金额上限=" + funds.gate().smallCapYuan() + "元；程序按实际流水认定截至本轮目标达成="
                + (prior.goalAchieved() || v2Goal) + "。候选结局仍需对照停止和反证事实，不能将首笔小额算成目标完成。");
        if (scene.v12()) variables.put("funds_context", variables.get("funds_context")
                + "\n" + (candidate ? "候选执行后的预检值（尚未入账）" : "正式轮末摘要")
                + "：累计实际付款=" + summary.totalPaidYuan() + "元，累计净亏损=" + summary.actualLossYuan()
                + "元，累计追加次数=" + summary.additionalPaymentCount());
        String prompt = PromptRenderer.render(scene.narratorTemplate(), variables);
        if (scene.v12()) {
            prompt += "\n【付款证据约束】点击链接不等于付款，同意体验、打开页面、填写金额、承诺充值也不等于已投入资金。"
                    + "付款类evidence必须由上述资金摘要支持，不能照抄输出结构中的付款证据示例。";
            if (summary.totalPaidYuan() == 0) prompt += "当前累计实际付款为0，evidence不得包含INITIAL_PAYMENT_COMPLETED、"
                    + "ADDITIONAL_PAYMENT_COMPLETED、INVESTMENT_COMPLETED、EXTRA_FEE_PAID、LOAN_TRANSFER_COMPLETED。"
                    + "删除付款类evidence，不得改成OTHER_DANGEROUS_ACTION_EXECUTED来代替付款；没有其他实际受骗事实时"
                    + "actualFraudOccurred=false、goalAchieved=false，无其他有效证据时evidence=[]；风险仍按实际行为判断。";
        }
        if (scene.trustV1() && !scene.trustV3()) {
            prompt += "\n【等待事项执行边界】waitingUpdates只记录本轮受害者实际执行的咨询/核实或明确放弃。"
                    + "仅索要高清图、备案号，或说‘我查一下’‘我问一下’但没有已发送、已拨打、正在核实等实际执行证据时，waitingUpdates=[]。"
                    + "basis必须逐字引用本轮受害者台词/action，不得编造执行或核实结果；其他分析字段仍按本轮真实情况输出。";
            prompt += "\n【态度变化】主动从询问转为认可、愿意参加或请求开始操作，可以构成INDEPENDENT_SUPPORT；单纯询问操作方法或重复配合不自动加分。"
                    + "正负态度都须引用本轮原文。仅当同一事由已有实际记录的解释/兑现事件时才避免重复计分，不能因理论上可归解释而漏掉尚未记录的独立认可。";
        }
        if (!validationFeedback.isBlank()) {
            prompt += "\n【上次输出未通过校验，请重新输出完整JSON】\n校验原因：" + validationFeedback;
            if (scene.trustV1() && !scene.trustV3()) {
                prompt += "\nattitudeEvents.quote只能逐字复制下列本轮受害者原文中的连续片段；CONTENT引用台词，ACTION引用动作。"
                        + "不能引用骗子、历史轮次、内部心理或自己的概括；同一疑点持续核实不算新的独立态度变化，没有新变化则attitudeEvents=[]。"
                        + "不得为了通过校验修改风险、资金或结局事实。\n【本轮态度事件可引用原文】";
                for (var line : lines) if (line.roundNo() == roundNo && line.speaker() == SpeakerRole.VICTIM) {
                    prompt += "\nCONTENT：" + line.content() + "\nACTION：" + (line.action() == null ? "（无动作，不可引用）" : line.action());
                }
            }
        }
        if (scene.trustV3()) {
            prompt += "\n【V3.5 整轮解说】本轮不是最后一轮时不要判断胜负，candidateOutcome与candidateReason必须为null；"
                    + "attitudeEvents必须为[]（整轮证据由EVENT任务单独提取，重复输出会二次计分）。"
                    + "speechAnalysis与methodAnalysis保持完整分析，供复盘报告使用；methodShort输出2至12字的手段标签，"
                    + "intentShort输出12至40字的意图句，建议目标18至24字，只用一句说明手段与目的，不复述金额、截图细节和完整经过。"
                    + "只用于实时解说卡，不得写分数、阈值或系数。标点、数字、英文字母都计入字数，提交前自检，不贴近40字上限。";

        }
        String raw = llm.completeJson(request(AgentRole.NARRATOR, prompt, config.getNarratorTemperature(), remainingMs, roundNo, attempt, scene));
        try {
            var judgement = AgentOutputParser.narrator(raw);
            if (!scene.trustV1() && !scene.trustV3() && (!judgement.waitingUpdates().isEmpty() || !judgement.attitudeEvents().isEmpty() || judgement.evidence().stream()
                    .anyMatch(e -> e.factCode() == com.aiscam.antiscam.simulation.rules.FactCode.DECISIVE_COUNTEREVIDENCE_FOUND))) {
                throw AgentOutputParser.fail(AgentCallException.Category.ANALYSIS, "legacy session cannot use new events");
            }
            return AgentOutputValidator.validate(judgement, scene.scenarioCode(), roundNo, lines, prior,
                    scene.v12() ? summary : null, v2Goal, scene.trustV3());
        } catch (AgentCallException e) {
            log.info("agent validation role=NARRATOR round={} attempt={} reason={}", roundNo, attempt, e.getMessage());
            throw e.withRejectedOutput(raw);
        }
    }

    private static String fundEvidenceInstructions() {
        return "\n【执行证据格式】line和action各不超过120个字符。资金quote必须包含阿拉伯数字金额与已完成的资金动词，"
                + "并逐字出现在本轮line/action中；自然台词用中文金额时，可以在实际action中写对应阿拉伯数字。"
                + "先按情境判断是否真的执行；未来承诺、等待截图/收款确认、查收旧款都保持fundActions=[]。"
                + "已执行可用已转账、转了、已发放、已返款、已释放预留等明确表述，不能为满足格式虚构执行。";
    }

    private LlmRequest request(AgentRole role, String prompt, double temperature, long remainingMs, int roundNo, int attempt,
            AgentScene scene) {
        long timeout = Math.max(1, Math.min(config.getCallTimeoutMs(), remainingMs));
        return new LlmRequest(role, prompt, temperature, config.getMaxTokens(), Duration.ofMillis(timeout), roundNo, attempt,
                scene.scriptKey());
    }
}
