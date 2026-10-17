package com.aiscam.antiscam.admin.service;

import com.aiscam.antiscam.admin.web.dto.*;
import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.content.avatar.AvatarStore;
import com.aiscam.antiscam.content.domain.*;
import com.aiscam.antiscam.content.page.*;
import com.aiscam.antiscam.content.web.dto.PageView;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class AdminProfileService {
    private static final String SCAMMERS = "ADMIN_SCAMMER|all|idAsc";
    private static final String VICTIMS = "ADMIN_VICTIM|all|idAsc";
    private final ScammerProfileRepository scammers;
    private final VictimProfileRepository victims;
    private final ContentCursor cursors;
    private final AvatarStore avatars;
    private final EntityManager em;
    public AdminProfileService(ScammerProfileRepository scammers, VictimProfileRepository victims,
            ContentCursor cursors, AvatarStore avatars, EntityManager em) {
        this.scammers = scammers; this.victims = victims; this.cursors = cursors; this.avatars = avatars; this.em = em;
    }

    private String avatar(String key) {
        try { return avatars.storedKey(key); }
        catch (ApiException e) {
            if (e.status != 400 && e.status != 404) throw e;
            throw ApiException.field("avatarKey", "VALUE_INVALID", "请先上传有效头像并确认可预览");
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteScammer(String id, VersionRequest request) {
        long target = PageQuery.id(id);
        var rows = scammers.lockAllByOrderByIdAsc();
        var row = rows.stream().filter(p -> p.getId() == target).findFirst().orElseThrow(ApiException::notFound);
        if (row.isEnabled() && rows.stream().filter(ScammerProfile::isEnabled).count() == 1) throw ApiException.lastEnabledProfile();
        if (row.getRowVersion() != request.version()) throw ApiException.versionConflict(row.getRowVersion());
        em.remove(row);
        em.flush();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteVictim(String id, VersionRequest request) {
        long target = PageQuery.id(id);
        var rows = victims.lockAllByOrderByIdAsc();
        var row = rows.stream().filter(p -> p.getId() == target).findFirst().orElseThrow(ApiException::notFound);
        if (row.isEnabled() && rows.stream().filter(VictimProfile::isEnabled).count() == 1) throw ApiException.lastEnabledProfile();
        if (row.getRowVersion() != request.version()) throw ApiException.versionConflict(row.getRowVersion());
        em.remove(row);
        em.flush();
    }

    public PageView<AdminScammerView> listScammers(PageQuery query, String account) {
        var rows = scammers.findByIdGreaterThanOrderByIdAsc(cursors.decode(SCAMMERS, account, query.cursor()), PageRequest.of(0, query.limit() + 1));
        boolean more = rows.size() > query.limit();
        var items = rows.stream().limit(query.limit()).map(AdminScammerView::from).toList();
        String next = more ? cursors.encode(SCAMMERS, account, rows.get(query.limit() - 1).getId()) : null;
        return new PageView<>(items, next, more);
    }

    public AdminScammerView getScammer(String id) {
        return AdminScammerView.from(scammers.findById(PageQuery.id(id)).orElseThrow(ApiException::notFound));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminScammerView createScammer(ScammerWriteRequest request) {
        request.validateText();
        var row = scammers.save(new ScammerProfile(request.values(avatar(request.avatarKey()))));
        em.flush(); em.refresh(row);
        return AdminScammerView.from(row);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminScammerView updateScammer(String id, ScammerUpdateRequest request) {
        var body = request.content();
        body.validateText();
        String avatarKey = avatar(body.avatarKey());
        var row = scammers.lockById(PageQuery.id(id)).orElseThrow(ApiException::notFound);
        if (row.getRowVersion() != request.version()) throw ApiException.versionConflict(row.getRowVersion());
        row.apply(body.values(avatarKey));
        em.flush();
        if (row.getRowVersion() == request.version()) {
            em.createQuery("update ScammerProfile p set p.rowVersion = p.rowVersion + 1 where p.id = :id")
                    .setParameter("id", row.getId()).executeUpdate();
        }
        em.refresh(row);
        return AdminScammerView.from(row);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminScammerView setScammerEnabled(String id, VersionRequest request, boolean enabled) {
        long target = PageQuery.id(id);
        var rows = scammers.lockAllByOrderByIdAsc();
        var row = rows.stream().filter(p -> p.getId() == target).findFirst().orElseThrow(ApiException::notFound);
        if (row.isEnabled() == enabled) return AdminScammerView.from(row);
        if (!enabled && rows.stream().filter(ScammerProfile::isEnabled).count() == 1) throw ApiException.lastEnabledProfile();
        if (row.getRowVersion() != request.version()) throw ApiException.versionConflict(row.getRowVersion());
        row.setEnabled(enabled);
        em.flush(); em.refresh(row);
        return AdminScammerView.from(row);
    }

    public PageView<AdminVictimView> listVictims(PageQuery query, String account) {
        var rows = victims.findByIdGreaterThanOrderByIdAsc(cursors.decode(VICTIMS, account, query.cursor()), PageRequest.of(0, query.limit() + 1));
        boolean more = rows.size() > query.limit();
        var items = rows.stream().limit(query.limit()).map(AdminVictimView::from).toList();
        String next = more ? cursors.encode(VICTIMS, account, rows.get(query.limit() - 1).getId()) : null;
        return new PageView<>(items, next, more);
    }

    public AdminVictimView getVictim(String id) {
        return AdminVictimView.from(victims.findById(PageQuery.id(id)).orElseThrow(ApiException::notFound));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminVictimView createVictim(VictimWriteRequest request) {
        request.validateText();
        var row = victims.save(new VictimProfile(request.values(avatar(request.avatarKey()))));
        em.flush(); em.refresh(row);
        return AdminVictimView.from(row);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminVictimView updateVictim(String id, VictimUpdateRequest request) {
        var body = request.content();
        body.validateText();
        String avatarKey = avatar(body.avatarKey());
        var row = victims.lockById(PageQuery.id(id)).orElseThrow(ApiException::notFound);
        if (row.getRowVersion() != request.version()) throw ApiException.versionConflict(row.getRowVersion());
        row.apply(body.values(avatarKey));
        em.flush();
        if (row.getRowVersion() == request.version()) {
            em.createQuery("update VictimProfile p set p.rowVersion = p.rowVersion + 1 where p.id = :id")
                    .setParameter("id", row.getId()).executeUpdate();
        }
        em.refresh(row);
        return AdminVictimView.from(row);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminVictimView setVictimEnabled(String id, VersionRequest request, boolean enabled) {
        long target = PageQuery.id(id);
        var rows = victims.lockAllByOrderByIdAsc();
        var row = rows.stream().filter(p -> p.getId() == target).findFirst().orElseThrow(ApiException::notFound);
        if (row.isEnabled() == enabled) return AdminVictimView.from(row);
        if (!enabled && rows.stream().filter(VictimProfile::isEnabled).count() == 1) throw ApiException.lastEnabledProfile();
        if (row.getRowVersion() != request.version()) throw ApiException.versionConflict(row.getRowVersion());
        row.setEnabled(enabled);
        em.flush(); em.refresh(row);
        return AdminVictimView.from(row);
    }
}
