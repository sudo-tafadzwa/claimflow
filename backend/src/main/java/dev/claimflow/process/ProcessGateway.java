package dev.claimflow.process;

import java.util.Map;
import java.util.UUID;

/** The only way application code talks to the workflow engine. */
public interface ProcessGateway {

    /** Starts the claim process and returns the process instance key. */
    long startClaim(UUID claimId);

    /** Resumes the process waiting on a human task. The task id is both correlation key and message id. */
    void completeTask(UUID taskId, Map<String, Object> variables);
}
