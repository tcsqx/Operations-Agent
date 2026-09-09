package org.example.entity;

import jakarta.persistence.*;
import org.example.model.enums.RiskLevel;
import org.example.model.enums.TaskStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "ops_tasks", indexes = {
    @Index(name = "idx_task_status", columnList = "status"),
    @Index(name = "idx_task_created_at", columnList = "created_at")
})
public class TaskEntity {

    @Id
    @Column(name = "task_id", length = 64, nullable = false, unique = true)
    private String taskId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "intent", length = 128)
    private String intent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 32)
    private RiskLevel riskLevel;

    @Lob
    @Column(name = "raw_prompt", columnDefinition = "CLOB")
    private String rawPrompt;

    @Lob
    @Column(name = "plan_json", columnDefinition = "CLOB")
    private String planJson;

    @Lob
    @Column(name = "diagnosis_report", columnDefinition = "CLOB")
    private String diagnosisReport;

    @Column(name = "error_msg", length = 1024)
    private String errorMsg;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public TaskEntity() {
    }

    public TaskEntity(String taskId, String title, String intent, TaskStatus status, RiskLevel riskLevel, String rawPrompt) {
        this.taskId = taskId;
        this.title = title;
        this.intent = intent;
        this.status = status;
        this.riskLevel = riskLevel;
        this.rawPrompt = rawPrompt;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getRawPrompt() {
        return rawPrompt;
    }

    public void setRawPrompt(String rawPrompt) {
        this.rawPrompt = rawPrompt;
    }

    public String getPlanJson() {
        return planJson;
    }

    public void setPlanJson(String planJson) {
        this.planJson = planJson;
    }

    public String getDiagnosisReport() {
        return diagnosisReport;
    }

    public void setDiagnosisReport(String diagnosisReport) {
        this.diagnosisReport = diagnosisReport;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
