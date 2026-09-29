package dev.claimflow.process;

import static io.camunda.zeebe.process.test.assertions.BpmnAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.extension.ZeebeProcessTest;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the BPMN model and the coverage DMN on an in-memory engine. Workers are stubbed here:
 * each test decides what a worker returns, so the tests prove routing, not worker logic.
 */
@ZeebeProcessTest
class ClaimProcessTest {

    private ZeebeTestEngine engine;
    private ZeebeClient client;

    @BeforeEach
    void deploy() {
        client.newDeployResourceCommand()
                .addResourceFromClasspath("process/claim.bpmn")
                .addResourceFromClasspath("process/coverage.dmn")
                .send()
                .join();
    }

    @Test
    @DisplayName("Confident extraction, small covered claim, low fraud: auto-approved and paid")
    void autoApprovedClaimIsPaid() throws Exception {
        var instance = start();

        complete("claim.extract-fields", extraction(false, 400));
        complete("policy.lookup", policy("TRAVEL", true, 3));
        complete("claim.fraud-score", Map.of("fraudScore", 0.1));
        completeHumanTask("PAYOUT_PREPARE", Map.of("payoutPreparedBy", "fiona.maker"));
        completeHumanTask("PAYOUT_RELEASE", Map.of("payoutReleasedBy", "chris.checker"));
        complete("payment.execute", Map.of("paymentReference", "PAY-1"));
        complete("ledger.post", Map.of());
        complete("claim.notify", Map.of());

        assertThat(instance)
                .hasPassedElementsInOrder("Task_ExtractFields", "Task_LookupPolicy", "Task_Coverage",
                        "Task_FraudScore", "Task_OpenPrepare", "Task_OpenRelease", "Task_ExecutePayout",
                        "Task_PostLedger", "Task_NotifyPaid", "End_Paid")
                .hasNotPassedElement("Task_OpenVerify")
                .hasNotPassedElement("Task_OpenReview")
                .isCompleted();
    }

    @Test
    @DisplayName("Low-confidence extraction goes to a person before any rule runs")
    void lowConfidenceRoutesToVerification() throws Exception {
        var instance = start();

        complete("claim.extract-fields", extraction(true, 400));
        assertThat(instance).isWaitingAtElements("Task_OpenVerify");

        completeHumanTask("VERIFY_FIELDS", Map.of("claimedAmount", 450, "lowConfidence", false));
        assertThat(instance).hasPassedElement("Wait_Verify").isWaitingAtElements("Task_LookupPolicy");
    }

    @Test
    @DisplayName("No matching policy: rejected by the DMN, never reaches fraud scoring")
    void noPolicyIsRejected() throws Exception {
        var instance = start();

        complete("claim.extract-fields", extraction(false, 400));
        var noPolicy = new HashMap<>(policy("TRAVEL", true, 3));
        noPolicy.put("policyFound", false);
        complete("policy.lookup", noPolicy);
        var notify = complete("claim.notify", Map.of());

        assertThat(notify.getCustomHeaders()).containsEntry("outcome", "REJECTED");
        assertThat(instance).hasPassedElement("End_Rejected").hasNotPassedElement("Task_FraudScore").isCompleted();
        assertThat(instance).hasVariableWithValue("coverage",
                Map.of("covered", false, "reason", "No matching policy", "payableAmount", 0, "autoApproveEligible", false));
    }

    @Test
    @DisplayName("Large claim needs a senior adjuster; rejection ends the claim")
    void largeClaimGoesToSeniorAdjusterAndCanBeRejected() throws Exception {
        var instance = start();

        complete("claim.extract-fields", extraction(false, 9000));
        complete("policy.lookup", policy("HOME", true, 5));
        complete("claim.fraud-score", Map.of("fraudScore", 0.1));

        ActivatedJob openReview = activate("task.open");
        assertThat(openReview.getCustomHeaders()).containsEntry("taskType", "ADJUSTER_REVIEW");
        assertThat(openReview.getVariablesAsMap()).containsEntry("candidateRole", "senior-adjuster");
        completeOpenTask(openReview, "review-1");
        publishTaskCompleted("review-1", Map.of("decision", "REJECT"));

        var notify = complete("claim.notify", Map.of());
        assertThat(notify.getCustomHeaders()).containsEntry("outcome", "REJECTED");
        assertThat(instance).hasPassedElement("End_Rejected").hasNotPassedElement("Task_OpenPrepare").isCompleted();
    }

    @Test
    @DisplayName("High fraud score forces review even for a small claim; approval continues to payout")
    void highFraudScoreForcesReview() throws Exception {
        var instance = start();

        complete("claim.extract-fields", extraction(false, 400));
        complete("policy.lookup", policy("TRAVEL", true, 3));
        complete("claim.fraud-score", Map.of("fraudScore", 0.7));

        ActivatedJob openReview = activate("task.open");
        assertThat(openReview.getVariablesAsMap()).containsEntry("candidateRole", "adjuster");
        completeOpenTask(openReview, "review-2");
        publishTaskCompleted("review-2", Map.of("decision", "APPROVE"));

        assertThat(instance).hasPassedElement("Gw_MergeApproved").isWaitingAtElements("Task_OpenPrepare");
    }

    @Test
    @DisplayName("A stalled verification escalates after 8 hours without cancelling the task")
    void verificationSlaEscalates() throws Exception {
        var instance = start();
        complete("claim.extract-fields", extraction(true, 400));
        completeOpenTask(activate("task.open"), "verify-1");

        engine.increaseTime(Duration.ofHours(9));
        engine.waitForIdleState(Duration.ofSeconds(5));

        complete("task.escalate", Map.of());
        assertThat(instance).hasPassedElement("End_VerifyEscalated").isWaitingAtElements("Wait_Verify");
    }

    @Test
    @DisplayName("Ledger rejection after payment triggers compensation: the payout is reversed")
    void ledgerRejectionReversesPayout() throws Exception {
        var instance = start();

        complete("claim.extract-fields", extraction(false, 400));
        complete("policy.lookup", policy("TRAVEL", true, 3));
        complete("claim.fraud-score", Map.of("fraudScore", 0.1));
        completeHumanTask("PAYOUT_PREPARE", Map.of("payoutPreparedBy", "fiona.maker"));
        completeHumanTask("PAYOUT_RELEASE", Map.of("payoutReleasedBy", "chris.checker"));
        complete("payment.execute", Map.of("paymentReference", "PAY-2"));

        ActivatedJob ledger = activate("ledger.post");
        client.newThrowErrorCommand(ledger.getKey()).errorCode("LEDGER_REJECTED").errorMessage("limit").send().join();
        engine.waitForIdleState(Duration.ofSeconds(5));

        complete("payment.reverse", Map.of());
        var notify = complete("claim.notify", Map.of());

        assertThat(notify.getCustomHeaders()).containsEntry("outcome", "PAYOUT_REVERSED");
        assertThat(instance).hasPassedElement("Task_ReversePayout").hasPassedElement("End_PayoutReversed")
                .hasNotPassedElement("End_Paid").isCompleted();
    }

    // ---------------------------------------------------------------------------------------

    private ProcessInstanceEvent start() throws Exception {
        var instance = client.newCreateInstanceCommand().bpmnProcessId("claim").latestVersion()
                .variables(Map.of("claimId", "c-1")).send().join();
        engine.waitForIdleState(Duration.ofSeconds(5));
        return instance;
    }

    private static Map<String, Object> extraction(boolean lowConfidence, int amount) {
        return Map.of("lowConfidence", lowConfidence, "claimedAmount", amount, "policyNumber", "TRV-1001");
    }

    private static Map<String, Object> policy(String type, boolean active, int daysSinceIncident) {
        return Map.of("policyFound", true, "policyActive", active, "policyType", type,
                "coverLimit", 20000, "deductible", 100, "daysSinceIncident", daysSinceIncident);
    }

    /** Timers fire asynchronously after the clock moves, so poll briefly rather than check once. */
    private ActivatedJob activate(String type) throws InterruptedException, TimeoutException {
        List<ActivatedJob> jobs = List.of();
        for (int attempt = 0; attempt < 25 && jobs.isEmpty(); attempt++) {
            engine.waitForIdleState(Duration.ofSeconds(5));
            jobs = client.newActivateJobsCommand().jobType(type).maxJobsToActivate(1)
                    .requestTimeout(Duration.ofMillis(200)).send().join().getJobs();
        }
        assertThat(jobs).as("expected an active job of type %s", type).hasSize(1);
        return jobs.getFirst();
    }

    private ActivatedJob complete(String type, Map<String, Object> variables) throws Exception {
        ActivatedJob job = activate(type);
        client.newCompleteCommand(job.getKey()).variables(variables).send().join();
        engine.waitForIdleState(Duration.ofSeconds(5));
        return job;
    }

    private void completeOpenTask(ActivatedJob job, String taskId) throws Exception {
        client.newCompleteCommand(job.getKey()).variables(Map.of("currentTaskId", taskId)).send().join();
        engine.waitForIdleState(Duration.ofSeconds(5));
    }

    private void completeHumanTask(String taskType, Map<String, Object> result) throws Exception {
        ActivatedJob open = activate("task.open");
        assertThat(open.getCustomHeaders()).containsEntry("taskType", taskType);
        String taskId = taskType.toLowerCase() + "-" + open.getKey();
        completeOpenTask(open, taskId);
        publishTaskCompleted(taskId, result);
    }

    private void publishTaskCompleted(String taskId, Map<String, Object> variables) throws Exception {
        client.newPublishMessageCommand().messageName("task-completed").correlationKey(taskId)
                .messageId(taskId).variables(variables).send().join();
        engine.waitForIdleState(Duration.ofSeconds(5));
    }
}
