package org.example.repository;

import org.example.entity.TaskEntity;
import org.example.model.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<TaskEntity, String> {
    List<TaskEntity> findByStatusOrderByCreatedAtDesc(TaskStatus status);
    List<TaskEntity> findAllByOrderByCreatedAtDesc();
}
