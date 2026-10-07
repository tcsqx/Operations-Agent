package org.example.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.engine.HitlApprovalManager;
import org.example.entity.HitlApprovalEntity;
import org.example.entity.TaskStepEntity;
import org.example.model.enums.RiskLevel;
import org.example.model.enums.StepStatus;
import org.example.repository.TaskStepRepository;
import org.example.security.AuditLogger;
import org.example.security.RiskClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, BaseTool> toolMap = new HashMap<>();
    private final AuditLogger auditLogger;
    private final RiskClassifier riskClassifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private TaskStepRepository stepRepository;

    @Autowired(required = false)
    private HitlApprovalManager approvalManager;

    private final InheritableThreadLocal<String> threadTaskId = new InheritableThreadLocal<>();
    private volatile String currentActiveTaskId;

    @Autowired
    public ToolRegistry(List<BaseTool> tools, AuditLogger auditLogger, RiskClassifier riskClassifier) {
        this.auditLogger = auditLogger;
        this.riskClassifier = riskClassifier;
        for (BaseTool tool : tools) {
            toolMap.put(tool.getName().toLowerCase(), tool);
            log.info("[ToolRegistry] Registered ops tool: {} ({})", tool.getName(), tool.getRiskLevel());
        }
    }

    public void bindActiveTask(String taskId) {
        threadTaskId.set(taskId);
        this.currentActiveTaskId = taskId;
    }

    public void clearActiveTask() {
        threadTaskId.remove();
        this.currentActiveTaskId = null;
    }

    public String getActiveTaskId() {
        String id = threadTaskId.get();
        return id != null ? id : currentActiveTaskId;
    }

    public BaseTool getTool(String name) {
        if (name == null) return null;
        return toolMap.get(name.toLowerCase());
    }

    public boolean hasTool(String name) {
        return name != null && toolMap.containsKey(name.toLowerCase());
    }

    public Collection<BaseTool> getAllTools() {
        return Collections.unmodifiableCollection(toolMap.values());
    }

    /**
     * Invoked by Spring AI @Tool methods during ReactAgent / SupervisorAgent execution.
     * Automatically binds to the active task (if any), checks HITL risk, records TaskStepEntity, and logs audit trail.
     */
    public ToolResult executeFromAgent(String toolName, String stepTitle, Map<String, Object> params) {
        String taskId = getActiveTaskId();
        String effectiveTaskId = taskId != null ? taskId : "chat-session";
        Map<String, Object> safeParams = params != null ? params : Collections.emptyMap();

        String paramsJson = "{}";
        try {
            paramsJson = objectMapper.writeValueAsString(safeParams);
        } catch (Exception ignored) {
            paramsJson = safeParams.toString();
        }

        RiskLevel classifiedRisk = riskClassifier.classify(toolName, paramsJson);
        BaseTool tool = getTool(toolName);
        if (tool != null && tool.getRiskLevel().ordinal() > classifiedRisk.ordinal()) {
            classifiedRisk = tool.getRiskLevel();
        }

        // Record step if executing within an Ops Task
        TaskStepEntity stepEntity = null;
        int nextIndex = 1;
        if (taskId != null && stepRepository != null) {
            List<TaskStepEntity> existing = stepRepository.findByTaskIdOrderByStepIndexAsc(taskId);
            nextIndex = existing.size() + 1;
            stepEntity = new TaskStepEntity(taskId, nextIndex, stepTitle != null ? stepTitle : ("Invoke " + toolName), toolName, paramsJson);
            stepEntity = stepRepository.save(stepEntity);
        }

        // Intercept HIGH / CRITICAL risk operations for HITL approval if inside a managed task
        if (taskId != null && approvalManager != null && (classifiedRisk == RiskLevel.HIGH || classifiedRisk == RiskLevel.CRITICAL)) {
            HitlApprovalEntity ticket = approvalManager.requestApproval(
                taskId,
                nextIndex,
                classifiedRisk,
                toolName,
                paramsJson,
                "LLM Agent requested high-impact operation: " + stepTitle,
                "Verify service health or rollback if needed",
                10
            );
            String msg = String.format("HITL_APPROVAL_REQUIRED: High-risk operation '%s' (%s) has been intercepted and paused for human approval. Ticket ID: %s. Do not retry this command until approved.",
                toolName, classifiedRisk, ticket.getApprovalId());
            return ToolResult.failure(msg, 0);
        }

        if (stepEntity != null) {
            stepEntity.setStatus(StepStatus.RUNNING);
            stepRepository.save(stepEntity);
        }

        ToolResult result = executeTool(effectiveTaskId, toolName, safeParams, "llm-agent");

        if (stepEntity != null) {
            stepEntity.setCostMs(result.getCostMs());
            if (result.isSuccess()) {
                stepEntity.setStatus(StepStatus.SUCCESS);
                stepEntity.setToolOutput(result.getOutput());
            } else {
                stepEntity.setStatus(StepStatus.FAILED);
                stepEntity.setToolOutput("ERROR: " + result.getError());
            }
            stepRepository.save(stepEntity);
        }

        return result;
    }

    /**
     * Executes a tool with auditing and risk classification.
     */
    public ToolResult executeTool(String taskId, String toolName, Map<String, Object> params, String operator) {
        BaseTool tool = getTool(toolName);
        if (tool == null) {
            String err = "Tool not found: " + toolName;
            log.warn(err);
            return ToolResult.failure(err, 0);
        }

        String paramsStr = params != null ? params.toString() : "{}";
        RiskLevel risk = tool.getRiskLevel();
        RiskLevel dynamicRisk = riskClassifier.classify(toolName, paramsStr);
        if (dynamicRisk.ordinal() > risk.ordinal()) {
            risk = dynamicRisk;
        }

        log.info("[ToolRegistry] Executing tool {} for task {} (risk: {})", toolName, taskId, risk);
        ToolResult result;
        try {
            result = tool.execute(params);
        } catch (Exception e) {
            log.error("[ToolRegistry] Error executing tool {}: {}", toolName, e.getMessage(), e);
            result = ToolResult.failure("Internal error: " + e.getMessage(), 0);
        }

        // Audit log
        auditLogger.logAction(
            taskId != null ? taskId : "chat-session",
            operator != null ? operator : "ops-agent",
            "TOOL_EXECUTE",
            toolName,
            paramsStr,
            risk,
            null,
            result.isSuccess() ? result.getOutput() : result.getError(),
            "127.0.0.1"
        );

        return result;
    }

    /**
     * Returns a formatted schema description of all tools for LLM prompts.
     */
    public String getToolsPromptSchema() {
        StringBuilder sb = new StringBuilder();
        for (BaseTool tool : toolMap.values()) {
            sb.append(String.format("- **%s** (Risk: %s): %s\n",
                tool.getName(), tool.getRiskLevel(), tool.getDescription()));
        }
        return sb.toString();
    }
}
