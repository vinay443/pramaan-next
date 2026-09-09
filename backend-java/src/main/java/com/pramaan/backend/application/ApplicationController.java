package com.pramaan.backend.application;

import com.pramaan.backend.application.ApplicationDtos.ApplicationView;
import com.pramaan.backend.application.ApplicationDtos.UpsertRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApplicationView> list() {
        return service.list();
    }

    @GetMapping("/{slug}")
    public ApplicationView get(@PathVariable String slug) {
        return service.get(slug);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationView create(@Valid @RequestBody UpsertRequest req) {
        return service.upsert(req);
    }

    @PutMapping("/{slug}")
    public ApplicationView update(@PathVariable String slug, @Valid @RequestBody UpsertRequest req) {
        if (!slug.equals(req.slug())) {
            throw com.pramaan.backend.common.ApiException.badRequest("slug in path and body must match");
        }
        return service.upsert(req);
    }

    /** Deboard: stop the scheduler treating this app as onboarded. Row + evidence retained. */
    @PostMapping("/{slug}/deboard")
    public ApplicationView deboard(@PathVariable String slug) {
        return service.setActive(slug, false);
    }

    /** Re-onboard a previously deboarded app. */
    @PostMapping("/{slug}/onboard")
    public ApplicationView onboard(@PathVariable String slug) {
        return service.setActive(slug, true);
    }
}
