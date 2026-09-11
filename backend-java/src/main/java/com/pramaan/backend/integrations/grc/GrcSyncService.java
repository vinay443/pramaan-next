package com.pramaan.backend.integrations.grc;

import com.pramaan.backend.config.PramaanProperties;
import com.pramaan.backend.config.PramaanProperties.Integrations.Endpoint;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceDashboard;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.insight.InsightDtos.LeadershipDashboard;
import com.pramaan.backend.insight.LeadershipService;
import com.pramaan.backend.integrations.grc.GrcDtos.GrcSyncOutcome;
import com.pramaan.backend.integrations.grc.GrcDtos.GrcSyncStatus;
import com.pramaan.backend.util.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Pushes an evidence + control-status summary to an external GRC system. Deterministic
 * mock only (Phase 1, {@code mock} on by default and no credentials read or stored —
 * only a secret-free base URL from {@code pramaan.integrations.grc}), mirroring how
 * {@link com.pramaan.backend.integrations.mock.AbstractMockIntegration} mocks inbound
 * enterprise integrations. The "push" is a same-process digest of the current
 * portfolio state rather than a real network call; a live adapter can replace the
 * body of {@link #sync()} with an actual HTTP call with no caller changes.
 */
@Service
public class GrcSyncService {

    private static final Logger log = LoggerFactory.getLogger(GrcSyncService.class);
    private static final String DEFAULT_ENDPOINT = "https://grc.example.com/api/ingest";

    private final PramaanProperties props;
    private final EvidenceQueryService evidenceQueries;
    private final LeadershipService leadership;
    private final Clock clock;
    private final AtomicReference<GrcSyncStatus> last = new AtomicReference<>();

    public GrcSyncService(PramaanProperties props, EvidenceQueryService evidenceQueries,
                          LeadershipService leadership, Clock clock) {
        this.props = props;
        this.evidenceQueries = evidenceQueries;
        this.leadership = leadership;
        this.clock = clock;
    }

    /** Last sync result, or a "never synced" placeholder if none has run yet this process. */
    public GrcSyncStatus status() {
        GrcSyncStatus s = last.get();
        if (s != null) {
            return s;
        }
        Endpoint cfg = endpointConfig();
        return new GrcSyncStatus(false, null, null, null,
                cfg.mockOrDefault(), cfg.baseUrlOrDefault(DEFAULT_ENDPOINT), 0, 0, 0.0,
                "no sync has run yet");
    }

    public GrcSyncStatus sync() {
        Endpoint cfg = endpointConfig();
        String endpoint = cfg.baseUrlOrDefault(DEFAULT_ENDPOINT);
        boolean mock = cfg.mockOrDefault();

        EvidenceDashboard evidence = evidenceQueries.dashboard();
        LeadershipDashboard posture = leadership.dashboard();

        String payload = "evidence=%d;versions=%d;expected=%d;compliant=%d;compliancePct=%.1f;asOf=%s"
                .formatted(evidence.records(), evidence.versions(), posture.expected(), posture.compliant(),
                        posture.compliancePct(), clock.instant());
        String reference = "grc-" + Hashing.sha256Hex(payload.getBytes(StandardCharsets.UTF_8)).substring(0, 16);

        log.info("GRC sync: mock={} endpoint={} evidenceRecords={} controlsEvaluated={} compliancePct={} ref={}",
                mock, endpoint, evidence.records(), posture.expected(), posture.compliancePct(), reference);

        GrcSyncStatus status = new GrcSyncStatus(true, clock.instant(), GrcSyncOutcome.SUCCESS, reference,
                mock, endpoint, (int) evidence.records(), posture.expected(), posture.compliancePct(),
                "synced %d evidence record(s) and %d control result(s) (mock)"
                        .formatted(evidence.records(), posture.expected()));
        last.set(status);
        return status;
    }

    private Endpoint endpointConfig() {
        return props.integrations() == null ? new Endpoint(true, null) : props.integrations().grcOrDefault();
    }
}
