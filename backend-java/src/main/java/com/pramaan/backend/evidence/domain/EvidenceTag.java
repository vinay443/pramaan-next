package com.pramaan.backend.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/** Mutable key/value tag on an evidence record (metadata tagging). */
@Entity
@Table(name = "evidence_tag")
public class EvidenceTag {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_record_id", nullable = false)
    private EvidenceRecord record;

    @Column(name = "tag_key", nullable = false)
    private String tagKey;

    @Column(name = "tag_value", length = 600)
    private String tagValue;

    protected EvidenceTag() {}

    public EvidenceTag(UUID id, String tagKey, String tagValue) {
        this.id = id;
        this.tagKey = tagKey;
        this.tagValue = tagValue;
    }

    void attachTo(EvidenceRecord r) { this.record = r; }

    public UUID getId() { return id; }
    public String getTagKey() { return tagKey; }
    public String getTagValue() { return tagValue; }
}
