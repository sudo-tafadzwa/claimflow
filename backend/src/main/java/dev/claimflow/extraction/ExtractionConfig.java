package dev.claimflow.extraction;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExtractionConfig {

    @ConfigurationProperties("claimflow.extraction")
    public record ExtractionProperties(String provider, double confidenceThreshold, Anthropic anthropic) {

        public record Anthropic(String apiKey, String model, Duration timeout) {
        }
    }

    @Bean
    ExtractionPort extractionPort(ExtractionProperties props) {
        if ("anthropic".equalsIgnoreCase(props.provider())) {
            var anthropic = props.anthropic();
            if (anthropic.apiKey() == null || anthropic.apiKey().isBlank()) {
                throw new IllegalStateException("EXTRACTION_PROVIDER=anthropic needs ANTHROPIC_API_KEY");
            }
            var client = AnthropicOkHttpClient.builder()
                    .apiKey(anthropic.apiKey())
                    .timeout(anthropic.timeout())
                    .build();
            return new AnthropicExtractionAdapter(client, anthropic.model());
        }
        return new FakeExtractionAdapter();
    }
}
