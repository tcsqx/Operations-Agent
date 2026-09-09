package org.example.entity;

import jakarta.persistence.*;
import org.example.model.enums.StepStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "ops_task_steps", indexes = {
    @Index(name = "idx_step_task_id", columnList = "task_id")
})
public class TaskStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", length = 64, nullable = false)
    private String taskId;

    @Column(name = "step_index", nullable = false)
    private Integer stepIndex;

    @Column(name = "step_name", length = 128)
    private String stepName;

    @Column(name = "tool_name", length = 64)
    private String toolName;

    @Lob
    @Column(name = "tool_params", columnDefinition = "CLOB")
    private String toolParams;

    @Lob
    @Column(name = "tool_output", columnDefinition = "CLOB")
    private String toolOutput;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private StepStatus status;

    @Column(name = "cost_ms")
    private Long costMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public TaskStepEntity() {
    }

    public TaskStepEntity(String taskId, Integer stepIndex, String stepName, String toolName, String toolParams) {
        this.taskId = taskId;
        this.stepIndex = stepIndex;
        this.stepName = stepName;
        this.toolName = toolName;
        this.toolParams = toolParams;
        this.status = StepStatus.PENDING;
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

    public Integer getStepIndex() {
        return stepIndex;
    }

    public void setStepIndex(Integer stepIndex) {
        this.stepIndex = stepIndex;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolParams() {
        return toolParams;
    }

    public void setToolParams(String toolParams) {
        this.toolParams = toolParams;
    }

    public String getToolOutput() {
        return toolOutput;
    }

    public void setToolOutput(String toolOutput) {
        this.toolOutput = toolOutput;
    }

    public StepStatus getStatus() {
        return status;
    }

    public void setStatus(StepStatus status) {
        this.status = status;
    }

    public Long getCostMs() {
        return costMs;
    }

    public void setCostMs(Long costMs) {
        this.costMs = costMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
