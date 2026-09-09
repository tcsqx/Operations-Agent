package org.example.entity;

import jakarta.persistence.*;
import org.example.model.enums.RiskLevel;

import java.time.LocalDateTime;

@Entity
@Table(name = "ops_audit_logs", indexes = {
    @Index(name = "idx_audit_task_id", columnList = "task_id"),
    @Index(name = "idx_audit_created_at", columnList = "created_at")
})
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", length = 64)
    private String taskId;

    @Column(name = "operator", length = 64)
    private String operator;

    @Column(name = "action", length = 64, nullable = false)
    private String action;

    @Column(name = "tool_name", length = 64)
    private String toolName;

    @Lob
    @Column(name = "parameters", columnDefinition = "CLOB")
    private String parameters;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 32)
    private RiskLevel riskLevel;

    @Column(name = "approved_by", length = 64)
    private String approvedBy;

    @Lob
    @Column(name = "execution_result", columnDefinition = "CLOB")
    private String executionResult;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AuditLogEntity() {
    }

    public AuditLogEntity(String taskId, String operator, String action, String toolName, String parameters, RiskLevel riskLevel, String approvedBy, String executionResult, String ipAddress) {
        this.taskId = taskId;
        this.operator = operator;
        this.action = action;
        this.toolName = toolName;
        this.parameters = parameters;
        this.riskLevel = riskLevel;
        this.approvedBy = approvedBy;
        this.executionResult = executionResult;
        this.ipAddress = ipAddress;
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getExecutionResult() {
        return executionResult;
    }

    public void setExecutionResult(String executionResult) {
        this.executionResult = executionResult;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
