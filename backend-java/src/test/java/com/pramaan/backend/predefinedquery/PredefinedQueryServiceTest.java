package com.pramaan.backend.predefinedquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.application.OnboardingService;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceView;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.evidence.EvidenceQueryService.TagFacets;
import com.pramaan.backend.predefinedquery.PredefinedQueryDtos.QueryRunResult;
import com.pramaan.backend.predefinedquery.PredefinedQueryDtos.RunSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PredefinedQueryServiceTest {

    @Autowired PredefinedQueryService queries;
    @Autowired PredefinedQueryCatalog catalog;
    @Autowired EvidenceQueryService evidence;
    @Autowired OnboardingService onboarding;
    @Autowired ApplicationService applications;

    @BeforeEach
    void onboard() {
        onboarding.apply(false);
    }

    @Test
    void catalogueLoadsAllEntriesWithFacets() {
        assertThat(catalog.all()).hasSize(208);
        assertThat(queries.list(null, null, null).technologies()).contains("PostgreSQL", "Linux", "NGINX");
        assertThat(queries.list("PostgreSQL", null, null).items())
                .allMatch(i -> i.technology().equals("PostgreSQL"));
    }

    @Test
    void runningOneQueryProducesTaggedEvidenceViaTheCanonicalPath() {
        String controlId = catalog.filter("PostgreSQL", null, null).get(0).controlId();
        QueryRunResult r = queries.run(controlId, "payments");

        assertThat(r.outcome()).isIn("CREATED", "NEW_VERSION");
        assertThat(r.mode()).isEqualTo("SIMULATED");
        assertThat(r.evidenceId()).isNotBlank();

        var page = evidence.search(new EvidenceFilter("payments", null, null, "PREDEFINED_QUERY",
                null, null, null, 0, 50), new TagFacets(null, "predefined-query"));
        assertThat(page.totalItems()).isGreaterThan(0);
        EvidenceView ev = page.items().get(0);
        assertThat(ev.tags()).containsEntry("collectionMethod", "predefined-query");
        assertThat(ev.tags()).containsKeys("name", "evidenceType", "frameworks"); // UC03 applied
        assertThat(ev.sourceSystem()).isEqualTo("PREDEFINED_QUERY");
    }

    @Test
    void evidenceTagsCarryEveryFrameworkFromTheCatalogueEntry() {
        PredefinedQuery multi = catalog.all().stream()
                .filter(q -> q.frameworksOrEmpty().size() >= 2)
                .findFirst().orElseThrow();
        assertThat(multi.frameworksOrEmpty()).hasSizeGreaterThanOrEqualTo(2);

        queries.run(multi.controlId(), "payments");

        var page = evidence.search(new EvidenceFilter("payments", null, multi.controlId(),
                "PREDEFINED_QUERY", null, null, null, 0, 10), TagFacets.NONE);
        assertThat(page.totalItems()).isEqualTo(1);
        String frameworksTag = page.items().get(0).tags().get("frameworks");
        assertThat(frameworksTag.split(",")).hasSize(multi.frameworksOrEmpty().size());
        for (String fw : multi.frameworksOrEmpty()) {
            assertThat(frameworksTag).contains(fw);
        }
    }

    @Test
    void runAllFilteredBySubsetReturnsSchedulerShapedSummary() {
        RunSummary s = queries.runAll("NGINX", null, null, "net-banking");
        int nginx = catalog.filter("NGINX", null, null).size();
        assertThat(s.received()).isEqualTo(nginx);
        assertThat(s.ingested() + s.duplicates() + s.failed()).isEqualTo(nginx);
        assertThat(s.ingested()).isGreaterThan(0);
        assertThat(s.applicationSlug()).isEqualTo("net-banking");
    }

    @Test
    void namingADeboardedApplicationIsRejected() {
        applications.setActive("payments", false);
        assertThatThrownBy(() -> queries.run(catalog.all().get(0).controlId(), "payments"))
                .hasMessageContaining("is not onboarded");
    }
}
