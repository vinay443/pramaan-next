package com.pramaan.backend.appowner;

import com.pramaan.backend.appowner.AppOwnerContext.AppOwnerIdentity;
import com.pramaan.backend.appowner.AppOwnerDtos.AppOwnerDashboard;
import com.pramaan.backend.appowner.AppOwnerDtos.CompliancePendingRow;
import com.pramaan.backend.appowner.AppOwnerDtos.NotificationView;
import com.pramaan.backend.appowner.AppOwnerDtos.ResubmitRequest;
import com.pramaan.backend.appowner.AppOwnerDtos.ShareTargetDateRequest;
import com.pramaan.backend.appowner.AppOwnerDtos.TargetDateHistoryView;
import com.pramaan.backend.appowner.AppOwnerDtos.UploadEvidenceRequest;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceLifecycleView;
import com.pramaan.backend.evidence.EvidenceDtos.EvidenceView;
import com.pramaan.backend.reporting.ReportService.ReportInfo;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Application Owner (APP) persona. Entirely new, additive surface — no existing
 * controller/route is touched. Every method resolves identity + owned-application
 * scope from {@code X-User-Role} / {@code X-User-Username} via {@link AppOwnerContext}
 * and rejects (403) anything outside that scope server-side, independent of what the
 * frontend happens to filter/display. There is deliberately no Approve/Reject/Admin
 * action anywhere on this controller.
 */
@RestController
@RequestMapping("/api/v1/app-owner")
public class AppOwnerController {

    private final AppOwnerContext context;
    private final AppOwnerService service;

    public AppOwnerController(AppOwnerContext context, AppOwnerService service) {
        this.context = context;
        this.service = service;
    }

    @GetMapping("/dashboard")
    public AppOwnerDashboard dashboard(@RequestHeader(value = "X-User-Role", required = false) String role,
                                       @RequestHeader(value = "X-User-Username", required = false) String username) {
        return service.dashboard(context.resolve(role, username));
    }

    @GetMapping("/compliance-pending")
    public List<CompliancePendingRow> compliancePending(
            @RequestHeader(value = "X-User-Role", required = false) String role, @RequestHeader(value = "X-User-Username", required = false) String username,
            @RequestParam(required = false) String complianceStatus,
            @RequestParam(required = false) String evidenceStatus,
            @RequestParam(required = false) String slaStatus,
            @RequestParam(required = false) String framework,
            @RequestParam(required = false, defaultValue = "lastUpdated") String sortBy) {
        return service.compliancePending(context.resolve(role, username), complianceStatus, evidenceStatus,
                slaStatus, framework, sortBy);
    }

    @GetMapping("/evidence/{id}")
    public EvidenceView evidenceDetail(@RequestHeader(value = "X-User-Role", required = false) String role,
                                       @RequestHeader(value = "X-User-Username", required = false) String username, @PathVariable UUID id) {
        return service.evidenceDetail(context.resolve(role, username), id);
    }

    @PostMapping("/evidence/upload")
    public EvidenceView upload(@RequestHeader(value = "X-User-Role", required = false) String role,
                               @RequestHeader(value = "X-User-Username", required = false) String username,
                               @RequestBody UploadEvidenceRequest req) {
        return service.uploadEvidence(context.resolve(role, username), req);
    }

    @PostMapping("/evidence/{id}/resubmit")
    public EvidenceLifecycleView resubmit(@RequestHeader(value = "X-User-Role", required = false) String role,
                                          @RequestHeader(value = "X-User-Username", required = false) String username,
                                          @PathVariable UUID id, @RequestBody(required = false) ResubmitRequest req) {
        String comment = req == null ? null : req.comment();
        return service.resubmitEvidence(context.resolve(role, username), id, comment);
    }

    @PostMapping("/evidence/{id}/target-date")
    public EvidenceView shareTargetDate(@RequestHeader(value = "X-User-Role", required = false) String role,
                                        @RequestHeader(value = "X-User-Username", required = false) String username,
                                        @PathVariable UUID id, @RequestBody ShareTargetDateRequest req) {
        return service.shareOrUpdateTargetDate(context.resolve(role, username), id, req.targetDate(), req.comment());
    }

    @GetMapping("/evidence/{id}/target-date/history")
    public List<TargetDateHistoryView> targetDateHistory(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                          @RequestHeader(value = "X-User-Username", required = false) String username,
                                                          @PathVariable UUID id) {
        return service.targetDateHistory(context.resolve(role, username), id);
    }

    /** View-only — no endpoint here ever marks a notification read or writes to it. */
    @GetMapping("/notifications")
    public List<NotificationView> notifications(@RequestHeader(value = "X-User-Role", required = false) String role,
                                                @RequestHeader(value = "X-User-Username", required = false) String username) {
        return service.notifications(context.resolve(role, username));
    }

    @GetMapping("/reports")
    public List<ReportInfo> reportCatalog(@RequestHeader(value = "X-User-Role", required = false) String role,
                                          @RequestHeader(value = "X-User-Username", required = false) String username) {
        context.resolve(role, username);
        return service.reportCatalog();
    }

    @GetMapping("/reports/{name}")
    public ResponseEntity<?> report(@RequestHeader(value = "X-User-Role", required = false) String role,
                                    @RequestHeader(value = "X-User-Username", required = false) String username,
                                    @PathVariable String name,
                                    @RequestParam(defaultValue = "json") String format,
                                    @RequestParam(required = false) String applicationSlug,
                                    @RequestParam(required = false) String framework) {
        AppOwnerIdentity identity = context.resolve(role, username);
        if ("csv".equalsIgnoreCase(format)) {
            String body = service.reportCsv(identity, name, applicationSlug, framework);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header("Content-Disposition",
                            ContentDisposition.attachment().filename(name + ".csv").build().toString())
                    .body(body);
        }
        if (!"json".equalsIgnoreCase(format)) {
            throw com.pramaan.backend.common.ApiException.badRequest("format must be 'json' or 'csv'");
        }
        return ResponseEntity.ok(service.reportJson(identity, name, applicationSlug, framework));
    }
}
