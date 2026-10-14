package com.pramaan.backend.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleAction;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleTransitionRequest;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.security.SecurityEventService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** FT006 - PATCH /api/v1/evidence/{id}/metadata: edit with a mandatory reason, audited, naming kept unique. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:metaedit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvidenceMetadataEditTest {

    private static final String ROLE = "X-Pramaan-Role";
    private static final String USER = "X-Pramaan-User";
    private static final String CONTROL = "MW-TLS-VERSION";
    private static final Instant COLLECTED = Instant.parse("2026-03-05T10:00:00Z");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EvidenceIngestionService ingestion;
    @Autowired EvidenceLifecycleService lifecycle;
    @Autowired EvidenceRecordRepository records;
    @Autowired SecurityEventService security;
    @Autowired ControlFrameworkCatalog frameworks;

    private UUID evidence(String source, String env) {
        Map<String, String> tags = env == null ? Map.of("technology", "nginx")
                : Map.of("technology", "nginx", "environment", env);
        return UUID.fromString(ingestion.ingest(new IngestRequest("meta-app", CONTROL, "PCI_DSS", "BULK_UPLOAD",
                source, source + ".txt", "text/plain", null, "content of " + source, COLLECTED, "tester",
                Map.of(), tags)).evidenceId());
    }

    private MvcResult edit(UUID id, String role, String body) throws Exception {
        var b = patch("/api/v1/evidence/" + id + "/metadata").contentType(MediaType.APPLICATION_JSON).content(body);
        if (role != null) b.header(ROLE, role);
        return mvc.perform(b.header(USER, "App Owner")).andReturn();
    }

    private JsonNode ok(UUID id, String body) throws Exception {
        MvcResult r = edit(id, "APP", body);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return json.readTree(r.getResponse().getContentAsString());
    }

    private JsonNode lifecycleOf(UUID id) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/evidence/" + id + "/lifecycle").header(ROLE, "APP"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String body(String changes, String reason) {
        return "{\"changes\":" + changes + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}";
    }

    private long metadataEvents(UUID id) throws Exception {
        long n = 0;
        for (JsonNode e : lifecycleOf(id).get("history")) if ("METADATA_EDITED".equals(e.get("action").asText())) n++;
        return n;
    }

    @Test
    void editsFieldsRenamesAndWritesAnAuditEventReadableThroughLifecycle() throws Exception {
        UUID id = evidence("a-success", "UAT");
        String before = records.findById(id).orElseThrow().getTitle();
        assertThat(before).endsWith("_UAT_20260305_001");

        JsonNode out = ok(id, body("{\"environment\":\"prod\",\"technology\":\"Tomcat\",\"title\":\"New title\","
                + "\"evidenceType\":\"tls scan\"}", "wrong environment recorded"));
        assertThat(out.get("lifecycleState").asText()).isEqualTo("DRAFT");
        assertThat(out.get("evidenceId").asText()).isEqualTo(id.toString());
        JsonNode tags = out.get("tags");
        assertThat(tags.get("environment").asText()).isEqualTo("PROD");
        assertThat(tags.get("technology").asText()).isEqualTo("tomcat");
        assertThat(tags.get("evidenceType").asText()).isEqualTo("TLS-SCAN");
        assertThat(tags.get("sourceTitle").asText()).isEqualTo("New title");
        assertThat(out.get("title").asText()).isEqualTo("meta-app_MW-TLS-VERSION_TLS-SCAN_PROD_20260305_001");
        assertThat(tags.get("name").asText()).isEqualTo(out.get("title").asText());
        assertThat(out.get("currentVersion").asInt()).isEqualTo(1); // content untouched, no new version

        JsonNode history = lifecycleOf(id).get("history");
        JsonNode ev = history.get(history.size() - 1);
        assertThat(ev.get("action").asText()).isEqualTo("METADATA_EDITED");
        assertThat(ev.get("actor").asText()).isEqualTo("App Owner");
        assertThat(ev.get("occurredAt").asText()).isNotBlank();
        JsonNode note = json.readTree(ev.get("note").asText());
        assertThat(note.get("reason").asText()).isEqualTo("wrong environment recorded");
        assertThat(note.get("role").asText()).isEqualTo("APP");
        assertThat(note.at("/changes/environment/old").asText()).isEqualTo("UAT");
        assertThat(note.at("/changes/environment/new").asText()).isEqualTo("PROD");
        assertThat(note.at("/changes/technology/old").asText()).isEqualTo("nginx");
        assertThat(note.at("/changes/technology/new").asText()).isEqualTo("tomcat");
        assertThat(note.at("/changes/name/old").asText()).isEqualTo(before);
        assertThat(note.at("/changes/name/new").asText()).isEqualTo(out.get("title").asText());
    }

    @Test
    void reasonIsRequired() throws Exception {
        UUID id = evidence("b-reason", "UAT");
        assertThat(edit(id, "APP", body("{\"technology\":\"x\"}", null)).getResponse().getStatus()).isEqualTo(400);
        assertThat(edit(id, "APP", body("{\"technology\":\"x\"}", "   ")).getResponse().getStatus()).isEqualTo(400);
        assertThat(metadataEvents(id)).isZero();
    }

    @Test
    void badKeysValuesAndEmptyChangesAreRejectedNamingTheProblem() throws Exception {
        UUID id = evidence("c-bad", "UAT");
        MvcResult badKey = edit(id, "APP", body("{\"sourceSystem\":\"x\"}", "r"));
        assertThat(badKey.getResponse().getStatus()).isEqualTo(400);
        assertThat(badKey.getResponse().getContentAsString()).contains("sourceSystem");
        assertThat(edit(id, "APP", body("{}", "r")).getResponse().getStatus()).isEqualTo(400);
        assertThat(edit(id, "APP", "{\"reason\":\"r\"}").getResponse().getStatus()).isEqualTo(400);

        MvcResult badEnv = edit(id, "APP", body("{\"environment\":\"STAGING\"}", "r"));
        assertThat(badEnv.getResponse().getStatus()).isEqualTo(400);
        assertThat(badEnv.getResponse().getContentAsString()).contains("STAGING");
        assertThat(edit(id, "APP", body("{\"technology\":\" \"}", "r")).getResponse().getStatus()).isEqualTo(400);
        assertThat(edit(id, "APP", body("{\"frameworks\":[\"NOT_A_FW\",\"PCI_DSS\"]}", "r")).getResponse()
                .getStatus()).isEqualTo(400);
        String other = frameworks.frameworks().stream().filter(f -> !f.equals("PCI_DSS")).findFirst().orElseThrow();
        assertThat(edit(id, "APP", body("{\"frameworks\":[\"" + other + "\"]}", "r")).getResponse().getStatus())
                .as("the record's primary framework must stay").isEqualTo(400);
        assertThat(metadataEvents(id)).isZero();
        assertThat(records.findById(id).orElseThrow().getTitle()).endsWith("_UAT_20260305_001");
    }

    @Test
    void frameworksCanBeExtendedWithKnownFrameworksKeepingThePrimaryFirst() throws Exception {
        UUID id = evidence("d-fw", "UAT");
        String other = frameworks.frameworks().stream().filter(f -> !f.equals("PCI_DSS")).findFirst().orElseThrow();
        JsonNode out = ok(id, body("{\"frameworks\":[\"" + other + "\",\"pci_dss\"]}", "also satisfies " + other));
        assertThat(out.at("/tags/frameworks").asText()).isEqualTo("PCI_DSS," + other);
        assertThat(out.get("framework").asText()).isEqualTo("PCI_DSS");
    }

    @Test
    void onlyAppAndAdminMayEditAndEveryRefusalIsAnAccessDeniedSecurityEvent() throws Exception {
        UUID id = evidence("e-roles", "UAT");
        for (String role : List.of("AUDITOR", "FH", "VH", "CIO")) {
            int denied = security.list("ACCESS_DENIED", 500).size();
            assertThat(edit(id, role, body("{\"technology\":\"x\"}", "r")).getResponse().getStatus())
                    .as(role).isEqualTo(403);
            assertThat(security.list("ACCESS_DENIED", 500).size()).as(role + " event").isEqualTo(denied + 1);
        }
        int unknown = security.list("UNKNOWN_ROLE", 500).size();
        assertThat(edit(id, null, body("{\"technology\":\"x\"}", "r")).getResponse().getStatus()).isEqualTo(403);
        assertThat(edit(id, "NOBODY", body("{\"technology\":\"x\"}", "r")).getResponse().getStatus()).isEqualTo(403);
        assertThat(security.list("UNKNOWN_ROLE", 500).size()).isEqualTo(unknown + 2);
        assertThat(metadataEvents(id)).isZero();

        assertThat(edit(id, "APP", body("{\"technology\":\"x\"}", "r")).getResponse().getStatus()).isEqualTo(200);
        assertThat(edit(id, "ADMIN", body("{\"technology\":\"y\"}", "r")).getResponse().getStatus()).isEqualTo(200);
        assertThat(metadataEvents(id)).isEqualTo(2);
    }

    @Test
    void archivedEvidenceCannotBeEdited() throws Exception {
        UUID id = evidence("f-archived", "UAT");
        lifecycle.archive(records.findById(id).orElseThrow(), "retention", "test");
        assertThat(edit(id, "ADMIN", body("{\"technology\":\"x\"}", "r")).getResponse().getStatus()).isEqualTo(409);
        assertThat(records.findById(id).orElseThrow().getLifecycleState().name()).isEqualTo("ARCHIVED");
    }

    @Test
    void anApprovedRecordGoesBackToDraftAndTheEventRecordsBothStates() throws Exception {
        UUID id = evidence("g-approved", "UAT");
        lifecycle.transition(id, new LifecycleTransitionRequest(LifecycleAction.SUBMIT, "owner", "go"),
                "APP_OWNER", null);
        lifecycle.transition(id, new LifecycleTransitionRequest(LifecycleAction.APPROVE, "auditor", "ok"),
                "AUDITOR", null);
        assertThat(lifecycleOf(id).get("state").asText()).isEqualTo("APPROVED");

        JsonNode out = ok(id, body("{\"technology\":\"haproxy\"}", "fix technology"));
        assertThat(out.get("lifecycleState").asText()).isEqualTo("DRAFT");
        JsonNode history = lifecycleOf(id).get("history");
        JsonNode ev = history.get(history.size() - 1);
        assertThat(ev.get("fromState").asText()).isEqualTo("APPROVED");
        assertThat(ev.get("toState").asText()).isEqualTo("DRAFT");
    }

    @Test
    void theSequenceIsKeptWhenTheNewPrefixIsFreeAndAdvancesOnACollision() throws Exception {
        UUID a = evidence("h-a", "UAT");
        UUID b = evidence("h-b", "PROD"); // PROD ..._001 is taken by b
        UUID c = evidence("h-c", "UAT");  // UAT ..._002
        assertThat(records.findById(c).orElseThrow().getTitle()).endsWith("_UAT_20260305_002");

        JsonNode kept = ok(a, body("{\"environment\":\"DR\"}", "moved to DR")); // DR is free: keeps 001
        assertThat(kept.get("title").asText()).endsWith("_DR_20260305_001");

        JsonNode next = ok(c, body("{\"environment\":\"PROD\"}", "collision")); // PROD 002 (b holds 001)
        assertThat(next.get("title").asText()).endsWith("_PROD_20260305_002");
        assertThat(records.findById(b).orElseThrow().getTitle()).endsWith("_PROD_20260305_001");

        JsonNode blank = ok(b, body("{\"environment\":\"\"}", "no environment")); // segment dropped, tag removed
        assertThat(blank.get("title").asText()).isEqualTo("meta-app_MW-TLS-VERSION_MIDDLEWARE-CONFIG_20260305_001");
        assertThat(blank.get("tags").has("environment")).isFalse();

        List<String> names = records.findAll().stream()
                .filter(r -> r.getApplicationSlug().equals("meta-app")).map(r -> r.getTitle()).toList();
        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    void anEditThatChangesNothingIsOkWithNoEventAndNoStateChange() throws Exception {
        UUID id = evidence("i-noop", "UAT");
        lifecycle.transition(id, new LifecycleTransitionRequest(LifecycleAction.SUBMIT, "owner", "go"),
                "APP_OWNER", null);
        JsonNode out = ok(id, body("{\"technology\":\"NGINX\",\"environment\":\"uat\"}", "same values"));
        assertThat(out.get("lifecycleState").asText()).isEqualTo("SUBMITTED");
        assertThat(metadataEvents(id)).isZero();
    }

    @Test
    void anUnknownIdIs404() throws Exception {
        assertThat(edit(UUID.randomUUID(), "APP", body("{\"technology\":\"x\"}", "r")).getResponse().getStatus())
                .isEqualTo(404);
    }
}
