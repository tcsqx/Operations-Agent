package org.example.tools;

import org.example.model.enums.RiskLevel;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("disk_usage")
public class DiskUsageTool implements BaseTool {

    @Override
    public String getName() {
        return "disk_usage";
    }

    @Override
    public String getDescription() {
        return "Inspect disk partitions, total storage, available storage, and disk utilization percentages.";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        try {
            File[] roots = File.listRoots();
            StringBuilder sb = new StringBuilder("Disk Partition Status:\n");
            List<Map<String, Object>> partitionList = new ArrayList<>();
            boolean hasAlert = false;

            for (File root : roots) {
                long total = root.getTotalSpace();
                long free = root.getFreeSpace();
                long usable = root.getUsableSpace();
                long used = total - free;
                double usedPct = total > 0 ? ((double) used / (double) total) * 100.0 : 0.0;

                long totalGb = total / (1024 * 1024 * 1024);
                long usedGb = used / (1024 * 1024 * 1024);
                long usableGb = usable / (1024 * 1024 * 1024);

                Map<String, Object> pInfo = new HashMap<>();
                pInfo.put("path", root.getAbsolutePath());
                pInfo.put("total_gb", totalGb);
                pInfo.put("used_gb", usedGb);
                pInfo.put("usable_gb", usableGb);
                pInfo.put("used_pct", String.format("%.1f%%", usedPct));
                partitionList.add(pInfo);

                String flag = "";
                if (usedPct > 90.0) {
                    flag = " [CRITICAL > 90%]";
                    hasAlert = true;
                } else if (usedPct > 80.0) {
                    flag = " [WARNING > 80%]";
                }

                sb.append(String.format("- Mount '%s': Total %d GB, Used %d GB (%.1f%%), Usable %d GB%s\n",
                    root.getAbsolutePath(), totalGb, usedGb, usedPct, usableGb, flag));
            }

            Map<String, Object> data = new HashMap<>();
            data.put("partitions", partitionList);
            data.put("alert", hasAlert);

            return ToolResult.success(sb.toString().trim(), System.currentTimeMillis() - start, data);
        } catch (Exception e) {
            return ToolResult.failure("Failed to query disk usage: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
