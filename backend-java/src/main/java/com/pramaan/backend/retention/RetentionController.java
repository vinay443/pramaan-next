package com.pramaan.backend.retention;

import com.pramaan.backend.application.ApplicationService;
import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.retention.RetentionPolicyService.PolicyView;
import com.pramaan.backend.retention.RetentionService.ArchiveResult;
import com.pramaan.backend.retention.RetentionService.RunView;
import com.pramaan.backend.security.SecurityEvent.Type;
import com.pramaan.backend.security.SecurityEventService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reads are open to every known role; changing the policy and archiving are ADMIN only (SchedulerAccessPolicy). */
@RestController
@RequestMapping("/api/v1/retention")
public class RetentionController {

    private final RetentionPolicyService policy;
    private final RetentionService retention;
    private final SecurityEventService security;
    private final ApplicationService applications;

    /** The whole archive contract: anything else is rejected, never silently ignored. */
    static final Set<String> ARCHIVE_KEYS = Set.of("mode", "application", "applicationSlug", "allApplications");

    public RetentionController(RetentionPolicyService policy, RetentionService retention, SecurityEventService security,
                               ApplicationService applications) {
        this.policy = policy;
        this.retention = retention;
        this.security = security;
        this.applications = applications;
    }

    @GetMapping("/policy")
    public PolicyView policy() {
        return policy.get();
    }

    /** The body is read as a map so a non-integer value is a clean 400 + INPUT_REJECTED, not a parse error. */
    @PutMapping("/policy")
    public PolicyView update(@RequestBody(required = false) Map<String, Object> body,
                             @RequestHeader(value = "X-Pramaan-User", required = false) String user) {
        Object days = body == null ? null : body.get("retentionDays");
        if (!(days instanceof Integer d) || d < 0 || d > RetentionPolicyService.MAX_DAYS) {
            security.record(Type.INPUT_REJECTED, "retentionDays must be an integer 0-3650, got: " + days);
            throw ApiException.badRequest("retentionDays must be an integer between 0 and " + RetentionPolicyService.MAX_DAYS);
        }
        Object version = body.get("version");
        if (version != null && !(version instanceof Number)) {
            security.record(Type.INPUT_REJECTED, "version must be a number, got: " + version);
            throw ApiException.badRequest("version must be a number");
        }
        return policy.update(d, version == null ? null : ((Number) version).longValue(), user);
    }

    /**
     * Body keys: {@code mode} (PREVIEW | EXECUTE), {@code application} (alias {@code applicationSlug}) and
     * {@code allApplications}. Unknown keys are a 400: a mistyped scope field must never widen the run to every record.
     * EXECUTE needs an explicit scope - a known application, or {@code allApplications: true}; PREVIEW may be unscoped
     * and keeps returning zero eligible items for an unknown application.
     */
    @PostMapping("/archive")
    public ArchiveResult archive(@RequestBody(required = false) Map<String, Object> body,
                                 @RequestHeader(value = "X-Pramaan-User", required = false) String user) {
        if (body != null) {
            for (String key : body.keySet()) {
                if (!ARCHIVE_KEYS.contains(key)) {
                    security.record(Type.INPUT_REJECTED, "unknown field '" + key + "' in retention archive request");
                    throw ApiException.badRequest("unknown field '" + key + "'; allowed: mode, application, "
                            + "applicationSlug, allApplications");
                }
            }
        }
        Object mode = body == null ? null : body.get("mode");
        if (!"PREVIEW".equals(mode) && !"EXECUTE".equals(mode)) {
            security.record(Type.INPUT_REJECTED, "mode must be PREVIEW or EXECUTE, got: " + mode);
            throw ApiException.badRequest("mode must be PREVIEW or EXECUTE");
        }
        String application = scopeOf(body.get("application"));
        String slug = scopeOf(body.get("applicationSlug"));
        if (application != null && slug != null && !application.equals(slug)) {
            security.record(Type.INPUT_REJECTED, "application and applicationSlug differ in retention archive request");
            throw ApiException.badRequest("application and applicationSlug must match when both are given");
        }
        if (application == null) {
            application = slug;
        }
        Object all = body.get("allApplications");
        if (all != null && !(all instanceof Boolean)) {
            security.record(Type.INPUT_REJECTED, "allApplications must be true or false, got: " + all);
            throw ApiException.badRequest("allApplications must be true or false");
        }
        boolean allApplications = Boolean.TRUE.equals(all);
        if (allApplications && application != null) {
            throw ApiException.badRequest("allApplications cannot be combined with an application");
        }
        if ("EXECUTE".equals(mode)) {
            if (application == null && !allApplications) {
                security.record(Type.INPUT_REJECTED, "EXECUTE without a scope (application or allApplications:true)");
                throw ApiException.badRequest("EXECUTE needs a scope: an application, or allApplications:true");
            }
            if (application != null && !applications.exists(application)) {
                security.record(Type.INPUT_REJECTED, "EXECUTE for an unknown application: " + application);
                throw ApiException.badRequest("unknown application '" + application + "'");
            }
        }
        return retention.archive((String) mode, application, user);
    }

    private String scopeOf(Object value) {
        return value == null || value.toString().isBlank() ? null : security.requireSafe("application", value.toString());
    }

    @GetMapping("/runs")
    public List<RunView> runs(@RequestParam(required = false) Integer limit) {
        return retention.runs(limit);
    }
}
