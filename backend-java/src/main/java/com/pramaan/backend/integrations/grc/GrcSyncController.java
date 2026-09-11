package com.pramaan.backend.integrations.grc;

import com.pramaan.backend.integrations.grc.GrcDtos.GrcSyncStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grc")
public class GrcSyncController {

    private final GrcSyncService service;

    public GrcSyncController(GrcSyncService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public GrcSyncStatus status() {
        return service.status();
    }

    @PostMapping("/sync")
    public GrcSyncStatus sync() {
        return service.sync();
    }
}
