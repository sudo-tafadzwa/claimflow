package dev.claimflow.web;

import dev.claimflow.claim.Claim;
import dev.claimflow.claim.ClaimRepository;
import dev.claimflow.security.CurrentUser;
import dev.claimflow.security.Roles;
import dev.claimflow.task.ReviewTask;
import dev.claimflow.task.TaskService;
import dev.claimflow.task.TaskService.Completion;
import dev.claimflow.web.Views.Me;
import dev.claimflow.web.Views.TaskView;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TaskController {

    private final TaskService tasks;
    private final ClaimRepository claims;

    public TaskController(TaskService tasks, ClaimRepository claims) {
        this.tasks = tasks;
        this.claims = claims;
    }

    @Operation(summary = "The caller's identity and roles")
    @GetMapping("/me")
    public Me me(Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        return new Me(user.username(), user.roles().stream().sorted().toList());
    }

    @Operation(summary = "Open tasks the caller may work on, most urgent first")
    @GetMapping("/tasks")
    @PreAuthorize(Roles.STAFF_EXPR)
    public List<TaskView> open(Authentication auth) {
        List<ReviewTask> open = tasks.openTasksFor(CurrentUser.from(auth));
        Map<UUID, String> refs = claims.findAllById(open.stream().map(ReviewTask::getClaimId).distinct().toList())
                .stream().collect(Collectors.toMap(Claim::getId, Claim::getReference, (a, b) -> a));
        return open.stream().map(t -> TaskView.of(t, refs.get(t.getClaimId()))).toList();
    }

    @Operation(summary = "Complete a task. The body depends on the task type.")
    @PostMapping("/tasks/{id}/complete")
    @PreAuthorize(Roles.STAFF_EXPR)
    public TaskView complete(Authentication auth, @PathVariable UUID id, @RequestBody Completion body) {
        ReviewTask task = tasks.complete(id, CurrentUser.from(auth), body);
        String ref = claims.findById(task.getClaimId()).map(Claim::getReference).orElse(null);
        return TaskView.of(task, ref);
    }
}
