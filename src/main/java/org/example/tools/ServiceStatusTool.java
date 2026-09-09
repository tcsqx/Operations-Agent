package org.example.tools;

import org.example.model.enums.RiskLevel;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component("service_status")
public class ServiceStatusTool implements BaseTool {

    private final SafeCommandExecutor commandExecutor;
    private final boolean isWindows;

    public ServiceStatusTool(SafeCommandExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
        this.isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    }

    @Override
    public String getName() {
        return "service_status";
    }

    @Override
    public String getDescription() {
        return "Check the current running status of a system service or process. Parameter: 'service' (required, e.g. 'docker', 'mysql', 'nginx', 'java').";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        if (params == null || !params.containsKey("service")) {
            return ToolResult.failure("Missing required parameter 'service'", 0);
        }

        String service = params.get("service").toString().trim();
        // Prevent command injection in service name
        if (!service.matches("^[a-zA-Z0-9_-]+$")) {
            return ToolResult.failure("Invalid characters in service name: " + service, 0);
        }

        String cmd;
        if (isWindows) {
            cmd = "powershell -Command \"$s = Get-Service -Name '" + service + "' -ErrorAction SilentlyContinue; if ($s) { Write-Output ($s.Name + ': ' + $s.Status) } else { $p = Get-Process -Name '" + service + "' -ErrorAction SilentlyContinue; if ($p) { Write-Output ($p.ProcessName + ': Running (Process)') } else { Write-Output 'NotFound' } }\"";
        } else {
            cmd = "systemctl is-active " + service + " 2>/dev/null || pgrep -f " + service + " >/dev/null && echo 'Running (Process)' || echo 'NotFound'";
        }

        SafeCommandExecutor.CommandResult res = commandExecutor.execute(cmd, 10);
        String output = res.getStdout().trim();
        if (output.isEmpty() || output.equalsIgnoreCase("NotFound")) {
            output = "Service or process '" + service + "' is NOT running (Not found).";
        }

        Map<String, Object> data = new HashMap<>();
        data.put("service", service);
        data.put("status_output", output);

        return ToolResult.success(output, System.currentTimeMillis() - start, data);
    }
}
