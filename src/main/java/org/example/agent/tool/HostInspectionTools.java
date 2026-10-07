package org.example.agent.tool;

import org.example.tools.ToolRegistry;
import org.example.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 真实宿主机与系统探针工具集（Spring AI @Tool 桥接组件）
 * 将 ToolRegistry 中的 9 个真实物理探针、安全沙箱命令执行器、审计日志与 HITL 审批流暴露给 ReactAgent 与 SupervisorAgent
 */
@Component
public class HostInspectionTools {

    private static final Logger logger = LoggerFactory.getLogger(HostInspectionTools.class);

    private final ToolRegistry toolRegistry;

    public HostInspectionTools(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Tool(description = "Inspect host server basic telemetry including OS name, version, architecture, available CPU cores, hostname, IP address, system load average, and JVM uptime.")
    public String inspectHostServerInfo() {
        logger.info("[Agent @Tool] Calling server_info via ToolRegistry");
        ToolResult res = toolRegistry.executeFromAgent("server_info", "Collect Host Server Telemetry", Collections.emptyMap());
        return formatResult(res);
    }

    @Tool(description = "Inspect real-time host CPU utilization metrics including total CPU core count, system-wide CPU load percentage, JVM process CPU load percentage, and system load average.")
    public String inspectHostCpu() {
        logger.info("[Agent @Tool] Calling cpu_inspector via ToolRegistry");
        ToolResult res = toolRegistry.executeFromAgent("cpu_inspector", "Inspect Host CPU Utilization", Collections.emptyMap());
        return formatResult(res);
    }

    @Tool(description = "Inspect real-time host physical RAM and JVM heap memory usage, including total/used/free megabytes and memory utilization percentages.")
    public String inspectHostMemory() {
        logger.info("[Agent @Tool] Calling memory_inspector via ToolRegistry");
        ToolResult res = toolRegistry.executeFromAgent("memory_inspector", "Inspect Physical & JVM Memory", Collections.emptyMap());
        return formatResult(res);
    }

    @Tool(description = "Inspect host disk partitions, mount points, total storage capacity, used space, available space (GB), and disk utilization percentages.")
    public String inspectHostDisk() {
        logger.info("[Agent @Tool] Calling disk_usage via ToolRegistry");
        ToolResult res = toolRegistry.executeFromAgent("disk_usage", "Inspect Disk Partition Usage", Collections.emptyMap());
        return formatResult(res);
    }

    @Tool(description = "List top resource-consuming processes running on the host OS sorted by CPU/memory usage.")
    public String inspectTopProcesses(
            @ToolParam(description = "Maximum number of top processes to return (default 10, max 30)") Integer limit) {
        logger.info("[Agent @Tool] Calling process_top via ToolRegistry, limit={}", limit);
        Map<String, Object> params = new HashMap<>();
        params.put("limit", (limit == null || limit <= 0) ? 10 : Math.min(limit, 30));
        ToolResult res = toolRegistry.executeFromAgent("process_top", "Inspect Top Resource Consuming Processes", params);
        return formatResult(res);
    }

    @Tool(description = "Check whether a TCP port on a target host is open, listening, and reachable, and measure connection latency in milliseconds.")
    public String checkHostPort(
            @ToolParam(description = "Target hostname or IP address, default '127.0.0.1'") String host,
            @ToolParam(description = "Target TCP port number (1-65535)") Integer port,
            @ToolParam(description = "Connection timeout in milliseconds, default 2000") Integer timeoutMs) {
        logger.info("[Agent @Tool] Calling port_check via ToolRegistry, host={}, port={}", host, port);
        Map<String, Object> params = new HashMap<>();
        params.put("host", (host == null || host.isBlank()) ? "127.0.0.1" : host.trim());
        if (port != null) {
            params.put("port", port);
        }
        params.put("timeout_ms", (timeoutMs == null || timeoutMs <= 0) ? 2000 : timeoutMs);
        ToolResult res = toolRegistry.executeFromAgent("port_check", "Check Port " + port + " Accessibility", params);
        return formatResult(res);
    }

    @Tool(description = "Check the running status of a system service or daemon process on the host (e.g. 'mysql', 'redis', 'nginx', 'docker', 'java').")
    public String checkServiceStatus(
            @ToolParam(description = "Service or process name to check, e.g. 'java', 'mysql', 'nginx', 'docker'") String service) {
        logger.info("[Agent @Tool] Calling service_status via ToolRegistry, service={}", service);
        Map<String, Object> params = new HashMap<>();
        if (service != null) {
            params.put("service", service.trim());
        }
        ToolResult res = toolRegistry.executeFromAgent("service_status", "Check Service Status: " + service, params);
        return formatResult(res);
    }

    @Tool(description = "Search recent lines of a local log file on the host filesystem for specific error keywords or patterns (supports UTF-8 logs and automatic sensitive data masking).")
    public String searchSystemLogFile(
            @ToolParam(description = "Path to the log file on the host") String filePath,
            @ToolParam(description = "Optional keyword to filter log lines (case-insensitive, e.g. 'ERROR', 'Exception', 'OOM')") String keyword,
            @ToolParam(description = "Maximum number of matching lines to return, default 50, max 200") Integer maxLines) {
        logger.info("[Agent @Tool] Calling system_log_search via ToolRegistry, filePath={}, keyword={}", filePath, keyword);
        Map<String, Object> params = new HashMap<>();
        if (filePath != null) {
            params.put("file_path", filePath.trim());
        }
        if (keyword != null) {
            params.put("keyword", keyword.trim());
        }
        params.put("max_lines", (maxLines == null || maxLines <= 0) ? 50 : Math.min(maxLines, 200));
        ToolResult res = toolRegistry.executeFromAgent("system_log_search", "Search Local Log File: " + filePath, params);
        return formatResult(res);
    }

    @Tool(description = "Execute a diagnostic shell command on the host OS within the security sandbox (protected by CommandGuard blacklist, RiskClassifier, and HITL human approval for high-risk commands).")
    public String executeSafeShellCommand(
            @ToolParam(description = "Shell command to execute") String command,
            @ToolParam(description = "Timeout in seconds, default 15, max 60") Integer timeoutSeconds) {
        logger.info("[Agent @Tool] Calling safe_shell via ToolRegistry, command={}", command);
        Map<String, Object> params = new HashMap<>();
        if (command != null) {
            params.put("command", command.trim());
        }
        params.put("timeout", (timeoutSeconds == null || timeoutSeconds <= 0) ? 15 : Math.min(timeoutSeconds, 60));
        ToolResult res = toolRegistry.executeFromAgent("safe_shell", "Execute Shell Command: " + command, params);
        return formatResult(res);
    }

    private String formatResult(ToolResult result) {
        if (result == null) {
            return "ERROR: Null tool result";
        }
        if (result.isSuccess()) {
            return result.getOutput();
        } else {
            return "ERROR: " + result.getError();
        }
    }
}
