package com.pramaan.backend.observation;

import com.pramaan.backend.observation.ObservationService.CreateObservationRequest;
import com.pramaan.backend.observation.ObservationService.ObservationView;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Roles (AUDITOR / ADMIN write, all view) and id hygiene are enforced by SchedulerAccessPolicy. */
@RestController
public class ObservationController {

    private final ObservationService service;

    public ObservationController(ObservationService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/evidence/{evidenceId}/observations")
    @ResponseStatus(HttpStatus.CREATED)
    public ObservationView create(@PathVariable UUID evidenceId, @RequestBody(required = false) CreateObservationRequest req,
                                  @RequestHeader("X-Pramaan-Role") String role,
                                  @RequestHeader(value = "X-Pramaan-User", required = false) String user) {
        return service.create(evidenceId, req, role.trim(), user);
    }

    @GetMapping("/api/v1/evidence/{evidenceId}/observations")
    public List<ObservationView> forEvidence(@PathVariable UUID evidenceId) {
        return service.forEvidence(evidenceId);
    }

    @PostMapping("/api/v1/observations/{id}/close")
    public ObservationView close(@PathVariable UUID id) {
        return service.close(id);
    }

    @GetMapping("/api/v1/observations")
    public List<ObservationView> search(@RequestParam(required = false) String application,
                                        @RequestParam(required = false) String framework,
                                        @RequestParam(required = false) String control,
                                        @RequestParam(required = false) String status) {
        return service.search(application, framework, control, status);
    }
}
