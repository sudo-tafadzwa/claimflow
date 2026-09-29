# 3. A read model outside the engine

Status: accepted

## Context

The web app needs lists, filters, claim details, task inboxes and an audit trail. Camunda can answer some of these through its APIs, but coupling every screen to the engine's query API ties UI performance and data shape to the engine, and Operate's data is eventually consistent.

## Decision

Each worker and each task completion writes the claim's current state, extracted fields, tasks and an audit event to Postgres in its own transaction. The UI and reports read only Postgres. The engine is the source of truth for *where the process is*; Postgres is the source of truth for *what we know about the claim*.

## Consequences

- Screens are simple SQL queries and stay fast whatever the engine is doing.
- The audit trail is ours, in our schema, with our retention rules.
- Two stores can drift. That is handled by making every writer idempotent and by the outbox in ADR 5; a nightly reconciliation against Operate would be the next step for production.
