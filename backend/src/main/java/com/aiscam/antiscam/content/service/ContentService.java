package com.aiscam.antiscam.content.service;

import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.content.avatar.*;
import com.aiscam.antiscam.content.domain.*;
import com.aiscam.antiscam.content.page.*;
import com.aiscam.antiscam.content.web.dto.*;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class ContentService {
    private final FraudScenarioRepository scenarios;
    private final ScammerProfileRepository scammers;
    private final VictimProfileRepository victims;
    private final ContentCursor cursors;
    private final AvatarStore avatars;

    public ContentService(FraudScenarioRepository scenarios, ScammerProfileRepository scammers,
            VictimProfileRepository victims, ContentCursor cursors, AvatarStore avatars) {
        this.scenarios = scenarios;
        this.scammers = scammers;
        this.victims = victims;
        this.cursors = cursors;
        this.avatars = avatars;
    }

    public List<ScenarioView> listScenarios() {
        var rows = scenarios.findAllByOrderByIdAsc();
        if (rows.size() != 3 || !rows.stream().map(FraudScenario::getScenarioCode).collect(Collectors.toSet())
                .equals(EnumSet.allOf(ScenarioCode.class))) {
            throw ApiException.dataInconsistent("基础场景数据不完整");
        }
        return rows.stream().map(s -> new ScenarioView(s.getId().toString(), s.getScenarioCode(),
                s.getName(), s.getDescription(), s.getScenarioCode().focus())).toList();
    }

    public PageView<ScammerView> listScammers(PageQuery query, String account) {
        long after = cursors.decode(ContentCursor.SCOPE_SCAMMERS, account, query.cursor());
        var rows = scammers.findByIdGreaterThanAndEnabledOrderByIdAsc(after, (byte) 1, PageRequest.of(0, query.limit() + 1));
        boolean more = rows.size() > query.limit();
        var items = rows.stream().limit(query.limit()).map(ScammerView::from).toList();
        String next = more ? cursors.encode(ContentCursor.SCOPE_SCAMMERS, account, rows.get(query.limit() - 1).getId()) : null;
        return new PageView<>(items, next, more);
    }

    public PageView<VictimView> listVictims(PageQuery query, String account) {
        long after = cursors.decode(ContentCursor.SCOPE_VICTIMS, account, query.cursor());
        var rows = victims.findByIdGreaterThanAndEnabledOrderByIdAsc(after, (byte) 1, PageRequest.of(0, query.limit() + 1));
        boolean more = rows.size() > query.limit();
        var items = rows.stream().limit(query.limit()).map(VictimView::from).toList();
        String next = more ? cursors.encode(ContentCursor.SCOPE_VICTIMS, account, rows.get(query.limit() - 1).getId()) : null;
        return new PageView<>(items, next, more);
    }

    public ScammerView scammer(String id) {
        return ScammerView.from(scammers.findByIdAndEnabled(PageQuery.id(id), (byte) 1).orElseThrow(ApiException::notFound));
    }

    public VictimView victim(String id) {
        return VictimView.from(victims.findByIdAndEnabled(PageQuery.id(id), (byte) 1).orElseThrow(ApiException::notFound));
    }

    public AvatarResource avatar(String key) {
        AvatarKey.validatePublicKey(key);
        for (String extension : List.of("png", "jpg")) {
            String storedKey = "avatars/" + key + "." + extension;
            if (scammers.existsByAvatarKeyAndEnabled(storedKey, (byte) 1)
                    || victims.existsByAvatarKeyAndEnabled(storedKey, (byte) 1)) return avatars.load(storedKey);
        }
        throw ApiException.notFound();
    }

    public void verify() {
        listScenarios();
        var s = scammers.findByEnabled((byte) 1);
        var v = victims.findByEnabled((byte) 1);
        if (s.isEmpty() || v.isEmpty()) throw ApiException.dataInconsistent("双方各须至少一个可用画像");
        s.forEach(p -> avatars.load(p.getAvatarKey()));
        v.forEach(p -> avatars.load(p.getAvatarKey()));
    }
}
