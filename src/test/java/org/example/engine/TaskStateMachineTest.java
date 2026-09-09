package org.example.engine;

import org.example.entity.TaskEntity;
import org.example.model.enums.RiskLevel;
import org.example.model.enums.TaskStatus;
import org.example.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TaskStateMachineTest {

    private TaskRepository taskRepository;
    private TaskStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        taskRepository = Mockito.mock(TaskRepository.class);
        stateMachine = new TaskStateMachine(taskRepository);
    }

    @Test
    @DisplayName("Should validate legal state transitions")
    void testLegalTransitions() {
        assertTrue(stateMachine.canTransition(TaskStatus.CREATED, TaskStatus.PLANNING));
        assertTrue(stateMachine.canTransition(TaskStatus.PLANNING, TaskStatus.RUNNING));
        assertTrue(stateMachine.canTransition(TaskStatus.RUNNING, TaskStatus.WAITING_APPROVAL));
        assertTrue(stateMachine.canTransition(TaskStatus.WAITING_APPROVAL, TaskStatus.RUNNING));
        assertTrue(stateMachine.canTransition(TaskStatus.RUNNING, TaskStatus.DIAGNOSING));
        assertTrue(stateMachine.canTransition(TaskStatus.DIAGNOSING, TaskStatus.SUCCESS));
    }

    @Test
    @DisplayName("Should reject illegal state transitions")
    void testIllegalTransitions() {
        assertFalse(stateMachine.canTransition(TaskStatus.CREATED, TaskStatus.SUCCESS));
        assertFalse(stateMachine.canTransition(TaskStatus.SUCCESS, TaskStatus.RUNNING));
        assertFalse(stateMachine.canTransition(TaskStatus.FAILED, TaskStatus.PLANNING));
    }

    @Test
    @DisplayName("Should throw IllegalStateException when illegal transition occurs")
    void testTransitionThrowsOnIllegal() {
        TaskEntity task = new TaskEntity("t-001", "Test", "DIAG", TaskStatus.CREATED, RiskLevel.LOW, "prompt");
        when(taskRepository.findById("t-001")).thenReturn(Optional.of(task));

        assertThrows(IllegalStateException.class, () -> {
            stateMachine.transition("t-001", TaskStatus.SUCCESS, "Direct complete");
        });
    }

    @Test
    @DisplayName("Should update task state and persist on legal transition")
    void testSuccessfulTransition() {
        TaskEntity task = new TaskEntity("t-002", "Test", "DIAG", TaskStatus.CREATED, RiskLevel.LOW, "prompt");
        when(taskRepository.findById("t-002")).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TaskEntity updated = stateMachine.transition("t-002", TaskStatus.PLANNING, "Starting plan");
        assertEquals(TaskStatus.PLANNING, updated.getStatus());
    }
}
