package com.pramaan.backend.appowner.repo;

import com.pramaan.backend.appowner.domain.ApplicationOwner;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationOwnerRepository extends JpaRepository<ApplicationOwner, UUID> {

    List<ApplicationOwner> findByUsernameOrderByApplicationSlugAsc(String username);

    long countByApplicationSlugAndUsername(String applicationSlug, String username);
}
