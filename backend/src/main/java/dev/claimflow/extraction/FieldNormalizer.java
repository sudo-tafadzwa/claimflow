package dev.claimflow.extraction;

import dev.claimflow.extraction.ExtractionPort.ExtractedValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic checks applied to every extracted value, whatever produced it. A model can return a
 * confident but malformed value; this is where that confidence is taken away.
 */
public final class FieldNormalizer {

    private static final Pattern POLICY = Pattern.compile("^(TRV|MTR|HOM)-\\d{6}$");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH));
    private static final List<String> INCIDENT_TYPES = List.of("THEFT", "ACCIDENT", "WATER_DAMAGE", "FIRE",
            "MEDICAL", "BAGGAGE", "CANCELLATION", "OTHER");

    private FieldNormalizer() {
    }

    public static ExtractedValue normalize(String field, ExtractedValue raw) {
        if (raw == null || raw.value() == null || raw.value().isBlank()) {
            return ExtractedValue.missing();
        }
        String value = raw.value().trim();
        double confidence = Math.max(0.0, Math.min(1.0, raw.confidence()));
        return switch (field) {
            case ClaimFields.POLICY_NUMBER -> {
                String upper = value.toUpperCase(Locale.ROOT).replace(' ', '-');
                yield POLICY.matcher(upper).matches() ? new ExtractedValue(upper, confidence) : invalid(value);
            }
            case ClaimFields.INCIDENT_DATE -> {
                LocalDate date = parseDate(value);
                yield date == null ? invalid(value) : new ExtractedValue(date.toString(), confidence);
            }
            case ClaimFields.CLAIMED_AMOUNT -> {
                BigDecimal amount = parseAmount(value);
                yield amount == null || amount.signum() <= 0 ? invalid(value)
                        : new ExtractedValue(amount.toPlainString(), confidence);
            }
            case ClaimFields.INCIDENT_TYPE -> {
                String type = value.toUpperCase(Locale.ROOT).replace(' ', '_');
                yield INCIDENT_TYPES.contains(type) ? new ExtractedValue(type, confidence)
                        : new ExtractedValue("OTHER", Math.min(confidence, 0.5));
            }
            default -> new ExtractedValue(value, confidence);
        };
    }

    public static LocalDate parseDate(String value) {
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value.trim(), format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    public static BigDecimal parseAmount(String value) {
        String cleaned = value.replaceAll("(?i)usd|us\\$|\\$|,|\\s", "");
        try {
            return new BigDecimal(cleaned).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Keeps the raw text for the person who verifies it, with zero confidence. */
    private static ExtractedValue invalid(String value) {
        return new ExtractedValue(value, 0.0);
    }
}
