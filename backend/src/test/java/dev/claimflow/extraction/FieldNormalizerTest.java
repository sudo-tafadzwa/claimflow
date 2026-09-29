package dev.claimflow.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import dev.claimflow.extraction.ExtractionPort.ExtractedValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FieldNormalizerTest {

    @ParameterizedTest
    @CsvSource({
            "TRV-100234, TRV-100234, 0.9",
            "trv 100234, TRV-100234, 0.9",
            "MTR 220988 I think, MTR 220988 I think, 0.0",
            "ABC-1, ABC-1, 0.0"
    })
    void policyNumbersMustMatchTheFormatOrLoseTheirConfidence(String raw, String value, double confidence) {
        ExtractedValue v = FieldNormalizer.normalize(ClaimFields.POLICY_NUMBER, new ExtractedValue(raw, 0.9));
        assertThat(v.value()).isEqualTo(value);
        assertThat(v.confidence()).isEqualTo(confidence);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026-09-01 | 2026-09-01",
            "1/9/2026 | 2026-09-01",
            "1 September 2026 | 2026-09-01",
            "September 1, 2026 | 2026-09-01"
    })
    void datesAreNormalisedToIso(String raw, String iso) {
        assertThat(FieldNormalizer.normalize(ClaimFields.INCIDENT_DATE, new ExtractedValue(raw, 0.95)).value())
                .isEqualTo(iso);
    }

    @Test
    void unreadableDateKeepsTheTextButHasNoConfidence() {
        ExtractedValue v = FieldNormalizer.normalize(ClaimFields.INCIDENT_DATE, new ExtractedValue("last Tuesday", 0.95));
        assertThat(v.value()).isEqualTo("last Tuesday");
        assertThat(v.confidence()).isZero();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "400 | 400.00", "USD 1,800.50 | 1800.50", "$ 9000 | 9000.00"
    })
    void amountsAreParsed(String raw, String amount) {
        assertThat(FieldNormalizer.normalize(ClaimFields.CLAIMED_AMOUNT, new ExtractedValue(raw, 0.9)).value())
                .isEqualTo(amount);
    }

    @Test
    void negativeOrWordyAmountsAreNotTrusted() {
        assertThat(FieldNormalizer.normalize(ClaimFields.CLAIMED_AMOUNT, new ExtractedValue("-50", 0.99)).confidence()).isZero();
        assertThat(FieldNormalizer.normalize(ClaimFields.CLAIMED_AMOUNT, new ExtractedValue("about 1,800", 0.99)).confidence()).isZero();
    }

    @Test
    void confidenceIsClampedAndBlankIsMissing() {
        assertThat(FieldNormalizer.normalize(ClaimFields.CLAIMANT_NAME, new ExtractedValue("Rudo", 7.0)).confidence()).isEqualTo(1.0);
        assertThat(FieldNormalizer.normalize(ClaimFields.CLAIMANT_NAME, new ExtractedValue("  ", 0.9))).isEqualTo(ExtractedValue.missing());
    }
}
