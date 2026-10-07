package org.example.tools;

import org.example.model.enums.RiskLevel;
import org.example.security.CommandGuard;
import org.example.security.DataMasker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GroundTruthToolsTest {

    private ServerInfoTool serverInfoTool;
    private CpuInspectorTool cpuInspectorTool;
    private MemoryInspectorTool memoryInspectorTool;
    private DiskUsageTool diskUsageTool;
    private PortCheckTool portCheckTool;

    @BeforeEach
    void setUp() {
        serverInfoTool = new ServerInfoTool();
        cpuInspectorTool = new CpuInspectorTool();
        memoryInspectorTool = new MemoryInspectorTool();
        diskUsageTool = new DiskUsageTool();
        portCheckTool = new PortCheckTool();
    }

    @Test
    @DisplayName("ServerInfoTool should return valid host OS details")
    void testServerInfoTool() {
        assertEquals("server_info", serverInfoTool.getName());
        assertEquals(RiskLevel.LOW, serverInfoTool.getRiskLevel());

        ToolResult res = serverInfoTool.execute(Collections.emptyMap());
        assertTrue(res.isSuccess());
        assertNotNull(res.getOutput());
        assertTrue(res.getOutput().contains("Hostname:"));
        assertTrue(res.getOutput().contains("OS:"));
        assertTrue(res.getData().containsKey("available_processors"));
    }

    @Test
    @DisplayName("CpuInspectorTool should return CPU cores and load metrics")
    void testCpuInspectorTool() {
        assertEquals("cpu_inspector", cpuInspectorTool.getName());

        ToolResult res = cpuInspectorTool.execute(Collections.emptyMap());
        assertTrue(res.isSuccess());
        assertNotNull(res.getOutput());
        assertTrue(res.getOutput().contains("Cores:"));
        assertTrue(res.getData().containsKey("cores"));
    }

    @Test
    @DisplayName("MemoryInspectorTool should return physical and JVM memory stats")
    void testMemoryInspectorTool() {
        assertEquals("memory_inspector", memoryInspectorTool.getName());

        ToolResult res = memoryInspectorTool.execute(Collections.emptyMap());
        assertTrue(res.isSuccess());
        assertNotNull(res.getOutput());
        assertTrue(res.getOutput().contains("Physical RAM:"));
        assertTrue(res.getOutput().contains("JVM Heap:"));
        assertTrue(res.getData().containsKey("physical_total_mb"));
    }

    @Test
    @DisplayName("DiskUsageTool should return disk partitions and free percentages")
    void testDiskUsageTool() {
        assertEquals("disk_usage", diskUsageTool.getName());

        ToolResult res = diskUsageTool.execute(Collections.emptyMap());
        assertTrue(res.isSuccess());
        assertNotNull(res.getOutput());
        assertTrue(res.getOutput().contains("Mount '"));
        assertTrue(res.getData().containsKey("partitions"));
    }

    @Test
    @DisplayName("PortCheckTool should check TCP port status")
    void testPortCheckTool() {
        assertEquals("port_check", portCheckTool.getName());

        Map<String, Object> params = new HashMap<>();
        params.put("host", "127.0.0.1");
        params.put("port", 65530); // Likely closed port
        params.put("timeout_ms", 500);

        ToolResult res = portCheckTool.execute(params);
        assertTrue(res.isSuccess());
        assertNotNull(res.getOutput());
        assertTrue(res.getData().containsKey("status"));
    }

    @Test
    @DisplayName("SystemLogSearchTool should preserve UTF-8 Chinese characters and mask sensitive data")
    void testSystemLogSearchToolUtf8AndMasking() throws Exception {
        SystemLogSearchTool logTool = new SystemLogSearchTool(new DataMasker());
        java.nio.file.Path tempLog = java.nio.file.Files.createTempFile("ops-utf8-test-", ".log");
        try {
            String logContent = String.join("\n",
                    "2026-10-05 10:00:01 [INFO] OrderService - 订单创建成功 orderId=1001",
                    "2026-10-05 10:01:02 [ERROR] PaymentService - 支付网关连接超时：无法连接数据库 password=MySecretPass123",
                    "2026-10-05 10:02:03 [ERROR] UserService - 用户鉴权异常，订单服务内存溢出 OutOfMemoryError"
            );
            java.nio.file.Files.writeString(tempLog, logContent, java.nio.charset.StandardCharsets.UTF_8);

            Map<String, Object> params = new HashMap<>();
            params.put("file_path", tempLog.toFile().getAbsolutePath());
            params.put("keyword", "ERROR");
            params.put("max_lines", 10);

            ToolResult res = logTool.execute(params);
            assertTrue(res.isSuccess());
            String output = res.getOutput();
            assertTrue(output.contains("支付网关连接超时"), "Should preserve UTF-8 Chinese characters without corruption");
            assertTrue(output.contains("订单服务内存溢出"), "Should preserve UTF-8 Chinese characters in second matched line");
            assertFalse(output.contains("MySecretPass123"), "Should mask sensitive password in log output");
        } finally {
            java.nio.file.Files.deleteIfExists(tempLog);
        }
    }
}
