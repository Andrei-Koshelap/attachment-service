package com.digivikings.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachments", indexes = {
        @Index(name = "idx_attachments_owner", columnList = "ownerId"),
        @Index(name = "idx_attachments_status", columnList = "status")
})
public class AttachmentEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false)
    private String ownerId; // subject/user id from JWT

    @Column(nullable = false)
    private String bucket;

    @Column(nullable = false, unique = true)
    private String objectKey; // UUID based key

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AttachmentStatus status;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String declaredContentType;

    @Column(nullable = false)
    private long declaredSize;

    @Column
    private String detectedContentType;

    @Column
    private String sha256;

    @Column
    private String uploadId; // multipart upload id

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant scannedAt;

    @Column
    private String rejectReason;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    // getters/setters
    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public AttachmentStatus getStatus() { return status; }
    public void setStatus(AttachmentStatus status) { this.status = status; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    public String getDeclaredContentType() { return declaredContentType; }
    public void setDeclaredContentType(String declaredContentType) { this.declaredContentType = declaredContentType; }
    public long getDeclaredSize() { return declaredSize; }
    public void setDeclaredSize(long declaredSize) { this.declaredSize = declaredSize; }
    public String getDetectedContentType() { return detectedContentType; }
    public void setDetectedContentType(String detectedContentType) { this.detectedContentType = detectedContentType; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getUploadId() { return uploadId; }
    public void setUploadId(String uploadId) { this.uploadId = uploadId; }
    public Instant getScannedAt() { return scannedAt; }
    public void setScannedAt(Instant scannedAt) { this.scannedAt = scannedAt; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
