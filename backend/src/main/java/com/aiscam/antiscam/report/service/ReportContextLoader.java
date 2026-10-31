package com.aiscam.antiscam.report.service;

import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportContextLoader {
    private final SimulationSessionRepository sessions;
    private final SimulationRoundRepository rounds;
    private final SimulationMessageRepository messages;
    private final AgentPromptRepository prompts;
    private final ConfigurationSnapshots snapshots;
    private final SimulationReportRepository reports;
    private final FundFlowRepository funds;

    public ReportContextLoader(SimulationSessionRepository sessions, SimulationRoundRepository rounds,
            SimulationMessageRepository messages, AgentPromptRepository prompts, ConfigurationSnapshots snapshots, SimulationReportRepository reports, FundFlowRepository funds) {
        this.reports = reports; this.funds = funds;
        this.sessions = sessions;
        this.rounds = rounds;
        this.messages = messages;
        this.prompts = prompts;
        this.snapshots = snapshots;
    }

    @Transactional(readOnly = true)
    public ReportContext load(long sessionId) {
        var session = sessions.findById(sessionId).orElseThrow(() -> new IllegalStateException("Missing session"));

        var prompt = prompts.findById(session.getReportPromptId()).orElseThrow(() -> new IllegalStateException("Missing report prompt"));
        var savedRounds = rounds.findBySessionIdOrderByRoundNoAsc(sessionId);
        if (!com.aiscam.antiscam.report.rules.ReportEligibility.isEligible(session,
                savedRounds.stream().filter(r -> r.getPhase()==RoundPhase.COMPLETE).count()))
            throw new IllegalStateException("Session is not eligible for a report");
        var byRound = new HashMap<Long, List<SimulationMessage>>();
        for (var round : savedRounds) byRound.put(round.getId(), messages.findByRoundId(round.getId()));
        var row = reports.findById(sessionId).orElseThrow();
        int version = row.getContentSchemaVersion();
        if (version != snapshots.read(session.getRuntimeSnapshot()).path("report_content_schema_version").asInt(version)) throw new com.aiscam.antiscam.agent.ReportValidationException("report prompt/version mismatch");
        var flows = version >= 2 ? funds.findBySessionIdOrderBySequenceNoAsc(sessionId) : List.<FundFlow>of();
        if (version == 2) {
            long paid = flows.stream().filter(f -> f.getFlowType() == FundFlowType.PAYMENT).mapToLong(FundFlow::getAmountYuan).sum();
            long refunded = flows.stream().filter(f -> f.getFlowType() == FundFlowType.REFUND).mapToLong(FundFlow::getAmountYuan).sum();
            if (paid != session.getTotalPaidYuan() || refunded != session.getTotalRefundedYuan()
                    || Math.max(0,paid-refunded) != session.getActualLossYuan()) throw new com.aiscam.antiscam.agent.ReportValidationException("ledger/summary mismatch");
        }
        if (version == 3 || version == 4) {
            long paid=0, refunded=0, rewarded=0, general=0, emergency=0;
            for (var flow : flows) switch(flow.getFlowType()) {
                case PAYMENT -> paid+=flow.getAmountYuan(); case REFUND -> refunded+=flow.getAmountYuan();
                case REWARD -> rewarded+=flow.getAmountYuan();
                case RESERVE_RELEASE -> { if ("GENERAL".equals(flow.getReserveTier())) general+=flow.getAmountYuan(); else emergency+=flow.getAmountYuan(); }
            }
            if (paid!=session.getTotalPaidYuan() || refunded!=session.getTotalRefundedYuan() || rewarded!=session.getTotalRewardedYuan()
                    || general!=session.getGeneralReserveReleasedYuan() || emergency!=session.getEmergencyReserveReleasedYuan()
                    || Math.max(0,paid-refunded-rewarded)!=session.getActualLossYuan()
                    || session.getInitialAvailableFundsYuan()+general+emergency-paid+refunded+rewarded!=session.getAvailableBalanceYuan())
                throw new com.aiscam.antiscam.agent.ReportValidationException("ledger/summary mismatch");
        }
        var variables = version >= 3 ? ReportContextAssembler.variablesV3(session,savedRounds,byRound,snapshots,flows) : version == 2 ? ReportContextAssembler.variablesV2(session,savedRounds,byRound,snapshots,flows)
                : ReportContextAssembler.variables(session,savedRounds,byRound,snapshots);
        if (version == 4 && session.getOutcomeCode() == null) {
            variables.put("outcome", "用户主动退出，未形成胜负结论；仅复盘已保存过程和实际资金事实，不判定胜负。");
        }
        // Schema 4 reuses the pinned REPORT template; the verdict wording is appended for this run only.
        String template = version == 4 ? prompt.getTemplateContent() + V4_RESULT_NOTE : prompt.getTemplateContent();
        return new ReportContext(template, variables,
                snapshots.read(session.getScammerSnapshot()).path("name").asText(), savedRounds.size(), session.getOutcomeCode() == null ? null : ReportContextAssembler.outcomeText(session,savedRounds), version,
                version == 2 ? com.aiscam.antiscam.agent.AgentModels.ReportFundsSummary.from(session) : null,
                ReportContextAssembler.quotes(savedRounds,byRound), row.getAttemptNo(),
                version >= 3 ? com.aiscam.antiscam.agent.AgentModels.ReportFundsSummaryV3.from(session) : null);
    }

    /** Result wording for schema 4: a finished run states the verdict, a user exit states that none was formed. */
    private static final String V4_RESULT_NOTE = """

            【本场结果措辞（V3.5）】
            以下结果规则优先于旧模板的受骗/识破措辞要求，不改变已保存的行为、资金和证据事实。
            本场固定15轮，胜负由程序判定。若本场已产生结论，result.text 必须原样包含程序给出的胜方文字；
            若本场是用户主动退出且尚未形成胜负，result.text 必须写明“未形成胜负结论”，不得宣称骗子胜或受害者胜；
            正文可以如实描述已经发生的付款与净损失，但不得据此自行判定胜负。报告结构仍使用 contentSchemaVersion 3。
            """;
}
