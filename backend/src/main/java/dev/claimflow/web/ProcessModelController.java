package dev.claimflow.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the deployed BPMN so the web app can draw the claim's position on the real model. */
@RestController
public class ProcessModelController {

    @GetMapping(value = "/process/claim.bpmn", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> claimModel() throws IOException {
        String xml = new ClassPathResource("process/claim.bpmn").getContentAsString(StandardCharsets.UTF_8);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5))).body(xml);
    }
}
