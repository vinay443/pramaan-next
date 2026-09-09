package com.pramaan.backend.insight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** UC19 — one point-in-time snapshot of the portfolio compliance posture. */
@Entity
@Table(name = "compliance_snapshot")
public class ComplianceSnapshot {

    @Id
    private UUID id;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    private int applications;
    private int expected;
    private int compliant;

    @Column(name = "compliance_pct", nullable = false)
    private double compliancePct;

    @Column(name = "completeness_pct", nullable = false)
    private double completenessPct;

    @Column(name = "approved_evidence")
    private int approvedEvidence;

    @Column(name = "open_findings")
    private int openFindings;

    /** UC — dashboard trend: total evidence records at snapshot time. */
    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;

    /** Hash-integrity check at snapshot time (current version of every record). */
    @Column(name = "integrity_checked", nullable = false)
    private int integrityChecked;

    @Column(name = "integrity_intact", nullable = false)
    private int integrityIntact;

    protected ComplianceSnapshot() {}

    public ComplianceSnapshot(UUID id, Instant takenAt, int applications, int expected, int compliant,
                              double compliancePct, double completenessPct, int approvedEvidence,
                              int openFindings) {
        this(id, takenAt, applications, expected, compliant, compliancePct, completenessPct,
                approvedEvidence, openFindings, 0, 0, 0);
    }

    public ComplianceSnapshot(UUID id, Instant takenAt, int applications, int expected, int compliant,
                              double compliancePct, double completenessPct, int approvedEvidence,
                              int openFindings, int evidenceCount, int integrityChecked,
                              int integrityIntact) {
        this.id = id;
        this.takenAt = takenAt;
        this.applications = applications;
        this.expected = expected;
        this.compliant = compliant;
        this.compliancePct = compliancePct;
        this.completenessPct = completenessPct;
        this.approvedEvidence = approvedEvidence;
        this.openFindings = openFindings;
        this.evidenceCount = evidenceCount;
        this.integrityChecked = integrityChecked;
        this.integrityIntact = integrityIntact;
    }

    public UUID getId() { return id; }
    public Instant getTakenAt() { return takenAt; }
    public int getApplications() { return applications; }
    public int getExpected() { return expected; }
    public int getCompliant() { return compliant; }
    public double getCompliancePct() { return compliancePct; }
    public double getCompletenessPct() { return completenessPct; }
    public int getApprovedEvidence() { return approvedEvidence; }
    public int getOpenFindings() { return openFindings; }
    public int getEvidenceCount() { return evidenceCount; }
    public int getIntegrityChecked() { return integrityChecked; }
    public int getIntegrityIntact() { return integrityIntact; }
}
