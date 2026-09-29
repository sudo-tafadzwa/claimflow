# Demo users

All passwords are `demo`. Defined in [infra/keycloak/claimflow-realm.json](../infra/keycloak/claimflow-realm.json). Fictional people.

| Username | Roles | Use it to |
|----------|-------|-----------|
| `rudo` | claimant | Submit and track claims |
| `tendai` | claimant | Submit the large home claim |
| `ann` | adjuster | Verify uncertain fields, review claims up to $5,000 payable |
| `sam` | senior-adjuster | Review any claim, pick up escalated work |
| `fiona` | finance-maker, finance-checker | Prepare payouts (and see that she can't release her own) |
| `chris` | finance-checker | Release payouts |
| `admin` | admin | See everything, retry claims whose process failed to start |
