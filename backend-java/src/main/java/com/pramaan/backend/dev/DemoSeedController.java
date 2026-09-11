package com.pramaan.backend.dev;

import com.pramaan.backend.evidence.EvidenceDtos.IngestOutcome;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.IngestResult;
import com.pramaan.backend.evidence.EvidenceIngestionService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-only demo data. Populates a handful of evidence records so the reuse /
 * completeness / dashboard views have something to show without running a
 * collection.
 *
 * <p><b>Gated at the bean level</b> on {@code pramaan.demo-mode} (env
 * {@code DEMO_MODE=true}) via {@link ConditionalOnProperty}, mirroring
 * {@code SeedRunner} / {@code TrendSeedRunner}. When demo mode is off the
 * controller is not registered at all — the path returns a genuine 404, and it
 * can never be hit in production. When it IS on, a line is logged at startup so
 * the operator can confirm the flag took effect.
 *
 * <p>Idempotent: every record is ingested through the normal
 * {@link EvidenceIngestionService} path with fixed identity + content, so a
 * repeat call dedups by evidence key + SHA-256 (outcome {@code DUPLICATE}) and
 * creates nothing new.
 */
@RestController
@RequestMapping("/api/v1/dev")
@ConditionalOnProperty(prefix = "pramaan", name = "demo-mode", havingValue = "true")
public class DemoSeedController {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedController.class);

    /** One evidence record to seed. {@code onlyFrameworks} pins a short framework tag when non-null. */
    private record Seed(String app, String controlId, String framework, String source,
                        String collectedAt, List<String> onlyFrameworks) {}

    // 7 records: 3 apps, 5 controls (all with 3+ frameworks in ControlFrameworkCatalog),
    // including OS-SSH-ROOT-LOGIN and DB-TLS-IN-TRANSIT. The payments/DB-TLS-IN-TRANSIT
    // record is deliberately tagged to PCI_DSS only, leaving DPSC + ISO27001 for
    // "Reuse for [framework]" on the Evidence Reuse page.
    private static final List<Seed> SEEDS = List.of(
            new Seed("net-banking", "OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                    "2026-08-14T09:00:00Z", null),
            new Seed("mobile-banking", "OS-SSH-ROOT-LOGIN", "C-SITE", "AGENT_OS_LINUX",
                    "2026-08-15T09:00:00Z", null),
            new Seed("payments", "DB-TLS-IN-TRANSIT", "PCI_DSS", "AGENT_DATABASE_POSTGRESQL",
                    "2026-08-16T09:00:00Z", List.of("PCI_DSS")),
            new Seed("net-banking", "DB-TLS-IN-TRANSIT", "PCI_DSS", "AGENT_DATABASE_POSTGRESQL",
                    "2026-08-17T09:00:00Z", null),
            new Seed("payments", "MW-TLS-VERSION", "PCI_DSS", "AGENT_MIDDLEWARE_NGINX",
                    "2026-08-18T09:00:00Z", null),
            new Seed("mobile-banking", "MW-HSTS", "DPSC", "AGENT_MIDDLEWARE_NGINX",
                    "2026-08-19T09:00:00Z", null),
            new Seed("net-banking", "TLS-PROTOCOL-VERSION", "PCI_DSS", "AGENT_TLS",
                    "2026-08-20T09:00:00Z", null));

    private final EvidenceIngestionService ingestion;

    public DemoSeedController(EvidenceIngestionService ingestion) {
        this.ingestion = ingestion;
        log.info("demo-mode ON — demo-evidence seeder registered at POST /api/v1/dev/seed-demo-evidence");
    }

    @PostMapping("/seed-demo-evidence")
    public Map<String, Object> seedDemoEvidence() {
        List<Map<String, Object>> records = new ArrayList<>();
        int created = 0;
        int duplicates = 0;
        for (Seed s : SEEDS) {
            IngestRequest req = new IngestRequest(
                    s.app(), s.controlId(), s.framework(), s.source(),
                    "demo-" + s.controlId().toLowerCase(),
                    null, "application/json", null,
                    "{\"demo\":true,\"application\":\"" + s.app() + "\",\"control\":\"" + s.controlId()
                            + "\",\"findings\":[{\"status\":\"PASS\"}]}",
                    Instant.parse(s.collectedAt()), "demo-seed",
                    Map.of(),
                    Map.of("collectionMethod", "demo-seed"));
            IngestResult r = ingestion.ingest(req, null, s.onlyFrameworks());
            if (r.outcome() == IngestOutcome.DUPLICATE) {
                duplicates++;
            } else {
                created++;
            }
            records.add(Map.of(
                    "evidenceId", r.evidenceId(),
                    "applicationSlug", r.applicationSlug(),
                    "controlId", r.controlId(),
                    "outcome", r.outcome().name()));
        }
        log.info("demo-evidence seed: {} created, {} already present", created, duplicates);
        return Map.of("seeded", SEEDS.size(), "created", created, "duplicates", duplicates,
                "records", records);
    }
}
