package dev.claimflow.claim;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "claim_document")
public class ClaimDocument {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID claimId;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false)
    private byte[] content;

    private String extractedText;

    @Column(nullable = false)
    private Instant createdAt;

    protected ClaimDocument() {
    }

    public ClaimDocument(UUID id, UUID claimId, String filename, String contentType, byte[] content,
                         String extractedText, Instant createdAt) {
        this.id = id;
        this.claimId = claimId;
        this.filename = filename;
        this.contentType = contentType;
        this.sizeBytes = content.length;
        this.content = content;
        this.extractedText = extractedText;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public String getFilename() { return filename; }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public byte[] getContent() { return content; }
    public String getExtractedText() { return extractedText; }
    public Instant getCreatedAt() { return createdAt; }
}
