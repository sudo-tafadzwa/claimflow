# 5. Idempotent workers and an outbox for resuming the process

Status: accepted

## Context

Two failure modes matter.

1. The engine retries a job when it does not hear back, so a worker may run twice. Paying twice is not acceptable.
2. Completing a human task writes to Postgres and resumes the process in Zeebe. Those cannot share a transaction. Publishing before commit let the next worker read uncommitted data (we hit this); publishing after commit risks a completed task whose process never resumes.

## Decision

- Every worker is safe to repeat: tasks are keyed by the job key, payouts by an idempotency key (`payout:<claimId>`), reversals are no-ops the second time, notifications check the current status.
- Task completion stores the resume variables in the same row, in the same transaction (a small outbox). After commit the process is resumed; if that fails, `TaskResumer` re-publishes every completed-but-not-resumed task. Messages use the task id as message id, so the engine drops duplicates.
- Starting a process after a claim is saved uses the same idea: if the start fails the claim is marked `START_FAILED`, and an admin can retry it.

## Consequences

- No distributed transaction, no lost completions, no double payouts.
- Resuming can be delayed by the sweep interval (15 seconds) when the engine is briefly unavailable.
