# Sample claim documents

Fictional documents for trying the demo. Upload one from **My claims** while signed in as `rudo` or `tendai`.

The coverage rules reject claims reported more than 30 days after the incident, so **change the incident
date to a recent one** before uploading (that rejection is also worth trying on purpose).

| File | Sign in as | What happens |
|------|------------|--------------|
| `clean-travel.txt` | rudo | All fields confident, small travel claim, low fraud: auto-approved, then maker-checker payout |
| `messy-motor.txt` | rudo | Policy and amount are vague, date missing: an adjuster (`ann`) verifies the fields first |
| `large-home.txt` | tendai | Too large to auto-approve and above the senior threshold: only `sam` can review it. With `LEDGER_REJECT_ABOVE=5000` the ledger rejects the payout and it is reversed |
| `unknown-policy.txt` | rudo | Policy does not exist: rejected by the coverage rules |
