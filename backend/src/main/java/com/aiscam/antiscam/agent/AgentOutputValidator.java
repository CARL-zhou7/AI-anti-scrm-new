package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentCallException.Category;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.OutcomeCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.FundRules;
import com.aiscam.antiscam.simulation.rules.FactCode;
import com.aiscam.antiscam.simulation.rules.OutcomeRules;
import com.aiscam.antiscam.simulation.rules.ScenarioGoalRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Business validation of parsed Agent output (plan Task 6 rules 1-6). Pure functions: nothing here touches the
 * database, and the program never re-judges on the model's behalf — a mismatch is a retryable failure.
 */
public final class AgentOutputValidator {
    /** Hard cap per text item (decision D-4); the ~50/~40 character targets live in the prompts. */
    public static final int MAX_CODE_POINTS = 120;
    private static final Set<Integer> RISK_LEVELS = Set.of(0, 25, 50, 75, 100);
    private static final Pattern ACTION_IN_LINE = Pattern.compile("[（(【\\[][^）)】\\]]*?(转账|付款|点击|输入|发送|打开)[^）)】\\]]*?[）)】\\]]");
    private AgentOutputValidator() {}

    public static ReportContent report(ReportContent content, int expectedRounds, String outcomeDisplayText) {
        if (content == null || expectedRounds < 1 || content.roundAnalyses() == null
                || content.roundAnalyses().size() != expectedRounds) {
            throw new ReportValidationException("round count mismatch");
        }
        reportText(content.summary(), 600);
        reportText(content.speechAnalysis(), 600);
        reportText(content.victimBehaviorAnalysis(), 600);
        reportText(content.outcomeAnalysis(), 600);
        for (int i = 0; i < expectedRounds; i++) {
            var round = content.roundAnalyses().get(i);
            if (round == null || round.roundNo() != i + 1) throw new ReportValidationException("round order mismatch");
            reportText(round.analysis(), 300);
        }
        if (outcomeDisplayText == null || outcomeDisplayText.isBlank() || !content.outcomeAnalysis().contains(outcomeDisplayText)) {
            throw new ReportValidationException("authoritative outcome missing");
        }
        return content;
    }

    private static void reportText(String text, int max) {
        // Defensive output limits, not product copy limits; count Unicode code points.
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > max
                || text.indexOf('<') >= 0 || text.indexOf('>') >= 0) {
            throw new ReportValidationException("invalid report text");
        }
    }

    public static ScammerTurn validate(ScammerTurn turn) {
        return new ScammerTurn(line(turn.line()), action(turn.action()), required(turn.intent(), Category.DIALOGUE), turn.tone(), turn.claimedRefundYuan(), turn.fundActions());
    }

    public static VictimTurn validate(VictimTurn turn) {
        return new VictimTurn(line(turn.line()), action(turn.action()), required(turn.psychology(), Category.DIALOGUE), turn.tone(), turn.fundActions());
    }

    public static void funds(SpeakerRole role, String line, String action, Integer claimed, List<FundAction> actions,
            int roundNo, FundsContext context, List<DialogueLine> history) {
        funds(role, line, action, claimed, actions, roundNo, context, history, false);
    }

    public static void funds(SpeakerRole role, String line, String action, Integer claimed, List<FundAction> actions,
            int roundNo, FundsContext context, List<DialogueLine> history, boolean v12) {
        if (context == null) {
            if (claimed != null || !actions.isEmpty()) throw fail(Category.DIALOGUE, "legacy session cannot post funds");
            return;
        }
        if (role == SpeakerRole.VICTIM && claimed != null) throw fail(Category.DIALOGUE, "victim cannot claim refunds");
        if (claimed != null && claimed < 0) throw fail(Category.DIALOGUE, "negative refund claim");
        if (role == SpeakerRole.SCAMMER && context.scenarioCode() == ScenarioCode.IMPERSONATION_LOAN
                && (claimed != null || !actions.isEmpty())) throw fail(Category.DIALOGUE, "loan cannot refund or claim structured refund");
        long refund = 0;
        for (var a : actions) {
            if (a.speaker() != role || a.roundNo() != roundNo || a.quote() == null || a.quote().isBlank()
                    || !(line.contains(a.quote()) || (action != null && action.contains(a.quote())))) {
                throw fail(Category.DIALOGUE, "fund quote must belong to current speaker message");
            }
            if ((role == SpeakerRole.SCAMMER) != (a.type() == FundFlowType.REFUND)) throw fail(Category.DIALOGUE, "wrong fund action role");
            // shortcut: lexical execution checks cannot resolve every paraphrase; extend with real-model regression cases when observed.
            var amount = Pattern.compile("(?<![0-9.])" + a.amountYuan() + "(?![0-9.])");
            boolean executed = java.util.Arrays.stream(a.quote().split("[，。；,;！!？?]"))
                    .anyMatch(clause -> amount.matcher(clause).find()
                            && clause.matches(".*(转|付|返|到账|释放|动用).*" )
                            && !clause.matches(".*(之前|上轮|上次|昨天|曾经|先前|未|没|准备|打算|将|稍后|以后|承诺|如果|等会|不会|不要|会[给返转退付]|要[给返转退付]).*"));
            if (!executed || a.quote().matches(".*(之前|上轮|上次|昨天|曾经|先前).*")
                    || history.stream().anyMatch(l -> l.roundNo() < roundNo && l.speaker() == role
                    && (l.content().contains(a.quote()) || (l.action() != null && l.action().contains(a.quote()))))) {
                throw fail(Category.DIALOGUE, "fund quote is not a new executed action");
            }
            if (a.type() == FundFlowType.REFUND) refund += a.amountYuan();
        }
        if (refund > 0 && (claimed == null || refund > claimed)) throw fail(Category.DIALOGUE, "actual refund exceeds claim");
        try {
            FundRules.apply(new FundsSummary(0, context.availableBalanceYuan(), context.totalPaidYuan(),
                    context.totalRefundedYuan(), FundRules.actualLoss(context.totalPaidYuan(), context.totalRefundedYuan()),
                    0, context.reserveReleasedYuan()), context.scenarioCode(), context.reservedFundsTotalYuan(), roundNo,
                    context.existingFlows().size() + 1, actions, context.existingFlows());
        } catch (IllegalArgumentException e) {
            long payments = actions.stream().filter(a -> a.type() == FundFlowType.PAYMENT).mapToLong(FundAction::amountYuan).sum();
            if (v12 && "payment exceeds balance".equals(e.getMessage())
                    && payments > (long) context.availableBalanceYuan() + context.releasableReserveYuan()) {
                throw new FundsUnavailableException();
            }
            throw fail(Category.DIALOGUE, e.getMessage());
        }
    }

    /**
     * @param lines every line up to and including the current round (the victim line may still be a candidate)
     * @param prior facts saved with the previous analysed round
     */
    public static ValidatedJudgement validate(NarratorJudgement j, ScenarioCode scenario, int roundNo,
            List<DialogueLine> lines, PriorFacts prior) {
        return validate(j, scenario, roundNo, lines, prior, null);
    }

    public static ValidatedJudgement validate(NarratorJudgement j, ScenarioCode scenario, int roundNo,
            List<DialogueLine> lines, PriorFacts prior, FundsSummary funds) {
        boolean v12 = funds != null;
        var c = Category.ANALYSIS;
        if (j.characterExit() && (!v12 || j.candidateOutcome() != null || j.cooperationStopped()
                || j.characterExitReason() == null || j.characterExitReason().isBlank())) throw fail(c, "invalid character exit");
        if (!j.characterExit() && j.characterExitReason() != null) throw fail(c, "unexpected exit reason");
        for (String text : List.of(j.fraudStage(), j.speechAnalysis(), j.methodAnalysis(), j.victimPsychology(), j.riskReason())) {
            if (text.isBlank() || text.codePointCount(0, text.length()) > MAX_CODE_POINTS) throw fail(c, "analysis text length");
        }
        if (!RISK_LEVELS.contains(j.riskScore())) throw fail(c, "risk score outside the five levels");
        if (j.goalAchieved() && !j.actualFraudOccurred()) throw fail(c, "goal requires actual fraud");
        if ((prior.actualFraud() && !j.actualFraudOccurred()) || (prior.goalAchieved() && !j.goalAchieved())) {
            throw fail(c, "cumulative facts cannot revert");
        }
        List<EvidenceItem> merged = EvidenceValidator.validateAndMerge(j.evidence(), prior.evidence(), roundNo, lines);
        boolean hasFraudFact = merged.stream().anyMatch(e -> e.factCode().isActualFraud());
        if (v12 && funds.totalPaidYuan() > 0 && !j.actualFraudOccurred()) throw fail(c, "saved payments are actual fraud");
        boolean monetaryFact = merged.stream().anyMatch(e -> switch (e.factCode()) {
            case INITIAL_PAYMENT_COMPLETED, ADDITIONAL_PAYMENT_COMPLETED, INVESTMENT_COMPLETED, EXTRA_FEE_PAID, LOAN_TRANSFER_COMPLETED -> true;
            default -> false;
        });
        if (v12 && monetaryFact && funds.totalPaidYuan() == 0) throw fail(c, "payment evidence requires posted funds");
        if (j.actualFraudOccurred() != hasFraudFact) throw fail(c, "actual fraud flag disagrees with evidence");
        var facts = merged.stream().map(e -> new ScenarioGoalRules.Fact(e.factCode(), e.roundNo(), e.eventOrder())).toList();
        if (j.goalAchieved() != (v12 ? prior.goalAchieved() || ScenarioGoalRules.goalAchieved(scenario, facts, funds) : ScenarioGoalRules.goalAchieved(scenario, facts))) throw fail(c, "goal flag disagrees with scenario rule");
        boolean stopEvidence = j.evidence().stream().anyMatch(e -> e.factCode() == FactCode.COOPERATION_STOPPED && e.roundNo() == roundNo);
        if (j.cooperationStopped() && !stopEvidence) throw fail(c, "stop needs victim evidence in this round");
        if (j.candidateOutcome() == OutcomeCode.UNDETERMINED || j.candidateOutcome() == OutcomeCode.LIMIT_REACHED) throw fail(c, "UNDETERMINED is never a candidate");
        if (j.candidateOutcome() != (v12 ? OutcomeRules.expected(j.goalAchieved(), j.actualFraudOccurred(), j.cooperationStopped()) : OutcomeRules.expectedLegacy(j.goalAchieved(), j.actualFraudOccurred(), j.cooperationStopped()))) {
            throw fail(c, "candidate outcome disagrees with outcome rules");
        }
        if ((j.candidateOutcome() == null) != (j.candidateReason() == null || j.candidateReason().isBlank())) {
            throw fail(c, "candidate reason must accompany candidate outcome");
        }
        return new ValidatedJudgement(j, merged);
    }

    private static String line(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > MAX_CODE_POINTS) throw fail(Category.DIALOGUE, "line length");
        if (ACTION_IN_LINE.matcher(trimmed).find()) throw fail(Category.DIALOGUE, "action narration inside line");
        return trimmed;
    }

    private static String action(String action) {
        if (action == null) return null;
        String trimmed = action.strip();
        if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > MAX_CODE_POINTS) throw fail(Category.DIALOGUE, "action length");
        return trimmed;
    }

    private static String required(String text, Category c) {
        if (text == null || text.isBlank()) throw fail(c, "internal note required");
        return text.strip();
    }

    static AgentCallException fail(Category c, String message) { return new AgentCallException(c, true, message); }

    /** Evidence rules (DB design §7.4): quotes are verbatim, only the victim's own behaviour counts. */
    public static final class EvidenceValidator {
        private EvidenceValidator() {}

        public static List<EvidenceItem> validateAndMerge(List<EvidenceItem> current, List<EvidenceItem> prior, int roundNo,
                List<DialogueLine> lines) {
            var c = Category.ANALYSIS;
            for (EvidenceItem e : current) {
                if (e.speaker() != SpeakerRole.VICTIM) throw fail(c, "facts may only cite the victim");
                if (e.roundNo() < 1 || e.roundNo() > roundNo || e.eventOrder() < 1) throw fail(c, "evidence position");
                DialogueLine line = lines.stream().filter(l -> l.roundNo() == e.roundNo() && l.speaker() == e.speaker())
                        .findFirst().orElseThrow(() -> fail(c, "evidence cites a missing line"));
                String source = e.source() == EvidenceSource.CONTENT ? line.content() : line.action();
                if (e.quote().isBlank() || source == null || !source.contains(e.quote())) throw fail(c, "quote not found verbatim");
            }
            for (EvidenceItem a : current) {
                for (EvidenceItem b : current) {
                    boolean samePlace = a.roundNo() == b.roundNo() && a.source() == b.source() && a.quote().equals(b.quote());
                    if (samePlace && pair(a.factCode(), b.factCode())) throw fail(c, "one transaction counted twice");
                }
            }
            Map<String, EvidenceItem> merged = new LinkedHashMap<>();
            for (EvidenceItem e : prior) merged.put(key(e), e);
            for (EvidenceItem e : current) merged.putIfAbsent(key(e), e);
            return List.copyOf(new ArrayList<>(merged.values()));
        }

        private static boolean pair(FactCode a, FactCode b) {
            return (a == FactCode.INITIAL_PAYMENT_COMPLETED && b == FactCode.ADDITIONAL_PAYMENT_COMPLETED)
                    || (a == FactCode.INVESTMENT_COMPLETED && b == FactCode.EXTRA_FEE_PAID);
        }

        private static String key(EvidenceItem e) {
            return e.factCode() + "|" + e.roundNo() + "|" + e.speaker() + "|" + e.source() + "|" + e.eventOrder() + "|" + e.quote();
        }
    }
}
