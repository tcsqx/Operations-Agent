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
}
