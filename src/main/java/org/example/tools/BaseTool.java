package org.example.tools;

import org.example.model.enums.RiskLevel;

import java.util.Map;

public interface BaseTool {

    /**
     * Unique identifier for tool invocation.
     */
    String getName();

    /**
     * Human and LLM readable description of tool function and parameters.
     */
    String getDescription();

    /**
     * Static or base risk level of the tool.
     */
    RiskLevel getRiskLevel();

    /**
     * Execute tool logic with parameters.
     */
    ToolResult execute(Map<String, Object> params);
}
