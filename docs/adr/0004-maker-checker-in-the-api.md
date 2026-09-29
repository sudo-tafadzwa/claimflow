# 4. Maker-checker is enforced in the API, not the UI

Status: accepted

## Context

A payout must be prepared by one person and released by another. Hiding the "release" button from the person who prepared it is a usability feature, not a control: anyone with a token can call the API directly.

## Decision

- `TaskService` refuses a release (403) when the caller prepared the payout. This holds even for a user who holds both finance roles.
- The payout worker checks again before moving money, and fails the job if preparer and releaser are the same or missing.
- Role checks are also enforced server-side: a task can only be completed by its candidate role (senior adjusters may take adjuster work, not the other way round).

## Consequences

- The control is tested end to end (`ClaimFlowEndToEndIT`), including the "one person, both roles" case.
- Two layers must be kept consistent; the second exists so that a future bug in the first cannot pay out on its own.
