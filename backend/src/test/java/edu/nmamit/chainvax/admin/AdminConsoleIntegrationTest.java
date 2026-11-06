package edu.nmamit.chainvax.admin;

import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin console API against real PostgreSQL: separate login, react-admin list contract, CRUD with
 * guard rails, and batch registration through the hash-chained ledger. Uses its own application
 * context (distinct admin password) so its changes never leak into the demo-scenario tests.
 */
@SpringBootTest(properties = "chainvax.admin.password=admin-test-pass")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class AdminConsoleIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void adminLoginIsSeparateFromTheStaffLogin() throws Exception {
        assertThat(adminToken()).contains(".");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin-test-pass\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"officer\",\"password\":\"demo1234\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + staffToken("officer")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/demo-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.username=='admin')]").isEmpty());
    }

    @Test
    void listsFollowTheReactAdminSimpleRestContract() throws Exception {
        String admin = adminToken();
        mvc.perform(as(admin, get("/api/admin/users").param("filter", "{\"role\":\"STOREKEEPER\"}")
                        .param("sort", "[\"username\",\"ASC\"]").param("range", "[0,0]")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Range", "users 0-0/2"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("store.dvs"));
        mvc.perform(as(admin, get("/api/admin/sites").param("filter", "{\"q\":\"phc\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("PHC-NTE"));
        mvc.perform(as(admin, get("/api/admin/sites").param("filter", "{\"id\":[1,2]}")))
                .andExpect(header().string("Content-Range", "sites 0-1/2"));
        mvc.perform(as(admin, get("/api/admin/ledger").param("sort", "[\"id\",\"DESC\"]").param("range", "[0,4]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
        mvc.perform(as(admin, get("/api/admin/stats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batches").isNumber())
                .andExpect(jsonPath("$.ledgerValid").value(true));
    }

    @Test
    void userAccountsCanBeCreatedDisabledAndDeletedWithGuardRails() throws Exception {
        String admin = adminToken();
        long phc = idOf(admin, "sites", "code", "PHC-NTE");
        String created = mvc.perform(as(admin, post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"store.krk\",\"fullName\":\"New Storekeeper (demo)\",\"role\":\"STOREKEEPER\","
                                + "\"siteId\":" + phc + ",\"enabled\":true,\"password\":\"secret-123\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteCode").value("PHC-NTE"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();
        assertThat(staffLogin("store.krk", "secret-123")).isEqualTo(200); // BCrypt hash works for the staff login

        mvc.perform(as(admin, put("/api/admin/users/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Storekeeper (demo)\",\"role\":\"STOREKEEPER\",\"siteId\":" + phc
                                + ",\"enabled\":false}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        assertThat(staffLogin("store.krk", "secret-123")).isEqualTo(401); // disabled accounts cannot sign in

        mvc.perform(as(admin, delete("/api/admin/users/" + id))).andExpect(status().isOk());
        mvc.perform(as(admin, get("/api/admin/users/" + id))).andExpect(status().isNotFound());

        mvc.perform(as(admin, post("/api/admin/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"officer\",\"fullName\":\"Dup\",\"role\":\"AUDITOR\",\"password\":\"secret-123\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        mvc.perform(as(admin, delete("/api/admin/users/" + idOf(admin, "users", "username", "store.dvs"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLDS_STOCK"));
        mvc.perform(as(admin, delete("/api/admin/users/" + idOf(admin, "users", "username", "admin"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_DELETE"));
    }

    @Test
    void referencedMasterDataCannotBeDeletedAndPolicyChangesAreAudited() throws Exception {
        String admin = adminToken();
        mvc.perform(as(admin, delete("/api/admin/sites/" + idOf(admin, "sites", "code", "DVS-UDP"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IN_USE"));
        mvc.perform(as(admin, get("/api/admin/sites").param("filter", "{\"code\":\"DVS-UDP\"}")))
                .andExpect(jsonPath("$.length()").value(1)); // the failed delete was rolled back

        long policyId = idOf(admin, "policies", "code", "DEMO-FAST");
        String policy = mvc.perform(as(admin, get("/api/admin/policies/" + policyId)))
                .andReturn().getResponse().getContentAsString();
        int version = JsonPath.read(policy, "$.version");
        String change = "{\"name\":\"Demo fast (accelerated)\",\"basis\":\"Demo-only accelerated timings\",\"demoOnly\":true,"
                + "\"heatAlarmC\":8.0,\"coldAlarmC\":-0.5,\"atRiskAfterSeconds\":0,\"heatCondemnAfterSeconds\":120,"
                + "\"coldCondemnAfterSeconds\":60,\"version\":" + version;
        mvc.perform(as(admin, put("/api/admin/policies/" + policyId).contentType(MediaType.APPLICATION_JSON).content(change + "}")))
                .andExpect(status().isBadRequest()); // a reason is mandatory
        mvc.perform(as(admin, put("/api/admin/policies/" + policyId).contentType(MediaType.APPLICATION_JSON)
                        .content(change + ",\"reason\":\"Slower demo for the viva audience\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heatCondemnAfterSeconds").value(120))
                .andExpect(jsonPath("$.version").value(version + 1));
        mvc.perform(as(admin, put("/api/admin/policies/" + policyId).contentType(MediaType.APPLICATION_JSON)
                        .content(change + ",\"reason\":\"Stale edit from another tab\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
        mvc.perform(as(admin, get("/api/admin/overrides").param("filter", "{\"type\":\"POLICY_CHANGE\"}")))
                .andExpect(jsonPath("$[0].officerUsername").value("admin"))
                .andExpect(jsonPath("$[0].reason").value("Slower demo for the viva audience"));
    }

    @Test
    void registeringABatchAppendsToTheLedgerAndBatchesCannotBeDeleted() throws Exception {
        String admin = adminToken();
        long store = idOf(admin, "sites", "code", "DVS-UDP");
        long van = idOf(admin, "sites", "code", "VAN-01");
        long custodian = idOf(admin, "users", "username", "store.dvs");
        long policy = idOf(admin, "policies", "code", "PQS-E006-T1");
        String batch = "{\"batchCode\":\"CVX-BCG-001\",\"product\":\"BCG\",\"lotNumber\":\"BCG-T1\",\"manufacturer\":\"Demo\","
                + "\"expiryDate\":\"" + LocalDate.now().plusDays(300) + "\",\"dosesPerVial\":20,\"initialVials\":50,"
                + "\"custodianId\":" + custodian + ",\"policyId\":" + policy + ",\"siteId\":";
        mvc.perform(as(admin, post("/api/admin/batches").contentType(MediaType.APPLICATION_JSON).content(batch + van + "}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_A_STORE"));
        String created = mvc.perform(as(admin, post("/api/admin/batches").contentType(MediaType.APPLICATION_JSON)
                        .content(batch + store + "}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VIABLE"))
                .andExpect(jsonPath("$.vialsOnHand").value(50))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(created, "$.id")).longValue();

        mvc.perform(as(admin, get("/api/admin/ledger").param("filter", "{\"batchId\":" + id + "}")))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventType").value("REGISTERED"))
                .andExpect(jsonPath("$[0].recordedBy").value("admin"));
        mvc.perform(as(admin, get("/api/admin/ledger/verify"))).andExpect(jsonPath("$.valid").value(true));
        mvc.perform(as(admin, delete("/api/admin/batches/" + id)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEDGER_PROTECTED"));
    }

    // ------------------------------------------------------------------ helpers

    private String adminToken() throws Exception {
        String json = mvc.perform(post("/api/admin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin-test-pass\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private String staffToken(String username) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"demo1234\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.accessToken");
    }

    private int staffLogin(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private long idOf(String token, String resource, String field, String value) throws Exception {
        String json = mvc.perform(as(token, get("/api/admin/" + resource)
                        .param("filter", "{\"" + field + "\":\"" + value + "\"}")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$[0].id")).longValue();
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
