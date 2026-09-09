package org.example.tools;

import org.example.model.enums.RiskLevel;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

@Component("port_check")
public class PortCheckTool implements BaseTool {

    @Override
    public String getName() {
        return "port_check";
    }

    @Override
    public String getDescription() {
        return "Check whether a TCP port on a target host is listening and accessible. Parameters: 'host' (default '127.0.0.1'), 'port' (required, integer), 'timeout_ms' (default 2000).";
    }

    @Override
    public RiskLevel getRiskLevel() {
        return RiskLevel.LOW;
    }

    @Override
    public ToolResult execute(Map<String, Object> params) {
        long start = System.currentTimeMillis();
        if (params == null || !params.containsKey("port")) {
            return ToolResult.failure("Missing required parameter 'port'", 0);
        }

        String host = params.getOrDefault("host", "127.0.0.1").toString();
        int port;
        try {
            port = Integer.parseInt(params.get("port").toString());
        } catch (NumberFormatException e) {
            return ToolResult.failure("Invalid port format: " + params.get("port"), 0);
        }

        int timeoutMs = 2000;
        if (params.containsKey("timeout_ms")) {
            try {
                timeoutMs = Integer.parseInt(params.get("timeout_ms").toString());
            } catch (Exception ignored) {
            }
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            long cost = System.currentTimeMillis() - start;

            Map<String, Object> data = new HashMap<>();
            data.put("host", host);
            data.put("port", port);
            data.put("status", "OPEN");
            data.put("latency_ms", cost);

            String msg = String.format("Port %s:%d is OPEN (Connected in %d ms)", host, port, cost);
            return ToolResult.success(msg, cost, data);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            Map<String, Object> data = new HashMap<>();
            data.put("host", host);
            data.put("port", port);
            data.put("status", "CLOSED");
            data.put("reason", e.getMessage());

            String msg = String.format("Port %s:%d is CLOSED or Unreachable (%s)", host, port, e.getMessage());
            return ToolResult.success(msg, cost, data);
        }
    }
}
