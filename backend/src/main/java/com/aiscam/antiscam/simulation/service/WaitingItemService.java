package com.aiscam.antiscam.simulation.service;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.agent.AgentOutputValidator;
import com.aiscam.antiscam.content.domain.ScenarioCode;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.FriendFeedbackRules;
import com.aiscam.antiscam.simulation.rules.FriendFeedbackRules.ConsultTarget;
import com.aiscam.antiscam.simulation.snapshot.ConfigurationSnapshots;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WaitingItemService {
    private final WaitingItemRepository items;
    private final SimulationSessionRepository sessions;
    private final SimulationRoundRepository rounds;
    private final SimulationMessageRepository messages;
    private final ConfigurationSnapshots snapshots;
    public WaitingItemService(WaitingItemRepository items, SimulationSessionRepository sessions,
            SimulationRoundRepository rounds, SimulationMessageRepository messages, ConfigurationSnapshots snapshots) {
        this.items=items; this.sessions=sessions; this.rounds=rounds; this.messages=messages; this.snapshots=snapshots;
    }
    private boolean enabled(SimulationSession s) {
        return "waiting-v1".equals(snapshots.read(s.getRuntimeSnapshot()).path("waiting_ruleset_version").asText());
    }
    private WaitingItem open(long session, WaitingItemType type, String subject, String text, String basis, int round) {
        var found=items.findBySessionIdAndItemTypeAndSubjectKey(session,type,subject);
        if (found.isPresent()) { found.get().refreshRequest(text,basis); return found.get(); }
        return items.save(new WaitingItem(session,type,subject,text,basis,round));
    }
    public void recordMaterials(SimulationSession session, int round, SimulationMessage message, List<TrustEventCandidate> events) {
        if (!enabled(session)) {
            if (events.stream().anyMatch(e -> !e.materials().isEmpty() || e.requirement()!=null))
                throw new IllegalArgumentException("Legacy session cannot use waiting details");
            return;
        }
        if (events.stream().allMatch(e -> e.materials().isEmpty() && e.requirement()==null)) return;
        var savedRounds=rounds.findBySessionIdOrderByRoundNoAsc(session.getId()).stream().filter(r -> r.getRoundNo()<=round).toList();
        var saved=messages.findByRoundIdIn(savedRounds.stream().map(SimulationRound::getId).toList());
        for (var event : events) {
            var req=event.requirement();
            if (req!=null) {
                if (saved.stream().noneMatch(m -> m.getSpeakerRole()==SpeakerRole.VICTIM && contains(m,req.basis())))
                    throw new IllegalArgumentException("Requirement must cite an actual victim request");
                var requested=open(session.getId(),req.type(),req.subjectKey(),req.text(),req.basis(),round);
                if (req.type()==WaitingItemType.INFORMATION && requested.getStatus()==WaitingItemStatus.OPEN
                        && event.type().positiveExplanation())
                    requested.resolve(WaitingItemStatus.RESPONDED,event.quote(),round);
            }
            for (var material : event.materials()) {
                String details="金额"+material.amountYuan()+"元；日期"+material.date()+"；户名"+material.accountName()+"；用途"+material.purpose();
                if (details.length()>255) throw new IllegalArgumentException("Material summary too long");
                var item=open(session.getId(),WaitingItemType.MATERIAL,material.subjectKey(),"核对这份材料",event.quote(),round);
                if (item.getStatus()==WaitingItemStatus.OPEN || item.getStatus()==WaitingItemStatus.RESPONDED)
                    item.resolve(WaitingItemStatus.SATISFIED,details,round);
            }
        }
    }
    public void recordVictim(SimulationSession session, int round, SimulationMessage message, List<WaitingUpdate> updates) {
        if (!enabled(session)) {
            if (!updates.isEmpty()) throw new IllegalArgumentException("Legacy session cannot use waiting updates");
            return;
        }
        for (var update : updates) {
            AgentOutputValidator.waitingUpdate(update);
            if (!contains(message,update.basis())) throw new IllegalArgumentException("Waiting evidence is not in the victim message");
            if (update.action()==WaitingAction.OPEN) {
                if (!com.aiscam.antiscam.simulation.rules.WaitingItemRules.executed(update.basis(),false)) throw new IllegalArgumentException("Consultation or verification was not sent");
                open(session.getId(),update.type(),update.subjectKey(),update.text(),update.basis(),round);
            } else {
                var item=items.findBySessionIdAndItemTypeAndSubjectKey(session.getId(),update.type(),update.subjectKey())
                        .orElseThrow(() -> new IllegalArgumentException("Missing waiting item"));
                if (update.action()==WaitingAction.ABANDON) {
                    if (!update.basis().matches(".*(不再等|不等|放弃|停止|取消|不再核实).*")) throw new IllegalArgumentException("Abandonment requires an explicit decision");
                    item.resolve(WaitingItemStatus.ABANDONED,update.basis(),round);
                } else {
                    if (!com.aiscam.antiscam.simulation.rules.WaitingItemRules.executed(update.basis(),true)) throw new IllegalArgumentException("Verification is not complete");
                    // Keep the actual evidence, never the model's ungrounded paraphrase of an external reply.
                    item.resolve(WaitingItemStatus.SATISFIED,update.basis(),round);
                }
            }
        }
    }
    private static boolean contains(SimulationMessage m,String quote) {
        return m.getContent().contains(quote) || m.getActionText()!=null && m.getActionText().contains(quote);
    }
    /** Only a saved consultation from an earlier round can receive a reply. */
    public void beforeVictim(long sessionId, int round) {
        var session=sessions.lockById(sessionId).orElseThrow();
        if (!session.isRunning() || session.getCurrentRoundNo()!=round || !enabled(session)) return;
        var scenario=ScenarioCode.valueOf(snapshots.read(session.getScenarioSnapshot()).path("scenario_code").asText());
        boolean changed=false;
        for (var item : items.findBySessionIdOrderByIdAsc(sessionId)) {
            if (item.getItemType()!=WaitingItemType.CONSULTATION || item.getStatus()!=WaitingItemStatus.OPEN || item.getOpenedRoundNo()>=round) continue;
            var target="consult:claimed-person".equals(item.getSubjectKey()) ? ConsultTarget.CLAIMED_PERSON : ConsultTarget.THIRD_PARTY;
            var feedback=FriendFeedbackRules.reply(scenario,target);
            item.resolve(feedback.text().contains("联系不上") ? WaitingItemStatus.UNAVAILABLE : WaitingItemStatus.RESPONDED,feedback.text(),round);
            changed=true;
        }
        if (changed) session.bumpVersion();
    }
    @Transactional(readOnly=true)
    public String facts(long sessionId, boolean publicOnly, boolean includeKeys) {
        var session=sessions.findById(sessionId).orElseThrow();
        if (!enabled(session)) return "";
        var result=new StringBuilder();
        for (var item : items.findBySessionIdOrderByIdAsc(sessionId)) {
            if (publicOnly && item.getItemType()!=WaitingItemType.MATERIAL && item.getItemType()!=WaitingItemType.INFORMATION) continue;
            if (includeKeys) result.append("主题键：").append(item.getSubjectKey()).append("；类别：").append(item.getItemType()).append("；");
            result.append("此前事项：").append(item.getRequestText()).append("；依据：").append(item.getBasisText()).append("；");
            result.append(switch(item.getStatus()) {
                case OPEN -> "尚在等待具体反馈"; case RESPONDED -> "已经收到反馈";
                case SATISFIED -> item.getItemType()==WaitingItemType.MATERIAL ? "材料信息已齐全，不要再因信息缺失重复索取；齐全不表示真实" : "已经实际完成";
                case UNAVAILABLE -> "暂时无法取得结果，不代表核实通过"; case ABANDONED -> "已经不再等待，不代表核实通过";
            });
            if (item.getResolutionText()!=null) result.append("；具体内容：").append(item.getResolutionText());
            result.append('\n');
        }
        return result.toString();
    }
}
