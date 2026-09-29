package dev.claimflow.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.claimflow.process.ProcessGateway;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Resumes the process after a task completion has committed. A completed task whose resume never reached
 * the engine (crash, engine down) is picked up by the sweeper and published again. Publishing twice is
 * harmless: the message id is the task id, and the engine drops duplicates.
 */
@Component
public class TaskResumer {

    private static final Logger log = LoggerFactory.getLogger(TaskResumer.class);
    private static final TypeReference<Map<String, Object>> VARIABLES = new TypeReference<>() {};

    private final ReviewTaskRepository tasks;
    private final ProcessGateway process;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Clock clock;

    public TaskResumer(ReviewTaskRepository tasks, ProcessGateway process, ObjectMapper json,
                       TransactionTemplate tx, Clock clock) {
        this.tasks = tasks;
        this.process = process;
        this.json = json;
        this.tx = tx;
        this.clock = clock;
    }

    public void resume(UUID taskId) {
        try {
            tx.executeWithoutResult(status -> {
                ReviewTask task = tasks.findById(taskId).orElseThrow();
                if (task.getResumedAt() != null) {
                    return;
                }
                process.completeTask(taskId, read(task.getResumeVariables()));
                task.markResumed(clock.instant());
            });
        } catch (RuntimeException e) {
            log.warn("Could not resume the process for task {}; the sweeper will retry", taskId, e);
        }
    }

    @Scheduled(fixedDelayString = "${claimflow.tasks.resume-sweep:PT15S}")
    public void resumePending() {
        try {
            tasks.findByStatusAndResumedAtIsNullAndCompletedAtBefore("COMPLETED", clock.instant().minus(Duration.ofSeconds(10)))
                    .forEach(task -> resume(task.getId()));
        } catch (RuntimeException e) {
            log.warn("Resume sweep failed; retrying on the next run", e);
        }
    }

    String write(Map<String, Object> variables) {
        try {
            return json.writeValueAsString(variables);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, Object> read(String variables) {
        try {
            return variables == null ? Map.of() : json.readValue(variables, VARIABLES);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
