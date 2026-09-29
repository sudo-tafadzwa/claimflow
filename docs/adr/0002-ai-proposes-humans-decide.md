# 2. AI proposes, people decide

Status: accepted

## Context

Reading claim documents is slow manual work and a good fit for an LLM. But a model can be confidently wrong, and in a claims process a wrong policy number or amount turns into a wrong payout.

## Decision

- The LLM only extracts fields. It never approves, rejects or pays anything. Decisions come from the DMN table, the fraud rules and people.
- Every field carries a confidence. If any required field is missing or below the threshold (0.85 by default), the process routes the claim to an adjuster to verify it before any rule runs.
- Every value is re-checked in code (`FieldNormalizer`): policy format, real dates, positive amounts. A malformed value loses its confidence, whatever the model said.
- A refusal or API failure returns every field as missing. The claim still moves, to a person.
- Structured outputs constrain the response to a schema derived from a Java record, so there is no free-text parsing.

## Consequences

- The worst case of a model error is extra human work, not a wrong payment, as long as the model is honest about uncertainty. That is why the evaluation measures **dangerous errors** (confident and wrong, or confident about an ambiguous field), not just accuracy.
- The rule-based fallback extractor runs the whole system without an API key, and doubles as the baseline the model must beat.
