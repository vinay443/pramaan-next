package com.pramaan.backend.integrations;

import com.pramaan.backend.integrations.SourceConnectionService.ConnectionView;
import com.pramaan.backend.integrations.SourceConnectionService.SetConnectionRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sources")
public class SourceConnectionController {

    private final SourceConnectionService service;

    public SourceConnectionController(SourceConnectionService service) {
        this.service = service;
    }

    @GetMapping("/connections")
    public List<ConnectionView> list() {
        return service.list();
    }

    @PutMapping("/{sourceSystem}/connection")
    public ConnectionView set(@PathVariable String sourceSystem,
                              @RequestBody(required = false) SetConnectionRequest req) {
        return service.set(sourceSystem, req == null ? new SetConnectionRequest(null, null) : req);
    }
}
