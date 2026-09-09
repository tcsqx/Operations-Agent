package org.example.tools;

import org.example.model.enums.RiskLevel;
import org.example.security.AuditLogger;
import org.example.security.RiskClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, BaseTool> toolMap = new HashMap<>();
    private final AuditLogger auditLogger;
    private final RiskClassifier riskClassifier;

    public ToolRegistry(List<BaseTool> tools, AuditLogger auditLogger, RiskClassifier riskClassifier) {
        this.auditLogger = auditLogger;
        this.riskClassifier = riskClassifier;
        for (BaseTool tool : tools) {
            toolMap.put(tool.getName().toLowerCase(), tool);
            log.info("[ToolRegistry] Registered ops tool: {} ({})", tool.getName(), tool.getRiskLevel());
        }
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
     * Executes a tool with auditing and risk classification.
     */
    public ToolResult executeTool(String taskId, String toolName, Map<String, Object> params, String operator) {
        BaseTool tool = getTool(toolName);
        if (tool == null) {
            String err = "Tool not found: " + toolName;
            log.warn(err);
            return ToolResult.failure(err, 0);
        }

        RiskLevel risk = tool.getRiskLevel();
        String paramsStr = params != null ? params.toString() : "{}";

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
            taskId,
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
