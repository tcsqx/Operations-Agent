package org.example.tools;

import com.sun.management.OperatingSystemMXBean;
import org.example.model.enums.RiskLevel;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

@Component("cpu_inspector")
public class CpuInspectorTool implements BaseTool {

    @Override
    public String getName() {
        return "cpu_inspector";
    }

    @Override
    public String getDescription() {
        return "Inspect host CPU metrics including overall CPU load, JVM process CPU load, core count, and system load average.";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        try {
            OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            int cores = osBean.getAvailableProcessors();
            double systemCpuLoad = osBean.getCpuLoad() * 100.0;
            double processCpuLoad = osBean.getProcessCpuLoad() * 100.0;
            double loadAverage = osBean.getSystemLoadAverage();

            Map<String, Object> data = new HashMap<>();
            data.put("cores", cores);
            data.put("system_cpu_load_pct", systemCpuLoad >= 0 ? String.format("%.2f%%", systemCpuLoad) : "N/A");
            data.put("process_cpu_load_pct", processCpuLoad >= 0 ? String.format("%.2f%%", processCpuLoad) : "N/A");
            data.put("system_load_avg", loadAverage >= 0 ? String.format("%.2f", loadAverage) : "N/A");

            String status = (systemCpuLoad > 85.0) ? "ALERT: High CPU Utilization (>85%)" : "HEALTHY";

            String output = String.format(
                "CPU Status: %s\n- Cores: %d\n- Host System CPU Load: %s\n- JVM Process CPU Load: %s\n- Load Average: %s",
                status, cores,
                data.get("system_cpu_load_pct"),
                data.get("process_cpu_load_pct"),
                data.get("system_load_avg")
            );

            return ToolResult.success(output, System.currentTimeMillis() - start, data);
        } catch (Exception e) {
            return ToolResult.failure("Failed to inspect CPU: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
