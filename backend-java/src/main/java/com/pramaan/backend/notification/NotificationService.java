package com.pramaan.backend.notification;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.notification.Notification.Channel;
import com.pramaan.backend.notification.Notification.Severity;
import com.pramaan.backend.notification.Notification.Type;
import com.pramaan.backend.scheduler.SchedulerAccessPolicy;
import com.pramaan.backend.security.SecurityEventService;
import com.pramaan.backend.scheduler.SchedulerDtos.RunView;
import com.pramaan.backend.scheduler.domain.SchedulerRun;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Run notifications. In-app rows are the real alerts; the EMAIL_SIMULATED channel only writes a delivery
 * record (created already read) - no SMTP or broker, by design.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** {@code version} is optional on PUT: when given, a stale value is rejected with 409 CONFLICT. */
    public record SettingsView(boolean enabled, boolean notifyOnSuccess, boolean notifyOnFailure,
                               List<String> recipientRoles, boolean emailSimulated, Long version) {
        public SettingsView(boolean enabled, boolean notifyOnSuccess, boolean notifyOnFailure,
                            List<String> recipientRoles, boolean emailSimulated) {
            this(enabled, notifyOnSuccess, notifyOnFailure, recipientRoles, emailSimulated, null);
        }
    }

    public record NotificationView(String id, Instant createdAt, String type, String severity, String title,
                                   String message, String runId, String scheduleName, String recipientRole,
                                   String channel, Instant readAt) {
        static NotificationView from(Notification n) {
            return new NotificationView(n.getId().toString(), n.getCreatedAt(), n.getType().name(),
                    n.getSeverity().name(), n.getTitle(), n.getMessage(),
                    n.getRunId() == null ? null : n.getRunId().toString(), n.getScheduleName(),
                    n.getRecipientRole(), n.getChannel().name(), n.getReadAt());
        }
    }

    public record NotificationList(List<NotificationView> items, long unreadCount) {}

    private final NotificationRepository notifications;
    private final NotificationSettingsRepository settingsRepo;
    private final Clock clock;

    private final SecurityEventService security;

    public NotificationService(NotificationRepository notifications, NotificationSettingsRepository settingsRepo,
                               Clock clock, SecurityEventService security) {
        this.security = security;
        this.notifications = notifications;
        this.settingsRepo = settingsRepo;
        this.clock = clock;
    }

    // ---- creation (called once a run has reached a terminal status) ----------------------------------

    /** Best effort and idempotent per (run, role, channel): never fails the run, never duplicates. */
    public void onRunFinished(SchedulerRun run) {
        try {
            NotificationSettings s = settings();
            Type type = switch (run.getStatus()) {
                case COMPLETED -> Type.RUN_COMPLETED;
                case PARTIAL -> Type.RUN_PARTIAL;
                case FAILED -> Type.RUN_FAILED;
                default -> null;
            };
            if (type == null || !s.isEnabled()) return;
            if (type == Type.RUN_COMPLETED ? !s.isNotifyOnSuccess() : !s.isNotifyOnFailure()) return;

            Severity severity = type == Type.RUN_COMPLETED ? Severity.INFO
                    : type == Type.RUN_PARTIAL ? Severity.WARNING : Severity.ERROR;
            String what = run.getScheduleName() != null ? run.getScheduleName() : "manual run";
            String title = switch (type) {
                case RUN_COMPLETED -> "Run completed: " + what;
                case RUN_PARTIAL -> "Run partially failed: " + what;
                case RUN_FAILED -> "Run failed: " + what;
            };
            String message = message(run);
            Instant now = clock.instant();
            for (String role : s.getRecipientRoles()) {
                save(new Notification(type, severity, title, message, run.getId(), run.getScheduleName(), role,
                        Channel.IN_APP, now, null));
                if (s.isEmailSimulated()) {
                    // simulated delivery record: "sent" immediately, so it never counts as unread
                    save(new Notification(type, severity, title, message, run.getId(), run.getScheduleName(), role,
                            Channel.EMAIL_SIMULATED, now, now));
                }
            }
        } catch (RuntimeException ex) {
            log.warn("could not create notifications for run {}: {}", run.getId(), ex.getMessage());
        }
    }

    private void save(Notification n) {
        if (!notifications.existsByRunIdAndRecipientRoleAndChannel(n.getRunId(), n.getRecipientRole(), n.getChannel())) {
            try {
                notifications.save(n);
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // a concurrent finalisation of the same run won the unique (run, role, channel) slot: already notified
            }
        }
    }

    private static String message(SchedulerRun run) {
        StringBuilder sb = new StringBuilder("Run ").append(run.getId()).append(" (").append(run.getTrigger())
                .append(run.getScheduleName() != null ? ", schedule " + run.getScheduleName() : "")
                .append("): received=").append(run.getReceived()).append(" ingested=").append(run.getIngested())
                .append(" duplicates=").append(run.getDuplicates()).append(" failed=").append(run.getFailed());
        var errors = RunView.from(run, 0).sourceErrors();
        if (!errors.isEmpty()) {
            sb.append(". ").append(errors.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue().errorCode() + " - " + e.getValue().errorMessage())
                    .collect(Collectors.joining("; ")));
        }
        return sb.length() > 2000 ? sb.substring(0, 2000) : sb.toString();
    }

    // ---- queries (scoped to the caller role) ----------------------------------------------------------

    public NotificationList list(String role, boolean unreadOnly) {
        var rows = unreadOnly ? notifications.findByRecipientRoleAndReadAtIsNullOrderByCreatedAtDesc(role)
                : notifications.findByRecipientRoleOrderByCreatedAtDesc(role);
        return new NotificationList(rows.stream().map(NotificationView::from).toList(),
                notifications.countByRecipientRoleAndReadAtIsNull(role));
    }

    public NotificationView markRead(UUID id, String role) {
        Notification n = notifications.findById(id).filter(x -> x.getRecipientRole().equals(role))
                .orElseThrow(() -> ApiException.notFound("unknown notification: " + id));
        n.markRead(clock.instant());
        return NotificationView.from(notifications.save(n));
    }

    public NotificationList markAllRead(String role) {
        Instant now = clock.instant();
        var unread = notifications.findByRecipientRoleAndReadAtIsNullOrderByCreatedAtDesc(role);
        unread.forEach(n -> n.markRead(now));
        notifications.saveAll(unread);
        return list(role, false);
    }

    // ---- settings -------------------------------------------------------------------------------------

    private NotificationSettings settings() {
        return settingsRepo.findById(NotificationSettings.ID).orElseGet(NotificationSettings::new);
    }

    public SettingsView getSettings() {
        return view(settings());
    }

    public SettingsView saveSettings(SettingsView req) {
        if (req == null || req.recipientRoles() == null) throw ApiException.badRequest("recipientRoles is required");
        security.requireSafeItems("recipientRoles", req.recipientRoles());
        List<String> roles = req.recipientRoles().stream().map(String::trim).distinct().toList();
        List<String> bad = roles.stream().filter(r -> !SchedulerAccessPolicy.KNOWN_ROLES.contains(r)).toList();
        if (!bad.isEmpty()) {
            throw ApiException.badRequest("unknown recipient roles " + bad + " (known: "
                    + SchedulerAccessPolicy.KNOWN_ROLES.stream().sorted().toList() + ")");
        }
        for (int attempt = 1; ; attempt++) {
            NotificationSettings s = settingsRepo.findById(NotificationSettings.ID).orElseGet(NotificationSettings::new);
            if (req.version() != null && s.getVersion() != null && !req.version().equals(s.getVersion())) {
                throw new org.springframework.orm.ObjectOptimisticLockingFailureException(NotificationSettings.class, 1);
            }
            s.apply(req.enabled(), req.notifyOnSuccess(), req.notifyOnFailure(), roles, req.emailSimulated());
            try {
                return view(settingsRepo.save(s));
            } catch (org.springframework.dao.OptimisticLockingFailureException
                     | org.springframework.dao.DataIntegrityViolationException e) {
                // two users saving at once (first save creates the row): re-apply on the fresh row, unless the
                // caller pinned a version - then the conflict is theirs to resolve.
                if (req.version() != null || attempt >= 3) throw e;
            }
        }
    }

    private static SettingsView view(NotificationSettings s) {
        return new SettingsView(s.isEnabled(), s.isNotifyOnSuccess(), s.isNotifyOnFailure(),
                s.getRecipientRoles(), s.isEmailSimulated(), s.getVersion());
    }
}
