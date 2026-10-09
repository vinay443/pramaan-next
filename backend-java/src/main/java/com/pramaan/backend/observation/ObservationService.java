package com.pramaan.backend.observation;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.evidence.domain.EvidenceRecord;
import com.pramaan.backend.evidence.repo.EvidenceRecordRepository;
import com.pramaan.backend.observation.AuditObservation.Status;
import com.pramaan.backend.security.SecurityEventService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ObservationService {

    static final int MAX_TEXT = 1000;

    public record ObservationView(String id, String evidenceId, String controlId, String framework,
                                  String application, String text, String status, String raisedByRole,
                                  String raisedBy, Instant createdAt, Instant closedAt) {
        static ObservationView from(AuditObservation o) {
            return new ObservationView(o.getId().toString(), o.getEvidenceId().toString(), o.getControlId(),
                    o.getFramework(), o.getApplication(), o.getText(), o.getStatus().name(),
                    o.getRaisedByRole(), o.getRaisedBy(), o.getCreatedAt(), o.getClosedAt());
        }
    }

    public record CreateObservationRequest(String text) {}

    private final AuditObservationRepository observations;
    private final EvidenceRecordRepository evidence;
    private final SecurityEventService security;
    private final Clock clock;

    public ObservationService(AuditObservationRepository observations, EvidenceRecordRepository evidence,
                              SecurityEventService security, Clock clock) {
        this.observations = observations;
        this.evidence = evidence;
        this.security = security;
        this.clock = clock;
    }

    @Transactional
    public ObservationView create(UUID evidenceId, CreateObservationRequest req, String role, String user) {
        String text = security.requireSafe("text", req == null ? null : req.text(), MAX_TEXT);
        EvidenceRecord e = evidence.findById(evidenceId)
                .orElseThrow(() -> ApiException.notFound("Unknown evidence: " + evidenceId));
        // control / framework / application are copied from the evidence so the observation stays attributable
        return ObservationView.from(observations.save(new AuditObservation(e.getId(), e.getControlId(),
                e.getFramework(), e.getApplicationSlug(), text, role,
                user == null || user.isBlank() ? "anonymous" : user.trim(), clock.instant())));
    }

    @Transactional
    public ObservationView close(UUID id) {
        AuditObservation o = observations.findById(id)
                .orElseThrow(() -> ApiException.notFound("Unknown observation: " + id));
        o.close(clock.instant());
        return ObservationView.from(observations.save(o));
    }

    @Transactional(readOnly = true)
    public List<ObservationView> forEvidence(UUID evidenceId) {
        if (!evidence.existsById(evidenceId)) throw ApiException.notFound("Unknown evidence: " + evidenceId);
        return observations.findByEvidenceIdOrderByCreatedAtDesc(evidenceId).stream().map(ObservationView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ObservationView> search(String application, String framework, String control, String status) {
        Status st = null;
        if (status != null && !status.isBlank()) {
            try {
                st = Status.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw ApiException.badRequest("status must be one of [OPEN, CLOSED]");
            }
        }
        final Status wanted = st;
        return observations.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> blank(application) || o.getApplication().equalsIgnoreCase(application.trim()))
                .filter(o -> blank(framework) || o.getFramework().equalsIgnoreCase(framework.trim()))
                .filter(o -> blank(control) || o.getControlId().equalsIgnoreCase(control.trim()))
                .filter(o -> wanted == null || o.getStatus() == wanted)
                .map(ObservationView::from).toList();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
