package dev.claimflow.process;

import io.camunda.client.CamundaClient;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "camunda.client.enabled", havingValue = "true", matchIfMissing = true)
public class CamundaProcessGateway implements ProcessGateway {

    public static final String PROCESS_ID = "claim";
    public static final String TASK_COMPLETED = "task-completed";

    private final CamundaClient client;

    public CamundaProcessGateway(CamundaClient client) {
        this.client = client;
    }

    @Override
    public long startClaim(UUID claimId) {
        return client.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(Map.of("claimId", claimId.toString()))
                .send()
                .join()
                .getProcessInstanceKey();
    }

    @Override
    public void completeTask(UUID taskId, Map<String, Object> variables) {
        // messageId = taskId: the engine drops a duplicate publish of the same completion.
        client.newPublishMessageCommand()
                .messageName(TASK_COMPLETED)
                .correlationKey(taskId.toString())
                .messageId(taskId.toString())
                .timeToLive(Duration.ofHours(1))
                .variables(variables)
                .send()
                .join();
    }
}
