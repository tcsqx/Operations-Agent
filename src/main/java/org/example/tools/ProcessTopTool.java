package org.example.tools;

import org.example.model.enums.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component("process_top")
public class ProcessTopTool implements BaseTool {

    private final SafeCommandExecutor commandExecutor;
    private final boolean isWindows;

    public ProcessTopTool(SafeCommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
        this.isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    }

    @Override
    public String getName() {
        return "process_top";
    }

    @Override
    public String getDescription() {
        return "List top running processes sorted by resource consumption (CPU or memory). Optional parameter 'limit' (default 10).";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        int limit = 10;
        if (params != null && params.containsKey("limit")) {
            try {
                limit = Integer.parseInt(params.get("limit").toString());
            } catch (Exception ignored) {
            }
        }

        String cmd;
        if (isWindows) {
            cmd = "powershell -Command \"Get-Process | Sort-Object CPU -Descending | Select-Object -First " + limit + " Id, ProcessName, @{Name='CPU_s';Expression={$_.CPU}}, @{Name='WorkingSet_MB';Expression={[math]::Round($_.WorkingSet / 1MB, 2)}} | Format-Table -AutoSize | Out-String\"";
        } else {
            cmd = "ps -eo pid,user,%cpu,%mem,comm --sort=-%cpu | head -n " + (limit + 1);
        }

        SafeCommandExecutor.CommandResult cmdResult = commandExecutor.execute(cmd, 10);
        if (!cmdResult.isSuccess()) {
            // Fallback for Windows if powershell fails: tasklist
            if (isWindows) {
                SafeCommandExecutor.CommandResult fallback = commandExecutor.execute("tasklist /FO TABLE /NH", 5);
                if (fallback.isSuccess()) {
                    String[] lines = fallback.getStdout().split("\n");
                    StringBuilder sb = new StringBuilder("Top Processes (tasklist sample):\n");
                    int count = 0;
                    for (String line : lines) {
                        if (!line.trim().isEmpty() && count++ < limit) {
                            sb.append(line).append("\n");
                        }
                    }
                    return ToolResult.success(sb.toString().trim(), System.currentTimeMillis() - start);
                }
            }
            return ToolResult.failure("Failed to list top processes: " + cmdResult.getStderr(), System.currentTimeMillis() - start);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("raw_output", cmdResult.getStdout());
        return ToolResult.success(cmdResult.getStdout().trim(), System.currentTimeMillis() - start, data);
    }
}
