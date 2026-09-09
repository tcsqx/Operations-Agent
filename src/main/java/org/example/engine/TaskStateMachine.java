package org.example.engine;

import org.example.entity.TaskEntity;
import org.example.model.enums.TaskStatus;
import org.example.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TaskStateMachine {

    private static final Logger log = LoggerFactory.getLogger(TaskStateMachine.class);

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(TaskStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(TaskStatus.CREATED, EnumSet.of(TaskStatus.PLANNING, TaskStatus.RUNNING, TaskStatus.WAITING_APPROVAL, TaskStatus.FAILED, TaskStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(TaskStatus.PLANNING, EnumSet.of(TaskStatus.RUNNING, TaskStatus.WAITING_APPROVAL, TaskStatus.DIAGNOSING, TaskStatus.FAILED, TaskStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(TaskStatus.RUNNING, EnumSet.of(TaskStatus.WAITING_APPROVAL, TaskStatus.DIAGNOSING, TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.TIMEOUT, TaskStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(TaskStatus.WAITING_APPROVAL, EnumSet.of(TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.TIMEOUT, TaskStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(TaskStatus.DIAGNOSING, EnumSet.of(TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(TaskStatus.SUCCESS, Collections.emptySet());
        ALLOWED_TRANSITIONS.put(TaskStatus.FAILED, Collections.emptySet());
        ALLOWED_TRANSITIONS.put(TaskStatus.TIMEOUT, Collections.emptySet());
        ALLOWED_TRANSITIONS.put(TaskStatus.CANCELLED, Collections.emptySet());
    }

    private final TaskRepository taskRepository;

    public TaskStateMachine(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public boolean canTransition(TaskStatus from, TaskStatus to) {
        if (from == null || to == null) {
            return false;
        }
        Set<TaskStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public synchronized TaskEntity transition(String taskId, TaskStatus targetStatus, String reason) {
        TaskEntity task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        TaskStatus currentStatus = task.getStatus();

        if (currentStatus == targetStatus) {
            return task;
        }

        if (!canTransition(currentStatus, targetStatus)) {
            String msg = String.format("Illegal state transition from %s to %s for task %s (reason: %s)",
                currentStatus, targetStatus, taskId, reason);
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        log.info("[TaskStateMachine] Task {} transitioned: {} -> {} (reason: {})",
            taskId, currentStatus, targetStatus, reason);

        task.setStatus(targetStatus);
        if (targetStatus == TaskStatus.FAILED && reason != null) {
            task.setErrorMsg(reason);
        }
        return taskRepository.save(task);
    }
}
