package dev.claimflow.fraud;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Transparent rule-based fraud signals. Deliberately simple and explainable: every point of score comes
 * with a sentence the adjuster can read. A statistical model could replace it behind the same method.
 */
@Component
public class FraudScorer {

    public record Input(BigDecimal claimedAmount, BigDecimal coverLimit, long daysSincePolicyStart,
                        long daysSinceIncident, long priorClaimsLast12Months) {
    }

    public record Score(double value, List<String> signals) {
    }

    public Score score(Input in) {
        double score = 0;
        List<String> signals = new ArrayList<>();

        if (in.daysSincePolicyStart() < 30) {
            score += 0.35;
            signals.add("Incident within 30 days of the policy starting");
        }
        if (in.coverLimit() != null && in.claimedAmount() != null
                && in.claimedAmount().compareTo(in.coverLimit().multiply(new BigDecimal("0.8"))) >= 0) {
            score += 0.25;
            signals.add("Claim is at least 80% of the cover limit");
        }
        if (in.claimedAmount() != null && in.claimedAmount().compareTo(new BigDecimal("1000")) >= 0
                && in.claimedAmount().remainder(new BigDecimal("1000")).signum() == 0) {
            score += 0.1;
            signals.add("Round-thousand amount");
        }
        if (in.priorClaimsLast12Months() >= 2) {
            score += 0.3;
            signals.add(in.priorClaimsLast12Months() + " other claims on this policy in the last 12 months");
        }
        if (in.daysSinceIncident() > 21) {
            score += 0.1;
            signals.add("Reported more than three weeks after the incident");
        }

        double capped = BigDecimal.valueOf(Math.min(score, 1.0)).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return new Score(capped, List.copyOf(signals));
    }
}
