package org.example.entity;

import jakarta.persistence.*;
import org.example.model.enums.ApprovalStatus;
import org.example.model.enums.RiskLevel;

import java.time.LocalDateTime;

@Entity
@Table(name = "ops_hitl_approvals", indexes = {
    @Index(name = "idx_hitl_task_id", columnList = "task_id"),
    @Index(name = "idx_hitl_status", columnList = "status")
})
public class HitlApprovalEntity {

    @Id
    @Column(name = "approval_id", length = 64, nullable = false, unique = true)
    private String approvalId;

    @Column(name = "task_id", length = 64, nullable = false)
    private String taskId;

    @Column(name = "step_index")
    private Integer stepIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 32, nullable = false)
    private RiskLevel riskLevel;

    @Column(name = "command_or_tool", length = 255, nullable = false)
    private String commandOrTool;

    @Lob
    @Column(name = "parameters", columnDefinition = "CLOB")
    private String parameters;

    @Lob
    @Column(name = "diff_impact", columnDefinition = "CLOB")
    private String diffImpact;

    @Lob
    @Column(name = "rollback_plan", columnDefinition = "CLOB")
    private String rollbackPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private ApprovalStatus status;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Column(name = "comment", length = 512)
    private String comment;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "expire_at", nullable = false)
    private LocalDateTime expireAt;

    @Column(name = "acted_at")
    private LocalDateTime actedAt;

    public HitlApprovalEntity() {
    }

    public HitlApprovalEntity(String approvalId, String taskId, Integer stepIndex, RiskLevel riskLevel, String commandOrTool, String parameters, String diffImpact, String rollbackPlan, int timeoutMinutes) {
        this.approvalId = approvalId;
        this.taskId = taskId;
        this.stepIndex = stepIndex;
        this.riskLevel = riskLevel;
        this.commandOrTool = commandOrTool;
        this.parameters = parameters;
        this.diffImpact = diffImpact;
        this.rollbackPlan = rollbackPlan;
        this.status = ApprovalStatus.PENDING;
        this.requestedAt = LocalDateTime.now();
        this.expireAt = LocalDateTime.now().plusMinutes(timeoutMinutes > 0 ? timeoutMinutes : 10);
    }

    @PrePersist
    public void prePersist() {
        if (this.requestedAt == null) {
            this.requestedAt = LocalDateTime.now();
        }
        if (this.expireAt == null) {
            this.expireAt = LocalDateTime.now().plusMinutes(10);
        }
        if (this.status == null) {
            this.status = ApprovalStatus.PENDING;
        }
    }

    public String getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(String approvalId) {
        this.approvalId = approvalId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Integer getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(Integer stepIndex) {
        this.stepIndex = stepIndex;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getCommandOrTool() {
        return commandOrTool;
    }

    public void setCommandOrTool(String commandOrTool) {
        this.commandOrTool = commandOrTool;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public String getDiffImpact() {
        return diffImpact;
    }

    public void setDiffImpact(String diffImpact) {
        this.diffImpact = diffImpact;
    }

    public String getRollbackPlan() {
        return rollbackPlan;
    }

    public void setRollbackPlan(String rollbackPlan) {
        this.rollbackPlan = rollbackPlan;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public void setStatus(ApprovalStatus status) {
        this.status = status;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public LocalDateTime getActedAt() {
        return actedAt;
    }

    public void setActedAt(LocalDateTime actedAt) {
        this.actedAt = actedAt;
    }
}
