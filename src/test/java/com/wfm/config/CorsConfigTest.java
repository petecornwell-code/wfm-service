package com.wfm.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression guard for a bug found during Phase 10 UAT: CorsConfig.allowedMethods omitted PATCH,
 * so the app's only PATCH endpoint (JobTitleConfigController.setNonSchedulable) was rejected with
 * 403 in every browser.
 *
 * The scenario modelled here is the one that actually broke — a SAME-ORIGIN PATCH carrying an
 * Origin header. Browsers send Origin on same-origin non-GET requests, so Spring evaluates them
 * as CORS requests, but they trigger no OPTIONS preflight. curl sends no Origin at all, which is
 * why the same call always succeeded from the command line and the bug survived to production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigTest {

    // Matches cors.allowed-origins in application.yml (no override in application-test.yml).
    private static final String ORIGIN = "http://localhost:3000";
    private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void patchWithOriginHeaderIsNotBlockedByCors() throws Exception {
        // 404 means the request reached the controller and the id simply does not exist — the
        // point is that it is NOT 403, which is what a CORS rejection produces.
        mockMvc.perform(patch("/api/v1/job-titles/" + UNKNOWN_ID)
                        .header("Origin", ORIGIN)
                        .header("X-Tenant-ID", "1")
                        .contentType("application/json")
                        .content("{\"nonSchedulable\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchFromDisallowedOriginIsStillRejected() throws Exception {
        // The allowlist must still mean something — a wide-open config would pass the test above
        // for the wrong reason.
        mockMvc.perform(patch("/api/v1/job-titles/" + UNKNOWN_ID)
                        .header("Origin", "https://attacker.example.com")
                        .header("X-Tenant-ID", "1")
                        .contentType("application/json")
                        .content("{\"nonSchedulable\":true}"))
                .andExpect(status().isForbidden());
    }
}
