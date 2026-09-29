# 1. Camunda 8 instead of a hand-rolled state machine

Status: accepted

## Context

A claim lives for days or weeks. It waits on people, has deadlines, calls unreliable services, and sometimes has to undo money movement. A status column plus scheduled jobs can do this, but every one of those needs is then custom code: timers, retries, incident handling, visibility of where each claim is stuck, and a history an auditor can read.

## Decision

Model the claim as one BPMN process on Camunda 8 (Zeebe), with coverage rules in a DMN table. Application code implements the steps as job workers; the engine owns ordering, waiting, timers, retries and compensation.

## Consequences

- SLA timers, retries with backoff, incidents and compensation come from the engine, and each is visible in Operate.
- The business can read the model, and the process tests exercise the same file that is deployed.
- It adds infrastructure: an engine, and Elasticsearch behind Operate. For a system with one or two simple states that cost would not be worth it; for this one it is.
- Workers must be idempotent, because the engine retries jobs whose completion it did not receive (see ADR 5).
