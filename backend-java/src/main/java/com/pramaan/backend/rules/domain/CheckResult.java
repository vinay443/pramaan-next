package com.pramaan.backend.rules.domain;

import com.pramaan.backend.rules.CheckStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One deterministic rule outcome for one evidence record. Overwritten on re-evaluation. */
@Entity
@Table(name = "check_result")
public class CheckResult {

    @Id
    private UUID id;

    @Column(name = "evidence_record_id", nullable = false)
    private UUID evidenceRecordId;

    @Column(name = "check_id", nullable = false)
    private String checkId;

    @Column(name = "application_slug", nullable = false)
    private String applicationSlug;

    @Column(name = "control_id", nullable = false)
    private String controlId;

    @Column(nullable = false)
    private String framework;

    @Column(name = "source_system", nullable = false)
    private String sourceSystem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckStatus status;

    @Column(length = 2000)
    private String observed;

    @Column(length = 2000)
    private String expected;

    @Column(length = 2000)
    private String detail;

    @Column(name = "evidence_version")
    private int evidenceVersion;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    protected CheckResult() {}

    public CheckResult(UUID id, UUID evidenceRecordId, String checkId, String applicationSlug,
                       String controlId, String framework, String sourceSystem, CheckStatus status,
                       String observed, String expected, String detail, int evidenceVersion,
                       Instant evaluatedAt) {
        this.id = id;
        this.evidenceRecordId = evidenceRecordId;
        this.checkId = checkId;
        this.applicationSlug = applicationSlug;
        this.controlId = controlId;
        this.framework = framework;
        this.sourceSystem = sourceSystem;
        this.status = status;
        this.observed = observed;
        this.expected = expected;
        this.detail = detail;
        this.evidenceVersion = evidenceVersion;
        this.evaluatedAt = evaluatedAt;
    }

    public void update(CheckStatus status, String observed, String expected, String detail,
                       int evidenceVersion, Instant evaluatedAt) {
        this.status = status;
        this.observed = observed;
        this.expected = expected;
        this.detail = detail;
        this.evidenceVersion = evidenceVersion;
        this.evaluatedAt = evaluatedAt;
    }

    public UUID getId() { return id; }
    public UUID getEvidenceRecordId() { return evidenceRecordId; }
    public String getCheckId() { return checkId; }
    public String getApplicationSlug() { return applicationSlug; }
    public String getControlId() { return controlId; }
    public String getFramework() { return framework; }
    public String getSourceSystem() { return sourceSystem; }
    public CheckStatus getStatus() { return status; }
    public String getObserved() { return observed; }
    public String getExpected() { return expected; }
    public String getDetail() { return detail; }
    public int getEvidenceVersion() { return evidenceVersion; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
}
