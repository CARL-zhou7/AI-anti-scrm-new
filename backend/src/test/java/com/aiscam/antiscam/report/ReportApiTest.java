package com.aiscam.antiscam.report;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.auth.domain.UserRole;
import com.aiscam.antiscam.common.error.*;
import com.aiscam.antiscam.common.web.RequestIdFilter;
import com.aiscam.antiscam.config.*;
import com.aiscam.antiscam.report.service.*;
import com.aiscam.antiscam.report.web.ReportController;
import com.aiscam.antiscam.simulation.web.dto.SimulationViews.ReportView;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ReportController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class,
        properties = "app.security.session-cookie-secure=false")
@Import({SecurityConfig.class, JacksonConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class ReportApiTest {
    private static final String PATH = "/anti.scam/v1/simulations/1/report";
    @Autowired MockMvc mvc;
    @MockitoBean ReportService service;

    private UsernamePasswordAuthenticationToken auth(UserRole role) {
        return new UsernamePasswordAuthenticationToken(new Account("7", "测试用户", role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    @Test
    void readAndRetryUseEnvelopeNoStoreAndContractStatusCodes() throws Exception {
        var view = new ReportView("1", "RUNNING", null, "SIMULATION_RUNNING", null, null, List.of());
        when(service.view(7, "1")).thenReturn(view);
        mvc.perform(get(PATH).with(authentication(auth(UserRole.USER)))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.unavailableReason").value("SIMULATION_RUNNING"));
        for (int status : List.of(202, 200)) {
            when(service.retry(7, "1")).thenReturn(new ReportRetryResult(status, view));
            mvc.perform(post(PATH + "/retry").with(authentication(auth(UserRole.USER))).with(csrf().asHeader()))
                    .andExpect(status().is(status)).andExpect(header().string("Cache-Control", "no-store"));
        }
        when(service.retry(7, "1")).thenThrow(ApiException.reportNotApplicable());
        mvc.perform(post(PATH + "/retry").with(authentication(auth(UserRole.USER))).with(csrf().asHeader()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_NOT_APPLICABLE"));
    }

    @Test
    void rejectsAnonymousAdminMissingCsrfUnexpectedBodyAndQuery() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).with(authentication(auth(UserRole.ADMIN)))).andExpect(status().isForbidden());
        mvc.perform(post(PATH + "/retry").with(authentication(auth(UserRole.USER))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(post(PATH + "/retry").with(authentication(auth(UserRole.USER))).with(csrf().asHeader())
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.fieldErrors[0].field").value("body"));
        mvc.perform(get(PATH + "?x=1").with(authentication(auth(UserRole.USER)))).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void returnsSafeNotFoundAndInconsistencyErrors() throws Exception {
        when(service.view(7, "1")).thenThrow(ApiException.notFound());
        mvc.perform(get(PATH).with(authentication(auth(UserRole.USER)))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("资源不存在或不可访问"));
        doThrow(ApiException.dataInconsistent("记录状态异常，请稍后重试")).when(service).view(7, "1");
        mvc.perform(get(PATH).with(authentication(auth(UserRole.USER)))).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATA_INCONSISTENT"));
    }
}
