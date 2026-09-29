package dev.claimflow.extraction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic extractor for tests, CI and running without an API key. It reads "Label: value" lines.
 * An exact label gives high confidence; a looser synonym gives lower confidence, which is enough to
 * exercise the human-verification path in the demo.
 */
public class FakeExtractionAdapter implements ExtractionPort {

    private static final Map<String, List<String>> EXACT = Map.of(
            ClaimFields.POLICY_NUMBER, List.of("policy number"),
            ClaimFields.INCIDENT_DATE, List.of("incident date", "date of incident"),
            ClaimFields.INCIDENT_TYPE, List.of("incident type", "type of loss"),
            ClaimFields.CLAIMED_AMOUNT, List.of("amount claimed", "claimed amount", "claim amount"),
            ClaimFields.CLAIMANT_NAME, List.of("claimant name", "name of claimant"));

    private static final Map<String, List<String>> LOOSE = Map.of(
            ClaimFields.POLICY_NUMBER, List.of("policy", "ref"),
            ClaimFields.INCIDENT_DATE, List.of("date", "when"),
            ClaimFields.INCIDENT_TYPE, List.of("what happened", "loss"),
            ClaimFields.CLAIMED_AMOUNT, List.of("amount", "total", "cost"),
            ClaimFields.CLAIMANT_NAME, List.of("name", "from"));

    @Override
    public ExtractionResult extract(String documentText) {
        Map<String, ExtractedValue> fields = new LinkedHashMap<>();
        for (String field : ClaimFields.ALL) {
            ExtractedValue raw = find(documentText, EXACT.get(field), 0.97);
            if (raw == null) {
                raw = find(documentText, LOOSE.get(field), 0.6);
            }
            fields.put(field, FieldNormalizer.normalize(field, raw));
        }
        return new ExtractionResult(fields, "Extracted by the rule-based fake extractor.", "fake");
    }

    private static ExtractedValue find(String text, List<String> labels, double confidence) {
        for (String label : labels) {
            Matcher m = Pattern.compile("(?im)^\\s*" + Pattern.quote(label) + "\\s*[:=-]\\s*(.+?)\\s*$").matcher(text);
            if (m.find()) {
                return new ExtractedValue(m.group(1), confidence);
            }
        }
        return null;
    }
}
