package org.example.security;

import org.example.model.enums.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Component
public class RiskClassifier {

    private static final Set<String> LOW_RISK_TOOLS = new HashSet<>(Arrays.asList(
        "server_info", "cpu_inspector", "memory_inspector", "disk_usage",
        "process_top", "port_check", "service_status", "system_log_search",
        "date_time", "query_metrics", "query_logs", "internal_docs"
    ));

    private static final Set<String> MEDIUM_RISK_TOOLS = new HashSet<>(Arrays.asList(
        "safe_shell_readonly", "network_ping", "dns_lookup", "file_checksum"
    ));

    private static final Set<String> HIGH_RISK_TOOLS = new HashSet<>(Arrays.asList(
        "service_restart", "config_modify", "docker_restart", "process_kill", "safe_shell_mutate"
    ));

    /**
     * Classifies risk based on tool name and command text.
     */
    public RiskLevel classify(String toolName, String commandOrParams) {
        if (toolName == null) {
            return RiskLevel.LOW;
        }

        String lowerTool = toolName.toLowerCase().trim();

        if (HIGH_RISK_TOOLS.contains(lowerTool)) {
            return RiskLevel.HIGH;
        }

        if (MEDIUM_RISK_TOOLS.contains(lowerTool)) {
            return RiskLevel.MEDIUM;
        }

        if (LOW_RISK_TOOLS.contains(lowerTool)) {
            return RiskLevel.LOW;
        }

        // If shell execution, classify by command contents
        if (lowerTool.contains("shell") || lowerTool.contains("exec") || lowerTool.contains("cmd")) {
            if (commandOrParams != null) {
                String lowerCmd = commandOrParams.toLowerCase();
                if (lowerCmd.contains("rm -rf") || lowerCmd.contains("mkfs") || lowerCmd.contains("dd ") || lowerCmd.contains("shutdown")) {
                    return RiskLevel.CRITICAL;
                }
                if (lowerCmd.contains("restart") || lowerCmd.contains("stop") || lowerCmd.contains("kill") || lowerCmd.contains("iptables")) {
                    return RiskLevel.HIGH;
                }
                if (lowerCmd.contains("cat ") || lowerCmd.contains("grep ") || lowerCmd.contains("tail ") || lowerCmd.contains("top ") || lowerCmd.contains("ps ")) {
                    return RiskLevel.LOW;
                }
            }
            return RiskLevel.MEDIUM;
        }

        return RiskLevel.LOW;
    }
}
