package com.pramaan.backend.appowner.repo;

import com.pramaan.backend.appowner.domain.AppOwnerNotification;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppOwnerNotificationRepository extends JpaRepository<AppOwnerNotification, UUID> {

    List<AppOwnerNotification> findByUsernameOrderByCreatedAtDesc(String username);
}
