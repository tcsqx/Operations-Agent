package org.example.repository;

import org.example.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findByTaskIdOrderByCreatedAtDesc(String taskId);
    List<AuditLogEntity> findAllByOrderByCreatedAtDesc();
}
