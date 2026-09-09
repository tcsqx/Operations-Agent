package org.example.controller;

import org.example.dto.v2.ApiResponse;
import org.example.tools.BaseTool;
import org.example.tools.ToolRegistry;
import org.example.tools.ToolResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v2/tools")
@CrossOrigin(origins = "*")
public class OpsToolController {

    private final ToolRegistry toolRegistry;

    public OpsToolController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (BaseTool tool : toolRegistry.getAllTools()) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", tool.getName());
            item.put("description", tool.getDescription());
            item.put("risk_level", tool.getRiskLevel());
            tools.add(item);
        }
        return ApiResponse.ok(tools);
    }

    @PostMapping("/execute/{toolName}")
    public ApiResponse<ToolResult> executeToolManually(
            @PathVariable String toolName,
            @RequestBody(required = false) Map<String, Object> params) {
        if (!toolRegistry.hasTool(toolName)) {
            return ApiResponse.error(404, "Tool not found: " + toolName);
        }

        ToolResult result = toolRegistry.executeTool("manual-exec", toolName, params != null ? params : Collections.emptyMap(), "web-console");
        return ApiResponse.ok(result);
    }
}
