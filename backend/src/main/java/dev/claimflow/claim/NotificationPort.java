package dev.claimflow.claim;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Where claimant notifications go. The demo logs them; a real deployment would send email or SMS. */
@Component
public class NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationPort.class);

    public void send(Claim claim, String subject, String body) {
        log.info("Notify {} about {}: {} | {}", claim.getClaimantEmail(), claim.getReference(), subject, body);
    }
}
