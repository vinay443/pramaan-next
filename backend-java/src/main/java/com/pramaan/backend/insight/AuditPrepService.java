package com.pramaan.backend.insight;

import com.pramaan.backend.ai.ChatModel;
import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.evidence.EvidenceQueryService;
import com.pramaan.backend.evidence.EvidenceQueryService.EvidenceFilter;
import com.pramaan.backend.evidence.domain.EvidenceLifecycleState;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.insight.InsightDtos.AuditPrepReport;
import com.pramaan.backend.insight.InsightDtos.CompletenessReport;
import com.pramaan.backend.insight.InsightDtos.ComplianceReport;
import com.pramaan.backend.insight.InsightDtos.ControlCoverage;
import com.pramaan.backend.insight.InsightDtos.ControlPosture;
import com.pramaan.backend.insight.InsightDtos.Coverage;
import com.pramaan.backend.insight.InsightDtos.ControlStatus;
import com.pramaan.backend.insight.InsightDtos.PrepFinding;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC18 — AI-assisted audit preparation. The checklist is 100% deterministic: it is
 * assembled from the existing completeness / compliance results and the evidence
 * lifecycle state (persisted data only). The chat model only narrates the
 * already-computed checklist and is deterministic under {@code pramaan.ai.mode=mock}.
 * No evidence is fabricated — every finding names a real (application, control).
 */
@Service
@Transactional(readOnly = true)
public class AuditPrepService {

    private final ApplicationService applications;
    private final CompletenessService completeness;
    private final ComplianceService compliance;
    private final EvidenceQueryService evidence;
    private final ChatModel chat;
    private final Clock clock;

    public AuditPrepService(ApplicationService applications, CompletenessService completeness,
                            ComplianceService compliance, EvidenceQueryService evidence,
                            ChatModel chat, Clock clock) {
        this.applications = applications;
        this.completeness = completeness;
        this.compliance = compliance;
        this.evidence = evidence;
        this.chat = chat;
        this.clock = clock;
    }

    public AuditPrepReport prepare(String applicationSlug, String framework) {
        List<String> apps = applicationSlug == null || applicationSlug.isBlank()
                ? applications.list().stream().map(a -> a.slug()).toList()
                : List.of(applicationSlug.trim());

        List<PrepFinding> findings = new ArrayList<>();
        double pctSum = 0;
        for (String slug : apps) {
            applications.require(slug);
            CompletenessReport cp = completeness.forApplication(slug, framework);
            ComplianceReport cr = compliance.forApplication(slug, framework);
            pctSum += (cp.completenessPct() + cr.compliancePct()) / 2.0;

            for (ControlCoverage c : cp.controls()) {
                if (c.coverage() == Coverage.MISSING) {
                    findings.add(new PrepFinding("EVIDENCE_GAP", "HIGH", slug, c.framework(), c.controlId(),
                            "no current evidence held", "collect evidence for this control"));
                } else if (c.coverage() == Coverage.STALE) {
                    findings.add(new PrepFinding("EVIDENCE_STALE", "MEDIUM", slug, c.framework(), c.controlId(),
                            "evidence is past the freshness window", "refresh the evidence"));
                }
            }
            for (ControlPosture c : cr.controls()) {
                if (c.status() == ControlStatus.NON_COMPLIANT) {
                    findings.add(new PrepFinding("NON_COMPLIANT", "HIGH", slug, c.framework(), c.controlId(),
                            c.detail(), "remediate the failing check then re-collect evidence"));
                } else if (c.status() == ControlStatus.PARTIALLY_COMPLIANT) {
                    findings.add(new PrepFinding("PARTIAL", "MEDIUM", slug, c.framework(), c.controlId(),
                            c.detail(), "resolve the warning"));
                } else if (c.status() == ControlStatus.NOT_ASSESSED) {
                    findings.add(new PrepFinding("NOT_ASSESSED", "LOW", slug, c.framework(), c.controlId(),
                            c.detail(), "run rule evaluation for this control"));
                }
            }
            for (EvidenceRecord r : evidence.recordsMatching(new EvidenceFilter(slug, framework, null, null,
                    null, null, null, 0, 100_000))) {
                EvidenceLifecycleState s = r.getLifecycleState();
                if (s != EvidenceLifecycleState.APPROVED && s != EvidenceLifecycleState.SUPERSEDED) {
                    findings.add(new PrepFinding("UNAPPROVED_EVIDENCE", "MEDIUM", slug, r.getFramework(),
                            r.getControlId(), "evidence is in state " + s + " (not approved)",
                            "review and approve before the audit"));
                }
            }
        }

        Map<String, Integer> bySeverity = new LinkedHashMap<>(Map.of("HIGH", 0, "MEDIUM", 0, "LOW", 0));
        findings.forEach(f -> bySeverity.merge(f.severity(), 1, Integer::sum));
        double readiness = apps.isEmpty() ? 0.0
                : Math.round((pctSum / apps.size()) * 10.0) / 10.0;

        String narrative = narrate(apps, framework, readiness, bySeverity, findings);
        return new AuditPrepReport(clock.instant(),
                applicationSlug == null || applicationSlug.isBlank() ? "portfolio" : applicationSlug,
                readiness, findings.size(), bySeverity, findings, narrative, chat.name(),
                chat.deterministic());
    }

    private String narrate(List<String> apps, String framework, double readiness,
                           Map<String, Integer> bySeverity, List<PrepFinding> findings) {
        String system = "You are an audit-preparation assistant. Summarise ONLY the checklist below. "
                + "Do not invent controls, evidence or numbers.";
        StringBuilder user = new StringBuilder();
        user.append("Scope: ").append(apps).append(framework == null ? "" : " / " + framework).append('\n');
        user.append("Readiness score: ").append(readiness).append('\n');
        user.append("Findings by severity: ").append(bySeverity).append('\n');
        findings.stream().limit(20).forEach(f -> user.append("- [").append(f.severity()).append("] ")
                .append(f.applicationSlug()).append(' ').append(f.framework()).append('/')
                .append(f.controlId()).append(": ").append(f.detail()).append(" -> ")
                .append(f.action()).append('\n'));
        user.append("\nWrite a short readiness summary and the top 3 priorities.");
        return chat.complete(system, user.toString());
    }
}
