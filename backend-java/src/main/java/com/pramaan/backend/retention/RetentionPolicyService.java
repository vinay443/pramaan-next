package com.pramaan.backend.retention;

import com.pramaan.backend.common.ApiException;
import com.pramaan.backend.config.PramaanProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * The retention window in force. Everything that shows or applies retention (lifecycle expiry, dashboard,
 * archival) reads {@link #days()} so a policy change takes effect everywhere at once.
 */
@Service
public class RetentionPolicyService {

    public static final int MAX_DAYS = 3650;

    public record PolicyView(int retentionDays, String updatedBy, Instant updatedAt, Long version) {}

    private final RetentionPolicyRepository policies;
    private final PramaanProperties props;
    private final Clock clock;

    public RetentionPolicyService(RetentionPolicyRepository policies, PramaanProperties props, Clock clock) {
        this.policies = policies;
        this.props = props;
        this.clock = clock;
    }

    /** The configured default until a policy has been saved. */
    public int days() {
        return policies.findById(RetentionPolicy.ID).map(RetentionPolicy::getRetentionDays)
                .orElseGet(() -> props.evidenceOrDefault().retentionDaysOrDefault());
    }

    public PolicyView get() {
        return policies.findById(RetentionPolicy.ID)
                .map(p -> new PolicyView(p.getRetentionDays(), p.getUpdatedBy(), p.getUpdatedAt(), p.getVersion()))
                .orElseGet(() -> new PolicyView(props.evidenceOrDefault().retentionDaysOrDefault(), null, null, null));
    }

    /** {@code expectedVersion}, when given and a policy row exists, must match or the answer is a 409. */
    public PolicyView update(int retentionDays, Long expectedVersion, String user) {
        if (retentionDays < 0 || retentionDays > MAX_DAYS) {
            throw ApiException.badRequest("retentionDays must be between 0 and " + MAX_DAYS);
        }
        RetentionPolicy p = policies.findById(RetentionPolicy.ID).orElse(null);
        String by = user == null || user.isBlank() ? "anonymous" : user.trim();
        if (p == null) {
            p = new RetentionPolicy(retentionDays, by, clock.instant());
        } else {
            if (expectedVersion != null && !expectedVersion.equals(p.getVersion())) {
                throw new ObjectOptimisticLockingFailureException(RetentionPolicy.class, RetentionPolicy.ID);
            }
            p.update(retentionDays, by, clock.instant());
        }
        RetentionPolicy saved = policies.save(p);
        return new PolicyView(saved.getRetentionDays(), saved.getUpdatedBy(), saved.getUpdatedAt(), saved.getVersion());
    }
}
