package com.aiscam.antiscam.content;

import com.aiscam.antiscam.admin.service.AdminProfileService;
import com.aiscam.antiscam.admin.web.AdminProfileController;
import com.aiscam.antiscam.admin.web.dto.VictimWriteRequest;
import com.aiscam.antiscam.auth.domain.UserRole;
import com.aiscam.antiscam.auth.web.dto.Account;
import com.aiscam.antiscam.common.error.GlobalExceptionHandler;
import com.aiscam.antiscam.common.web.RequestIdFilter;
import com.aiscam.antiscam.config.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminProfileController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class,
        properties = "app.security.session-cookie-secure=false")
@Import({SecurityConfig.class, JacksonConfig.class, RequestIdFilter.class, GlobalExceptionHandler.class})
class VictimProfileApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean AdminProfileService service;

    private ObjectNode body() {
        var profiles = List.of(VictimScenarioProfileRulesTest.scenario("TASK_REBATE"), VictimScenarioProfileRulesTest.scenario("FAKE_INVESTMENT"), VictimScenarioProfileRulesTest.scenario("IMPERSONATION_LOAN"));
        ObjectNode body = mapper.valueToTree(VictimScenarioProfileRulesTest.profile(1000, 1000, profiles));
        body.put("avatarKey", "a").put("speakingAvatarKey", "b");
        return body;
    }
    private void request(ObjectNode body, int status, boolean update) throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(new Account("7", "管理员", UserRole.ADMIN), null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        if (update) body.put("version", 0);
        mvc.perform((update ? put("/anti.scam/v1/admin/victim-profiles/1") : post("/anti.scam/v1/admin/victim-profiles"))
                .with(authentication(auth)).with(csrf().asHeader()).contentType("application/json").content(mapper.writeValueAsBytes(body)))
                .andExpect(status().is(status));
    }

    @Test
    void createAndUpdateValidateThreeProfilesAmountsAndRemovedTime() throws Exception {
        when(service.createVictim(any())).thenAnswer(i -> { i.<VictimWriteRequest>getArgument(0).validateText(); return null; });
        when(service.updateVictim(any(), any())).thenAnswer(i -> { i.<com.aiscam.antiscam.admin.web.dto.VictimUpdateRequest>getArgument(1).content().validateText(); return null; });
        for (boolean update : List.of(false, true)) {
            request(body(), update ? 200 : 201, update);
            var missing = body(); missing.withArray("scenarioProfiles").remove(2); request(missing, 400, update);
            var duplicate = body(); duplicate.withArray("scenarioProfiles").set(2, duplicate.withArray("scenarioProfiles").get(0)); request(duplicate, 400, update);
            for (String field : List.of("availableFundsYuan", "reservedFundsYuan")) {
                request(body().put(field, 2001), 400, update);
                request(body().put(field, -1), 400, update);
            }
            request(body().put("contextTime", "今天"), 400, update);
            request(body().put("monthlyExpenseYuan", 1.5), 400, update);
            request(body().put("initialSavingsYuan", "2000"), 400, update);
        }
    }
}
