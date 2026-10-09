package com.pramaan.backend.integrations;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.integrations.SourceConnection.Status;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SourceConnectionService {

    public record ConnectionView(String sourceSystem, String status, Instant updatedAt, String updatedBy) {}

    public record SetConnectionRequest(String status, String updatedBy) {}

    private final SourceConnectionRepository connections;
    private final IntegrationRegistry integrations;
    private final Clock clock;

    public SourceConnectionService(SourceConnectionRepository connections, IntegrationRegistry integrations,
                                   Clock clock) {
        this.connections = connections;
        this.integrations = integrations;
        this.clock = clock;
    }

    /** Lazy default: a source with no stored row is VALID. */
    public Status statusOf(String sourceSystem) {
        return connections.findById(key(sourceSystem)).map(SourceConnection::getStatus).orElse(Status.VALID);
    }

    public List<ConnectionView> list() {
        return integrations.available().stream().map(this::view).toList();
    }

    public ConnectionView set(String sourceSystem, SetConnectionRequest req) {
        String source = key(sourceSystem);
        if (!integrations.available().contains(source)) {
            throw ApiException.badRequest("unknown source '" + sourceSystem + "' (available: "
                    + integrations.available() + ")");
        }
        Status status;
        try {
            status = Status.valueOf(req.status() == null ? "" : req.status().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("status must be one of " + Arrays.toString(Status.values()));
        }
        String by = req.updatedBy() == null || req.updatedBy().isBlank() ? "ui" : req.updatedBy().trim();
        SourceConnection c = connections.findById(source).orElse(null);
        if (c == null) {
            c = new SourceConnection(source, status, by, clock.instant());
        } else {
            c.update(status, by, clock.instant());
        }
        connections.save(c);
        return view(source);
    }

    private ConnectionView view(String source) {
        return connections.findById(source)
                .map(c -> new ConnectionView(source, c.getStatus().name(), c.getUpdatedAt(), c.getUpdatedBy()))
                .orElse(new ConnectionView(source, Status.VALID.name(), null, null));
    }

    private static String key(String s) {
        return s == null ? "" : s.trim().toUpperCase();
    }
}
