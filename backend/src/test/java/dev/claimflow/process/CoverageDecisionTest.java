package dev.claimflow.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Table-driven checks of the coverage DMN, evaluated by the same engine that runs the process. */
@ZeebeProcessTest
class CoverageDecisionTest {

    private ZeebeClient client;

    @BeforeEach
    void deploy() {
        client.newDeployResourceCommand().addResourceFromClasspath("process/coverage.dmn").send().join();
    }

    static Stream<Arguments> cases() {
        return Stream.of(
                // found, active, days, type, amount -> covered, payable, autoApprove, reason
                Arguments.of(false, true, 2, "TRAVEL", 400, false, 0, false, "No matching policy"),
                Arguments.of(true, false, 2, "TRAVEL", 400, false, 0, false, "Policy not in force on the incident date"),
                Arguments.of(true, true, 31, "TRAVEL", 400, false, 0, false, "Reported more than 30 days after the incident"),
                Arguments.of(true, true, 30, "TRAVEL", 400, true, 300, true, "Covered"),
                Arguments.of(true, true, 2, "TRAVEL", 100, false, 0, false, "Claimed amount does not exceed the deductible"),
                Arguments.of(true, true, 2, "TRAVEL", 1001, true, 901, false, "Covered, review required"),
                Arguments.of(true, true, 2, "MOTOR", 2500, true, 2400, true, "Covered"),
                Arguments.of(true, true, 2, "HOME", 2501, true, 2401, false, "Covered, review required"),
                // Payable is capped at the cover limit before the deductible comes off
                Arguments.of(true, true, 2, "HOME", 50000, true, 19900, false, "Covered, review required"));
    }

    @ParameterizedTest(name = "{3} {4} found={0} active={1} days={2} -> covered={5} payable={6}")
    @MethodSource("cases")
    void decidesCoverage(boolean found, boolean active, int days, String type, int amount,
                         boolean covered, int payable, boolean autoApprove, String reason) {
        var result = client.newEvaluateDecisionCommand().decisionId("coverage")
                .variables(Map.of("policyFound", found, "policyActive", active, "daysSinceIncident", days,
                        "policyType", type, "claimedAmount", amount, "coverLimit", 20000, "deductible", 100))
                .send().join();

        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) new com.fasterxml.jackson.databind.ObjectMapper()
                .convertValue(parse(result.getDecisionOutput()), Map.class);

        assertThat(output).containsEntry("covered", covered).containsEntry("autoApproveEligible", autoApprove)
                .containsEntry("reason", reason);
        assertThat(((Number) output.get("payableAmount")).intValue()).isEqualTo(payable);
    }

    private static Object parse(String json) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
