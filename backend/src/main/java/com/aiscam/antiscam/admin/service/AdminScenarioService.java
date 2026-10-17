package com.aiscam.antiscam.admin.service;

import com.aiscam.antiscam.admin.web.dto.*;
import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.common.web.RequestContent;
import com.aiscam.antiscam.content.domain.*;
import com.aiscam.antiscam.content.page.PageQuery;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class AdminScenarioService {
    private final FraudScenarioRepository scenarios;
    private final EntityManager em;
    public AdminScenarioService(FraudScenarioRepository scenarios, EntityManager em) {
        this.scenarios = scenarios; this.em = em;
    }

    public List<AdminScenarioView> list() {
        var rows = scenarios.findAllByOrderByIdAsc();
        if (rows.size() != 3) throw ApiException.dataInconsistent("基础场景数据不完整");
        return rows.stream().map(AdminScenarioView::from).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminScenarioView update(String id, ScenarioUpdateRequest request) {
        RequestContent.checkField("name", request.name(), 100);
        RequestContent.checkField("description", request.description(), RequestContent.MAX_TEXT_CHARS);
        var row = scenarios.lockById(PageQuery.id(id)).orElseThrow(ApiException::notFound);
        if (row.getRowVersion() != request.version()) throw ApiException.versionConflict(row.getRowVersion());
        row.rename(request.name(), request.description());
        em.flush();
        // Identical edits still consume a version, so an old form cannot be silently reused.
        if (row.getRowVersion() == request.version()) {
            em.createQuery("update FraudScenario p set p.rowVersion = p.rowVersion + 1 where p.id = :id")
                    .setParameter("id", row.getId()).executeUpdate();
        }
        em.refresh(row);
        return AdminScenarioView.from(row);
    }
}
