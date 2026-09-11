package com.pramaan.backend.evidence;

import com.pramaan.backend.common.PageResponse;
import com.pramaan.backend.evidence.EvidenceDtos.BulkIngestResponse;
import com.pramaan.backend.evidence.EvidenceDtos.DeterministicQueryResult;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceDashboard;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceLifecycleView;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceVersionView;
import com.pramaan.backend.evidence.EvidenceDtos.LifecycleTransitionRequest;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceView;
import com.pramaan.backend.evidence.EvidenceDtos.IngestRequest;
import com.pramaan.backend.evidence.EvidenceDtos.IngestResult;
import com.pramaan.backend.evidence.EvidenceDtos.IntegrityReport;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/evidence")
public class EvidenceController {

    private final EvidenceIngestionService ingestion;
    private final EvidenceQueryService queries;
    private final EvidenceLifecycleService lifecycle;

    public EvidenceController(EvidenceIngestionService ingestion, EvidenceQueryService queries,
                              EvidenceLifecycleService lifecycle) {
        this.ingestion = ingestion;
        this.queries = queries;
        this.lifecycle = lifecycle;
    }

    // ---- ingestion ----------------------------------------------------------

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.CREATED)
    public IngestResult ingest(@Valid @RequestBody IngestRequest req) {
        return ingestion.ingest(req);
    }

    /**
     * Bulk ingest with documented partial-success semantics: a malformed item is
     * reported as a per-item failure while the rest of the batch is still processed.
     * Deliberately no bean-validation cascade ({@code @Valid}) on the list elements —
     * that fails the whole request at the web layer before the per-item loop runs.
     * Each item is validated inside {@link EvidenceIngestionService#ingestBulk} via
     * the same {@code requireField} checks as a single ingest, with failures collected
     * into the response's {@code errors} list.
     */
    @PostMapping("/bulk")
    public BulkIngestResponse bulk(@RequestBody List<IngestRequest> items) {
        return ingestion.ingestBulk(items);
    }

    /** Bulk file upload: each file becomes one evidence item under the given app/framework/control. */
    @PostMapping(path = "/bulk/upload", consumes = "multipart/form-data")
    public BulkIngestResponse bulkUpload(
            @RequestParam String applicationSlug,
            @RequestParam String framework,
            @RequestParam String controlId,
            @RequestParam(defaultValue = "BULK_UPLOAD") String sourceSystem,
            @RequestParam(required = false) String collectedBy,
            @RequestParam(required = false) String technology,
            @RequestParam("files") List<MultipartFile> files) {
        List<IngestRequest> requests = new ArrayList<>();
        Map<String, String> tags = technology == null || technology.isBlank()
                ? Map.of("ingest.channel", "bulk-upload")
                : Map.of("ingest.channel", "bulk-upload", "technology", technology.trim());
        for (MultipartFile file : files) {
            try {
                requests.add(new IngestRequest(applicationSlug, controlId, framework, sourceSystem,
                        file.getOriginalFilename(), file.getOriginalFilename(), file.getContentType(),
                        Base64.getEncoder().encodeToString(file.getBytes()), null,
                        Instant.now(), collectedBy,
                        Map.of("upload.filename", String.valueOf(file.getOriginalFilename())),
                        tags));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return ingestion.ingestBulk(requests);
    }

    // ---- repository reads --------------------------------------------------

    /** Use Case 4 — consolidated evidence dashboard + repository-wide hash integrity. */
    @GetMapping("/dashboard")
    public EvidenceDashboard dashboard() {
        return queries.dashboard();
    }

    @GetMapping
    public PageResponse<EvidenceView> search(
            @RequestParam(required = false) String applicationSlug,
            @RequestParam(required = false) String framework,
            @RequestParam(required = false) String controlId,
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) String collectedAfter,
            @RequestParam(required = false) String collectedBefore,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) String collectionMethod,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return queries.search(new EvidenceFilter(applicationSlug, framework, controlId, sourceSystem,
                parseInstant(collectedAfter), parseInstant(collectedBefore), tag, page, size),
                new EvidenceQueryService.TagFacets(technology, collectionMethod));
    }

    @GetMapping("/{id}")
    public EvidenceView get(@PathVariable UUID id) {
        return queries.get(id);
    }

    @GetMapping("/{id}/versions")
    public List<EvidenceVersionView> versions(@PathVariable UUID id) {
        return queries.versions(id);
    }

    @GetMapping("/{id}/versions/{version}")
    public EvidenceVersionView version(@PathVariable UUID id, @PathVariable int version) {
        return queries.version(id, version);
    }

    // ---- Use Case 13 — lifecycle -----------------------------------------

    @GetMapping("/{id}/lifecycle")
    public EvidenceLifecycleView lifecycle(@PathVariable UUID id) {
        return lifecycle.get(id);
    }

    @PostMapping("/{id}/lifecycle")
    public EvidenceLifecycleView transition(@PathVariable UUID id,
                                            @RequestBody LifecycleTransitionRequest req) {
        return lifecycle.transition(id, req);
    }

    @GetMapping("/{id}/verify")
    public IntegrityReport verify(@PathVariable UUID id,
                                  @RequestParam(required = false) Integer version) {
        return queries.verify(id, version);
    }

    // ---- UC03 — associate held evidence with an additional framework (reuse) ----

    @PostMapping("/{id}/frameworks")
    public EvidenceView addFramework(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ingestion.addFrameworkMapping(id, body.get("framework"));
    }

    @GetMapping("/query/{name}")
    public DeterministicQueryResult query(
            @PathVariable String name,
            @RequestParam(required = false) String applicationSlug,
            @RequestParam(required = false) String framework,
            @RequestParam(required = false) String controlId,
            @RequestParam(required = false) String sourceSystem) {
        return queries.run(name, new EvidenceFilter(applicationSlug, framework, controlId, sourceSystem,
                null, null, null, 0, 5000));
    }

    private static Instant parseInstant(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(v.trim());
        } catch (RuntimeException e) {
            throw com.pramaan.backend.common.ApiException.badRequest(
                    "expected ISO-8601 instant (e.g. 2026-01-31T00:00:00Z), got: " + v);
        }
    }
}
