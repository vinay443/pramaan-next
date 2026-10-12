package com.pramaan.backend.integrations;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.integrations.SourceConnection.Status;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SourceConnectionService {

    private static final Logger log = LoggerFactory.getLogger(SourceConnectionService.class);

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

    /**
     * INVALID is a transient credential failure: after a run has recorded the failure, connectivity is
     * simulated as restored so the next run succeeds with no manual repair. DISABLED is never touched.
     * Returns true when the source was INVALID and is now VALID.
     */
    public boolean restoreAfterAuthFailure(String sourceSystem) {
        SourceConnection c = connections.findById(key(sourceSystem)).orElse(null);
        if (c == null || c.getStatus() != Status.INVALID) {
            return false;
        }
        c.update(Status.VALID, "system:auto-restore", clock.instant());
        connections.save(c);
        log.info("source {} credential was INVALID; connectivity restored automatically for the next run",
                c.getSourceSystem());
        return true;
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
