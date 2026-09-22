package com.pramaan.backend.insight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Per-application slice of a {@link ComplianceSnapshot}, used to scope the trend by business unit. */
@Entity
@Table(name = "compliance_snapshot_app")
public class ComplianceSnapshotApp {

    @Id
    private UUID id;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "application_slug", nullable = false)
    private String applicationSlug;

    @Column(name = "business_unit")
    private String businessUnit;

    private int expected;
    private int compliant;
    private int covered;

    protected ComplianceSnapshotApp() {}

    public ComplianceSnapshotApp(UUID snapshotId, String applicationSlug, String businessUnit,
                                 int expected, int compliant, int covered) {
        this.id = UUID.randomUUID();
        this.snapshotId = snapshotId;
        this.applicationSlug = applicationSlug;
        this.businessUnit = businessUnit;
        this.expected = expected;
        this.compliant = compliant;
        this.covered = covered;
    }

    public UUID getSnapshotId() { return snapshotId; }
    public String getApplicationSlug() { return applicationSlug; }
    public String getBusinessUnit() { return businessUnit; }
    public int getExpected() { return expected; }
    public int getCompliant() { return compliant; }
    public int getCovered() { return covered; }
}
