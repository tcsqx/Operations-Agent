package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import org.example.agent.tool.DateTimeTools;
import org.example.agent.tool.HostInspectionTools;
import org.example.agent.tool.InternalDocsTools;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 聊天服务
 * 封装 ReactAgent 对话的公共逻辑，包括模型创建、系统提示词构建、Agent 配置等
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private InternalDocsTools internalDocsTools;

    @Autowired
    private DateTimeTools dateTimeTools;

    @Autowired
    private QueryMetricsTools queryMetricsTools;

    @Autowired(required = false)  // Mock 模式下才注册，所以设置为 optional,真实环境通过mcp配置注入
    private QueryLogsTools queryLogsTools;

    @Autowired(required = false)
    private HostInspectionTools hostInspectionTools;

    @Autowired(required = false)
    private VectorSearchService vectorSearchService;

    @Autowired(required = false)
    private ToolCallbackProvider tools;

    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Value("${rag.model:qwen-max}")
    private String modelName;

    private static final String MULTIMODAL_COMPLETIONS_PATH = "/api/v1/services/aigc/multimodal-generation/generation";

    /**
     * 判断指定模型在 DashScope 原生协议下是否必须走 multimodal-generation 端点
     * （例如 qwen3.8-flash、qwen3.5 系列、qwen-vl / qwen-omni 全模态模型，若走 text-generation 会报错 url error）
     */
    public boolean isMultimodalEndpointModel(String model) {
        if (model == null || model.isBlank()) {
            return false;
        }
        String m = model.trim().toLowerCase();
        return m.startsWith("qwen3.5")
                || m.startsWith("qwen3.6")
                || m.startsWith("qwen3.7")
                || m.startsWith("qwen3.8")
                || m.startsWith("qwen3.9")
                || m.startsWith("qwen4")
                || m.contains("-vl")
                || m.contains("-omni")
                || m.startsWith("qvq");
    }

    /**
     * 创建 DashScope API 实例（自动根据模型类型匹配 text-generation 或 multimodal-generation 端点）
     */
    public DashScopeApi createDashScopeApi() {
        String targetModel = (modelName != null && !modelName.isBlank()) ? modelName.trim() : "qwen-max";
        DashScopeApi.Builder builder = DashScopeApi.builder()
                .apiKey(dashScopeApiKey);
        if (isMultimodalEndpointModel(targetModel)) {
            logger.info("检测到全模态/新一代模型 [{}]，自动切换 DashScope 端点至 {}", targetModel, MULTIMODAL_COMPLETIONS_PATH);
            builder.completionsPath(MULTIMODAL_COMPLETIONS_PATH);
        }
        return builder.build();
    }

    /**
     * 创建 ChatModel
     * @param temperature 控制随机性 (0.0-1.0)
     * @param maxToken 最大输出长度
     * @param topP 核采样参数
     */
    public DashScopeChatModel createChatModel(DashScopeApi dashScopeApi, double temperature, int maxToken, double topP) {
        String targetModel = (modelName != null && !modelName.isBlank()) ? modelName.trim() : "qwen-max";
        boolean multiModel = isMultimodalEndpointModel(targetModel);
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .withModel(targetModel)
                        .withMultiModel(multiModel)
                        .withTemperature(temperature)
                        .withMaxToken(maxToken)
                        .withTopP(topP)
                        .build())
                .build();
    }

    /**
     * 创建标准对话 ChatModel（默认参数）
     */
    public DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi) {
        return createChatModel(dashScopeApi, 0.7, 2000, 0.9);
    }

    /**
     * 构建系统提示词（包含历史消息）
     * @param history 历史消息列表
     * @return 完整的系统提示词
     */
    public String buildSystemPrompt(List<Map<String, String>> history) {
        return buildSystemPrompt(history, null, null);
    }

    /**
     * 构建增强版系统提示词（支持分层记忆摘要 + Pre-retrieval & Agentic 混合 RAG）
     *
     * @param history         近期滑动窗口历史消息列表
     * @param historySummary  被滑出窗口的早期多轮对话压缩摘要（可选）
     * @param currentQuestion 当前用户问题（用于 RAG 预检索注入，可选）
     * @return 完整的系统提示词
     */
    public String buildSystemPrompt(List<Map<String, String>> history, String historySummary, String currentQuestion) {
        StringBuilder systemPromptBuilder = new StringBuilder();

        // 基础系统提示
        systemPromptBuilder.append("你是一个专业的企业级智能运维助手 (AIOps & OnCall Agent)，可以获取当前时间、搜索内部运维文档知识库 (RAG)、查询 Prometheus 告警与 CLS 日志，并可调用宿主机真实探针检测 CPU、内存、磁盘、进程、端口、服务状态与本地日志。\n");
        systemPromptBuilder.append("当用户询问时间相关问题时，使用 getCurrentDateTime 工具。\n");
        systemPromptBuilder.append("当用户需要查询公司内部文档、故障排查手册、流程或最佳实践时，优先参考下方预检索的文档片段；若需进一步检索更多细节，使用 queryInternalDocs 工具。\n");
        systemPromptBuilder.append("当用户需要查询 Prometheus 告警、监控指标或系统告警状态时，使用 queryPrometheusAlerts 工具。\n");
        systemPromptBuilder.append("当用户需要查询云端日志时，先使用 getAvailableLogTopics 了解可用主题，再使用 queryLogs 工具（默认地域 ap-guangzhou）。\n");
        systemPromptBuilder.append("当用户需要排查当前服务器宿主机真实负载或本地环境时，按需调用 inspectHostServerInfo、inspectHostCpu、inspectHostMemory、inspectHostDisk、inspectTopProcesses、checkHostPort、checkServiceStatus、searchSystemLogFile 或 executeSafeShellCommand 工具。\n\n");

        // Pre-retrieval RAG 预检索注入（Pre-retrieval + Agentic Hybrid RAG）
        if (currentQuestion != null && !currentQuestion.trim().isEmpty() && vectorSearchService != null) {
            try {
                List<VectorSearchService.SearchResult> ragDocs =
                        vectorSearchService.searchSimilarDocuments(currentQuestion, 3);
                if (ragDocs != null && !ragDocs.isEmpty()) {
                    systemPromptBuilder.append("--- 内部知识库预检索参考文档 (RAG Context) ---\n");
                    for (int i = 0; i < ragDocs.size(); i++) {
                        VectorSearchService.SearchResult doc = ragDocs.get(i);
                        systemPromptBuilder.append(String.format("[参考片段 %d | 相关度: %.3f | 元数据: %s]\n%s\n\n",
                                i + 1, doc.getScore(), doc.getMetadata(), doc.getContent()));
                    }
                    systemPromptBuilder.append("--- 知识库预检索结束 ---\n\n");
                }
            } catch (Exception e) {
                logger.warn("构建系统提示词时预检索 RAG 知识库失败: {}", e.getMessage());
            }
        }

        // 添加早期对话压缩摘要（分层记忆机制 Tiered Memory）
        if (historySummary != null && !historySummary.trim().isEmpty()) {
            systemPromptBuilder.append("--- 早期对话关键上下文摘要 ---\n");
            systemPromptBuilder.append(historySummary.trim()).append("\n");
            systemPromptBuilder.append("--- 早期对话摘要结束 ---\n\n");
        }

        // 添加近期滑动窗口历史消息
        if (history != null && !history.isEmpty()) {
            systemPromptBuilder.append("--- 对话历史 ---\n");
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    systemPromptBuilder.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    systemPromptBuilder.append("助手: ").append(content).append("\n");
                }
            }
            systemPromptBuilder.append("--- 对话历史结束 ---\n\n");
        }

        systemPromptBuilder.append("请基于以上知识库上下文、对话历史及真实工具调用结果，严谨准确地回答用户的新问题。严禁编造虚假数据。");

        return systemPromptBuilder.toString();
    }

    /**
     * 动态构建方法工具数组
     * 包含原有的 4 类运维工具 + 宿主机 9 个真实物理探针工具 (HostInspectionTools)
     */
    public Object[] buildMethodToolsArray() {
        java.util.List<Object> toolBeans = new java.util.ArrayList<>();
        if (dateTimeTools != null) toolBeans.add(dateTimeTools);
        if (internalDocsTools != null) toolBeans.add(internalDocsTools);
        if (queryMetricsTools != null) toolBeans.add(queryMetricsTools);
        if (queryLogsTools != null) toolBeans.add(queryLogsTools);
        if (hostInspectionTools != null) toolBeans.add(hostInspectionTools);
        return toolBeans.toArray();
    }

    /**
     * 获取工具回调列表，mcp服务提供的工具
     */
    public ToolCallback[] getToolCallbacks() {
        if (tools != null) {
            return tools.getToolCallbacks();
        }
        return new ToolCallback[0];
    }

    /**
     * 记录可用工具列表：mcp服务提供的工具
     */
    public void logAvailableTools() {
        if (tools == null) {
            logger.info("ToolCallbackProvider 未启用或为空，无外部 MCP 工具回调");
            return;
        }
        ToolCallback[] toolCallbacks = tools.getToolCallbacks();
        logger.info("可用工具列表:");
        for (ToolCallback toolCallback : toolCallbacks) {
            logger.info(">>> {}", toolCallback.getToolDefinition().name());
        }
    }

    /**
     * 创建 ReactAgent
     * @param chatModel 聊天模型
     * @param systemPrompt 系统提示词
     * @return 配置好的 ReactAgent
     */
    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt) {
        return ReactAgent.builder()
                .name("intelligent_assistant")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .methodTools(buildMethodToolsArray())
                .tools(getToolCallbacks())
                .build();
    }

    /**
     * 检查是否配置了有效的 DashScope API Key
     */
    public boolean isApiKeyConfigured() {
        return dashScopeApiKey != null
                && !dashScopeApiKey.trim().isEmpty()
                && !"your-api-key-here".equals(dashScopeApiKey.trim())
                && !"test-dummy-key".equals(dashScopeApiKey.trim());
    }

    /**
     * 当未配置 DASHSCOPE_API_KEY 时的本地混合 RAG + 真实宿主机探针降级回复
     */
    public String buildLocalDiagnosticReply(String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("> 💡 **提示**：当前未检测到有效的 `DASHSCOPE_API_KEY`（当前为默认占位符），已自动切换至 **本地混合 RAG 知识库检索 + 宿主机真实物理探针** 模式。如需开启通义千问 `qwen3-max` 多 Agent 深度推理，请在 IDEA 运行配置环境变量中设置 `DASHSCOPE_API_KEY`。\n\n");

        String lower = question != null ? question.toLowerCase() : "";

        // 1. 检索混合 RAG 知识库
        if (vectorSearchService != null && question != null && !question.trim().isEmpty()) {
            try {
                List<VectorSearchService.SearchResult> docs = vectorSearchService.searchSimilarDocuments(question, 3);
                if (docs != null && !docs.isEmpty()) {
                    sb.append("### 📚 内部知识库检索命中 (Hybrid RAG)\n\n");
                    for (int i = 0; i < docs.size(); i++) {
                        VectorSearchService.SearchResult doc = docs.get(i);
                        sb.append(String.format("**[%d] 相关度 `%.3f`** (`%s`)\n", i + 1, doc.getScore(), doc.getMetadata()));
                        sb.append(doc.getContent()).append("\n\n");
                    }
                }
            } catch (Exception e) {
                logger.warn("本地降级检索 RAG 失败: {}", e.getMessage());
            }
        }

        // 2. 按需调用真实宿主机探针与告警查询
        if (hostInspectionTools != null) {
            sb.append("### 🖥️ 宿主机真实探针实时采集数据\n\n");
            if (lower.contains("cpu") || lower.contains("负载") || lower.contains("慢") || lower.contains("巡检")) {
                sb.append("#### CPU 与负载指标 (`cpu_inspector`)\n```text\n")
                  .append(hostInspectionTools.inspectHostCpu()).append("\n```\n\n");
            }
            if (lower.contains("内存") || lower.contains("memory") || lower.contains("oom") || lower.contains("gc") || lower.contains("巡检")) {
                sb.append("#### 物理内存指标 (`memory_inspector`)\n```text\n")
                  .append(hostInspectionTools.inspectHostMemory()).append("\n```\n\n");
            }
            if (lower.contains("磁盘") || lower.contains("disk") || lower.contains("空间") || lower.contains("巡检")) {
                sb.append("#### 磁盘分区指标 (`disk_usage`)\n```text\n")
                  .append(hostInspectionTools.inspectHostDisk()).append("\n```\n\n");
            }
            if (!lower.contains("cpu") && !lower.contains("内存") && !lower.contains("memory")
                    && !lower.contains("磁盘") && !lower.contains("disk")) {
                sb.append("#### 主机基础信息 (`server_info`)\n```text\n")
                  .append(hostInspectionTools.inspectHostServerInfo()).append("\n```\n\n");
            }
        }

        if (queryMetricsTools != null && (lower.contains("告警") || lower.contains("alert") || lower.contains("prometheus") || lower.contains("巡检"))) {
            sb.append("### 🚨 Prometheus 活动告警 (`queryPrometheusAlerts`)\n```json\n")
              .append(queryMetricsTools.queryPrometheusAlerts()).append("\n```\n\n");
        }

        return sb.toString();
    }

    /**
     * 执行 ReactAgent 对话（非流式）
     * @param agent ReactAgent 实例
     * @param question 用户问题
     * @return AI 回复
     */
    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException {
        logger.info("执行 ReactAgent.call() - 自动处理工具调用");
        var response = agent.call(question);
        String answer = response.getText();
        if (answer != null && answer.trim().startsWith("Exception:")) {
            throw new RuntimeException(answer.trim());
        }
        logger.info("ReactAgent 对话完成，答案长度: {}", answer != null ? answer.length() : 0);
        return answer;
    }
}
