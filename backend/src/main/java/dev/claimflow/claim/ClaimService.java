package dev.claimflow.claim;

import dev.claimflow.audit.AuditLog;
import dev.claimflow.extraction.DocumentText;
import dev.claimflow.process.ProcessGateway;
import dev.claimflow.security.CurrentUser;
import dev.claimflow.web.ApiErrors.BadRequest;
import dev.claimflow.web.ApiErrors.Conflict;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Accepts claims and starts their process. The database write and the engine call cannot share a
 * transaction, so the claim is committed first and the process started after. If the start fails the
 * claim is marked START_FAILED and an admin can retry it; nothing is lost and nothing starts twice.
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);
    static final int MAX_DOCUMENT_BYTES = 5 * 1024 * 1024;

    private final ClaimRepository claims;
    private final ClaimDocumentRepository documents;
    private final ProcessGateway process;
    private final AuditLog audit;
    private final TransactionTemplate tx;
    private final MeterRegistry meters;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, ClaimDocumentRepository documents, ProcessGateway process,
                        AuditLog audit, TransactionTemplate tx, MeterRegistry meters, Clock clock) {
        this.claims = claims;
        this.documents = documents;
        this.process = process;
        this.audit = audit;
        this.tx = tx;
        this.meters = meters;
        this.clock = clock;
    }

    public Claim submit(CurrentUser user, String claimantName, String claimantEmail,
                        String filename, String contentType, byte[] content) {
        if (content == null || content.length == 0) throw new BadRequest("A claim document is required");
        if (content.length > MAX_DOCUMENT_BYTES) throw new BadRequest("The document must be 5 MB or smaller");
        if (!DocumentText.isSupported(contentType)) throw new BadRequest("Upload a PDF or a plain-text file");
        String text = DocumentText.extract(contentType, content);
        if (text.isBlank()) throw new BadRequest("No readable text was found in the document");

        Claim saved = tx.execute(status -> {
            var now = clock.instant();
            String reference = "CLM-" + Year.now(clock.withZone(ZoneOffset.UTC)) + "-" + claims.nextReferenceNumber();
            Claim claim = claims.save(new Claim(UUID.randomUUID(), reference, claimantName.trim(),
                    claimantEmail.trim(), user.username(), now));
            documents.save(new ClaimDocument(UUID.randomUUID(), claim.getId(), sanitize(filename), contentType,
                    content, text, now));
            audit.record(claim.getId(), user.username(), "CLAIM_SUBMITTED", reference);
            return claim;
        });
        meters.counter("claimflow.claims.submitted").increment();
        return startProcess(saved.getId());
    }

    /** Admin recovery for claims whose process never started. */
    public Claim retryStart(UUID claimId) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NoSuchElementException("No claim " + claimId));
        if (claim.getStatus() != ClaimStatus.START_FAILED) {
            throw new Conflict("Only claims whose process failed to start can be retried");
        }
        return startProcess(claimId);
    }

    private Claim startProcess(UUID claimId) {
        tx.executeWithoutResult(status -> {
            Claim claim = claims.findById(claimId).orElseThrow();
            if (claim.getStatus() == ClaimStatus.START_FAILED) {
                claim.moveTo(ClaimStatus.SUBMITTED, clock.instant());
            }
        });
        long key;
        try {
            key = process.startClaim(claimId);
        } catch (RuntimeException e) {
            log.error("Could not start the process for claim {}", claimId, e);
            return tx.execute(status -> {
                Claim claim = claims.findById(claimId).orElseThrow();
                claim.moveTo(ClaimStatus.START_FAILED, clock.instant());
                audit.system(claimId, "PROCESS_START_FAILED", e.getClass().getSimpleName());
                return claim;
            });
        }
        // Workers may already be updating this claim, so write only the key (no optimistic-lock clash).
        return tx.execute(status -> {
            claims.setProcessInstanceKey(claimId, key);
            audit.system(claimId, "PROCESS_STARTED", "instance " + key);
            return claims.findById(claimId).orElseThrow();
        });
    }

    private static String sanitize(String filename) {
        String name = filename == null ? "document" : filename.replaceAll("[^A-Za-z0-9._ -]", "_");
        return name.length() > 120 ? name.substring(0, 120) : name;
    }
}
