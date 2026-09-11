package com.pramaan.backend.integrations.grc;

import java.time.Instant;

public final class GrcDtos {

    private GrcDtos() {}

    public enum GrcSyncOutcome { SUCCESS, FAILED }

    /** Current sync state — also the response of a manual sync trigger. */
    public record GrcSyncStatus(
            boolean everSynced,
            Instant lastSyncedAt,
            GrcSyncOutcome lastOutcome,
            String externalReference,
            boolean mock,
            String endpoint,
            int evidenceRecords,
            int controlsEvaluated,
            double compliancePct,
            String detail) {}
}
