package com.aiscam.antiscam.history;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aiscam.antiscam.auth.domain.UserRole;
import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.error.*;
import com.aiscam.antiscam.common.web.RequestIdFilter;
import com.aiscam.antiscam.config.*;
import com.aiscam.antiscam.history.service.HistoryService;
import com.aiscam.antiscam.history.web.HistoryController;
import com.aiscam.antiscam.history.web.dto.HistoryViews.HistoryPage;
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

@WebMvcTest(controllers = HistoryController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class,
        properties = "app.security.session-cookie-secure=false")
@Import({SecurityConfig.class, JacksonConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class HistoryApiTest {
    private static final String PATH = "/anti.scam/v1/simulations";
    @Autowired MockMvc mvc;
    @MockitoBean HistoryService service;

    private UsernamePasswordAuthenticationToken auth(UserRole role) {
        return new UsernamePasswordAuthenticationToken(new Account("7", "测试用户", role), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    @Test
    void listHasEnvelopeNoStoreAndRejectsUnknownDuplicateOrInvalidParameters() throws Exception {
        when(service.list(7, "7", null, 20)).thenReturn(new HistoryPage(List.of(), null, false));
        mvc.perform(get(PATH).with(authentication(auth(UserRole.USER)))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.total").doesNotExist());
        for (String query : List.of("?userId=", "?enabled=true", "?limit=0", "?limit=101", "?limit=x", "?limit=1&limit=2")) {
            mvc.perform(get(PATH + query).with(authentication(auth(UserRole.USER)))).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        when(service.list(7, "7", "bad", 20)).thenThrow(ApiException.invalidCursor());
        mvc.perform(get(PATH + "?cursor=bad").with(authentication(auth(UserRole.USER)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void bothReadsRequireUserAndDetailReturnsSafeErrors() throws Exception {
        for (String path : List.of(PATH, PATH + "/1/detail")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(authentication(auth(UserRole.ADMIN)))).andExpect(status().isForbidden());
        }
        mvc.perform(get(PATH + "/1/detail?x=1").with(authentication(auth(UserRole.USER)))).andExpect(status().isBadRequest());
        when(service.detail(7, "1")).thenThrow(ApiException.notFound());
        mvc.perform(get(PATH + "/1/detail").with(authentication(auth(UserRole.USER)))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("资源不存在或不可访问"));
        doThrow(ApiException.dataInconsistent("记录状态异常，请稍后重试")).when(service).detail(7, "1");
        mvc.perform(get(PATH + "/1/detail").with(authentication(auth(UserRole.USER)))).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATA_INCONSISTENT"));
    }
}
