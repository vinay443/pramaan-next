package com.pramaan.backend.insight;

import com.pramaan.backend.insight.DashboardKpiService.DashboardKpis;
import com.pramaan.backend.insight.DashboardKpiService.Filters;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** One filterable KPI snapshot for the App Owner dashboard (evidence + automated collection). */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardKpiController {

    private final DashboardKpiService service;

    public DashboardKpiController(DashboardKpiService service) {
        this.service = service;
    }

    @GetMapping("/kpis")
    public DashboardKpis kpis(@RequestParam(required = false) String application,
                              @RequestParam(required = false) String framework,
                              @RequestParam(required = false) String collectionMethod,
                              @RequestParam(required = false) String from,
                              @RequestParam(required = false) String to,
                              @RequestHeader(value = "X-Pramaan-Role", required = false) String role) {
        return service.kpis(new Filters(application, framework, collectionMethod,
                DashboardKpiService.parseInstant("from", from, false),
                DashboardKpiService.parseInstant("to", to, true)), role == null ? null : role.trim());
    }
}
