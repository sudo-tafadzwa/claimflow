package dev.claimflow;

import io.camunda.client.annotation.Deployment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@Deployment(resources = {"classpath*:/process/*.bpmn", "classpath*:/process/*.dmn"})
public class ClaimflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClaimflowApplication.class, args);
    }
}
