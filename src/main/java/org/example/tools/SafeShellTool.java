package org.example.tools;

import org.example.model.enums.RiskLevel;
import org.example.security.CommandGuard;
import org.example.security.RiskClassifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component("safe_shell")
public class SafeShellTool implements BaseTool {

    private final SafeCommandExecutor commandExecutor;
    private final CommandGuard commandGuard;
    private final RiskClassifier riskClassifier;

    public SafeShellTool(SafeCommandExecutor commandExecutor, CommandGuard commandGuard, RiskClassifier riskClassifier) {
        this.commandExecutor = commandExecutor;
        this.commandGuard = commandGuard;
        this.riskClassifier = riskClassifier;
    }

    @Override
    public String getName() {
        return "safe_shell";
    }

    @Override
    public String getDescription() {
        return "Execute safe shell commands on host OS with timeout (15s) and security sandboxing. Parameter: 'command' (required string).";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.MEDIUM;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        if (params == null || !params.containsKey("command")) {
            return ToolResult.failure("Missing required parameter 'command'", 0);
        }

        String command = params.get("command").toString().trim();

        // 1. Guard check
        CommandGuard.ValidationResult guardResult = commandGuard.validate(command);
        if (!guardResult.isAllowed()) {
            return ToolResult.failure("Command execution blocked: " + guardResult.getReason(), System.currentTimeMillis() - start);
        }

        // 2. Timeout override if specified
        int timeoutSeconds = 15;
        if (params.containsKey("timeout")) {
            try {
                timeoutSeconds = Math.min(60, Integer.parseInt(params.get("timeout").toString()));
            } catch (Exception ignored) {
            }
        }

        SafeCommandExecutor.CommandResult result = commandExecutor.execute(command, timeoutSeconds);

        Map<String, Object> data = new HashMap<>();
        data.put("command", command);
        data.put("exit_code", result.getExitCode());
        data.put("timed_out", result.isTimedOut());

        if (result.isSuccess()) {
            String out = result.getStdout().isEmpty() ? "(No standard output, exit code 0)" : result.getStdout();
            return ToolResult.success(out, result.getDurationMs(), data);
        } else {
            String err = result.getStderr().isEmpty() ? result.getStdout() : result.getStderr();
            return ToolResult.failure("Command failed (exit code " + result.getExitCode() + "): " + err, result.getDurationMs());
        }
    }
}
