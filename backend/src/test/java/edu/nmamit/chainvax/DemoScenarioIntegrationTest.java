package edu.nmamit.chainvax;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import edu.nmamit.chainvax.batch.BatchStatus;
import edu.nmamit.chainvax.batch.StatusChange;
import edu.nmamit.chainvax.common.CurrentActor;
import edu.nmamit.chainvax.telemetry.ExcursionService;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the presentation scenario end to end against real PostgreSQL (embedded), Flyway migrations,
 * the seeded demo data, JWT security and the real ingestion API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoScenarioIntegrationTest {

    private static final String MR = "Measles-Rubella (MR)";
    private static final String PENTA = "Pentavalent (DTwP-HepB-Hib)";
    private static final Map<String, String> TOKENS = new HashMap<>();

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ExcursionService excursions;

    @Test
    @Order(1)
    void loginIssuesJwtAndProtectsTheApi() throws Exception {
        assertThat(token("officer")).contains(".");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"officer\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/batches")).andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    void seededBatchesAndCustodyChainAreVisibleAndValid() throws Exception {
        mvc.perform(as("auditor", get("/api/batches")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[?(@.batchCode=='CVX-MR-001')].siteCode").value("VAN-01"));
        mvc.perform(as("auditor", get("/api/ledger/verify")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.entriesChecked").value(13));
    }

    @Test
    @Order(3)
    void rolesAreEnforcedOnTheServer() throws Exception {
        long mr1 = batchId("CVX-MR-001");
        long penta1 = batchId("CVX-PENTA-001");
        // auditor is read-only
        mvc.perform(as("auditor", post("/api/custody/transfers")).contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(mr1, siteId("DVS-UDP"), "store.dvs")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_PERMITTED"));
        // a transporter cannot hand over stock held at the district store
        mvc.perform(as("driver.van01", post("/api/custody/transfers")).contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(penta1, siteId("VAN-01"), "driver.van01")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_SCOPE"));
        // a storekeeper cannot override FEFO
        mvc.perform(as("store.dvs", post("/api/stock/issue")).contentType(MediaType.APPLICATION_JSON)
                        .content(issueJson(siteId("DVS-UDP"), PENTA, 5, penta1, "Prefer the newer lot")))
                .andExpect(status().isForbidden());
        // staff tokens cannot pose as a temperature logger
        mvc.perform(as("officer", post("/api/telemetry/readings")).contentType(MediaType.APPLICATION_JSON)
                        .content(readingsJson("LOG-VAN-01", Instant.now(), "5.0")))
                .andExpect(status().isForbidden());
        // a logger token can only post for its own device
        mvc.perform(post("/api/telemetry/readings").header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceToken("LOG-VAN-01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(readingsJson("LOG-DVS-01", Instant.now(), "5.0")))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void sustainedHeatExcursionCondemnsDemoPolicyBatchButOnlyFlagsReferencePolicyBatch() throws Exception {
        Instant now = Instant.now();
        String body = "{\"deviceCode\":\"LOG-VAN-01\",\"source\":\"SIMULATOR\",\"readings\":["
                + "{\"recordedAt\":\"" + now.minusSeconds(110) + "\",\"temperatureC\":10.8},"
                + "{\"recordedAt\":\"" + now.minusSeconds(55) + "\",\"temperatureC\":11.2},"
                + "{\"recordedAt\":\"" + now + "\",\"temperatureC\":11.4}]}";
        mvc.perform(post("/api/telemetry/readings").header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceToken("LOG-VAN-01"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(3))
                .andExpect(jsonPath("$.statusChanges[?(@.batchCode=='CVX-MR-001' && @.to=='CONDEMNED')]").exists())
                .andExpect(jsonPath("$.statusChanges[?(@.batchCode=='CVX-TD-001' && @.to=='AT_RISK')]").exists());
        assertThat(batchField("CVX-MR-001", "status")).isEqualTo("CONDEMNED");
        assertThat(batchField("CVX-TD-001", "status")).isEqualTo("AT_RISK"); // PQS policy: 10 h before condemnation
        assertThat(batchField("CVX-PENTA-001", "status")).isEqualTo("VIABLE"); // different site, unaffected
    }

    @Test
    @Order(5)
    void condemnedStockCanOnlyBeReturnedAndEveryTransferExtendsTheHashChain() throws Exception {
        long mr1 = batchId("CVX-MR-001");
        mvc.perform(as("driver.van01", post("/api/custody/transfers")).contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(mr1, siteId("PHC-NTE"), "store.phc")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONDEMNED_RETURN_ONLY"));

        String before = mvc.perform(as("auditor", get("/api/ledger/verify"))).andReturn().getResponse().getContentAsString();
        String headBefore = JsonPath.read(before, "$.headHash");
        String result = mvc.perform(as("driver.van01", post("/api/custody/transfers")).contentType(MediaType.APPLICATION_JSON)
                        .content(transferJson(mr1, siteId("DVS-UDP"), "store.dvs")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.seq").value(14))
                .andExpect(jsonPath("$.entry.batchStatus").value("CONDEMNED"))
                .andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(result, "$.entry.previousHash")).isEqualTo(headBefore);
        String newHash = JsonPath.read(result, "$.entry.hash");
        mvc.perform(as("auditor", get("/api/ledger/verify")))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.entriesChecked").value(14))
                .andExpect(jsonPath("$.headHash").value(newHash));
    }

    @Test
    @Order(6)
    void condemnedStockIsWithdrawnFromIssuableStock() throws Exception {
        long dvs = siteId("DVS-UDP");
        mvc.perform(as("store.dvs", get("/api/stock/issuable")).param("siteId", String.valueOf(dvs)).param("product", MR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuable[*].batchCode").value(org.hamcrest.Matchers.contains("CVX-MR-002")))
                .andExpect(jsonPath("$.withdrawn[0].batchCode").value("CVX-MR-001"))
                .andExpect(jsonPath("$.withdrawn[0].status").value("CONDEMNED"));
        mvc.perform(as("officer", post("/api/stock/issue")).contentType(MediaType.APPLICATION_JSON)
                        .content(issueJson(dvs, MR, 5, batchId("CVX-MR-001"), "Trying to use the condemned lot")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BATCH_CONDEMNED"));
        mvc.perform(as("store.dvs", post("/api/stock/issue")).contentType(MediaType.APPLICATION_JSON)
                        .content(issueJson(dvs, MR, 10, null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations[0].batchCode").value("CVX-MR-002"))
                .andExpect(jsonPath("$.skipped[0].batchCode").value("CVX-MR-001"));
    }

    @Test
    @Order(7)
    void officerOverridesAreAttributedToTheOfficer() throws Exception {
        long dvs = siteId("DVS-UDP");
        mvc.perform(as("officer", post("/api/stock/issue")).contentType(MediaType.APPLICATION_JSON)
                        .content(issueJson(dvs, PENTA, 20, batchId("CVX-PENTA-001"), "PENTA-002 reserved for outreach")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fefoOrder").value(false))
                .andExpect(jsonPath("$.overrideAuditId").isNumber());
        // AT_RISK release is refused while the excursion is still open ...
        long td1 = batchId("CVX-TD-001");
        mvc.perform(as("officer", post("/api/batches/" + td1 + "/release")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"VVM checked, not at discard point\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXCURSION_STILL_OPEN"));
        // ... an in-range reading closes it, then the officer may release
        mvc.perform(post("/api/telemetry/readings").header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceToken("LOG-VAN-01"))
                        .contentType(MediaType.APPLICATION_JSON).content(readingsJson("LOG-VAN-01", Instant.now(), "5.1")))
                .andExpect(status().isOk());
        mvc.perform(as("officer", post("/api/batches/" + td1 + "/release")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"VVM checked, not at discard point\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value("VIABLE"));
        mvc.perform(as("auditor", get("/api/audit/overrides")))
                .andExpect(jsonPath("$[0].type").value("STATUS_RELEASE"))
                .andExpect(jsonPath("$[0].officerUsername").value("officer"))
                .andExpect(jsonPath("$[1].type").value("FEFO_OVERRIDE"))
                .andExpect(jsonPath("$[1].details").value(org.hamcrest.Matchers.containsString("FEFO would have used CVX-PENTA-002")));
    }

    @Test
    @Order(8)
    void tamperingWithTheLedgerTableIsDetected() throws Exception {
        mvc.perform(as("officer", post("/api/demo/ledger/3/tamper"))).andExpect(status().isOk());
        mvc.perform(as("auditor", get("/api/ledger/verify")))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.problems[0].seq").value(3))
                .andExpect(jsonPath("$.problems[0].code").value("HASH_MISMATCH"));
        mvc.perform(as("officer", post("/api/demo/ledger/3/restore"))).andExpect(status().isOk());
        mvc.perform(as("auditor", get("/api/ledger/verify"))).andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    @Order(9)
    void enversHistoryAttributesEachChangeToItsActor() throws Exception {
        String json = mvc.perform(as("auditor", get("/api/audit/batches/" + batchId("CVX-MR-001") + "/revisions")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> actors = JsonPath.read(json, "$[*].username");
        assertThat(actors).contains("system:demo-seed", "device:LOG-VAN-01", "driver.van01");
    }

    @Test
    @Order(10)
    void dashboardAggregatesAreCachedUntilSomethingChanges() throws Exception {
        String first = mvc.perform(as("officer", get("/api/dashboard"))).andReturn().getResponse().getContentAsString();
        String second = mvc.perform(as("officer", get("/api/dashboard"))).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(second, "$.generatedAt")).isEqualTo(JsonPath.read(first, "$.generatedAt"));
        assertThat((Integer) JsonPath.read(first, "$.condemned")).isEqualTo(1);
        mvc.perform(as("store.dvs", post("/api/stock/issue")).contentType(MediaType.APPLICATION_JSON)
                        .content(issueJson(siteId("DVS-UDP"), PENTA, 1, null, null)))
                .andExpect(status().isOk());
        String third = mvc.perform(as("officer", get("/api/dashboard"))).andReturn().getResponse().getContentAsString();
        assertThat((Integer) JsonPath.read(third, "$.issuableVials")).isEqualTo((Integer) JsonPath.read(first, "$.issuableVials") - 1);
    }

    @Test
    @Order(11)
    void silentLoggerCannotHideASustainedExcursionFromTheScheduledWorker() throws Exception {
        // One heat reading 100 s ago, then the logger goes silent: no in-range reading ever closes the window.
        mvc.perform(post("/api/telemetry/readings").header(HttpHeaders.AUTHORIZATION, "Bearer " + deviceToken("LOG-DVS-01"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(readingsJson("LOG-DVS-01", Instant.now().minusSeconds(100), "9.5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusChanges[?(@.batchCode=='CVX-MR-002' && @.to=='AT_RISK')]").exists())
                .andExpect(jsonPath("$.statusChanges[?(@.to=='CONDEMNED')]").doesNotExist());
        assertThat(batchField("CVX-MR-002", "status")).isEqualTo("AT_RISK");

        // Exactly what the @Scheduled ExcursionWorker runs: exposure is measured against the clock, not the last reading.
        List<StatusChange> changes = new ArrayList<>();
        CurrentActor.runAs("system:excursion-worker", () -> changes.addAll(excursions.reevaluateOpenWindows()));

        assertThat(changes).anySatisfy(c -> {
            assertThat(c.batchCode()).isEqualTo("CVX-MR-002");
            assertThat(c.to()).isEqualTo(BatchStatus.CONDEMNED);
        });
        assertThat(batchField("CVX-MR-002", "status")).isEqualTo("CONDEMNED");
        assertThat(batchField("CVX-MR-002", "statusReason")).contains("scheduled re-evaluation");
    }

    @Test
    @Order(12)
    void policyChangesAreOfficerOnlyVersionedAndAudited() throws Exception {
        String policies = mvc.perform(as("officer", get("/api/policies"))).andReturn().getResponse().getContentAsString();
        int id = JsonPath.<List<Integer>>read(policies, "$[?(@.code=='PQS-E006-T1')].id").get(0);
        int version = JsonPath.<List<Integer>>read(policies, "$[?(@.code=='PQS-E006-T1')].version").get(0);
        String body = "{\"heatAlarmC\":8.0,\"coldAlarmC\":-0.5,\"atRiskAfterSeconds\":300,\"heatCondemnAfterSeconds\":36000,"
                + "\"coldCondemnAfterSeconds\":3600,\"reason\":\"Five-minute grace period before AT_RISK\",\"expectedVersion\":"
                + version + "}";
        mvc.perform(as("store.dvs", put("/api/policies/" + id)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(as("officer", put("/api/policies/" + id)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atRiskAfterSeconds").value(300))
                .andExpect(jsonPath("$.version").value(version + 1)) // the response carries the committed @Version
                .andExpect(jsonPath("$.updatedBy").value("officer"));
        // Re-submitting the edit made against the old version is a lost update: refused.
        mvc.perform(as("officer", put("/api/policies/" + id)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
        mvc.perform(as("auditor", get("/api/audit/overrides")))
                .andExpect(jsonPath("$[0].type").value("POLICY_CHANGE"))
                .andExpect(jsonPath("$[0].officerUsername").value("officer"));
    }

    // ---------------------------------------------------------------- helpers

    private MockHttpServletRequestBuilder as(String user, MockHttpServletRequestBuilder request) throws Exception {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(user));
    }

    private String token(String username) throws Exception {
        if (!TOKENS.containsKey(username)) {
            String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + username + "\",\"password\":\"demo1234\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            TOKENS.put(username, JsonPath.read(json, "$.accessToken"));
        }
        return TOKENS.get(username);
    }

    private String deviceToken(String deviceCode) throws Exception {
        String key = "device:" + deviceCode;
        if (!TOKENS.containsKey(key)) {
            String json = mvc.perform(post("/api/auth/device-token").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"deviceCode\":\"" + deviceCode + "\",\"deviceSecret\":\"demo-logger-secret\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            TOKENS.put(key, JsonPath.read(json, "$.accessToken"));
        }
        return TOKENS.get(key);
    }

    private long batchId(String code) throws Exception {
        return ((Number) batchValue(code, "id")).longValue();
    }

    private String batchField(String code, String field) throws Exception {
        return (String) batchValue(code, field);
    }

    private Object batchValue(String code, String field) throws Exception {
        String json = mvc.perform(as("auditor", get("/api/batches"))).andReturn().getResponse().getContentAsString();
        List<Object> values = JsonPath.read(json, "$[?(@.batchCode=='" + code + "')]." + field);
        return values.get(0);
    }

    private long siteId(String code) throws Exception {
        String json = mvc.perform(as("auditor", get("/api/sites"))).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(json, "$[?(@.code=='" + code + "')].id");
        return ids.get(0).longValue();
    }

    private static String transferJson(long batchId, long toSiteId, String toCustodian) {
        return "{\"batchId\":" + batchId + ",\"toSiteId\":" + toSiteId + ",\"toCustodian\":\"" + toCustodian
                + "\",\"note\":\"integration test\"}";
    }

    private static String issueJson(long siteId, String product, int vials, Long overrideBatchId, String reason) {
        return "{\"siteId\":" + siteId + ",\"product\":\"" + product + "\",\"vials\":" + vials
                + ",\"purpose\":\"integration test\""
                + (overrideBatchId == null ? "" : ",\"overrideBatchId\":" + overrideBatchId + ",\"overrideReason\":\"" + reason + "\"")
                + "}";
    }

    private static String readingsJson(String deviceCode, Instant at, String temperature) {
        return "{\"deviceCode\":\"" + deviceCode + "\",\"readings\":[{\"recordedAt\":\"" + at + "\",\"temperatureC\":"
                + temperature + "}]}";
    }
}
