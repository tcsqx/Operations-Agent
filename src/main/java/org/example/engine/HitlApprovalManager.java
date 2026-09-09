package org.example.engine;

import org.example.entity.HitlApprovalEntity;
import org.example.entity.TaskEntity;
import org.example.model.enums.ApprovalStatus;
import org.example.model.enums.RiskLevel;
import org.example.model.enums.TaskStatus;
import org.example.repository.HitlApprovalRepository;
import org.example.repository.TaskRepository;
import org.example.security.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class HitlApprovalManager {

    private static final Logger log = LoggerFactory.getLogger(HitlApprovalManager.class);

    private final HitlApprovalRepository approvalRepository;
    private final TaskRepository taskRepository;
    private final TaskStateMachine stateMachine;
    private final AuditLogger auditLogger;

    public HitlApprovalManager(HitlApprovalRepository approvalRepository,
                               TaskRepository taskRepository,
                               TaskStateMachine stateMachine,
                               AuditLogger auditLogger) {
        this.approvalRepository = approvalRepository;
        this.taskRepository = taskRepository;
        this.stateMachine = stateMachine;
        this.auditLogger = auditLogger;
    }

    @Transactional
    public HitlApprovalEntity requestApproval(String taskId, Integer stepIndex, RiskLevel riskLevel,
                                             String commandOrTool, String parameters,
                                             String diffImpact, String rollbackPlan, int timeoutMinutes) {
        String approvalId = "appr-" + UUID.randomUUID().toString().substring(0, 8);
        HitlApprovalEntity approval = new HitlApprovalEntity(
            approvalId, taskId, stepIndex, riskLevel, commandOrTool, parameters,
            diffImpact, rollbackPlan, timeoutMinutes > 0 ? timeoutMinutes : 10
        );

        approvalRepository.save(approval);

        // Transition task to WAITING_APPROVAL
        stateMachine.transition(taskId, TaskStatus.WAITING_APPROVAL, "Waiting for human approval: " + commandOrTool);

        auditLogger.logAction(
            taskId,
            "system",
            "APPROVAL_REQUESTED",
            commandOrTool,
            parameters,
            riskLevel,
            null,
            "Approval ticket generated: " + approvalId,
            "127.0.0.1"
        );

        log.info("[HITL] Approval requested ticket: {} for task: {}, command: {}", approvalId, taskId, commandOrTool);
        return approval;
    }

    @Transactional
    public HitlApprovalEntity approve(String approvalId, String operator, String comment) {
        HitlApprovalEntity approval = approvalRepository.findById(approvalId)
            .orElseThrow(() -> new IllegalArgumentException("Approval ticket not found: " + approvalId));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Ticket " + approvalId + " is not pending (current: " + approval.getStatus() + ")");
        }

        if (LocalDateTime.now().isAfter(approval.getExpireAt())) {
            approval.setStatus(ApprovalStatus.EXPIRED);
            approvalRepository.save(approval);
            throw new IllegalStateException("Ticket " + approvalId + " has expired");
        }

        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setApprovedBy(operator);
        approval.setComment(comment);
        approval.setActedAt(LocalDateTime.now());
        approvalRepository.save(approval);

        // Transition task back to RUNNING
        stateMachine.transition(approval.getTaskId(), TaskStatus.RUNNING, "Approved by " + operator);

        auditLogger.logAction(
            approval.getTaskId(),
            operator,
            "APPROVAL_ACCEPTED",
            approval.getCommandOrTool(),
            approval.getParameters(),
            approval.getRiskLevel(),
            operator,
            "Approved: " + comment,
            "127.0.0.1"
        );

        log.info("[HITL] Ticket {} APPROVED by {}", approvalId, operator);
        return approval;
    }

    @Transactional
    public HitlApprovalEntity reject(String approvalId, String operator, String comment) {
        HitlApprovalEntity approval = approvalRepository.findById(approvalId)
            .orElseThrow(() -> new IllegalArgumentException("Approval ticket not found: " + approvalId));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Ticket " + approvalId + " is not pending");
        }

        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setApprovedBy(operator);
        approval.setComment(comment);
        approval.setActedAt(LocalDateTime.now());
        approvalRepository.save(approval);

        // Transition task to CANCELLED
        stateMachine.transition(approval.getTaskId(), TaskStatus.CANCELLED, "Rejected by " + operator + ": " + comment);

        auditLogger.logAction(
            approval.getTaskId(),
            operator,
            "APPROVAL_REJECTED",
            approval.getCommandOrTool(),
            approval.getParameters(),
            approval.getRiskLevel(),
            operator,
            "Rejected: " + comment,
            "127.0.0.1"
        );

        log.info("[HITL] Ticket {} REJECTED by {}", approvalId, operator);
        return approval;
    }

    public List<HitlApprovalEntity> getPendingApprovals() {
        return approvalRepository.findByStatusOrderByRequestedAtDesc(ApprovalStatus.PENDING);
    }

    public Optional<HitlApprovalEntity> getApproval(String approvalId) {
        return approvalRepository.findById(approvalId);
    }
}
