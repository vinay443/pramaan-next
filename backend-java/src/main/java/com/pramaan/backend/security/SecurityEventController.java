package com.pramaan.backend.security;

import com.pramaan.backend.security.SecurityEventService.EventView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Blocked-request audit trail. Access (ADMIN, AUDITOR only) is enforced by SchedulerAccessPolicy. */
@RestController
@RequestMapping("/api/v1/security")
public class SecurityEventController {

    private final SecurityEventService service;

    public SecurityEventController(SecurityEventService service) {
        this.service = service;
    }

    @GetMapping("/events")
    public List<EventView> events(@RequestParam(required = false) String type,
                                  @RequestParam(required = false) Integer limit) {
        return service.list(type, limit);
    }
}
