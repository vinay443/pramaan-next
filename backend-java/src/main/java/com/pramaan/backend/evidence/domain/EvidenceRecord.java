package com.pramaan.backend.evidence.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Logical evidence identity. Content lives in {@link EvidenceVersion} rows + the object store. */
@Entity
@Table(name = "evidence_record")
public class EvidenceRecord {

    @Id
    private UUID id;

    @Column(name = "evidence_key", nullable = false, unique = true, length = 600)
    private String evidenceKey;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "application_slug", nullable = false)
    private String applicationSlug;

    @Column(name = "control_id", nullable = false)
    private String controlId;

    @Column(nullable = false)
    private String framework;

    @Column(name = "source_system", nullable = false)
    private String sourceSystem;

    @Column(name = "source_object_id")
    private String sourceObjectId;

    private String title;

    @Column(name = "current_version", nullable = false)
    private int currentVersion = 0;

    @Column(name = "latest_sha256", length = 64)
    private String latestSha256;

    @Column(name = "latest_collected_at")
    private Instant latestCollectedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false, length = 20)
    private EvidenceLifecycleState lifecycleState = EvidenceLifecycleState.DRAFT;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "lifecycle_note", length = 2000)
    private String lifecycleNote;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("versionNumber ASC")
    private List<EvidenceVersion> versions = new ArrayList<>();

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("tagKey ASC")
    private List<EvidenceTag> tags = new ArrayList<>();

    protected EvidenceRecord() {}

    public EvidenceRecord(UUID id, String evidenceKey, UUID applicationId, String applicationSlug,
                          String controlId, String framework, String sourceSystem,
                          String sourceObjectId, String title, Instant now) {
        this.id = id;
        this.evidenceKey = evidenceKey;
        this.applicationId = applicationId;
        this.applicationSlug = applicationSlug;
        this.controlId = controlId;
        this.framework = framework;
        this.sourceSystem = sourceSystem;
        this.sourceObjectId = sourceObjectId;
        this.title = title;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public EvidenceVersion addVersion(EvidenceVersion v) {
        v.attachTo(this);
        versions.add(v);
        currentVersion = v.getVersionNumber();
        latestSha256 = v.getSha256();
        latestCollectedAt = v.getCollectedAt();
        updatedAt = v.getCreatedAt();
        return v;
    }

    public EvidenceVersion latestVersion() {
        return versions.isEmpty() ? null : versions.get(versions.size() - 1);
    }

    /**
     * Merge in place rather than clear-then-add: clearing the managed collection and
     * re-adding fresh {@link EvidenceTag} entities with the same {@code tagKey} makes
     * Hibernate schedule the inserts before the orphan-removal deletes in the same
     * flush, which trips the {@code (evidence_record_id, tag_key)} unique constraint
     * the moment a record is re-tagged with a key it already had (e.g. every scheduler
     * re-run over unchanged evidence). Updating existing entries and only
     * inserting/removing genuinely new/gone keys avoids that insert-before-delete
     * collision entirely.
     */
    public void replaceTags(List<EvidenceTag> newTags) {
        Map<String, String> incoming = new LinkedHashMap<>();
        newTags.forEach(t -> incoming.put(t.getTagKey(), t.getTagValue()));

        tags.removeIf(existing -> !incoming.containsKey(existing.getTagKey()));
        tags.forEach(existing -> existing.setTagValue(incoming.get(existing.getTagKey())));

        List<String> existingKeys = tags.stream().map(EvidenceTag::getTagKey).toList();
        incoming.forEach((key, value) -> {
            if (!existingKeys.contains(key)) {
                EvidenceTag t = new EvidenceTag(UUID.randomUUID(), key, value);
                t.attachTo(this);
                tags.add(t);
            }
        });
    }

    public UUID getId() { return id; }
    public String getEvidenceKey() { return evidenceKey; }
    public UUID getApplicationId() { return applicationId; }
    public String getApplicationSlug() { return applicationSlug; }
    public String getControlId() { return controlId; }
    public String getFramework() { return framework; }
    public String getSourceSystem() { return sourceSystem; }
    public String getSourceObjectId() { return sourceObjectId; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public int getCurrentVersion() { return currentVersion; }
    public String getLatestSha256() { return latestSha256; }
    public Instant getLatestCollectedAt() { return latestCollectedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<EvidenceVersion> getVersions() { return versions; }
    public List<EvidenceTag> getTags() { return tags; }

    public EvidenceLifecycleState getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(EvidenceLifecycleState s) { this.lifecycleState = s; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getLifecycleNote() { return lifecycleNote; }

    /** Apply a lifecycle transition (validation happens in the service). */
    public void applyLifecycle(EvidenceLifecycleState to, String actor, String note, Instant now) {
        this.lifecycleState = to;
        this.lifecycleNote = note;
        if (to == EvidenceLifecycleState.APPROVED || to == EvidenceLifecycleState.REJECTED) {
            this.reviewedBy = actor;
            this.reviewedAt = now;
        }
        this.updatedAt = now;
    }
}
