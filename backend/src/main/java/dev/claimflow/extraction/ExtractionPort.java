package dev.claimflow.extraction;

import java.util.Map;

/**
 * Reads a claim document and proposes field values. Implementations never decide anything about the
 * claim: they return values with a confidence, and the process routes uncertain values to a person.
 */
public interface ExtractionPort {

    ExtractionResult extract(String documentText);

    /** A proposed value and how sure the extractor is about it, from 0 to 1. Value is null when not found. */
    record ExtractedValue(String value, double confidence) {

        public static ExtractedValue missing() {
            return new ExtractedValue(null, 0.0);
        }
    }

    record ExtractionResult(Map<String, ExtractedValue> fields, String summary, String provider) {

        public ExtractedValue get(String field) {
            return fields.getOrDefault(field, ExtractedValue.missing());
        }
    }
}
