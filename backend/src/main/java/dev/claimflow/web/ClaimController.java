package dev.claimflow.web;

import dev.claimflow.audit.AuditEventRepository;
import dev.claimflow.claim.Claim;
import dev.claimflow.claim.ClaimDocument;
import dev.claimflow.claim.ClaimDocumentRepository;
import dev.claimflow.claim.ClaimRepository;
import dev.claimflow.claim.ClaimService;
import dev.claimflow.claim.ClaimStatus;
import dev.claimflow.claim.ExtractedFieldRepository;
import dev.claimflow.security.CurrentUser;
import dev.claimflow.security.Roles;
import dev.claimflow.task.ReviewTaskRepository;
import dev.claimflow.web.Views.AuditView;
import dev.claimflow.web.Views.ClaimDetail;
import dev.claimflow.web.Views.ClaimSummary;
import dev.claimflow.web.Views.DocumentView;
import dev.claimflow.web.Views.FieldView;
import dev.claimflow.web.Views.Staff;
import dev.claimflow.web.Views.TaskView;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/claims")
@Validated
public class ClaimController {

    private final ClaimService service;
    private final ClaimRepository claims;
    private final ClaimDocumentRepository documents;
    private final ExtractedFieldRepository fields;
    private final ReviewTaskRepository tasks;
    private final AuditEventRepository audit;

    public ClaimController(ClaimService service, ClaimRepository claims, ClaimDocumentRepository documents,
                           ExtractedFieldRepository fields, ReviewTaskRepository tasks, AuditEventRepository audit) {
        this.service = service;
        this.claims = claims;
        this.documents = documents;
        this.fields = fields;
        this.tasks = tasks;
        this.audit = audit;
    }

    @Operation(summary = "Submit a claim with one document (PDF or text)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('claimant')")
    public ClaimSummary submit(Authentication auth,
                               @RequestParam @NotBlank @Size(max = 120) String claimantName,
                               @RequestParam @NotBlank @Email String claimantEmail,
                               @RequestPart("document") MultipartFile document) throws IOException {
        Claim claim = service.submit(CurrentUser.from(auth), claimantName, claimantEmail,
                document.getOriginalFilename(), document.getContentType(), document.getBytes());
        return ClaimSummary.of(claim);
    }

    @Operation(summary = "The caller's own claims")
    @GetMapping("/mine")
    @PreAuthorize("hasRole('claimant')")
    public List<ClaimSummary> mine(Authentication auth) {
        return claims.findBySubmittedByOrderByCreatedAtDesc(CurrentUser.from(auth).username()).stream()
                .map(ClaimSummary::of).toList();
    }

    @Operation(summary = "All claims, optionally filtered by status (staff)")
    @GetMapping
    @PreAuthorize(Roles.STAFF_EXPR)
    public List<ClaimSummary> all(@RequestParam(required = false) List<ClaimStatus> status) {
        List<Claim> found = status == null || status.isEmpty()
                ? claims.findAllByOrderByCreatedAtDesc()
                : claims.findByStatusInOrderByCreatedAtDesc(status);
        return found.stream().map(ClaimSummary::of).toList();
    }

    @Operation(summary = "One claim. Claimants see only their own, without staff sections.")
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ClaimDetail detail(Authentication auth, @PathVariable UUID id) {
        CurrentUser user = CurrentUser.from(auth);
        Claim c = claims.findById(id).orElseThrow(() -> new NoSuchElementException("No claim " + id));
        if (!user.isStaff() && !c.getSubmittedBy().equals(user.username())) {
            // Same answer as a missing claim, so claim ids can't be probed.
            throw new NoSuchElementException("No claim " + id);
        }
        Staff staff = !user.isStaff() ? null : new Staff(c.getFraudScore(), c.getProcessInstanceKey(),
                c.getPayoutAccount(), c.getPayoutPreparedBy(), c.getPayoutReleasedBy(),
                fields.findByClaimIdOrderByName(id).stream().map(FieldView::of).toList(),
                tasks.findByClaimIdOrderByCreatedAtAsc(id).stream().map(t -> TaskView.of(t, c.getReference())).toList(),
                audit.findByClaimIdOrderByOccurredAtAscIdAsc(id).stream().map(AuditView::of).toList(),
                documents.findByClaimIdOrderByCreatedAt(id).stream().map(DocumentView::of).toList());
        return new ClaimDetail(c.getId(), c.getReference(), c.getClaimantName(), c.getClaimantEmail(),
                c.getStatus(), c.getPolicyNumber(), c.getPolicyType(), c.getIncidentDate(), c.getIncidentType(),
                c.getClaimedAmount(), c.getPayableAmount(), c.getDecisionReason(), c.getPaymentReference(),
                c.getCreatedAt(), c.getUpdatedAt(), staff);
    }

    @Operation(summary = "Download a claim document (staff)")
    @GetMapping("/{id}/documents/{documentId}")
    @PreAuthorize(Roles.STAFF_EXPR)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> document(@PathVariable UUID id, @PathVariable UUID documentId) {
        ClaimDocument doc = documents.findById(documentId)
                .filter(d -> d.getClaimId().equals(id))
                .orElseThrow(() -> new NoSuchElementException("No document " + documentId));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(doc.getFilename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(doc.getContent());
    }

    @Operation(summary = "Retry starting the process for a claim stuck in START_FAILED (admin)")
    @PostMapping("/{id}/retry-start")
    @PreAuthorize("hasRole('admin')")
    public ClaimSummary retryStart(@PathVariable UUID id) {
        return ClaimSummary.of(service.retryStart(id));
    }
}
