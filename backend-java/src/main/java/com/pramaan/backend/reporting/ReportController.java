package com.pramaan.backend.reporting;

import com.pramaan.backend.reporting.ReportService.ReportInfo;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** UC17 — automated regulatory reporting. Views over existing insight/evidence data as JSON or CSV. */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping
    public List<ReportInfo> catalog() {
        return reports.catalog();
    }

    @GetMapping("/{name}")
    public ResponseEntity<?> report(@PathVariable String name,
                                    @RequestParam(defaultValue = "json") String format,
                                    @RequestParam(required = false) String applicationSlug,
                                    @RequestParam(required = false) String framework) {
        if ("csv".equalsIgnoreCase(format)) {
            String body = reports.csv(name, applicationSlug, framework);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header("Content-Disposition", ContentDisposition.attachment()
                            .filename(reports.filename(name, "csv")).build().toString())
                    .body(body);
        }
        return ResponseEntity.ok(reports.json(name, applicationSlug, framework));
    }
}
