package dev.claimflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.claimflow.payment.PayoutRepository;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.engine.EngineFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The whole system in one test: the real Spring app, a real Postgres, and a real Zeebe engine (the
 * in-memory test engine, reached over gRPC exactly like a cluster). Claims are driven only through the
 * REST API with role-bearing JWTs, and the job workers run for real.
 */
@SpringBootTest(properties = {
        "claimflow.ledger.reject-above=5000",
        "claimflow.tasks.resume-sweep=PT1S"
})
@AutoConfigureMockMvc
@Testcontainers
class ClaimFlowEndToEndIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final ZeebeTestEngine engine = EngineFactory.create();

    static {
        engine.start();
    }

    @DynamicPropertySource
    static void engineAddress(DynamicPropertyRegistry registry) {
        registry.add("camunda.client.grpc-address", () -> "http://" + engine.getGatewayAddress());
        registry.add("camunda.client.prefer-rest-over-grpc", () -> "false");
    }

    @AfterAll
    static void stopEngine() {
        engine.stop();
    }

    @MockitoBean
    JwtDecoder jwtDecoder; // tokens are injected by the jwt() post-processor, never decoded

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    PayoutRepository payouts;

    @Test
    @DisplayName("Clean small travel claim: extracted, auto-approved, paid after maker-checker")
    void cleanClaimIsPaid() throws Exception {
        UUID claimId = submit("clean-travel.txt", "rudo");

        awaitTask(claimId, "PAYOUT_PREPARE");
        complete(claimId, "PAYOUT_PREPARE", user("fiona", "finance-maker", "finance-checker"),
                Map.of("payoutAccount", "ZW01 2345 6789"), 200);

        String release = awaitTask(claimId, "PAYOUT_RELEASE");
        // The same person holds both finance roles, but may not release their own payout.
        mvc.perform(post("/api/tasks/{id}/complete", release).with(user("fiona", "finance-maker", "finance-checker"))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        complete(claimId, "PAYOUT_RELEASE", user("chris", "finance-checker"), Map.of(), 200);

        JsonNode claim = awaitStatus(claimId, "PAID");
        assertThat(claim.at("/payableAmount").decimalValue()).isEqualByComparingTo("300.00");
        assertThat(claim.at("/staff/fraudScore").doubleValue()).isZero();
        assertThat(payouts.findByIdempotencyKey("payout:" + claimId)).get()
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo("EXECUTED"));
        assertThat(auditTypes(claim)).containsSubsequence("CLAIM_SUBMITTED", "FIELDS_EXTRACTED", "POLICY_FOUND",
                "FRAUD_SCORED", "TASK_OPENED", "TASK_COMPLETED", "PAYOUT_EXECUTED", "LEDGER_POSTED", "CLAIM_PAID");
    }

    @Test
    @DisplayName("Messy claim: uncertain fields go to an adjuster, who corrects them before any rule runs")
    void uncertainFieldsAreVerifiedByAPerson() throws Exception {
        UUID claimId = submit("messy-motor.txt", "kofi");

        String verify = awaitTask(claimId, "VERIFY_FIELDS");
        JsonNode before = claim(claimId, staff());
        assertThat(before.at("/status").asText()).isEqualTo("NEEDS_VERIFICATION");

        mvc.perform(post("/api/tasks/{id}/complete", verify).with(user("ann", "adjuster"))
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of("policyNumber", "MTR-220988",
                                "incidentDate", LocalDate.now().minusDays(2).toString(), "claimedAmount", "1800"))))
                .andExpect(status().isOk());

        awaitTask(claimId, "PAYOUT_PREPARE");
        JsonNode after = claim(claimId, staff());
        assertThat(after.at("/policyNumber").asText()).isEqualTo("MTR-220988");
        assertThat(after.at("/payableAmount").decimalValue()).isEqualByComparingTo("1300.00");
        assertThat(after.at("/staff/fields").findValuesAsText("verifiedBy")).contains("ann");
    }

    @Test
    @DisplayName("Large home claim: senior adjuster approves, ledger rejects, payout is reversed")
    void ledgerRejectionReversesThePayout() throws Exception {
        UUID claimId = submit("large-home.txt", "tendai");

        String review = awaitTask(claimId, "ADJUSTER_REVIEW");
        // A plain adjuster may not take a claim above the senior threshold.
        mvc.perform(post("/api/tasks/{id}/complete", review).with(user("ann", "adjuster"))
                        .contentType("application/json").content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
        complete(claimId, "ADJUSTER_REVIEW", user("sam", "senior-adjuster"), Map.of("decision", "APPROVE"), 200);

        awaitTask(claimId, "PAYOUT_PREPARE");
        complete(claimId, "PAYOUT_PREPARE", user("fiona", "finance-maker"), Map.of("payoutAccount", "ZW99 0001"), 200);
        awaitTask(claimId, "PAYOUT_RELEASE");
        complete(claimId, "PAYOUT_RELEASE", user("chris", "finance-checker"), Map.of(), 200);

        JsonNode claim = awaitStatus(claimId, "PAYOUT_REVERSED");
        assertThat(payouts.findByIdempotencyKey("payout:" + claimId)).get()
                .satisfies(p -> assertThat(p.getStatus()).isEqualTo("REVERSED"));
        assertThat(auditTypes(claim)).containsSubsequence("PAYOUT_EXECUTED", "PAYOUT_REVERSED", "CLAIM_PAYOUT_REVERSED");
    }

    @Test
    @DisplayName("Claimants see only their own claims, and never the staff sections")
    void claimantsSeeOnlyTheirOwnClaims() throws Exception {
        UUID claimId = submit("clean-travel.txt", "rudo");

        JsonNode own = claim(claimId, user("rudo", "claimant"));
        assertThat(own.at("/staff").isNull()).isTrue();

        mvc.perform(get("/api/claims/{id}", claimId).with(user("mallory", "claimant")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/claims").with(user("rudo", "claimant")))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------------------------------------

    private UUID submit(String fixture, String claimant) throws Exception {
        String text = new String(getClass().getResourceAsStream("/claims/" + fixture).readAllBytes(), StandardCharsets.UTF_8)
                .replace("{{INCIDENT_DATE}}", LocalDate.now().minusDays(3).toString());
        var document = new MockMultipartFile("document", fixture, "text/plain", text.getBytes(StandardCharsets.UTF_8));
        String body = mvc.perform(multipart("/api/claims").file(document)
                        .param("claimantName", claimant).param("claimantEmail", claimant + "@example.com")
                        .with(user(claimant, "claimant")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asText());
    }

    private String awaitTask(UUID claimId, String type) {
        return await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).until(() -> {
            for (JsonNode task : claim(claimId, staff()).at("/staff/tasks")) {
                if (task.get("type").asText().equals(type) && task.get("status").asText().equals("OPEN")) {
                    return task.get("id").asText();
                }
            }
            return null;
        }, id -> id != null);
    }

    private JsonNode awaitStatus(UUID claimId, String status) {
        return await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200))
                .until(() -> claim(claimId, staff()), c -> c.get("status").asText().equals(status));
    }

    private void complete(UUID claimId, String type, RequestPostProcessor who, Map<String, Object> body, int expected)
            throws Exception {
        String taskId = awaitTask(claimId, type);
        mvc.perform(post("/api/tasks/{id}/complete", taskId).with(who)
                        .contentType("application/json").content(json.writeValueAsString(body)))
                .andExpect(status().is(expected));
    }

    private JsonNode claim(UUID claimId, RequestPostProcessor who) throws Exception {
        return json.readTree(mvc.perform(get("/api/claims/{id}", claimId).with(who))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static java.util.List<String> auditTypes(JsonNode claim) {
        return claim.at("/staff/audit").findValuesAsText("type");
    }

    private static RequestPostProcessor staff() {
        return user("auditor", "admin");
    }

    private static RequestPostProcessor user(String username, String... roles) {
        return jwt().jwt(j -> j.claim("preferred_username", username).subject(username))
                .authorities(java.util.Arrays.stream(roles).map(r -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }
}
