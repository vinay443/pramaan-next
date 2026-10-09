package com.pramaan.backend.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.evidence.EvidenceDtos.IntegrityReport;
import com.pramaan.backend.evidence.EvidenceDtos.IntegrityStatus;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.insight.AuditPrepService;
import com.pramaan.backend.insight.ComplianceService;
import com.pramaan.backend.insight.ControlCatalog;
import com.pramaan.backend.insight.InsightDtos.ControlPosture;
import com.pramaan.backend.insight.InsightDtos.ControlStatus;
import com.pramaan.backend.integrations.EnterpriseIntegration.CollectedEvidence;
import com.pramaan.backend.integrations.EnterpriseIntegration.CollectionRequest;
import com.pramaan.backend.integrations.IntegrationRegistry;
import com.pramaan.backend.scheduler.ScheduleJobService;
import com.pramaan.backend.scheduler.SchedulerDtos.CreateScheduleRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunRequest;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.SchedulerRunExecutor;
import com.pramaan.backend.scheduler.SchedulerService;
import com.pramaan.backend.scheduler.domain.SchedulerRun.Trigger;
import com.pramaan.backend.storage.ObjectStore;
import com.pramaan.backend.util.Hashing;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC01-FT013 (evidence integrity) and FT014 (compliance mapping) for evidence collected by scheduled runs.
 * Verification only: these tests pass against the existing behaviour, no production code was changed for them.
 */
class EvidenceIntegrityMappingTest {

    private static final String HEX64 = "[0-9a-f]{64}";

    abstract static class Base {
        @Autowired ObjectStore store;
        @Autowired EvidenceQueryService queries;
        @Autowired EvidenceRecordRepository records;
        @Autowired OnboardingService onboarding;

        @BeforeEach
        void onboardApps() {
            onboarding.apply(false);
        }

        /**
         * The object store is write-once by design (put never overwrites), so tampering has to bypass its API.
         * Test-only: reaches into the test profile's own in-memory store; real data is never touched.
         */
        @SuppressWarnings("unchecked")
        void overwrite(String key, byte[] bytes) {
            try {
                var f = store.getClass().getDeclaredField("blobs");
                f.setAccessible(true);
                ((java.util.Map<String, byte[]>) f.get(store)).put(key, bytes);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("test object store is not the in-memory store", e);
            }
        }

        /** Stored SHA-256 is well-formed, equals a hash recomputed from the object bytes, verify reports PASS. */
        void assertIntact(EvidenceRecord r) {
            var v = r.latestVersion();
            assertThat(v.getSha256()).matches(HEX64);
            byte[] bytes = store.get(v.getObjectKey()).orElseThrow();
            assertThat(Hashing.sha256Hex(bytes)).isEqualTo(v.getSha256());
            IntegrityReport report = queries.verify(r.getId(), null);
            assertThat(report.intact()).isTrue();
            assertThat(report.expectedSha256()).isEqualTo(report.actualSha256());
            assertThat(queries.get(r.getId()).integrityStatus()).isEqualTo(IntegrityStatus.VERIFIED);
        }
    }

    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    @Transactional
    static class Scheduled extends Base {

        @Autowired SchedulerService scheduler;
        @Autowired SchedulerRunExecutor executor;
        @Autowired ScheduleJobService jobs;
        @Autowired ControlCatalog catalog;
        @Autowired ComplianceService compliance;
        @Autowired AuditPrepService auditPrep;
        @Autowired IntegrationRegistry integrations;

        private RunView finished(RunView started) {
            executor.execute(UUID.fromString(started.runId()));
            return scheduler.get(UUID.fromString(started.runId()));
        }

        private RunView runSchedule(String name) {
            return finished(jobs.runNow(UUID.fromString(jobs.list().stream()
                    .filter(s -> s.name().equals(name)).findFirst().orElseThrow().id()), "tester"));
        }

        @Test
        void scheduledAndManualEvidenceKeepIntegrityThroughDuplicatesAndTamperingIsDetected() {
            jobs.create(new CreateScheduleRequest("TLS pull", "MOCK_GITHUB", "payments", "TLS-SCAN", "DAILY", "tester"));
            RunView scheduled = runSchedule("TLS pull");
            RunView manual = finished(scheduler.trigger(new RunRequest(List.of("payments"), List.of(),
                    List.of("MOCK_JIRA"), "tester"), Trigger.MANUAL));
            assertThat(scheduled.status()).isEqualTo("COMPLETED");
            assertThat(manual.status()).isEqualTo("COMPLETED");

            List<EvidenceRecord> all = records.findByApplicationSlug("payments");
            assertThat(all).hasSize(scheduled.ingested() + manual.ingested());
            for (EvidenceRecord r : all) {
                assertThat(r.getVersions()).hasSize(1);
                assertIntact(r);
            }
            // the schedule's override tags the scheduled evidence only
            assertThat(all.stream().filter(r -> r.getSourceSystem().equals("MOCK_GITHUB")).map(r -> queries.get(r.getId()).tags().get("evidenceType")))
                    .containsOnly("TLS-SCAN");

            // duplicate re-run: hash and object key unchanged, still PASS
            var before = all.stream().collect(Collectors.toMap(EvidenceRecord::getId,
                    r -> r.latestVersion().getSha256() + "|" + r.latestVersion().getObjectKey()));
            RunView again = runSchedule("TLS pull");
            assertThat(again.ingested()).isZero();
            assertThat(again.duplicates()).isEqualTo(scheduled.ingested());
            for (EvidenceRecord r : records.findByApplicationSlug("payments")) {
                assertThat(r.latestVersion().getSha256() + "|" + r.latestVersion().getObjectKey()).isEqualTo(before.get(r.getId()));
                assertThat(r.getVersions()).hasSize(1);
                assertIntact(r);
            }

            // tamper with one stored object: verify must fail and the detail view must say so; then restore
            EvidenceRecord victim = all.get(0);
            String key = victim.latestVersion().getObjectKey();
            byte[] original = store.get(key).orElseThrow();
            overwrite(key, "tampered".getBytes());
            try {
                IntegrityReport report = queries.verify(victim.getId(), null);
                assertThat(report.intact()).isFalse();
                assertThat(report.detail()).isEqualTo("sha-256 mismatch");
                assertThat(report.expectedSha256()).isNotEqualTo(report.actualSha256());
                assertThat(queries.get(victim.getId()).integrityStatus()).isEqualTo(IntegrityStatus.TAMPERED);
            } finally {
                overwrite(key, original);
            }
            assertIntact(victim);
        }

        @Test
        void scheduledEvidenceMapsToCatalogControlsAndMovesTheComplianceAndAuditPrepRollups() {
            Set<String> gapsBefore = auditPrep.prepare("payments", null).findings().stream()
                    .filter(f -> f.category().equals("EVIDENCE_GAP")).map(f -> f.framework() + "/" + f.controlId())
                    .collect(Collectors.toSet());
            var postureBefore = compliance.forApplication("payments", null).controls().stream()
                    .collect(Collectors.toMap(c -> c.framework() + "/" + c.controlId(), ControlPosture::status, (a, b) -> a));

            jobs.create(new CreateScheduleRequest("Jira pull", "MOCK_JIRA", "payments", "TLS-SCAN", "DAILY", "tester"));
            RunView run = runSchedule("Jira pull");
            assertThat(run.status()).isEqualTo("COMPLETED");

            List<EvidenceRecord> mine = records.findByApplicationSlug("payments");
            assertThat(mine).isNotEmpty();
            List<String> unresolved = new ArrayList<>();
            for (EvidenceRecord r : mine) {
                assertThat(r.getControlId()).isNotBlank();
                assertThat(r.getFramework()).isNotBlank();
                var expectation = catalog.find(r.getFramework(), r.getControlId());
                if (expectation.isEmpty() || !expectation.get().framework().equalsIgnoreCase(r.getFramework())
                        || expectation.get().title() == null || expectation.get().title().isBlank()) {
                    unresolved.add(r.getFramework() + "/" + r.getControlId());
                }
            }
            assertThat(unresolved).as("control ids that do not resolve in the Control Library catalog").isEmpty();

            // the evidence-type override must not influence the control mapping: same controls as the collector yields
            Set<String> collected = integrations.require("MOCK_JIRA")
                    .collect(new CollectionRequest("payments", List.of(), Instant.now())).stream()
                    .map(CollectedEvidence::controlId).collect(Collectors.toSet());
            assertThat(mine.stream().map(EvidenceRecord::getControlId).collect(Collectors.toSet())).isEqualTo(collected);

            // compliance: each evidenced control left MISSING_EVIDENCE; audit prep no longer lists a gap for it
            var report = compliance.forApplication("payments", null);
            var gapsAfter = auditPrep.prepare("payments", null).findings().stream()
                    .filter(f -> f.category().equals("EVIDENCE_GAP")).map(f -> f.framework() + "/" + f.controlId())
                    .collect(Collectors.toSet());
            int moved = 0;
            for (EvidenceRecord r : mine) {
                String k = r.getFramework() + "/" + r.getControlId();
                ControlPosture p = report.controls().stream()
                        .filter(c -> (c.framework() + "/" + c.controlId()).equalsIgnoreCase(k)).findFirst().orElseThrow();
                assertThat(p.status()).as("posture of %s", k).isNotEqualTo(ControlStatus.MISSING_EVIDENCE);
                assertThat(gapsAfter).as("audit-prep gaps still list %s", k).doesNotContain(k);
                if (postureBefore.get(k) == ControlStatus.MISSING_EVIDENCE) moved++;
                if (gapsBefore.contains(k)) assertThat(gapsAfter).doesNotContain(k);
            }
            assertThat(moved).isGreaterThan(0);
        }
    }

    /** FT013 check 4: evidence written by the concurrent 10-run burst (retry-on-conflict path) still verifies. */
    @SpringBootTest(properties = {"pramaan.scheduler.dispatch=true",
            "spring.datasource.url=jdbc:h2:mem:integrityburst;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    static class Burst extends Base {

        @Autowired MockMvc mvc;
        @Autowired ObjectMapper json;
        @Autowired org.springframework.transaction.PlatformTransactionManager tm;

        @Test
        void evidenceFromTenConcurrentRunsStillVerifiesAndHasSingleVersions() throws Exception {
            String body = "{\"applications\":[\"payments\"],\"sources\":[\"MOCK_JIRA\",\"MOCK_GITHUB\"]}";
            ExecutorService clients = Executors.newFixedThreadPool(10);
            List<Future<String>> ids = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                Callable<String> c = () -> json.readTree(mvc.perform(post("/api/v1/scheduler/runs").header("X-Pramaan-Role", "APP")
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).get("runId").asText();
                ids.add(clients.submit(c));
            }
            for (Future<String> f : ids) {
                String id = f.get();
                Instant deadline = Instant.now().plus(Duration.ofSeconds(90));
                String st;
                do {
                    Thread.sleep(100);
                    JsonNode run = json.readTree(mvc.perform(get("/api/v1/scheduler/runs/" + id).header("X-Pramaan-Role", "APP"))
                            .andReturn().getResponse().getContentAsString());
                    st = run.get("status").asText();
                } while ((st.equals("PENDING") || st.equals("RUNNING")) && Instant.now().isBefore(deadline));
                assertThat(st).isEqualTo("COMPLETED");
            }
            clients.shutdown();

            // lazy collections need a session; the burst itself ran on worker threads and is long committed
            new org.springframework.transaction.support.TransactionTemplate(tm).executeWithoutResult(tx -> {
                List<EvidenceRecord> all = records.findByApplicationSlug("payments");
                assertThat(all).isNotEmpty();
                for (EvidenceRecord r : all) {
                    assertThat(r.getVersions()).hasSize(1);
                    assertIntact(r);
                }
            });
        }
    }
}
