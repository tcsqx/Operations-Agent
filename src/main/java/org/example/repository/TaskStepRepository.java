package org.example.repository;

import org.example.entity.TaskStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskStepRepository extends JpaRepository<TaskStepEntity, Long> {
    List<TaskStepEntity> findByTaskIdOrderByStepIndexAsc(String taskId);
    void deleteByTaskId(String taskId);
}
