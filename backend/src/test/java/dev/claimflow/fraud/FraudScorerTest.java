package dev.claimflow.fraud;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FraudScorerTest {

    private final FraudScorer scorer = new FraudScorer();

    @Test
    void ordinaryClaimHasNoSignals() {
        var score = scorer.score(new FraudScorer.Input(new BigDecimal("400"), new BigDecimal("5000"), 200, 3, 0));
        assertThat(score.value()).isZero();
        assertThat(score.signals()).isEmpty();
    }

    @Test
    void everySignalIsExplained() {
        var score = scorer.score(new FraudScorer.Input(new BigDecimal("4000"), new BigDecimal("5000"), 10, 25, 2));
        assertThat(score.value()).isEqualTo(1.0); // 0.35 + 0.25 + 0.1 + 0.3 + 0.1, capped
        assertThat(score.signals()).hasSize(5).allSatisfy(s -> assertThat(s).isNotBlank());
    }

    @Test
    void newPolicyAloneIsEnoughToForceReview() {
        var score = scorer.score(new FraudScorer.Input(new BigDecimal("400"), new BigDecimal("5000"), 5, 1, 0));
        assertThat(score.value()).isGreaterThanOrEqualTo(0.3); // the auto-approve gate is fraudScore < 0.3
    }
}
