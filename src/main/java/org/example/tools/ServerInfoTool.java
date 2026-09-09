package org.example.tools;

import org.example.model.enums.RiskLevel;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component("server_info")
public class ServerInfoTool implements BaseTool {

    @Override
    public String getName() {
        return "server_info";
    }

    @Override
    public String getDescription() {
        return "Retrieve general host system information including OS name, architecture, available processors, hostname, and JVM uptime.";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            InetAddress localHost = InetAddress.getLocalHost();

            Map<String, Object> data = new HashMap<>();
            data.put("os_name", osBean.getName());
            data.put("os_version", osBean.getVersion());
            data.put("os_arch", osBean.getArch());
            data.put("available_processors", osBean.getAvailableProcessors());
            data.put("hostname", localHost.getHostName());
            data.put("ip", localHost.getHostAddress());
            data.put("system_load_average", osBean.getSystemLoadAverage());
            data.put("jvm_uptime_ms", ManagementFactory.getRuntimeMXBean().getUptime());
            data.put("server_time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            String formatted = String.format(
                "Hostname: %s (%s)\nOS: %s %s (%s)\nAvailable Cores: %d\nSystem Load Average: %.2f\nJVM Uptime: %d seconds\nServer Time: %s",
                data.get("hostname"), data.get("ip"),
                data.get("os_name"), data.get("os_version"), data.get("os_arch"),
                data.get("available_processors"),
                data.get("system_load_average"),
                ((Long) data.get("jvm_uptime_ms")) / 1000,
                data.get("server_time")
            );

            return ToolResult.success(formatted, System.currentTimeMillis() - start, data);
        } catch (Exception e) {
            return ToolResult.failure("Failed to query server info: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
