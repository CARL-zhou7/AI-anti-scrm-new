package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentCallException.Category;
import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.OutcomeCode;
import com.aiscam.antiscam.simulation.domain.SpeakerRole;
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

    public static ScammerTurn validate(ScammerTurn turn) {
        return new ScammerTurn(line(turn.line()), action(turn.action()), required(turn.intent(), Category.DIALOGUE), turn.tone());
    }

    public static VictimTurn validate(VictimTurn turn) {
        return new VictimTurn(line(turn.line()), action(turn.action()), required(turn.psychology(), Category.DIALOGUE), turn.tone());
    }

    /**
     * @param lines every line up to and including the current round (the victim line may still be a candidate)
     * @param prior facts saved with the previous analysed round
     */
    public static ValidatedJudgement validate(NarratorJudgement j, ScenarioCode scenario, int roundNo,
            List<DialogueLine> lines, PriorFacts prior) {
        var c = Category.ANALYSIS;
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
        if (j.actualFraudOccurred() != hasFraudFact) throw fail(c, "actual fraud flag disagrees with evidence");
        var facts = merged.stream().map(e -> new ScenarioGoalRules.Fact(e.factCode(), e.roundNo(), e.eventOrder())).toList();
        if (j.goalAchieved() != ScenarioGoalRules.goalAchieved(scenario, facts)) throw fail(c, "goal flag disagrees with scenario rule");
        boolean stopEvidence = j.evidence().stream().anyMatch(e -> e.factCode() == FactCode.COOPERATION_STOPPED && e.roundNo() == roundNo);
        if (j.cooperationStopped() && !stopEvidence) throw fail(c, "stop needs victim evidence in this round");
        if (j.candidateOutcome() == OutcomeCode.UNDETERMINED) throw fail(c, "UNDETERMINED is never a candidate");
        if (j.candidateOutcome() != OutcomeRules.expected(j.goalAchieved(), j.actualFraudOccurred(), j.cooperationStopped())) {
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
