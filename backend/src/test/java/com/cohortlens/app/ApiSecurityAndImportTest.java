package com.cohortlens.app;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cohortlens.core.ImportProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Runs the real app on the in memory H2 database (dev profile) and checks roles and the import flow. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ApiSecurityAndImportTest {

    private static final String CSV = String.join(",", ImportProcessor.REQUIRED_COLUMNS) + "\n"
            + "S1,Maple Grove,5,2022,FALL,LOW_INCOME,N,70,68,0.95,0\n"
            + "S2,Maple Grove,5,2022,FALL,NOT_LOW_INCOME,N,75,72,0.97,1\n"
            + "S3,Maple Grove,5,2022,FALL,LOW_INCOME,N,105,68,0.95,0\n";

    @Autowired
    private MockMvc mvc;

    @Test
    void dataRoutesRequireALogin() throws Exception {
        mvc.perform(get("/api/analytics/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthCheckIsOpen() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void viewerCannotImportSeeIndividualRiskOrReadTheAuditLog() throws Exception {
        mvc.perform(post("/api/imports").with(httpBasic("viewer", "viewer-dev"))
                .contentType("text/csv").content(CSV)).andExpect(status().isForbidden());
        mvc.perform(get("/api/analytics/risk/students").with(httpBasic("viewer", "viewer-dev")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").with(httpBasic("viewer", "viewer-dev"))).andExpect(status().isForbidden());
    }

    @Test
    void researcherCanSeeIndividualRiskButCannotImport() throws Exception {
        mvc.perform(get("/api/analytics/risk/students").with(httpBasic("researcher", "researcher-dev")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/imports").with(httpBasic("researcher", "researcher-dev"))
                .contentType("text/csv").content(CSV)).andExpect(status().isForbidden());
    }

    @Test
    void adminImportReportsRejectedRowsAndWritesTheAuditLog() throws Exception {
        mvc.perform(post("/api/imports?filename=test.csv&mode=REPLACE").with(httpBasic("admin", "admin-dev"))
                        .contentType("text/csv").content(CSV))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.acceptedRows").value(2))
                .andExpect(jsonPath("$.rejectedRows").value(1))
                .andExpect(jsonPath("$.issueCounts.SCORE_OUT_OF_RANGE").value(1));

        mvc.perform(get("/api/analytics/options").with(httpBasic("viewer", "viewer-dev")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schools[0]").value("Maple Grove"));

        mvc.perform(get("/api/audit").with(httpBasic("admin", "admin-dev")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].actor").value("admin"));
    }

    @Test
    void invalidModeIsABadRequest() throws Exception {
        mvc.perform(post("/api/imports?mode=DROP").with(httpBasic("admin", "admin-dev"))
                .contentType("text/csv").content(CSV)).andExpect(status().isBadRequest());
    }
}
