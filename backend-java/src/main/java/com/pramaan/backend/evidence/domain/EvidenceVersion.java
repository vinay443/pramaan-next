package com.pramaan.backend.evidence.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** One immutable revision of an evidence record. */
@Entity
@Table(name = "evidence_version")
public class EvidenceVersion {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_record_id", nullable = false)
    private EvidenceRecord record;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "content_type", nullable = false)
    private String contentType = "application/octet-stream";

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "object_key", nullable = false, length = 600)
    private String objectKey;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(name = "collected_by")
    private String collectedBy;

    @Column(name = "ingestion_run_id")
    private UUID ingestionRunId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "evidence_version_metadata", joinColumns = @JoinColumn(name = "evidence_version_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value", length = 2000)
    private Map<String, String> metadata = new LinkedHashMap<>();

    protected EvidenceVersion() {}

    public EvidenceVersion(UUID id, int versionNumber, String sha256, String contentType,
                           long sizeBytes, String objectKey, Instant collectedAt, String collectedBy,
                           UUID ingestionRunId, Map<String, String> metadata, Instant now) {
        this.id = id;
        this.versionNumber = versionNumber;
        this.sha256 = sha256;
        this.contentType = contentType == null ? "application/octet-stream" : contentType;
        this.sizeBytes = sizeBytes;
        this.objectKey = objectKey;
        this.collectedAt = collectedAt;
        this.collectedBy = collectedBy;
        this.ingestionRunId = ingestionRunId;
        if (metadata != null) {
            this.metadata.putAll(metadata);
        }
        this.createdAt = now;
    }

    void attachTo(EvidenceRecord r) { this.record = r; }

    public UUID getId() { return id; }
    public EvidenceRecord getRecord() { return record; }
    public int getVersionNumber() { return versionNumber; }
    public String getSha256() { return sha256; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getObjectKey() { return objectKey; }
    public Instant getCollectedAt() { return collectedAt; }
    public String getCollectedBy() { return collectedBy; }
    public UUID getIngestionRunId() { return ingestionRunId; }
    public Instant getCreatedAt() { return createdAt; }
    public Map<String, String> getMetadata() { return metadata; }
}
