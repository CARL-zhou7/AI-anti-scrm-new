package com.aiscam.antiscam.simulation.rules;

import com.aiscam.antiscam.simulation.domain.WaitingItemStatus;

public final class WaitingItemRules {
    private WaitingItemRules() {}
    // shortcut: execution wording uses a small Chinese vocabulary; extend it with observed real-model failures.
    public static boolean executed(String basis, boolean completed) {
        if (basis.matches(".*(准备|打算|考虑|稍后|将要|还没|尚未|没有|未能|联系不上|不再|放弃).*")) return false;
        return completed ? basis.matches(".*(已|完成|得到|收到).*(核实|确认|回复|查证).*|.*(核实|查证).*(完成|证实).*" )
                : basis.matches(".*(已|正在|发出|拨打|发送|打了).*(问|咨询|联系|核实|电话|消息).*|.*(问|咨询|联系|核实).*(了|发出).*" );
    }
    public static void transition(WaitingItemStatus before, WaitingItemStatus after) {
        boolean valid = after != null && ( before == WaitingItemStatus.OPEN && after != WaitingItemStatus.OPEN
                || before == WaitingItemStatus.RESPONDED && (after == WaitingItemStatus.SATISFIED || after == WaitingItemStatus.ABANDONED));
        if (!valid) throw new IllegalStateException("Invalid waiting item transition");
    }
}
