package org.example.repository;

import org.example.entity.HitlApprovalEntity;
import org.example.model.enums.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HitlApprovalRepository extends JpaRepository<HitlApprovalEntity, String> {
    Optional<HitlApprovalEntity> findByTaskIdAndStatus(String taskId, ApprovalStatus status);
    List<HitlApprovalEntity> findByStatusOrderByRequestedAtDesc(ApprovalStatus status);
    List<HitlApprovalEntity> findByStatusAndExpireAtBefore(ApprovalStatus status, LocalDateTime dateTime);
}
