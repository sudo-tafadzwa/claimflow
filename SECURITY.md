# Security

ClaimFlow is a portfolio reference project on fictional data. Please report anything you find by opening an issue, or privately via the email on my GitHub profile.

## What is in place

| Area | Control | Where |
|------|---------|-------|
| Authentication | Keycloak OIDC, authorization code + PKCE for the web app; the API is a stateless resource server that validates signature and issuer | `SecurityConfig`, `infra/keycloak` |
| Authorisation | Realm roles mapped to Spring authorities; method security on every endpoint; tasks completable only by their candidate role | `Roles`, `TaskService` |
| Separation of duties | Payout release refused (403) for the person who prepared it, checked again before money moves | `TaskService`, `ClaimSteps.executePayout` |
| Data isolation | Claimants see only their own claims; other claims answer 404, not 403, so ids can't be probed; staff-only data (fraud score, fields, audit) is omitted for claimants | `ClaimController` |
| Input handling | Uploads limited to PDF or text and 5 MB; filenames sanitised; documents served as attachments with `nosniff` | `ClaimService`, `ClaimController` |
| AI | Document text is sent as data inside tags, the system prompt says it is not instructions, and the model has no tools and cannot act. Output is schema-constrained and re-validated. | `AnthropicExtractionAdapter`, `FieldNormalizer` |
| Money movement | Idempotency key per claim; reversal is idempotent; every step audited | `PaymentService`, `AuditLog` |
| Secrets | Only from environment; `.env` is git-ignored; CI runs gitleaks | `.env.example`, CI |
| Web | Security headers, tokens in session storage (not cookies, so no CSRF surface), strict CORS on the API | `next.config.ts`, `SecurityConfig` |

## Known limits of the demo

- Demo users share the password `demo`, and the realm allows direct password grants for scripting. Neither belongs in production.
- Camunda runs with basic auth and an unprotected API inside the Compose network.
- No rate limiting on the API; a gateway or ingress would add it in production.
- Documents are stored in Postgres; production would use object storage with malware scanning.
