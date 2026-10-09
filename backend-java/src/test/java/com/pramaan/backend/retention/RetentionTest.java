package com.pramaan.backend.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.evidence.EvidenceLifecycleService;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.domain.EvidenceLifecycleState;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.insight.DashboardKpiService;
import com.pramaan.backend.insight.DashboardKpiService.Filters;
import com.pramaan.backend.scheduler.ScheduleJobService;
import com.pramaan.backend.scheduler.SchedulerDtos.CreateScheduleRequest;
import com.pramaan.backend.scheduler.SchedulerRunExecutor;
import com.pramaan.backend.scheduler.SchedulerService;
import com.pramaan.backend.security.SecurityEventService;
import com.pramaan.backend.storage.ObjectStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** UC01-FT017 - retention policy and archival. Test records only; archival never touches objects or hashes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RetentionTest {

    private static final String ROLE = "X-Pramaan-Role";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired OnboardingService onboarding;
    @Autowired ScheduleJobService jobs;
    @Autowired SchedulerService scheduler;
    @Autowired SchedulerRunExecutor executor;
    @Autowired EvidenceRecordRepository records;
    @Autowired EvidenceQueryService queries;
    @Autowired EvidenceLifecycleService lifecycle;
    @Autowired RetentionPolicyService policy;
    @Autowired DashboardKpiService dashboard;
    @Autowired SecurityEventService security;
    @Autowired ObjectStore store;
    @Autowired JdbcTemplate jdbc;
    @PersistenceContext EntityManager em;

    UUID oldId;
    UUID freshId;
    UUID otherAppOldId;

    @BeforeEach
    void evidence() {
        onboarding.apply(false);
        collect("Pay pull", "payments", "MOCK_JIRA");
        collect("Net pull", "net-banking", "MOCK_JIRA");
        List<EvidenceRecord> pay = records.findByApplicationSlug("payments");
        List<EvidenceRecord> net = records.findByApplicationSlug("net-banking");
        assertThat(pay).hasSizeGreaterThan(1);
        oldId = pay.get(0).getId();
        freshId = pay.get(1).getId();
        otherAppOldId = net.get(0).getId();
        backdate(oldId, 400);
        backdate(otherAppOldId, 400);
    }

    private void collect(String name, String app, String source) {
        var job = jobs.create(new CreateScheduleRequest(name, source, app, "GENERAL", "DAILY", "tester"));
        executor.execute(UUID.fromString(jobs.runNow(UUID.fromString(job.id()), "tester").runId()));
    }

    /** Sets collectedAt directly in the (test) database. */
    private void backdate(UUID id, int daysAgo) {
        em.flush();
        jdbc.update("update evidence_record set latest_collected_at = ? where id = ?",
                OffsetDateTime.ofInstant(Instant.now().minus(daysAgo, ChronoUnit.DAYS), ZoneOffset.UTC), id);
        em.clear();
    }

    private JsonNode archive(String mode, String app) throws Exception {
        String body = "{\"mode\":\"" + mode + "\"" + (app == null ? "" : ",\"application\":\"" + app + "\"") + "}";
        return json.readTree(mvc.perform(post("/api/v1/retention/archive").header(ROLE, "ADMIN")
                        .header("X-Pramaan-User", "Platform Admin").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private List<String> ids(JsonNode result) {
        return result.get("items").findValuesAsText("evidenceId");
    }

    private EvidenceRecord load(UUID id) {
        return records.findById(id).orElseThrow();
    }

    @Test
    void defaultPolicyIs365AndTheDisplayedExpiryFollowsIt() {
        assertThat(policy.get().retentionDays()).isEqualTo(365);
        var view = lifecycle.get(freshId);
        assertThat(view.retentionDays()).isEqualTo(365);
        assertThat(view.expiresAt()).isEqualTo(load(freshId).getLatestCollectedAt().plus(365, ChronoUnit.DAYS));

        policy.update(30, null, "Platform Admin");
        var changed = lifecycle.get(freshId);
        assertThat(changed.retentionDays()).isEqualTo(30);
        assertThat(changed.expiresAt()).isEqualTo(load(freshId).getLatestCollectedAt().plus(30, ChronoUnit.DAYS));
    }

    @Test
    void previewChangesNothingAndExecuteArchivesOnlyEligibleEvidenceWithoutTouchingTheStoredObject() throws Exception {
        EvidenceRecord old = load(oldId);
        String objectKey = old.latestVersion().getObjectKey();
        String sha = old.latestVersion().getSha256();
        byte[] bytes = store.get(objectKey).orElseThrow();
        int versions = old.getVersions().size();
        int eventsBefore = lifecycle.get(oldId).history().size();

        JsonNode preview = archive("PREVIEW", null);
        assertThat(ids(preview)).contains(oldId.toString(), otherAppOldId.toString()).doesNotContain(freshId.toString());
        assertThat(preview.get("eligible").asInt()).isEqualTo(2);
        assertThat(preview.get("archived").asInt()).isZero();
        assertThat(load(oldId).getLifecycleState()).isNotEqualTo(EvidenceLifecycleState.ARCHIVED);
        assertThat(lifecycle.get(oldId).history()).hasSize(eventsBefore);

        JsonNode run = archive("EXECUTE", null);
        assertThat(run.get("archived").asInt()).isEqualTo(2);
        EvidenceRecord archived = load(oldId);
        assertThat(archived.getLifecycleState()).isEqualTo(EvidenceLifecycleState.ARCHIVED);
        assertThat(archived.getArchivedAt()).isNotNull();
        var history = lifecycle.get(oldId).history();
        assertThat(history).hasSize(eventsBefore + 1);
        var last = history.get(history.size() - 1);
        assertThat(last.action()).isEqualTo("ARCHIVED");
        assertThat(last.toState()).isEqualTo("ARCHIVED");
        assertThat(last.actor()).isEqualTo("Platform Admin");
        assertThat(last.note()).isEqualTo("retention 365d elapsed (policy)");

        // object, hash and versions are exactly as before; verify still passes
        assertThat(archived.latestVersion().getObjectKey()).isEqualTo(objectKey);
        assertThat(archived.latestVersion().getSha256()).isEqualTo(sha);
        assertThat(archived.getVersions()).hasSize(versions);
        assertThat(store.get(objectKey).orElseThrow()).isEqualTo(bytes);
        assertThat(queries.verify(oldId, null).intact()).isTrue();
        // the fresh evidence is untouched
        assertThat(load(freshId).getLifecycleState()).isNotEqualTo(EvidenceLifecycleState.ARCHIVED);
        assertThat(lifecycle.get(freshId).history().stream().map(e -> e.action())).doesNotContain("ARCHIVED");

        // archived is terminal
        mvc.perform(post("/api/v1/evidence/" + oldId + "/lifecycle").header(ROLE, "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"SUBMIT\",\"actor\":\"x\"}"))
                .andExpect(status().isConflict());

        // idempotent
        JsonNode again = archive("EXECUTE", null);
        assertThat(again.get("archived").asInt()).isZero();
        assertThat(again.get("eligible").asInt()).isZero();

        // history of calls, newest first
        mvc.perform(get("/api/v1/retention/runs").header(ROLE, "CIO")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mode").value("EXECUTE")).andExpect(jsonPath("$[0].archived").value(0))
                .andExpect(jsonPath("$[1].archived").value(2)).andExpect(jsonPath("$[2].mode").value("PREVIEW"));

        // dashboard per-state counts, including archived, still sum to the repository total
        var kpi = dashboard.kpis(new Filters(null, null, null, null, null), "ADMIN").evidence();
        var c = kpi.counts();
        assertThat(c.archived()).isEqualTo(2);
        assertThat(c.draft() + c.submitted() + c.approved() + c.rejected() + c.expired() + c.superseded() + c.archived())
                .isEqualTo(kpi.total());
        assertThat(kpi.total()).isEqualTo((int) queries.search(new com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter(
                null, null, null, null, null, null, null, 0, 10)).totalItems());
    }

    @Test
    void applicationScopeArchivesOnlyThatApplicationsEligibleEvidence() throws Exception {
        JsonNode run = archive("EXECUTE", "payments");
        assertThat(run.get("application").asText()).isEqualTo("payments");
        assertThat(ids(run)).containsExactly(oldId.toString());
        assertThat(load(oldId).getLifecycleState()).isEqualTo(EvidenceLifecycleState.ARCHIVED);
        assertThat(load(otherAppOldId).getLifecycleState()).isNotEqualTo(EvidenceLifecycleState.ARCHIVED);
    }

    @Test
    void policyChangesWhatIsEligible() throws Exception {
        assertThat(ids(archive("PREVIEW", "payments"))).doesNotContain(freshId.toString());
        mvc.perform(put("/api/v1/retention/policy").header(ROLE, "ADMIN").contentType(MediaType.APPLICATION_JSON)
                .content("{\"retentionDays\":0}")).andExpect(status().isOk()).andExpect(jsonPath("$.retentionDays").value(0));
        assertThat(ids(archive("PREVIEW", "payments"))).contains(freshId.toString(), oldId.toString());
        mvc.perform(put("/api/v1/retention/policy").header(ROLE, "ADMIN").contentType(MediaType.APPLICATION_JSON)
                .content("{\"retentionDays\":365}")).andExpect(status().isOk());
        assertThat(ids(archive("PREVIEW", "payments"))).doesNotContain(freshId.toString());
    }

    @Test
    void rerunningCollectionOverArchivedEvidenceReportsDuplicatesAndDoesNotUnarchive() throws Exception {
        archive("EXECUTE", "payments");
        assertThat(load(oldId).getLifecycleState()).isEqualTo(EvidenceLifecycleState.ARCHIVED);
        var job = jobs.list().stream().filter(s -> s.name().equals("Pay pull")).findFirst().orElseThrow();
        var run = jobs.runNow(UUID.fromString(job.id()), "tester");
        executor.execute(UUID.fromString(run.runId()));
        var done = scheduler.get(UUID.fromString(run.runId()));
        assertThat(done.ingested()).isZero();
        assertThat(done.duplicates()).isGreaterThan(0);
        assertThat(load(oldId).getLifecycleState()).isEqualTo(EvidenceLifecycleState.ARCHIVED);
        assertThat(load(oldId).getVersions()).hasSize(1);
    }

    @Test
    void onlyAdminChangesThePolicyOrArchivesAndInputIsValidated() throws Exception {
        for (String role : new String[] {"AUDITOR", "APP", "CIO", "FH", "VH"}) {
            long before = deniedCount(role);
            mvc.perform(put("/api/v1/retention/policy").header(ROLE, role).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"retentionDays\":10}")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
            mvc.perform(post("/api/v1/retention/archive").header(ROLE, role).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"mode\":\"EXECUTE\"}")).andExpect(status().isForbidden());
            assertThat(deniedCount(role)).isEqualTo(before + 2);
            mvc.perform(get("/api/v1/retention/policy").header(ROLE, role)).andExpect(status().isOk());
            mvc.perform(get("/api/v1/retention/runs").header(ROLE, role)).andExpect(status().isOk());
        }
        assertThat(policy.days()).isEqualTo(365);
        assertThat(load(oldId).getLifecycleState()).isNotEqualTo(EvidenceLifecycleState.ARCHIVED);
        mvc.perform(get("/api/v1/retention/policy")).andExpect(status().isForbidden()); // no role

        for (String bad : new String[] {"-1", "99999", "\"abc\"", "1.5", "null"}) {
            long rejected = security.list("INPUT_REJECTED", 500).stream()
                    .filter(e -> e.path() != null && e.path().equals("/api/v1/retention/policy")).count();
            mvc.perform(put("/api/v1/retention/policy").header(ROLE, "ADMIN").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"retentionDays\":" + bad + "}")).andExpect(status().isBadRequest());
            assertThat(security.list("INPUT_REJECTED", 500).stream()
                    .filter(e -> e.path() != null && e.path().equals("/api/v1/retention/policy")).count()).isEqualTo(rejected + 1);
        }
        mvc.perform(post("/api/v1/retention/archive").header(ROLE, "ADMIN").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"DELETE\"}")).andExpect(status().isBadRequest());
        assertThat(policy.days()).isEqualTo(365);

        // optimistic locking: first save creates the row (version 0); a stale version is a 409
        JsonNode first = json.readTree(mvc.perform(put("/api/v1/retention/policy").header(ROLE, "ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content("{\"retentionDays\":200}"))
                .andReturn().getResponse().getContentAsString());
        em.flush(); // one test transaction: flush like a real commit so the row version moves
        long v = first.get("version").asLong();
        mvc.perform(put("/api/v1/retention/policy").header(ROLE, "ADMIN").contentType(MediaType.APPLICATION_JSON)
                .content("{\"retentionDays\":100,\"version\":" + v + "}")).andExpect(status().isOk());
        em.flush();
        mvc.perform(put("/api/v1/retention/policy").header(ROLE, "ADMIN").contentType(MediaType.APPLICATION_JSON)
                .content("{\"retentionDays\":50,\"version\":" + v + "}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
        assertThat(policy.days()).isEqualTo(100);
    }

    private long deniedCount(String role) {
        return security.list("ACCESS_DENIED", 500).stream()
                .filter(e -> role.equals(e.role()) && e.path() != null && e.path().startsWith("/api/v1/retention")).count();
    }
}
