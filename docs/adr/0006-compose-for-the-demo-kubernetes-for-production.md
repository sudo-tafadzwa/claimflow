# 6. Docker Compose for the demo, Kubernetes for production

Status: accepted

## Context

The demo must start with one command on a laptop or a single small VM. A production claims platform needs availability, scaling and managed upgrades.

## Decision

The repository ships a Compose stack: Camunda 8.8 orchestration (Zeebe, Operate, Tasklist) on Elasticsearch, Keycloak, Postgres, the API and the web app. It is explicitly not a production topology.

For production the path is Camunda's Helm charts on Kubernetes (Zeebe with 3 brokers and replication, a managed Elasticsearch or OpenSearch), the API and workers as separate deployments scaled by replicas and by job type, managed Postgres, and Keycloak in its own namespace with a real database.

## Consequences

- One command runs everything; the demo is honest about what it is.
- The production path is described, not built. I have run self-managed Camunda 8 on Kubernetes professionally, so this is deliberately left as a design rather than duplicated here.
