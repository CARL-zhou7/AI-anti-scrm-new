package com.aiscam.antiscam.admin.service;

import com.aiscam.antiscam.admin.web.dto.TrustPolicyViews.*;
import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.simulation.domain.TrustPolicyRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class TrustPolicyService {
    private final TrustPolicyRepository policies;
    private final EntityManager em;
    public TrustPolicyService(TrustPolicyRepository policies,EntityManager em) { this.policies=policies; this.em=em; }
    public View get() { return View.from(policies.findById(1).orElseThrow(() -> ApiException.dataInconsistent("信任分机制配置缺失"))); }
    @Transactional
    public View update(Update request) {
        request.validate();
        var row=policies.lockById(1).orElseThrow(() -> ApiException.dataInconsistent("信任分机制配置缺失"));
        if (row.getRowVersion()!=request.version()) throw ApiException.versionConflict(row.getRowVersion());
        row.update(request.thresholdAccept(),request.thresholdSmall(),request.thresholdLarge());
        em.flush();
        if (row.getRowVersion()==request.version()) em.createQuery("update TrustPolicy p set p.rowVersion=p.rowVersion+1 where p.id=1").executeUpdate();
        em.refresh(row);
        return View.from(row);
    }
}
