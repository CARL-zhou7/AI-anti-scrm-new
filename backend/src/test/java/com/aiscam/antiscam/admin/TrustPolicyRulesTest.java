package com.aiscam.antiscam.admin;

import com.aiscam.antiscam.admin.service.TrustPolicyService;
import com.aiscam.antiscam.admin.web.dto.TrustPolicyViews.Update;
import com.aiscam.antiscam.common.error.ApiException;
import com.aiscam.antiscam.simulation.domain.*;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TrustPolicyRulesTest {
    @Test void staleVersionCannotChangePolicy() {
        var repository=mock(TrustPolicyRepository.class); var row=mock(TrustPolicy.class);
        when(repository.lockById(1)).thenReturn(Optional.of(row)); when(row.getRowVersion()).thenReturn(4);
        var service=new TrustPolicyService(repository,mock(EntityManager.class));
        assertEquals("VERSION_CONFLICT",assertThrows(ApiException.class,()->service.update(new Update(60,80,90,3))).code);
        verify(row,never()).update(anyInt(),anyInt(),anyInt());
    }
}
