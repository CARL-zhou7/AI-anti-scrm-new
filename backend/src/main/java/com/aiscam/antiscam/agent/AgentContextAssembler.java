package com.aiscam.antiscam.agent;

import com.aiscam.antiscam.agent.AgentModels.*;
import com.aiscam.antiscam.simulation.domain.*;
import com.aiscam.antiscam.simulation.rules.ScenarioGoalRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds each role's prompt variables from only what that role may see (plan Task 6 isolation table):
 * the scammer and the victim never receive internal notes, narration or risk; the victim never receives the
 * scenario type or key-goal definition; only the narrator sees internal notes.
 */
public final class AgentContextAssembler {
    public static final int LINE_TARGET_CHARS = 50;
    public static final int ANALYSIS_TARGET_CHARS = 40;
    private AgentContextAssembler() {}

    public static Map<String, String> scammer(AgentScene scene, List<DialogueLine> history, int roundNo, long remainingSeconds) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("line_target_chars", Integer.toString(LINE_TARGET_CHARS));
        v.put("scenario_name", scene.scenarioName());
        v.put("scenario_description", scene.scenarioDescription());
        v.put("scenario_goal_hint", ScenarioGoalRules.scammerHint(scene.scenarioCode()));
        v.put("scammer_profile", scene.scammerProfile());
        if (scene.trustV1()) v.put("victim_public_profile", scene.victimPublicProfile()
                + "\n【称呼对象】以上是受话者的公开背景，不是你的身份。姓名或姓氏未明确公开、对方也未自我介绍时，用你或您，不编姓或称呼。"
                + "对方叫你的称呼指向你，不能反过来用它称呼对方；你对对方的称呼也不是你的姓名。历史中的无依据称呼应改用中性称呼，不为了保持连续而沿用错误。");
        v.put("dialogue_history", publicHistory(history));
        v.put("round_no", Integer.toString(roundNo));
        if (scene.scammerTemplate() != null && scene.scammerTemplate().contains("{{remaining_seconds}}"))
            v.put("remaining_seconds", Long.toString(remainingSeconds));
        String pacing = roundNo <= 4 ? "当前处于前4轮，以建立联系、建立信任和铺垫为主，不要急于收网。"
                : "可以根据对方状态加大推进力度。";
        if (remainingSeconds < 120) pacing += "剩余时间较少，台词请更简短。";
        if (scene.trustV2()) pacing += "本场最多20轮，当前剩余" + Math.max(0,20-roundNo)
                + "轮。持续回应当前问题，已有小额投入后围绕结果和新增安排推进；尽量18轮内自然形成决定，不为轮数虚构对方配合。"
                + (roundNo >= 12 ? "已进入后半段，不再引入无关支线或更换主事件，围绕剩余顾虑及是否继续展开。" : "");
        if (scene.trustV3()) pacing = "本场固定15轮，当前第" + roundNo + "轮，还剩" + Math.max(0, 15 - roundNo)
                + "轮。第1至4轮建立联系、建立信任并铺垫，不急于收网；第5至12轮每轮围绕对方一个真实顾虑推进；"
                + "第13至15轮收束，不再引入无关支线或更换主事件，围绕剩余顾虑与是否继续展开，不为轮数虚构对方配合。"
                + (roundNo >= 13 ? "已进入收束阶段。" : "");
        v.put("pacing_hint", pacing);
        // The v19 scammer prompt carries {{scene_rules}}; older pinned prompts do not, and the renderer rejects an
        // unused variable, so the rule text is only handed to a template that can actually consume it.
        if (scene.scammerTemplate() != null && scene.scammerTemplate().contains("{{scene_rules}}"))
            v.put("scene_rules", scene.sceneRules() == null || scene.sceneRules().isBlank()
                    ? "（本场没有额外的场景规则）" : scene.sceneRules());
        return v;
    }

    public static Map<String, String> victim(AgentScene scene, List<DialogueLine> history, DialogueLine scammerLine,
            int roundNo, long remainingSeconds, boolean regeneration) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("line_target_chars", Integer.toString(LINE_TARGET_CHARS));
        v.put("minimum_round_constraint", roundNo <= 4
                ? "【前4轮行为约束】当前为第" + roundNo + "轮（前4轮）。你可以怀疑、质疑、拖延、表示要核实或着手核实，也可以部分配合；"
                  + "但本轮不要作出明确终止配合（如拉黑、挂断后不再联系、明确拒绝继续）或彻底完成对方全部要求等决定性行为。"
                : "");
        v.put("regeneration_note", regeneration
                ? "【重新生成提示】你上一版回应会形成决定性的结束行为，已被退回。请保持人物性格，给出一个尚未作出决定性行为的回应。"
                : "");
        v.put("victim_profile", scene.victimProfile() + (scene.trustV1()
                ? "\n【称呼对象】以上是你本人的背景，不是联系人的身份。联系人发言开头对你的称呼指向你，不是对方自我介绍；不能照搬这个称呼去称对方。"
                  + "对方没有明确介绍姓名或可确认的关系称谓时，用你或您。不能从对方给你的称呼推断其姓名、姓氏或性别；此前误称不需要继续沿用，改用中性称呼。" : ""));
        if (scene.trustV1()) v.put("fund_purposes", fundPurposes(scene.scenarioCode()));
        v.put("dialogue_history", scene.trustV1() ? personalHistory(history) : publicHistory(history));
        v.put("scammer_line", scene.trustV1() ? scammerLine.content()
                + (scammerLine.action() == null ? "" : "\n对方的行动：" + scammerLine.action()) : publicLine(scammerLine));
        if (scene.trustV1() && roundNo <= 4) v.put("minimum_round_constraint",
                "前几轮通常不要仅为结束聊天而终止往来，但已有明确付款意愿且执行条件齐备时可以本轮付款，不必等待更多轮次。若已有确切反证并明确停止，或已实际付款后决定止损、拒绝追加，可以如实停止。不要为了继续聊天而否认事实。");
        v.put("round_no", Integer.toString(roundNo));
        v.put("remaining_seconds", Long.toString(remainingSeconds));
        return v;
    }

    /** @param lines all lines up to and including the current round, internal notes included (narrator only). */
    public static Map<String, String> narrator(AgentScene scene, List<DialogueLine> lines, int roundNo, PriorFacts prior) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("scenario_name", scene.scenarioName());
        v.put("scenario_code", scene.scenarioCode().name());
        v.put("scenario_goal", (scene.trustV2()
                ? "先发生小额实际付款，之后再次实际追加，使累计付款减本金退款超过本人的小额金额上限。报酬不抵扣该本金；只有意向、首次小额或单纯提高信任均不达成目标"
                : ScenarioGoalRules.description(scene.scenarioCode(), scene.v12()))
                + (scene.trustV1() ? "。已执行行为满足目标时，即使处于前四轮也须如实判定goalAchieved=true、candidateOutcome=DEFRAUDED；不能为了凑轮数否认付款或目标。候选预检按候选执行后的资金摘要，正式解说按已入账流水判断。" : "")
                + (scene.trustV3() ? "。本场固定15轮，胜负由程序在第15轮按最终受骗倾向分与实际净损失判定，本段只作目标参考；"
                     + "你只负责如实分析本轮，不判断胜负、不输出候选结局，也不得为凑目标或凑轮数否认已发生的事实。" : ""));
        v.put("scammer_profile", scene.scammerProfile());
        v.put("victim_profile", scene.victimProfile());
        v.put("dialogue_history", fullHistory(lines.stream().filter(l -> l.roundNo() < roundNo).toList()));
        v.put("round_no", Integer.toString(roundNo));
        v.put("current_round", fullHistory(lines.stream().filter(l -> l.roundNo() == roundNo).toList()));
        v.put("previous_facts", facts(prior));
        v.put("analysis_target_chars", Integer.toString(ANALYSIS_TARGET_CHARS));
        return v;
    }

    public static String victimFunds(FundsContext f) {
        return victimFunds(f, false, f.gate() == null ? 0 : f.gate().score());
    }

    public static String victimFunds(FundsContext f, boolean v2, int previousScore) {
        if (f.v3()) {
            var gate = f.gate();
            long available = (long) f.availableBalanceYuan() + f.generalReserveTotalYuan() - f.generalReserveReleasedYuan();
            long willing = gate.score() < gate.small() ? 0 : gate.score() < gate.large()
                    ? Math.min(available, Math.max(0L, (long) gate.smallCapYuan() - f.totalPaidYuan() + f.totalRefundedYuan()))
                    : available + f.emergencyReserveTotalYuan() - f.emergencyReserveReleasedYuan();
            return "当前可动用余额：" + f.availableBalanceYuan() + "元；普通预留剩余："
                + (f.generalReserveTotalYuan() - f.generalReserveReleasedYuan()) + "元；应急预留剩余："
                + (f.emergencyReserveTotalYuan() - f.emergencyReserveReleasedYuan())
                + "元。预留不在余额中，只能在本轮实际付款不足时释放对应差额，先普通再应急。"
                + "RESERVE_RELEASE必须带reserveTier=GENERAL或EMERGENCY和具体理由；其余动作reserveTier=null。"
                + "真实持有资金合计：" + (available + f.emergencyReserveTotalYuan() - f.emergencyReserveReleasedYuan()) + "元（包括预留，不代表都愿意支付）。"
                + "你此刻愿意新增支付的总额最多为" + willing + "元，不能拆分绕过这个总额。"
                + "意愿额度为0时，fundActions必须为[]，不得付款或释放预留，台词和动作也不得声称已经执行；亲友求助或情况紧急不改变这个限制。"
                + "此前实际收到本金返款" + f.totalRefundedYuan() + "元、报酬" + f.totalRewardedYuan()
                + "元。只有已保存资金事实才代表到账；对方的承诺、截图和口头声称不等于收款，不得据此编造微信到账或确认收款。"
                + "付款意愿额度不是账户余额，即使意愿为零也不代表没有钱。余额足以支付请求时，不得声称拿不出、没钱、凑不出或生活困难；"
                + "若暂不愿付，应表达不放心转、暂不转或不愿追加，不编造资金不足、限额、账户冻结、家人管钱等理由。"
                + "此前说过没钱但与真实资金不符时，本轮纠正为暂不愿转，不能为保持前文而继续虚构穷困。内部付款意愿额度不向联系人报数。"
                + "\n【你此刻的判断倾向，仅用于形成自然回应】\n"
                + (v2 ? victimInclinationV2(f, previousScore) : victimInclination(f));
        }
        return "当前可动用余额：" + f.availableBalanceYuan() + "元；尚可释放预留：" + f.releasableReserveYuan()
                + "元。只记录本轮新执行付款/预留释放，不将旧转账复述作为新动作。";
    }

    private static String victimInclinationV2(FundsContext f, int previousScore) {
        var gate = f.gate();
        double progress = Math.max(0, Math.min(1, (gate.score() - gate.accept()) / (double) (gate.small() - gate.accept())));
        String caution = progress < 0.5
                ? "仍倾向要求能说明来源的依据，并通过自己已有或自行查到的渠道核验关键疑点；对已回答部分可以认可，不逐条怀疑。"
                : "更容易接受联系人给出的具体解释或材料；普通疑问得到合理说明后可以不再独立核验，转而讨论下一步。主观相信不能写成客观已核实。";
        String movement = gate.score() > previousScore ? "刚收到的信息使你比上一轮更愿意相信，具体放松此前相关顾虑。"
                : gate.score() < previousScore ? "新出现的问题使你重新谨慎，重新关注尚未解决的关键疑点，不机械延续之前的相信。"
                : "结合此前态度自然延续，不因多聊一轮自动变得更相信或更怀疑。";
        String payment = gate.score() < gate.small() ? "当前暂不投入自己的钱；可以接受解释、了解参与步骤并表达尝试意愿，不把暂不付款表现成全面怀疑。"
                : gate.score() < gate.large() ? "当前愿意小额尝试；新请求符合本轮愿意支付的总额、金额用途和收款对象齐备时直接实际付款，不固定再等两轮。超过当前可接受总额的追加请求可以讨论或拒绝，不能拆单绕过。"
                : "当前已愿意配合较多资金的安排；有明确新请求且在意愿和资金范围内时实际执行，不仅表达考虑或准备。确切反证和已明确停止仍须尊重。";
        String afterPayment = f.totalPaidYuan() == 0 ? "尚未付款，先针对本次请求形成判断，不凭空发起投入。"
                : "已经发生小额投入，是否继续须看新请求。自身经历中容易延续顺利体验时可以逐步放松；习惯谨慎时，对新增金额、费用和条件重新关注风险。"
                  + "前一次已回答的问题不重问，旧解释也不自动回答新增顾虑。仍可能被有效解释说服；不预设必须继续或必须止损。"
                  + "态度须承接上一轮的实际付款与关切：暂不愿追加时，台词说明一个来自已知事实的具体理由，"
                  + "如先等已付费用的检查结果、对新增费用用途仍不清楚或不愿独自承担余款；不能突然只说不追加了。"
                  + "没有新反证不能突然宣称已识破。内部额度不是人物知道的理由，不能据此编造新矛盾或资金困难。"
                  + "短暂犹豫、等待已付款的结果或本轮暂不追加不等于停止往来；只有明确拒绝后续配合才是停止，一旦明确停止就保持决定。";
        return victimCaution(gate.score()) + String.format(java.util.Locale.ROOT,
                "【本轮生成侧连续参考，不在台词或心理中报数】了解至尝试的接纳进度=%.4f。", progress)
                + caution + movement + payment + afterPayment
                + "以上意愿范围不覆盖本轮具体质疑：付款后反复加费、改变提现条件或违背到账承诺时，可以抵触并拒绝追加；为拿回本金继续联系不代表仍然相信。"
                + "画像里的核验习惯是会随具体经历变化的偏好，不是每次付款必须满足的固定程序；人格和已知家庭事实仍保持一致。"
                + "照片、声音、资料来自对方不等于独立核验，也不意味着你已经知道它们伪造。已有确切反证仍如实处理。"
                + "没有新付款请求不自行付款，同一笔已付请求不重复支付，缺少执行信息只问缺少的内容；模拟收款对象足够，不索要真实账号。";
    }

    private static String victimInclination(FundsContext f) {
        var gate = f.gate();
        String inclination;
        if (gate.score() < gate.accept()) {
            inclination = "你目前还不太愿意依赖联系人的说法，可以先了解或提出一个具体疑问，暂不投入自己的钱。";
        } else if (gate.score() < gate.small()) {
            inclination = "你愿意认真了解，也愿意配合不涉及付款的具体事项；暂不投入自己的钱，不必反复盘问已经回答清楚的问题。";
        } else if (gate.score() < gate.large()) {
            inclination = (long) f.totalPaidYuan() - f.totalRefundedYuan() >= gate.smallCapYuan()
                    ? "你仍愿意继续交流，但已有投入尚未收回，暂不继续增加资金投入，可以等待实际结果或作出明确决定。"
                    : "你已决定配合小额安排；对方提出明确金额、用途和收款对象，且未超过你此刻愿意支付的总额时，本轮就实际执行付款，不再停留在考虑、准备或核对后再转。尚未补齐的小额差额、费用变化或对方先前承诺承担费用属于可以随口提出的普通顾虑，不自动取消本轮已形成的付款决定。涉及较多投入或应急资金时暂缓，不自行缩小或拆分对方请求来付款。";
        } else {
            inclination = "你已决定相信当前联系人并配合较多资金的安排；对方提出明确金额、用途和收款对象，且未超过你此刻愿意支付的总额时，本轮就实际执行付款，包括按需要动用自己的预留，不再停留在考虑、准备或核对后再转。尚未补齐的小额差额、费用变化或对方先前承诺承担费用属于可以随口提出的普通顾虑，不自动取消本轮已形成的付款决定。";
        }
        return victimCaution(gate.score()) + inclination + "这是你此刻已经形成的行动决定，优先于背景里一般性的谨慎、细致、记账习惯及此前尚未完成的核实打算。"
                + "人物特点只影响语气与简短顾虑：可以随口问一句或叮嘱，但不能把答复、照片、身份证尾号或另一人接话设为新的付款前提。"
                + "缺少实际执行所需的金额、用途或收款对象时，只问缺少的信息；不要求真实账号，已知收款对象或模拟账户足够。没有新付款请求或已经付过的同一笔，不自行付款或重复入账。"
                + "尚未独立核实、普通风险担忧和材料来自对方，不等于已经证实对方有假，不能因此无限拖延。"
                + "已经确认的身份冲突、确切反证或明确停止往来仍应优先考虑，不能改写成已核实；"
                + "确切反证须有独立渠道的明确否认、既定本人事实冲突等依据，费用争议、等待返款或普通话术变化本身不能等同已识破。"
                + "本轮尚无明确的新付款请求时不要自行转账；有明确且允许的请求时，不能因前轮未要求付款就沿用一直不付款的惯性。不要复述本段说明。";
    }

    private static String victimCaution(int trust) {
        double questioning = Math.pow(1 - trust / 100.0, 2);
        return String.format(java.util.Locale.ROOT,
                "【生成侧表达控制，不属于人物所知事实，不得在台词或心理中复述】主动追问强度=%.4f；接纳解释倾向=%.4f。",
                questioning, 1 - questioning)
                + "这些是本轮表达强弱，不能作为付款权限。追问越弱，越少主动要求照片、单据、回拨或反复确认，普通顾虑只随口问；"
                + "接纳倾向越强，越自然接受已回答的内容，把关切和帮忙意愿放在盘问前，不把每个普通细节当作破绽。"
                + "两种强度逐渐变化，不因尚未愿意付款就恢复成全面警惕，也不要机械维持早先的核实措辞。"
                + "已经确认的反证仍如实处理；实际付款按以下行动决定与资金限制执行。";
    }

    public static String fundPurposes(com.aiscam.antiscam.content.domain.ScenarioCode scenario) {
        return switch (scenario) {
            case TASK_REBATE -> "INITIAL_PAYMENT / ADDITIONAL_PAYMENT / OTHER_PAYMENT";
            case FAKE_INVESTMENT -> "INVESTMENT_PAYMENT / EXTRA_FEE_PAYMENT / OTHER_PAYMENT";
            case IMPERSONATION_LOAN -> "LOAN_TRANSFER / OTHER_PAYMENT";
        } + "；RESERVE_RELEASE为预留释放，必须给出具体reason";
    }

    public static Map<String, String> event(AgentScene scene, List<DialogueLine> history, int roundNo, FundsContext funds) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("round_no", Integer.toString(roundNo));
        v.put("dialogue_history", publicHistory(history.stream().filter(l -> l.roundNo() < roundNo).toList()));
        v.put("scammer_line", publicHistory(history.stream().filter(l -> l.roundNo() == roundNo && l.speaker() == SpeakerRole.SCAMMER).toList()));
        v.put("funds_context", funds == null ? "无资金流水" : narratorFunds(funds, null, false));
        if (scene.trustV2()) {
            v.put("scenario_name", scene.scenarioName());
            v.put("victim_profile", scene.victimProfile());
        }
        if (scene.trustV3()) {
            v.put("scenario_name", scene.scenarioName());
            v.put("victim_profile", scene.victimProfile());
            // Whole-round evidence: both saved lines of the current round, never history or internal notes.
            v.put("scammer_line", publicHistory(history.stream().filter(l -> l.roundNo() == roundNo).toList()));
        }
        return v;
    }

    private static String personalHistory(List<DialogueLine> lines) {
        return lines.isEmpty() ? "尚无往来" : lines.stream().map(l -> "第" + l.roundNo() + "轮 "
                + (l.speaker() == SpeakerRole.SCAMMER ? "对方" : "我") + "说：" + l.content()
                + (l.action() == null ? "" : "\n行动：" + l.action())).collect(Collectors.joining("\n"));
    }

    public static String scammerFunds(FundsContext f) {
        String payments = f.existingFlows().stream().filter(p -> p.getFlowType() == FundFlowType.PAYMENT)
                .map(p -> {
                    int refunded = f.existingFlows().stream().filter(r -> java.util.Objects.equals(r.getRelatedPaymentId(), p.getId()))
                            .mapToInt(FundFlow::getAmountYuan).sum();
                    return "付款ID=" + p.getId() + "，收款=" + p.getAmountYuan() + "元，尚可关联返款=" + (p.getAmountYuan() - refunded) + "元";
                }).collect(Collectors.joining("\n"));
        String refunds = f.existingFlows().stream().filter(p -> p.getFlowType() == FundFlowType.REFUND)
                .map(p -> "返款流水ID=" + p.getId() + "，关联付款ID=" + p.getRelatedPaymentId() + "，已返款=" + p.getAmountYuan() + "元")
                .collect(Collectors.joining("\n"));
        if (f.v3()) {
            String payouts = f.existingFlows().stream().filter(p -> p.getFlowType() == FundFlowType.REFUND || p.getFlowType() == FundFlowType.REWARD)
                    .map(p -> "已入账出款：" + p.getFlowType() + " " + p.getAmountYuan() + "元").collect(Collectors.joining("\n"));
            return "已收款：" + f.totalPaidYuan() + "元；已退回本金：" + f.totalRefundedYuan()
                    + "元；已发报酬：" + f.totalRewardedYuan() + "元；剩余总出款额度："
                    + Math.max(0, f.refundCapYuan() - f.totalRefundedYuan() - f.totalRewardedYuan()) + "元。\n" + payments + "\n" + payouts
                    + (f.refundCapYuan() == 0 ? "本场不安排实际出款，claimedRefundYuan为null且fundActions=[]。"
                    : "只允许REFUND/REFUND或REWARD/REWARD。报酬不要求先前付款，relatedPaymentId=null；本金退回须关联上方原付款ID。"
                      + "claimedRefundYuan为本轮声称出款总额，实际出款不超过声称；退回与报酬共用每场300元额度。"
                      + "小额体验后应围绕受害者出资推进，不连续升级免费任务并一直替对方垫付。额度耗尽后不得再实际出款。"
                      + "本轮明确说已发放、已转出或已到账，必须有对应fundActions；没有实际出款时只能表达承诺或待处理，不用截图冒充真实到账。")
                    + "仅记本轮实际新出款，quote须含阿拉伯数字金额与已经支付、实际转账或已发放等执行说明。"
                    + "先前到账的确认、提醒查收或口头承诺不再记账；再次同额出款需明确说又实际支付或另外发放。reserveTier=null。";
        }
        return "已收款：" + f.totalPaidYuan() + "元；已实际返款：" + f.totalRefundedYuan()
                + "元；剩余可返额度：" + Math.max(0, Math.min(f.refundCapYuan(), f.totalPaidYuan()) - f.totalRefundedYuan())
                + "元。" + (f.refundCapYuan() == 0 ? "本场禁止实际返款，claimedRefundYuan必须为null。" : "声称不入账，实际返款不得超过声称。") + "\n" + payments
                + "\n已实际返款流水（已入账，不得重复）：\n" + (refunds.isEmpty() ? "无" : refunds)
                + "\n只将本轮明确完成的新返款写入fundActions，quote须包含金额和已返款/实际返款等执行说明。"
                + "先返、愿意返、发送退款承诺不是实际返款，fundActions=[]。"
                + "对已有返款的到账说明、查收提醒和对方收款确认不得再次入账，fundActions=[]。"
                + "若确实另外执行同一付款的同额返款，须明确写出又实际返款/再次实际返款及金额；不能把同一笔换个说法重复提交。";
    }

    public static String narratorFunds(FundsContext f, Integer claimed, boolean candidate) {
        String flows = f.existingFlows().stream().map(p -> "顺序" + p.getSequenceNo() + " " + p.getFlowType()
                + " " + p.getAmountYuan() + "元，发生后余额" + p.getBalanceAfterYuan() + "元").collect(Collectors.joining("\n"));
        return (candidate ? "候选预检：本轮受害者行为尚未保存，只作最低轮数筛查，不作为已入账事实。\n" : "正式分析，仅以下流水为已入账事实。\n")
                + "本轮骗子声称返款：" + (claimed == null ? "未提供" : claimed + "元（不是实际到账）")
                + "\n当前余额：" + f.availableBalanceYuan() + "元\n" + flows
                + (f.v3() ? "\n普通预留剩余：" + (f.generalReserveTotalYuan() - f.generalReserveReleasedYuan())
                        + "元；应急预留剩余：" + (f.emergencyReserveTotalYuan() - f.emergencyReserveReleasedYuan())
                        + "元。判断支付能力以这些真实资金为准。受害者说没钱不等于实际资金不足；余额足够却未付款时应分析为暂不愿转、拒绝或台词与资金不符，不能断言本人确实无钱，也不能把意愿不足当成余额不足。" : "");
    }

    private static String publicHistory(List<DialogueLine> lines) {
        if (lines.isEmpty()) return "（暂无，这是第一轮）";
        return lines.stream().map(AgentContextAssembler::publicLine).collect(Collectors.joining("\n"));
    }

    private static String publicLine(DialogueLine l) {
        return "第" + l.roundNo() + "轮 " + who(l.speaker()) + "：" + l.content()
                + (l.action() == null ? "" : "\n    （行动：" + l.action() + "）");
    }

    private static String fullHistory(List<DialogueLine> lines) {
        if (lines.isEmpty()) return "（暂无）";
        return lines.stream().map(l -> publicLine(l) + "\n    ［" + (l.speaker() == SpeakerRole.SCAMMER ? "内部意图" : "内部心理")
                + "：" + (l.internalNote() == null ? "无" : l.internalNote()) + "］").collect(Collectors.joining("\n"));
    }

    private static String facts(PriorFacts prior) {
        StringBuilder sb = new StringBuilder();
        sb.append("已发生实际受骗行为：").append(prior.actualFraud() ? "是" : "否")
                .append("；已达成场景关键目标：").append(prior.goalAchieved() ? "是" : "否").append("\n证据：");
        if (prior.evidence().isEmpty()) return sb.append("无").toString();
        for (EvidenceItem e : prior.evidence()) {
            sb.append("\n- ").append(e.factCode()).append(" 第").append(e.roundNo()).append("轮 ").append(e.speaker())
                    .append(' ').append(e.source()).append(" 「").append(e.quote()).append("」 eventOrder=").append(e.eventOrder());
        }
        return sb.toString();
    }

    private static String who(SpeakerRole role) { return role == SpeakerRole.SCAMMER ? "骗子" : "受害者"; }
}
