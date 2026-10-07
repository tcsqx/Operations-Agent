package org.example.service;

import io.milvus.client.MilvusServiceClient;
import org.example.agent.tool.HostInspectionTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.controller.ChatController;
import org.example.engine.OpsPilotAgentEngine;
import org.example.entity.TaskEntity;
import org.example.entity.TaskStepEntity;
import org.example.repository.TaskStepRepository;
import org.example.tools.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AgentAndRagUpgradeTest {

    @MockBean
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private ChatService chatService;

    @Autowired
    private AiOpsService aiOpsService;

    @Autowired
    private HostInspectionTools hostInspectionTools;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private OpsPilotAgentEngine agentEngine;

    @Autowired
    private TaskStepRepository stepRepository;

    @Test
    @DisplayName("RAG Upgrade: Should auto-index aiops-docs, retrieve relevant SOP chunks, and filter irrelevant noise")
    void testHybridRagSearchAndThresholdFiltering() {
        assertTrue(vectorSearchService.getLocalCorpusSize() > 0, "Local RAG corpus should be auto-indexed from aiops-docs");

        // 1. Relevant query: HighCPUUsage on payment-service
        List<VectorSearchService.SearchResult> cpuDocs =
                vectorSearchService.searchSimilarDocuments("HighCPUUsage payment-service CPU使用率过高排查", 3);
        assertFalse(cpuDocs.isEmpty(), "Should find relevant SOP chunks for HighCPUUsage");
        assertTrue(cpuDocs.get(0).getScore() >= 0.15f);
        assertTrue(cpuDocs.stream().anyMatch(d -> d.getContent().toLowerCase().contains("cpu")));

        // 2. InternalDocsTools @Tool should return valid JSON with matched chunks
        String toolJson = internalDocsTools.queryInternalDocs("SlowResponse 数据库慢查询排查");
        assertNotNull(toolJson);
        assertFalse(toolJson.contains("\"status\": \"error\""));
        assertFalse(toolJson.contains("\"status\": \"no_results\""));
        assertTrue(toolJson.contains("content"));

        // 3. Irrelevant query should be filtered out by similarity threshold
        List<VectorSearchService.SearchResult> noiseDocs =
                vectorSearchService.searchSimilarDocuments("xyzzyspoon999888777", 3);
        assertTrue(noiseDocs.isEmpty(), "Irrelevant query below similarity threshold should return empty list");
    }

    @Test
    @DisplayName("RAG Upgrade: Should auto-archive completed incident report and retrieve it in subsequent searches")
    void testIncidentReportAutoArchiving() {
        String uniqueIncidentCode = "INCIDENT_REDIS_POOL_20261005";
        int archivedChunks = vectorSearchService.archiveIncidentReport(
                "task-archive-01",
                "Redis Cluster Timeout Postmortem",
                "## 故障根因结论\n唯一故障标识: " + uniqueIncidentCode + "，原因为 payment-service 连接池最大连接数不足导致阻塞。"
        );
        assertTrue(archivedChunks > 0);

        List<VectorSearchService.SearchResult> found =
                vectorSearchService.searchSimilarDocuments(uniqueIncidentCode + " payment-service", 3);
        assertFalse(found.isEmpty());
        assertTrue(found.stream().anyMatch(r -> r.getContent().contains(uniqueIncidentCode)));
    }

    @Test
    @DisplayName("Tiered Memory: SessionInfo should retain sliding window and compress evicted turns into historySummary")
    void testSessionTieredMemoryCompression() {
        ChatController.SessionInfo session = new ChatController.SessionInfo("test-session-memory");

        // Add 8 turns (exceeds MAX_WINDOW_SIZE = 6 by 2 turns)
        session.addMessage("第1轮：order-service 的 Pod pod-order-5c7d8 出现 OOMKilled", "已记录 pod-order-5c7d8 OOM 告警，建议检查堆内存");
        session.addMessage("第2轮：当前 JVM 堆配置是 4GB", "收到，4GB 堆内存下出现频繁 Full GC");
        for (int i = 3; i <= 8; i++) {
            session.addMessage("第" + i + "轮用户追问", "第" + i + "轮助手回答");
        }

        // Sliding window should hold exactly 6 pairs
        assertEquals(6, session.getMessagePairCount());
        // Evicted turns #1 and #2 should be preserved in compressed summary
        String summary = session.getHistorySummary();
        assertNotNull(summary);
        assertTrue(summary.contains("pod-order-5c7d8"), "Evicted turn entity must be preserved in compressed summary");
        assertTrue(summary.contains("4GB"), "Second evicted turn must be preserved in compressed summary");

        // ChatService system prompt should include both RAG context and compressed summary
        String systemPrompt = chatService.buildSystemPrompt(session.getHistory(), summary, "HighMemoryUsage 内存泄漏怎么排查？");
        assertTrue(systemPrompt.contains("早期对话关键上下文摘要"));
        assertTrue(systemPrompt.contains("pod-order-5c7d8"));
        assertTrue(systemPrompt.contains("内部知识库预检索参考文档"));
    }

    @Test
    @DisplayName("Agent Tool Bridge: ChatService and AiOpsService should register HostInspectionTools and record TaskStepEntity")
    void testHostInspectionToolsRegisteredAndTracked() {
        List<Object> chatTools = Arrays.asList(chatService.buildMethodToolsArray());
        List<Object> aiOpsTools = Arrays.asList(aiOpsService.buildMethodToolsArray());
        assertTrue(chatTools.contains(hostInspectionTools), "ChatService should register HostInspectionTools");
        assertTrue(aiOpsTools.contains(hostInspectionTools), "AiOpsService should register HostInspectionTools");

        // Bind an active task and invoke @Tool methods directly as ReactAgent/ExecutorAgent would
        TaskEntity task = agentEngine.createTask("Agent @Tool invocation test", "DIAGNOSIS");
        toolRegistry.bindActiveTask(task.getTaskId());
        try {
            String cpuOut = hostInspectionTools.inspectHostCpu();
            String memOut = hostInspectionTools.inspectHostMemory();
            assertTrue(cpuOut.contains("CPU Status:"));
            assertTrue(memOut.contains("Memory Status:"));

            List<TaskStepEntity> steps = stepRepository.findByTaskIdOrderByStepIndexAsc(task.getTaskId());
            assertEquals(2, steps.size());
            assertEquals("cpu_inspector", steps.get(0).getToolName());
            assertEquals("memory_inspector", steps.get(1).getToolName());
        } finally {
            toolRegistry.clearActiveTask();
        }
    }
}
