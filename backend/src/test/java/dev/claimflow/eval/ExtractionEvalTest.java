package dev.claimflow.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.claimflow.extraction.AnthropicExtractionAdapter;
import dev.claimflow.extraction.ClaimFields;
import dev.claimflow.extraction.ExtractionPort;
import dev.claimflow.extraction.ExtractionPort.ExtractedValue;
import dev.claimflow.extraction.FakeExtractionAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Scores an extractor against the synthetic set in /eval/cases. The number that matters most is
 * dangerousErrors: a required field the extractor was confident about and got wrong, or was confident
 * about when the document was ambiguous. Those are the errors that would skip the human check.
 *
 * <pre>
 * ./mvnw test -Dtest=ExtractionEvalTest                                  # rule-based baseline (CI)
 * ANTHROPIC_API_KEY=... ./mvnw test -Dtest=ExtractionEvalTest -Deval.provider=anthropic
 * </pre>
 * With a real provider the gates are enforced; the baseline only proves the harness works.
 */
class ExtractionEvalTest {

    static final double THRESHOLD = 0.85;
    static final Path CASES = Path.of("..", "eval", "cases");
    static final Path RESULTS = Path.of("..", "eval", "results");

    record Report(String provider, String date, int cases, int requiredFields, int automatedCorrect,
                  int sentToPersonUnnecessarily, int dangerousErrors, int mustFlag, int flagged,
                  double automationRate, double flagRecall, int routingCorrect, List<String> dangerous) {
    }

    @Test
    void scoreExtractor() throws Exception {
        String provider = System.getProperty("eval.provider", "fake");
        ExtractionPort extractor = "anthropic".equals(provider)
                ? new AnthropicExtractionAdapter(AnthropicOkHttpClient.fromEnv(),
                        System.getProperty("eval.model", "claude-opus-5-5"))
                : new FakeExtractionAdapter();

        ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        List<Path> docs;
        try (Stream<Path> files = Files.list(CASES)) {
            docs = files.filter(p -> p.toString().endsWith(".txt")).sorted().toList();
        }
        assertThat(docs).hasSize(30);

        int required = 0, automated = 0, unnecessary = 0, mustFlag = 0, flagged = 0, routingCorrect = 0;
        List<String> dangerous = new ArrayList<>();

        for (Path doc : docs) {
            String name = doc.getFileName().toString().replace(".txt", "");
            JsonNode truth = json.readTree(CASES.resolve(name + ".expected.json").toFile());
            List<String> flagFields = new ArrayList<>();
            truth.get("mustFlag").forEach(n -> flagFields.add(n.asText()));
            var result = extractor.extract(Files.readString(doc));

            boolean routedToPerson = false;
            for (String field : ClaimFields.REQUIRED) {
                ExtractedValue got = result.get(field);
                boolean confident = got.value() != null && got.confidence() >= THRESHOLD;
                routedToPerson |= !confident;
                if (flagFields.contains(field)) {
                    mustFlag++;
                    if (confident) dangerous.add(name + "." + field + " confident on ambiguous: " + got.value());
                    else flagged++;
                    continue;
                }
                required++;
                String expected = truth.get("expected").get(field).asText();
                if (!confident) unnecessary++;
                else if (matches(field, expected, got.value())) automated++;
                else dangerous.add(name + "." + field + " expected " + expected + " got " + got.value());
            }
            // Routing is right when ambiguous claims go to a person and clean ones don't.
            if (routedToPerson == !flagFields.isEmpty()) {
                routingCorrect++;
            }
        }

        Report report = new Report(provider + ("anthropic".equals(provider) ? ":" + System.getProperty("eval.model", "claude-opus-5-5") : ""),
                LocalDate.now().toString(), docs.size(), required, automated, unnecessary, dangerous.size(), mustFlag,
                flagged, round((double) automated / required), round((double) flagged / mustFlag), routingCorrect,
                dangerous);
        Files.createDirectories(RESULTS);
        String suffix = "fake".equals(provider) ? "" : ".local";
        json.writeValue(RESULTS.resolve(provider + suffix + ".json").toFile(), report);
        System.out.println(json.writeValueAsString(report));

        if (!"fake".equals(provider)) {
            assertThat(report.dangerousErrors()).as("confident but wrong fields: %s", dangerous).isZero();
            assertThat(report.automationRate()).as("share of fields extracted correctly and confidently")
                    .isGreaterThanOrEqualTo(0.9);
        }
    }

    private static boolean matches(String field, String expected, String got) {
        if (ClaimFields.CLAIMED_AMOUNT.equals(field)) {
            return new java.math.BigDecimal(expected).compareTo(new java.math.BigDecimal(got)) == 0;
        }
        return expected.equalsIgnoreCase(got);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
