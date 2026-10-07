package org.example.engine;

import io.milvus.client.MilvusServiceClient;
import org.example.entity.HitlApprovalEntity;
import org.example.entity.TaskEntity;
import org.example.model.enums.ApprovalStatus;
import org.example.model.enums.RiskLevel;
import org.example.model.enums.TaskStatus;
import org.example.repository.HitlApprovalRepository;
import org.example.repository.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class HitlApprovalFlowTest {

    @MockBean
    private MilvusServiceClient milvusClient;

    @Autowired
    private OpsPilotAgentEngine agentEngine;

    @Autowired
    private HitlApprovalManager approvalManager;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private HitlApprovalRepository approvalRepository;

    @Test
    @DisplayName("HITL: Request approval should pause task, and approve should resume task")
    void testApprovalPauseAndResume() {
        // 1. Create task
        TaskEntity task = agentEngine.createTask("重启关键生产服务", "SERVICE_RESTART");

        // 2. Request HITL approval
        HitlApprovalEntity ticket = approvalManager.requestApproval(
            task.getTaskId(), 1, RiskLevel.HIGH, "service_restart",
            "{\"service\":\"nginx\"}", "Service downtime ~5s", "Rollback via backup config", 10
        );

        assertNotNull(ticket.getApprovalId());
        assertEquals(ApprovalStatus.PENDING, ticket.getStatus());

        // Check task is paused in WAITING_APPROVAL
        TaskEntity pausedTask = taskRepository.findById(task.getTaskId()).orElseThrow();
        assertEquals(TaskStatus.WAITING_APPROVAL, pausedTask.getStatus());

        // 3. Admin approves ticket
        HitlApprovalEntity approvedTicket = approvalManager.approve(ticket.getApprovalId(), "sre-lead", "Approved after window verification");
        assertEquals(ApprovalStatus.APPROVED, approvedTicket.getStatus());
        assertEquals("sre-lead", approvedTicket.getApprovedBy());

        // Check task transitioned back to RUNNING
        TaskEntity runningTask = taskRepository.findById(task.getTaskId()).orElseThrow();
        assertEquals(TaskStatus.RUNNING, runningTask.getStatus());
    }

    @Test
    @DisplayName("HITL: Rejecting ticket should cancel task")
    void testApprovalRejectCancelsTask() {
        TaskEntity task = agentEngine.createTask("删除日志临时目录", "CLEANUP");

        HitlApprovalEntity ticket = approvalManager.requestApproval(
            task.getTaskId(), 1, RiskLevel.HIGH, "cleanup_dir",
            "{\"path\":\"/tmp/logs\"}", "Risk of removing active logs", "None", 10
        );

        HitlApprovalEntity rejected = approvalManager.reject(ticket.getApprovalId(), "sec-admin", "Prohibited during business hours");
        assertEquals(ApprovalStatus.REJECTED, rejected.getStatus());

        TaskEntity cancelledTask = taskRepository.findById(task.getTaskId()).orElseThrow();
        assertEquals(TaskStatus.CANCELLED, cancelledTask.getStatus());
    }

    @Test
    @DisplayName("HITL E2E: High-risk task should pause at WAITING_APPROVAL and complete to SUCCESS after approve + resumeAfterApproval")
    void testFullEngineHitlPauseApproveAndResumeToSuccess() {
        // 1. Create a task that includes a high-risk restart directive
        TaskEntity task = agentEngine.createTask("检查 CPU 负载并执行 restart service:nginx", "REMEDIATION");

        // 2. Execute task -> should execute low-risk probes and pause at high-risk step
        TaskEntity paused = agentEngine.executeTask(task.getTaskId());
        assertEquals(TaskStatus.WAITING_APPROVAL, paused.getStatus());

        // 3. Find pending approval ticket for this task
        HitlApprovalEntity pendingTicket = approvalManager.getPendingApprovals().stream()
                .filter(a -> task.getTaskId().equals(a.getTaskId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected pending HITL ticket for task " + task.getTaskId()));

        // 4. Approve ticket (transitions WAITING_APPROVAL -> RUNNING) and resume execution
        HitlApprovalEntity approved = approvalManager.approve(pendingTicket.getApprovalId(), "oncall-sre", "Approved remediation");
        assertEquals(ApprovalStatus.APPROVED, approved.getStatus());

        TaskEntity completed = agentEngine.resumeAfterApproval(task.getTaskId(), approved.getApprovalId());
        assertEquals(TaskStatus.SUCCESS, completed.getStatus());
        assertNotNull(completed.getDiagnosisReport());
        assertFalse(completed.getDiagnosisReport().isBlank());
    }
}
