package dev.claimflow.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A piece of work waiting for a person. The process opens it (a job worker writes this row) and then
 * waits on a receive task; completing it through the API publishes a message correlated by the task id.
 */
@Entity
@Table(name = "review_task")
public class ReviewTask {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID claimId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskType taskType;

    @Column(nullable = false)
    private String candidateRole;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false, unique = true)
    private long jobKey;

    @Column(nullable = false)
    private Instant slaDueAt;

    @Column(nullable = false)
    private boolean escalated;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant completedAt;
    private String completedBy;
    private String outcome;
    private String resumeVariables;
    private Instant resumedAt;

    protected ReviewTask() {
    }

    public ReviewTask(UUID claimId, TaskType taskType, String candidateRole, long jobKey, Instant slaDueAt, Instant now) {
        this.id = UUID.randomUUID();
        this.claimId = claimId;
        this.taskType = taskType;
        this.candidateRole = candidateRole;
        this.status = "OPEN";
        this.jobKey = jobKey;
        this.slaDueAt = slaDueAt;
        this.createdAt = now;
    }

    public boolean isOpen() {
        return "OPEN".equals(status);
    }

    public void complete(String by, String outcome, String resumeVariables, Instant now) {
        if (!isOpen()) {
            throw new IllegalStateException("Task " + id + " is already completed");
        }
        this.status = "COMPLETED";
        this.completedBy = by;
        this.outcome = outcome;
        this.resumeVariables = resumeVariables;
        this.completedAt = now;
    }

    public void markResumed(Instant now) {
        this.resumedAt = now;
    }

    /** SLA breached: flag it and, for adjuster work, hand it to a senior adjuster. */
    public void escalate() {
        this.escalated = true;
        if ("adjuster".equals(candidateRole)) {
            this.candidateRole = "senior-adjuster";
        }
    }

    public UUID getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public TaskType getTaskType() { return taskType; }
    public String getCandidateRole() { return candidateRole; }
    public String getStatus() { return status; }
    public long getJobKey() { return jobKey; }
    public Instant getSlaDueAt() { return slaDueAt; }
    public boolean isEscalated() { return escalated; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getCompletedBy() { return completedBy; }
    public String getOutcome() { return outcome; }
    public String getResumeVariables() { return resumeVariables; }
    public Instant getResumedAt() { return resumedAt; }
}
