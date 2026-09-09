package com.pramaan.backend.insight;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.domain.EvidenceTag;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.insight.InsightDtos.EvidenceContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use Case 1 — the Evidence Detail context: for one evidence record, its source
 * technology, collection method, collection timestamp, the control(s) it
 * satisfies and the frameworks that reference those controls.
 */
@Service
@Transactional(readOnly = true)
public class EvidenceContextService {

    private final EvidenceRecordRepository records;
    private final ControlCatalog catalog;

    public EvidenceContextService(EvidenceRecordRepository records, ControlCatalog catalog) {
        this.records = records;
        this.catalog = catalog;
    }

    public EvidenceContext forEvidence(UUID id) {
        EvidenceRecord r = records.findById(id)
                .orElseThrow(() -> ApiException.notFound("Unknown evidence: " + id));

        Set<String> frameworks = new LinkedHashSet<>();
        if (r.getFramework() != null && !r.getFramework().isBlank()) {
            frameworks.add(r.getFramework());
        }
        frameworks.addAll(catalog.frameworksForControl(r.getControlId()));

        List<String> satisfies = new ArrayList<>();
        if (r.getControlId() != null && !r.getControlId().isBlank()) {
            satisfies.add(r.getControlId());
        }

        return new EvidenceContext(
                r.getId().toString(),
                r.getApplicationSlug(),
                r.getControlId(),
                tag(r, "technology", "unknown"),
                tag(r, "collectionMethod", "manual"),
                r.getSourceSystem(),
                r.getLatestCollectedAt(),
                r.getCurrentVersion(),
                satisfies,
                List.copyOf(frameworks));
    }

    private static String tag(EvidenceRecord r, String key, String fallback) {
        for (EvidenceTag t : r.getTags()) {
            if (key.equals(t.getTagKey()) && t.getTagValue() != null && !t.getTagValue().isBlank()) {
                return t.getTagValue();
            }
        }
        return fallback;
    }
}
