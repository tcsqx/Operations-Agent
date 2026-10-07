package org.example.engine;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.agent.tool.QueryLogsTools;
import org.example.agent.tool.QueryMetricsTools;
import org.example.entity.HitlApprovalEntity;
import org.example.entity.TaskEntity;
import org.example.entity.TaskStepEntity;
import org.example.model.enums.RiskLevel;
import org.example.model.enums.StepStatus;
import org.example.model.enums.TaskStatus;
import org.example.repository.TaskRepository;
import org.example.repository.TaskStepRepository;
import org.example.security.AuditLogger;
import org.example.security.RiskClassifier;
import org.example.service.AiOpsService;
import org.example.service.ChatService;
import org.example.service.VectorSearchService;
import org.example.tools.ToolRegistry;
import org.example.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能运维任务编排引擎 (OpsPilotAgentEngine)
 * 将任务状态机 (TaskStateMachine)、安全分级与人工审批 (RiskClassifier + HitlApprovalManager)、
 * 宿主机真实探针 (ToolRegistry) 与原版多 Agent 协同引擎 (AiOpsService + ChatService + RAG) 深度融合。
 */
@Service
public class OpsPilotAgentEngine {

    private static final Logger log = LoggerFactory.getLogger(OpsPilotAgentEngine.class);

    private final TaskRepository taskRepository;
    private final TaskStepRepository stepRepository;
    private final TaskStateMachine stateMachine;
    private final ToolRegistry toolRegistry;
    private final RiskClassifier riskClassifier;
    private final HitlApprovalManager approvalManager;
    private final AuditLogger auditLogger;
    private final AiOpsService aiOpsService;
    private final ChatService chatService;
    private final VectorSearchService vectorSearchService;
    private final QueryMetricsTools queryMetricsTools;
    private final QueryLogsTools queryLogsTools;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashScopeApiKey;

    @Autowired
    public OpsPilotAgentEngine(TaskRepository taskRepository,
                               TaskStepRepository stepRepository,
                               TaskStateMachine stateMachine,
                               ToolRegistry toolRegistry,
                               RiskClassifier riskClassifier,
                               HitlApprovalManager approvalManager,
                               AuditLogger auditLogger,
                               @Autowired(required = false) AiOpsService aiOpsService,
                               @Autowired(required = false) ChatService chatService,
                               @Autowired(required = false) VectorSearchService vectorSearchService,
                               @Autowired(required = false) QueryMetricsTools queryMetricsTools,
                               @Autowired(required = false) QueryLogsTools queryLogsTools) {
        this.taskRepository = taskRepository;
        this.stepRepository = stepRepository;
        this.stateMachine = stateMachine;
        this.toolRegistry = toolRegistry;
        this.riskClassifier = riskClassifier;
        this.approvalManager = approvalManager;
        this.auditLogger = auditLogger;
        this.aiOpsService = aiOpsService;
        this.chatService = chatService;
        this.vectorSearchService = vectorSearchService;
        this.queryMetricsTools = queryMetricsTools;
        this.queryLogsTools = queryLogsTools;
    }

    public TaskEntity createTask(String prompt, String intent) {
        String taskId = "task-" + UUID.randomUUID().toString().substring(0, 8);
        RiskLevel baseRisk = riskClassifier.classify("safe_shell", prompt);
        if (baseRisk == RiskLevel.MEDIUM && !prompt.toLowerCase().contains("shell")) {
            baseRisk = RiskLevel.LOW;
        }
        String lower = prompt.toLowerCase();
        if (lower.contains("restart") || lower.contains("kill") || lower.contains("stop") || lower.contains("重启")) {
            baseRisk = RiskLevel.HIGH;
        }

        TaskEntity task = new TaskEntity(taskId, "Ops Task: " + (intent != null ? intent : "Auto-Diagnosis"),
                intent != null ? intent : "DIAGNOSIS", TaskStatus.CREATED, baseRisk, prompt);

        taskRepository.save(task);
        log.info("[OpsPilotAgentEngine] Task created: {} with prompt: '{}', baseRisk: {}", taskId, prompt, baseRisk);
        return task;
    }

    public CompletableFuture<TaskEntity> executeTaskAsync(String taskId) {
        return CompletableFuture.supplyAsync(() -> executeTask(taskId));
    }

    public CompletableFuture<TaskEntity> resumeAfterApprovalAsync(String taskId, String approvalId) {
        return CompletableFuture.supplyAsync(() -> resumeAfterApproval(taskId, approvalId));
    }

    public synchronized TaskEntity executeTask(String taskId) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        // 若任务正处于 WAITING_APPROVAL 或 RUNNING（审批后恢复），则转交 resumeAfterApproval
        if (task.getStatus() == TaskStatus.RUNNING || task.getStatus() == TaskStatus.WAITING_APPROVAL) {
            return resumeAfterApproval(taskId, null);
        }

        toolRegistry.bindActiveTask(taskId);
        try {
            // 1. PLANNING 阶段
            task = stateMachine.transition(taskId, TaskStatus.PLANNING, "Planning diagnosis and remediation workflow");
            stepRepository.deleteByTaskId(taskId);

            // 检查是否包含高危操作指令需要前置审批或规划步骤
            List<PlanStep> plannedSteps = generateBaselineSteps(task.getRawPrompt());
            task.setPlanJson(objectMapper.writeValueAsString(plannedSteps));
            task = taskRepository.save(task);

            // 2. RUNNING 阶段：优先使用 LLM SupervisorAgent / ReactAgent 驱动真实工具调用与推理
            task = stateMachine.transition(taskId, TaskStatus.RUNNING, "Executing Agent workflow and host telemetry probes");

            // 若任务中包含明确的高危命令步骤（如重启服务、kill进程），先执行前置只读探针并在高危步骤处触发 HITL 审批拦截
            boolean hasHighRiskStep = plannedSteps.stream()
                    .anyMatch(ps -> {
                        RiskLevel r = riskClassifier.classify(ps.toolName, ps.params != null ? ps.params.toString() : "");
                        return r == RiskLevel.HIGH || r == RiskLevel.CRITICAL;
                    });

            if (!hasHighRiskStep && isLlmConfigured()) {
                Optional<String> llmReport = tryExecuteWithLlmAgent(task);
                TaskEntity refreshed = taskRepository.findById(taskId).orElse(task);
                if (refreshed.getStatus() == TaskStatus.WAITING_APPROVAL) {
                    log.info("[OpsPilotAgentEngine] Task {} paused for HITL approval during LLM Agent execution", taskId);
                    return refreshed;
                }
                if (llmReport.isPresent() && !llmReport.get().isBlank()) {
                    task = stateMachine.transition(taskId, TaskStatus.DIAGNOSING, "LLM Agent synthesizing final SRE report");
                    task = stateMachine.transition(taskId, TaskStatus.SUCCESS, "LLM Agent diagnosis completed successfully");
                    task.setDiagnosisReport(llmReport.get());
                    task.setUpdatedAt(LocalDateTime.now());
                    archiveReportIfPossible(taskId, task.getTitle(), llmReport.get());
                    return taskRepository.save(task);
                }
            }

            // 持久化计划步骤并逐步执行（含 HITL 高危操作审批拦截）
            stepRepository.deleteByTaskId(taskId);
            for (int i = 0; i < plannedSteps.size(); i++) {
                PlanStep ps = plannedSteps.get(i);
                TaskStepEntity stepEntity = new TaskStepEntity(
                        taskId, i + 1, ps.stepName, ps.toolName,
                        objectMapper.writeValueAsString(ps.params)
                );
                stepRepository.save(stepEntity);
            }

            List<TaskStepEntity> persistedSteps = stepRepository.findByTaskIdOrderByStepIndexAsc(taskId);
            for (TaskStepEntity step : persistedSteps) {
                RiskLevel stepRisk = riskClassifier.classify(step.getToolName(), step.getToolParams());
                if (stepRisk == RiskLevel.HIGH || stepRisk == RiskLevel.CRITICAL) {
                    log.info("[OpsPilotAgentEngine] Step {} ({}) requires HITL approval (risk: {})",
                            step.getStepIndex(), step.getStepName(), stepRisk);
                    approvalManager.requestApproval(
                            taskId, step.getStepIndex(), stepRisk, step.getToolName(),
                            step.getToolParams(), "High impact operation: " + step.getStepName(),
                            "Verify service status and rollback if necessary", 10
                    );
                    return taskRepository.findById(taskId).orElse(task);
                }

                executeSingleStep(taskId, step, "ops-agent");
            }

            // 3. DIAGNOSING 阶段：将采集到的真实探针证据 + RAG 知识库交给 LLM（若在线）或生成基于真实证据链的报告
            task = stateMachine.transition(taskId, TaskStatus.DIAGNOSING, "Synthesizing telemetry evidence and RAG knowledge");
            List<TaskStepEntity> executedSteps = stepRepository.findByTaskIdOrderByStepIndexAsc(taskId);
            String finalReport = synthesizeDiagnosisReport(task, executedSteps);

            // 4. SUCCESS 阶段
            task = stateMachine.transition(taskId, TaskStatus.SUCCESS, "Diagnosis completed successfully");
            task.setDiagnosisReport(finalReport);
            task.setUpdatedAt(LocalDateTime.now());
            archiveReportIfPossible(taskId, task.getTitle(), finalReport);
            return taskRepository.save(task);

        } catch (Exception e) {
            log.error("[OpsPilotAgentEngine] Task {} failed: {}", taskId, e.getMessage(), e);
            try {
                task.setErrorMsg(e.getMessage());
                task = stateMachine.transition(taskId, TaskStatus.FAILED, e.getMessage());
            } catch (Exception ignored) {
            }
            return taskRepository.findById(taskId).orElse(task);
        } finally {
            toolRegistry.clearActiveTask();
        }
    }

    /**
     * 人工审批通过后恢复任务执行（修复原版 handleApproval 调用 executeTask 导致非法状态转换的 Bug）
     */
    public synchronized TaskEntity resumeAfterApproval(String taskId, String approvalId) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        toolRegistry.bindActiveTask(taskId);
        try {
            if (task.getStatus() == TaskStatus.WAITING_APPROVAL) {
                task = stateMachine.transition(taskId, TaskStatus.RUNNING,
                        "Resumed after approval" + (approvalId != null ? (" (" + approvalId + ")") : ""));
            }

            List<TaskStepEntity> steps = stepRepository.findByTaskIdOrderByStepIndexAsc(taskId);
            for (TaskStepEntity step : steps) {
                if (step.getStatus() == StepStatus.SUCCESS) {
                    continue;
                }
                executeSingleStep(taskId, step, "hitl-approved");
            }

            task = stateMachine.transition(taskId, TaskStatus.DIAGNOSING, "Synthesizing report after approved execution");
            List<TaskStepEntity> updatedSteps = stepRepository.findByTaskIdOrderByStepIndexAsc(taskId);
            String report = synthesizeDiagnosisReport(task, updatedSteps);

            task = stateMachine.transition(taskId, TaskStatus.SUCCESS, "Approved execution completed");
            task.setDiagnosisReport(report);
            task.setUpdatedAt(LocalDateTime.now());
            archiveReportIfPossible(taskId, task.getTitle(), report);
            return taskRepository.save(task);

        } catch (Exception e) {
            log.error("[OpsPilotAgentEngine] Resume task {} failed: {}", taskId, e.getMessage(), e);
            try {
                task.setErrorMsg(e.getMessage());
                task = stateMachine.transition(taskId, TaskStatus.FAILED, e.getMessage());
            } catch (Exception ignored) {
            }
            return taskRepository.findById(taskId).orElse(task);
        } finally {
            toolRegistry.clearActiveTask();
        }
    }

    private void executeSingleStep(String taskId, TaskStepEntity step, String operator) {
        step.setStatus(StepStatus.RUNNING);
        stepRepository.save(step);

        Map<String, Object> paramsMap = parseParams(step.getToolParams());
        ToolResult result = toolRegistry.executeTool(taskId, step.getToolName(), paramsMap, operator);

        step.setCostMs(result.getCostMs());
        if (result.isSuccess()) {
            step.setStatus(StepStatus.SUCCESS);
            step.setToolOutput(result.getOutput());
        } else {
            step.setStatus(StepStatus.FAILED);
            step.setToolOutput("ERROR: " + result.getError());
        }
        stepRepository.save(step);
    }

    private boolean isLlmConfigured() {
        return aiOpsService != null
                && chatService != null
                && dashScopeApiKey != null
                && !dashScopeApiKey.trim().isEmpty()
                && !dashScopeApiKey.equals("your-api-key-here");
    }

    /**
     * 调用原版 AiOpsService (SupervisorAgent: Planner + Executor) 执行多 Agent 闭环推理
     */
    private Optional<String> tryExecuteWithLlmAgent(TaskEntity task) {
        try {
            log.info("[OpsPilotAgentEngine] Invoking AiOpsService (SupervisorAgent) for task {}", task.getTaskId());
            DashScopeApi api = chatService.createDashScopeApi();
            DashScopeChatModel chatModel = chatService.createChatModel(api, 0.3, 6000, 0.9);

            Optional<OverAllState> stateOpt = aiOpsService.executeAiOpsAnalysis(
                    chatModel, chatService.getToolCallbacks(), task.getRawPrompt());
            if (stateOpt.isPresent()) {
                Optional<String> reportOpt = aiOpsService.extractFinalReport(stateOpt.get());
                if (reportOpt.isPresent() && !reportOpt.get().isBlank()) {
                    return reportOpt;
                }
            }
        } catch (Exception e) {
            log.warn("[OpsPilotAgentEngine] LLM SupervisorAgent invocation failed ({}), falling back to grounded probe + RAG execution",
                    e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 基于真实探针输出、Prometheus 告警、CLS 日志与 RAG 知识库文档生成诊断报告
     * 若 DashScope API 可用，优先通过 ReactAgent 汇总生成；若在无 Key 的离线测试环境中，生成完全基于真实证据与 SOP 的结构化报告（无任何硬编码伪造置信度）。
     */
    private String synthesizeDiagnosisReport(TaskEntity task, List<TaskStepEntity> steps) {
        // 1. 检索 RAG 知识库中的匹配 SOP 分片
        List<VectorSearchService.SearchResult> ragResults = Collections.emptyList();
        if (vectorSearchService != null) {
            try {
                ragResults = vectorSearchService.searchSimilarDocuments(task.getRawPrompt(), 3);
            } catch (Exception e) {
                log.warn("RAG 检索异常: {}", e.getMessage());
            }
        }

        // 2. 获取当前活跃告警摘要（如果可用）
        String alertsJson = null;
        if (queryMetricsTools != null) {
            try {
                alertsJson = queryMetricsTools.queryPrometheusAlerts();
            } catch (Exception ignored) {
            }
        }

        // 3. 若配置了有效 DashScope API Key，调用大模型基于已采集的真实证据链生成报告
        if (isLlmConfigured()) {
            try {
                DashScopeApi api = chatService.createDashScopeApi();
                DashScopeChatModel model = chatService.createChatModel(api, 0.2, 4000, 0.9);
                StringBuilder prompt = new StringBuilder();
                prompt.append("请根据以下已执行的真实宿主机探针输出、Prometheus 告警以及内部 RAG 运维知识库手册，生成一份严谨的 Markdown 格式《告警与故障诊断报告》。\n");
                prompt.append("严禁编造任何未在证据中出现的数据。\n\n");
                prompt.append("## 任务原始请求\n").append(task.getRawPrompt()).append("\n\n");
                prompt.append("## 宿主机探针执行记录\n");
                for (TaskStepEntity step : steps) {
                    prompt.append(String.format("- 步骤 %d [%s / %s] (%s, %dms):\n```\n%s\n```\n",
                            step.getStepIndex(), step.getStepName(), step.getToolName(),
                            step.getStatus(), step.getCostMs() != null ? step.getCostMs() : 0L,
                            step.getToolOutput() != null ? step.getToolOutput() : ""));
                }
                if (ragResults != null && !ragResults.isEmpty()) {
                    prompt.append("\n## 内部运维知识库参考 (RAG)\n");
                    for (VectorSearchService.SearchResult r : ragResults) {
                        prompt.append("- [相关度 ").append(r.getScore()).append("]: ").append(r.getContent()).append("\n\n");
                    }
                }
                ReactAgent reportAgent = ReactAgent.builder()
                        .name("sre_report_synthesizer")
                        .model(model)
                        .systemPrompt("你是资深 SRE 架构师，负责基于真实采集证据与知识库手册输出结构化 Markdown 诊断报告。")
                        .build();
                String llmReport = chatService.executeChat(reportAgent, prompt.toString());
                if (llmReport != null && !llmReport.isBlank()) {
                    return llmReport;
                }
            } catch (Exception e) {
                log.warn("[OpsPilotAgentEngine] LLM report synthesis unavailable ({}), building grounded telemetry + RAG report", e.getMessage());
            }
        }

        // 4. 离线/无 API Key 环境下的真实证据链 + RAG 知识库汇总报告（绝不硬编码虚假置信度）
        StringBuilder md = new StringBuilder();
        md.append("# 告警分析与系统诊断报告\n\n");
        md.append("- **任务 ID**: `").append(task.getTaskId()).append("`\n");
        md.append("- **任务意图**: `").append(task.getIntent()).append("`\n");
        md.append("- **原始请求**: ").append(task.getRawPrompt()).append("\n");
        md.append("- **风险级别**: `").append(task.getRiskLevel()).append("`\n");
        md.append("- **生成时间**: ").append(LocalDateTime.now()).append("\n\n");
        md.append("---\n\n");

        md.append("## 🔍 真实探针执行与证据链 (Ground-Truth Telemetry)\n\n");
        if (steps.isEmpty()) {
            md.append("未记录探针步骤。\n\n");
        } else {
            for (TaskStepEntity step : steps) {
                md.append("### 步骤 ").append(step.getStepIndex()).append(": ").append(step.getStepName())
                  .append(" (`").append(step.getToolName()).append("`)\n");
                md.append("- **执行状态**: `").append(step.getStatus()).append("` (耗时: ")
                  .append(step.getCostMs() != null ? step.getCostMs() : 0).append(" ms)\n");
                md.append("- **采集输出**:\n```text\n")
                  .append(step.getToolOutput() != null ? step.getToolOutput().trim() : "(无输出)")
                  .append("\n```\n\n");
            }
        }

        if (ragResults != null && !ragResults.isEmpty()) {
            md.append("---\n\n");
            md.append("## 📚 关联内部运维知识库指南 (Hybrid RAG Matched SOPs)\n\n");
            for (int i = 0; i < ragResults.size(); i++) {
                VectorSearchService.SearchResult doc = ragResults.get(i);
                md.append("### 参考手册 ").append(i + 1).append(" (匹配度: `").append(doc.getScore()).append("`)\n");
                md.append(doc.getContent().trim()).append("\n\n");
            }
        }

        if (alertsJson != null && alertsJson.contains("\"success\" : true")) {
            md.append("---\n\n");
            md.append("## 🚨 Prometheus 活动告警快照\n\n");
            md.append("```json\n").append(alertsJson.trim()).append("\n```\n\n");
        }

        md.append("---\n\n");
        md.append("## 📊 诊断结论与后续建议\n\n");
        long failedCount = steps.stream().filter(s -> s.getStatus() == StepStatus.FAILED).count();
        md.append("- **探针执行统计**: 共执行 ").append(steps.size()).append(" 个步骤，成功 ")
          .append(steps.size() - failedCount).append(" 个，失败 ").append(failedCount).append(" 个。\n");
        md.append("- **知识库命中**: 检索到 ").append(ragResults != null ? ragResults.size() : 0).append(" 条高相关度内部 SOP 条目。\n");
        md.append("- **建议措施**: 请结合上方宿主机实测指标与匹配的《内部运维知识库指南》章节执行后续排查与修复。\n");

        return md.toString();
    }

    private void archiveReportIfPossible(String taskId, String title, String report) {
        if (vectorSearchService != null && report != null && !report.isBlank()) {
            try {
                vectorSearchService.archiveIncidentReport(taskId, title, report);
            } catch (Exception e) {
                log.warn("归档故障报告失败: {}", e.getMessage());
            }
        }
    }

    private Map<String, Object> parseParams(String json) {
        if (json == null || json.trim().isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * 生成基线探针采集与操作步骤（支持高危命令识别以触发 HITL 审批流）
     */
    private List<PlanStep> generateBaselineSteps(String prompt) {
        List<PlanStep> steps = new ArrayList<>();
        String lower = prompt.toLowerCase();

        // 1. 基础宿主机信息采集
        steps.add(new PlanStep("Collect Server Host Telemetry", "server_info", Collections.emptyMap()));

        // 2. CPU / 负载排查
        if (lower.contains("cpu") || lower.contains("load") || lower.contains("performance") || lower.contains("slow") || lower.contains("卡顿") || lower.contains("高")) {
            steps.add(new PlanStep("Inspect CPU Utilization", "cpu_inspector", Collections.emptyMap()));
            Map<String, Object> topParams = new HashMap<>();
            topParams.put("limit", 10);
            steps.add(new PlanStep("Inspect Top Resource Consuming Processes", "process_top", topParams));
        }

        // 3. 内存 / OOM 排查
        if (lower.contains("mem") || lower.contains("oom") || lower.contains("outofmemory") || lower.contains("内存") || lower.contains("leak")) {
            steps.add(new PlanStep("Inspect Physical & JVM Memory", "memory_inspector", Collections.emptyMap()));
            Map<String, Object> topParams = new HashMap<>();
            topParams.put("limit", 10);
            steps.add(new PlanStep("Inspect Top Memory Processes", "process_top", topParams));
        }

        // 4. 磁盘空间排查
        if (lower.contains("disk") || lower.contains("storage") || lower.contains("space") || lower.contains("磁盘") || lower.contains("空间")) {
            steps.add(new PlanStep("Inspect Disk Partition Usage", "disk_usage", Collections.emptyMap()));
        }

        // 5. 端口连通性检测
        Pattern portPattern = Pattern.compile("(port|端口)\\s*[:=]?\\s*(\\d+)");
        Matcher portMatcher = portPattern.matcher(lower);
        if (portMatcher.find()) {
            int port = Integer.parseInt(portMatcher.group(2));
            Map<String, Object> pParams = new HashMap<>();
            pParams.put("port", port);
            pParams.put("host", "127.0.0.1");
            steps.add(new PlanStep("Check Port " + port + " Accessibility", "port_check", pParams));
        }

        // 6. 服务状态检测
        Pattern svcPattern = Pattern.compile("(service|服务|process|进程)\\s*[:=]?\\s*([a-zA-Z0-9_-]+)");
        Matcher svcMatcher = svcPattern.matcher(lower);
        if (svcMatcher.find()) {
            String serviceName = svcMatcher.group(2);
            Map<String, Object> sParams = new HashMap<>();
            sParams.put("service", serviceName);
            steps.add(new PlanStep("Check Service Status: " + serviceName, "service_status", sParams));
        }

        // 7. 高危变更/重启命令检测（交由 safe_shell 并在执行前由 RiskClassifier + HitlApprovalManager 拦截）
        if (lower.contains("restart") || lower.contains("kill") || lower.contains("重启")) {
            Map<String, Object> shellParams = new HashMap<>();
            shellParams.put("command", "echo \"Simulated controlled service restart for: " + prompt.replace("\"", "'") + "\"");
            steps.add(new PlanStep("Execute High-Risk Remediation Operation (" + prompt + ")", "safe_shell", shellParams));
        }

        // 8. 默认综合巡检
        if (steps.size() == 1) {
            steps.add(new PlanStep("Inspect CPU Load", "cpu_inspector", Collections.emptyMap()));
            steps.add(new PlanStep("Inspect Memory Consumption", "memory_inspector", Collections.emptyMap()));
            steps.add(new PlanStep("Inspect Disk Space", "disk_usage", Collections.emptyMap()));
            Map<String, Object> topParams = new HashMap<>();
            topParams.put("limit", 5);
            steps.add(new PlanStep("Inspect Top Processes", "process_top", topParams));
        }

        return steps;
    }

    public static class PlanStep {
        public String stepName;
        public String toolName;
        public Map<String, Object> params;

        public PlanStep() {}

        public PlanStep(String stepName, String toolName, Map<String, Object> params) {
            this.stepName = stepName;
            this.toolName = toolName;
            this.params = params != null ? params : new HashMap<>();
        }
    }
}
