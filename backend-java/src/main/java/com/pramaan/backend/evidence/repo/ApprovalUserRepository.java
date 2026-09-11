package com.pramaan.backend.evidence.repo;

import com.pramaan.backend.evidence.domain.ApprovalUser;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalUserRepository extends JpaRepository<ApprovalUser, String> {

    List<ApprovalUser> findAllByOrderByUsernameAsc();
}
