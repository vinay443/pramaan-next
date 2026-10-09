package com.pramaan.backend.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientRoleOrderByCreatedAtDesc(String recipientRole);

    List<Notification> findByRecipientRoleAndReadAtIsNullOrderByCreatedAtDesc(String recipientRole);

    long countByRecipientRoleAndReadAtIsNull(String recipientRole);

    boolean existsByRunIdAndRecipientRoleAndChannel(UUID runId, String recipientRole, Notification.Channel channel);
}
