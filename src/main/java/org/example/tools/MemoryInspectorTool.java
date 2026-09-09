package org.example.tools;

import com.sun.management.OperatingSystemMXBean;
import org.example.model.enums.RiskLevel;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

@Component("memory_inspector")
public class MemoryInspectorTool implements BaseTool {

    @Override
    public String getName() {
        return "memory_inspector";
    }

    @Override
    public String getDescription() {
        return "Inspect physical and JVM memory usage, total memory, used memory, free memory, and memory utilization percentages.";
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
            long totalPhysical = osBean.getTotalMemorySize();
            long freePhysical = osBean.getFreeMemorySize();
            long usedPhysical = totalPhysical - freePhysical;
            double physicalPct = (double) usedPhysical / (double) totalPhysical * 100.0;

            Runtime runtime = Runtime.getRuntime();
            long maxJvm = runtime.maxMemory();
            long totalJvm = runtime.totalMemory();
            long freeJvm = runtime.freeMemory();
            long usedJvm = totalJvm - freeJvm;
            double jvmPct = (double) usedJvm / (double) maxJvm * 100.0;

            Map<String, Object> data = new HashMap<>();
            data.put("physical_total_mb", totalPhysical / (1024 * 1024));
            data.put("physical_used_mb", usedPhysical / (1024 * 1024));
            data.put("physical_free_mb", freePhysical / (1024 * 1024));
            data.put("physical_usage_pct", String.format("%.2f%%", physicalPct));

            data.put("jvm_max_mb", maxJvm / (1024 * 1024));
            data.put("jvm_used_mb", usedJvm / (1024 * 1024));
            data.put("jvm_usage_pct", String.format("%.2f%%", jvmPct));

            String alert = physicalPct > 90.0 ? "CRITICAL: Physical Memory >90%" :
                          (physicalPct > 80.0 ? "WARNING: Physical Memory >80%" : "HEALTHY");

            String output = String.format(
                "Memory Status: %s\n" +
                "- Physical RAM: %d MB Total, %d MB Used, %d MB Free (Usage: %.2f%%)\n" +
                "- JVM Heap: %d MB Max, %d MB Used (Usage: %.2f%%)",
                alert,
                data.get("physical_total_mb"), data.get("physical_used_mb"), data.get("physical_free_mb"), physicalPct,
                data.get("jvm_max_mb"), data.get("jvm_used_mb"), jvmPct
            );

            return ToolResult.success(output, System.currentTimeMillis() - start, data);
        } catch (Exception e) {
            return ToolResult.failure("Failed to inspect memory: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
