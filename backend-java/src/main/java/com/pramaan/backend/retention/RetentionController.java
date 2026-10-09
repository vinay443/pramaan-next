package com.pramaan.backend.retention;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.retention.RetentionPolicyService.PolicyView;
import com.pramaan.backend.retention.RetentionService.ArchiveResult;
import com.pramaan.backend.retention.RetentionService.RunView;
import com.pramaan.backend.security.SecurityEvent.Type;
import com.pramaan.backend.security.SecurityEventService;
import java.util.List;
import java.util.Map;
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

    public RetentionController(RetentionPolicyService policy, RetentionService retention, SecurityEventService security) {
        this.policy = policy;
        this.retention = retention;
        this.security = security;
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

    @PostMapping("/archive")
    public ArchiveResult archive(@RequestBody(required = false) Map<String, Object> body,
                                 @RequestHeader(value = "X-Pramaan-User", required = false) String user) {
        Object mode = body == null ? null : body.get("mode");
        if (!"PREVIEW".equals(mode) && !"EXECUTE".equals(mode)) {
            security.record(Type.INPUT_REJECTED, "mode must be PREVIEW or EXECUTE, got: " + mode);
            throw ApiException.badRequest("mode must be PREVIEW or EXECUTE");
        }
        Object app = body.get("application");
        String application = null;
        if (app != null && !app.toString().isBlank()) {
            application = security.requireSafe("application", app.toString());
        }
        return retention.archive((String) mode, application, user);
    }

    @GetMapping("/runs")
    public List<RunView> runs(@RequestParam(required = false) Integer limit) {
        return retention.runs(limit);
    }
}
