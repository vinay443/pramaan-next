package com.pramaan.backend.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Arrays;
import java.util.List;

/** The single settings row (id = 1). Defaults apply while no row has been saved. */
@Entity
@Table(name = "notification_settings")
public class NotificationSettings {

    static final int ID = 1;

    @Id
    private int id = ID;

    private boolean enabled = true;

    @Column(name = "notify_on_success")
    private boolean notifyOnSuccess = true;

    @Column(name = "notify_on_failure")
    private boolean notifyOnFailure = true;

    @Column(name = "recipient_roles", nullable = false, length = 200)
    private String recipientRoles = "APP,ADMIN";

    @Column(name = "email_simulated")
    private boolean emailSimulated = true;

    @Version
    private Long version;

    public NotificationSettings() {}

    public Long getVersion() { return version; }

    public void apply(boolean enabled, boolean notifyOnSuccess, boolean notifyOnFailure,
                      List<String> roles, boolean emailSimulated) {
        this.enabled = enabled;
        this.notifyOnSuccess = notifyOnSuccess;
        this.notifyOnFailure = notifyOnFailure;
        this.recipientRoles = String.join(",", roles);
        this.emailSimulated = emailSimulated;
    }

    public boolean isEnabled() { return enabled; }
    public boolean isNotifyOnSuccess() { return notifyOnSuccess; }
    public boolean isNotifyOnFailure() { return notifyOnFailure; }
    public boolean isEmailSimulated() { return emailSimulated; }

    public List<String> getRecipientRoles() {
        return recipientRoles.isBlank() ? List.of() : Arrays.asList(recipientRoles.split(","));
    }
}
