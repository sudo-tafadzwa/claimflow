package dev.claimflow.process;

import dev.claimflow.payment.PaymentService.LedgerRejectedException;
import dev.claimflow.task.TaskType;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.exception.CamundaError;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Thin adapters from Camunda jobs to {@link ClaimSteps}. They translate variables in and out and turn
 * business errors into BPMN errors; all logic and persistence live in ClaimSteps.
 */
@Component
public class ClaimJobWorkers {

    private final ClaimSteps steps;

    public ClaimJobWorkers(ClaimSteps steps) {
        this.steps = steps;
    }

    @JobWorker(type = "claim.extract-fields", timeout = 120_000)
    public Map<String, Object> extractFields(@Variable String claimId) {
        return withClaim(claimId, () -> steps.extractFields(UUID.fromString(claimId)));
    }

    @JobWorker(type = "policy.lookup")
    public Map<String, Object> lookupPolicy(@Variable String claimId) {
        return withClaim(claimId, () -> steps.lookupPolicy(UUID.fromString(claimId)));
    }

    @JobWorker(type = "claim.fraud-score")
    public Map<String, Object> scoreFraud(@Variable String claimId, @Variable Map<String, Object> coverage,
                                          @Variable Long daysSincePolicyStart, @Variable Long daysSinceIncident,
                                          @Variable BigDecimal coverLimit) {
        return withClaim(claimId, () -> steps.scoreFraud(UUID.fromString(claimId), coverage,
                daysSincePolicyStart, daysSinceIncident, coverLimit));
    }

    @JobWorker(type = "task.open")
    public Map<String, Object> openTask(ActivatedJob job, @Variable String claimId) {
        Map<String, String> headers = job.getCustomHeaders();
        Object mappedRole = job.getVariablesAsMap().get("candidateRole");
        String role = mappedRole != null ? mappedRole.toString() : headers.get("candidateRole");
        return withClaim(claimId, () -> steps.openTask(UUID.fromString(claimId), job.getKey(),
                TaskType.valueOf(headers.get("taskType")), role, Integer.parseInt(headers.get("slaHours"))));
    }

    @JobWorker(type = "task.escalate")
    public void escalate(@Variable String claimId, @Variable String currentTaskId) {
        withClaim(claimId, () -> {
            steps.escalate(UUID.fromString(currentTaskId));
            return null;
        });
    }

    @JobWorker(type = "payment.execute")
    public Map<String, Object> executePayout(@Variable String claimId) {
        return withClaim(claimId, () -> steps.executePayout(UUID.fromString(claimId)));
    }

    @JobWorker(type = "payment.reverse")
    public void reversePayout(@Variable String claimId) {
        withClaim(claimId, () -> {
            steps.reversePayout(UUID.fromString(claimId));
            return null;
        });
    }

    @JobWorker(type = "ledger.post")
    public void postToLedger(@Variable String claimId) {
        withClaim(claimId, () -> {
            try {
                steps.postToLedger(UUID.fromString(claimId));
            } catch (LedgerRejectedException e) {
                throw CamundaError.bpmnError("LEDGER_REJECTED", e.getMessage());
            }
            return null;
        });
    }

    @JobWorker(type = "claim.notify")
    public void notifyOutcome(ActivatedJob job, @Variable String claimId) {
        Map<String, Object> vars = job.getVariablesAsMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> coverage = (Map<String, Object>) vars.get("coverage");
        Object note = vars.get("reviewNote");
        withClaim(claimId, () -> {
            steps.notifyOutcome(UUID.fromString(claimId), job.getCustomHeaders().get("outcome"), coverage,
                    note == null ? null : note.toString());
            return null;
        });
    }

    /** Puts the claim id on every log line written while the job runs. */
    private static <T> T withClaim(String claimId, java.util.function.Supplier<T> work) {
        try (var ignored = MDC.putCloseable("claimId", claimId)) {
            return work.get();
        }
    }
}
