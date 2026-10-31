package com.aiscam.antiscam.admin;

import com.aiscam.antiscam.admin.service.TrustPolicyService;
import com.aiscam.antiscam.admin.web.TrustPolicyController;
import com.aiscam.antiscam.admin.web.dto.TrustPolicyViews.*;
import com.aiscam.antiscam.auth.domain.UserRole;
import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.error.GlobalExceptionHandler;
import com.aiscam.antiscam.common.web.RequestIdFilter;
import com.aiscam.antiscam.config.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers=TrustPolicyController.class,excludeAutoConfiguration=UserDetailsServiceAutoConfiguration.class,
        properties="app.security.session-cookie-secure=false")
@Import({SecurityConfig.class,JacksonConfig.class,RequestIdFilter.class,GlobalExceptionHandler.class})
class TrustPolicyApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean TrustPolicyService service;
    private UsernamePasswordAuthenticationToken auth(UserRole role) {
        return new UsernamePasswordAuthenticationToken(new Account("7","账号",role),null,List.of(new SimpleGrantedAuthority("ROLE_"+role.name())));
    }
    @Test void adminOnlyAndStrictIncreasingValidation() throws Exception {
        mvc.perform(get("/anti.scam/v1/admin/trust-policy").with(authentication(auth(UserRole.USER)))).andExpect(status().isForbidden());
        var view=new View(60,80,90,0,"trust-v1","2026-10-25T00:00:00.000Z");
        when(service.get()).thenReturn(view);
        when(service.update(any())).thenAnswer(call -> { call.<Update>getArgument(0).validate(); return view; });
        mvc.perform(get("/anti.scam/v1/admin/trust-policy").with(authentication(auth(UserRole.ADMIN))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.thresholdSmall").value(80));
        mvc.perform(put("/anti.scam/v1/admin/trust-policy").with(authentication(auth(UserRole.ADMIN))).with(csrf().asHeader())
                .contentType("application/json").content("{\"thresholdAccept\":60,\"thresholdSmall\":60,\"thresholdLarge\":90,\"version\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/anti.scam/v1/admin/trust-policy").with(authentication(auth(UserRole.ADMIN))).with(csrf().asHeader())
                .contentType("application/json").content("{\"thresholdAccept\":60,\"thresholdSmall\":80,\"thresholdLarge\":90,\"version\":0}"))
                .andExpect(status().isOk());
    }
}
