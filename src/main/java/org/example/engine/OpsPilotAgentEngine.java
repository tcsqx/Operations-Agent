package org.example.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.diagnosis.Evidence;
import org.example.diagnosis.Hypothesis;
import org.example.diagnosis.HypothesisEngine;
import org.example.diagnosis.ReportBuilder;
import org.example.entity.HitlApprovalEntity;
import org.example.entity.TaskEntity;
import org.example.entity.TaskStepEntity;
import org.example.model.enums.ApprovalStatus;
import org.example.model.enums.RiskLevel;
import org.example.model.enums.StepStatus;
import org.example.model.enums.TaskStatus;
import org.example.repository.TaskRepository;
import org.example.repository.TaskStepRepository;
import org.example.security.AuditLogger;
import org.example.security.RiskClassifier;
import org.example.tools.BaseTool;
import org.example.tools.ToolRegistry;
import org.example.tools.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OpsPilotAgentEngine {

    private static final Logger log = LoggerFactory.getLogger(OpsPilotAgentEngine.class);

    private final TaskRepository taskRepository;
    private final TaskStepRepository stepRepository;
    private final TaskStateMachine stateMachine;
    private final ToolRegistry toolRegistry;
    private final RiskClassifier riskClassifier;
    private final HitlApprovalManager approvalManager;
    private final HypothesisEngine hypothesisEngine;
    private final ReportBuilder reportBuilder;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpsPilotAgentEngine(TaskRepository taskRepository,
                              TaskStepRepository stepRepository,
                              TaskStateMachine stateMachine,
                              ToolRegistry toolRegistry,
                              RiskClassifier riskClassifier,
                              HitlApprovalManager approvalManager,
                              HypothesisEngine hypothesisEngine,
                              ReportBuilder reportBuilder,
                              AuditLogger auditLogger) {
        this.taskRepository = taskRepository;
        this.stepRepository = stepRepository;
        this.stateMachine = stateMachine;
        this.toolRegistry = toolRegistry;
        this.riskClassifier = riskClassifier;
        this.approvalManager = approvalManager;
        this.hypothesisEngine = hypothesisEngine;
        this.reportBuilder = reportBuilder;
        this.auditLogger = auditLogger;
    }

    public TaskEntity createTask(String prompt, String intent) {
        String taskId = "task-" + UUID.randomUUID().toString().substring(0, 8);
        RiskLevel baseRisk = RiskLevel.LOW;
        if (prompt.toLowerCase().contains("restart") || prompt.toLowerCase().contains("kill")) {
            baseRisk = RiskLevel.HIGH;
        }

        TaskEntity task = new TaskEntity(taskId, "Ops Task: " + (intent != null ? intent : "Auto-Diagnosis"),
            intent != null ? intent : "DIAGNOSIS", TaskStatus.CREATED, baseRisk, prompt);

        taskRepository.save(task);
        log.info("[OpsPilotAgentEngine] Task created: {} with prompt: '{}'", taskId, prompt);
        return task;
    }

    public CompletableFuture<TaskEntity> executeTaskAsync(String taskId) {
        return CompletableFuture.supplyAsync(() -> executeTask(taskId));
    }

    public synchronized TaskEntity executeTask(String taskId) {
        TaskEntity task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        try {
            // 1. PLANNING phase
            task = stateMachine.transition(taskId, TaskStatus.PLANNING, "Decomposing task prompt into plan steps");
            List<PlanStep> plan = generatePlan(task.getRawPrompt());
            task.setPlanJson(objectMapper.writeValueAsString(plan));
            task = taskRepository.save(task);

            // Persist initial steps
            stepRepository.deleteByTaskId(taskId);
            for (int i = 0; i < plan.size(); i++) {
                PlanStep ps = plan.get(i);
                TaskStepEntity stepEntity = new TaskStepEntity(
                    taskId, i + 1, ps.stepName, ps.toolName,
                    objectMapper.writeValueAsString(ps.params)
                );
                stepRepository.save(stepEntity);
            }

            // 2. RUNNING phase
            task = stateMachine.transition(taskId, TaskStatus.RUNNING, "Starting tool execution");
            List<TaskStepEntity> persistedSteps = stepRepository.findByTaskIdOrderByStepIndexAsc(taskId);
            List<Evidence> evidences = new ArrayList<>();

            for (TaskStepEntity step : persistedSteps) {
                // Check if step requires approval
                RiskLevel stepRisk = riskClassifier.classify(step.getToolName(), step.getToolParams());
                if (stepRisk == RiskLevel.HIGH || stepRisk == RiskLevel.CRITICAL) {
                    log.info("[OpsPilotAgentEngine] Step {} requires HITL approval", step.getStepIndex());
                    approvalManager.requestApproval(
                        taskId, step.getStepIndex(), stepRisk, step.getToolName(),
                        step.getToolParams(), "High impact operation: " + step.getStepName(),
                        "Rollback or restart service if necessary", 10
                    );
                    // Task enters WAITING_APPROVAL, execution halts until user approves
                    return taskRepository.findById(taskId).orElse(task);
                }

                // Execute safe step
                step.setStatus(StepStatus.RUNNING);
                stepRepository.save(step);

                Map<String, Object> paramsMap = parseParams(step.getToolParams());
                ToolResult result = toolRegistry.executeTool(taskId, step.getToolName(), paramsMap, "ops-agent");

                step.setCostMs(result.getCostMs());
                if (result.isSuccess()) {
                    step.setStatus(StepStatus.SUCCESS);
                    step.setToolOutput(result.getOutput());

                    // Extract telemetry evidence & anomaly detection
                    Evidence ev = extractEvidence(step.getToolName(), result);
                    if (ev != null) {
                        evidences.add(ev);
                    }
                } else {
                    step.setStatus(StepStatus.FAILED);
                    step.setToolOutput("ERROR: " + result.getError());
                    evidences.add(new Evidence(step.getToolName(), "Tool failed: " + result.getError(), result.getError(), true));
                }
                stepRepository.save(step);
            }

            // 3. DIAGNOSING phase
            task = stateMachine.transition(taskId, TaskStatus.DIAGNOSING, "Synthesizing evidence and building SRE diagnosis report");
            List<Hypothesis> hypotheses = hypothesisEngine.analyze(evidences);

            // 4. Transition to SUCCESS
            task = stateMachine.transition(taskId, TaskStatus.SUCCESS, "Diagnosis completed successfully");

            // 5. Report generation & save
            String report = reportBuilder.buildReport(task, persistedSteps, evidences, hypotheses);
            task.setDiagnosisReport(report);
            task.setUpdatedAt(LocalDateTime.now());
            return taskRepository.save(task);

        } catch (Exception e) {
            log.error("[OpsPilotAgentEngine] Task {} failed: {}", taskId, e.getMessage(), e);
            try {
                task.setErrorMsg(e.getMessage());
                task = stateMachine.transition(taskId, TaskStatus.FAILED, e.getMessage());
            } catch (Exception ignored) {
            }
            return taskRepository.findById(taskId).orElse(task);
        }
    }

    /**
     * Resumes execution after a HITL approval is granted.
     */
    public synchronized TaskEntity resumeAfterApproval(String taskId, String approvalId) {
        TaskEntity task = taskRepository.findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        List<TaskStepEntity> pendingSteps = stepRepository.findByTaskIdOrderByStepIndexAsc(taskId);
        List<Evidence> evidences = new ArrayList<>();

        for (TaskStepEntity step : pendingSteps) {
            if (step.getStatus() == StepStatus.SUCCESS) {
                // Reconstruct evidence from previous run
                evidences.add(new Evidence(step.getToolName(), step.getToolOutput(), step.getToolOutput(), false));
                continue;
            }

            step.setStatus(StepStatus.RUNNING);
            stepRepository.save(step);

            Map<String, Object> params = parseParams(step.getToolParams());
            ToolResult result = toolRegistry.executeTool(taskId, step.getToolName(), params, "hitl-approved");

            step.setCostMs(result.getCostMs());
            if (result.isSuccess()) {
                step.setStatus(StepStatus.SUCCESS);
                step.setToolOutput(result.getOutput());
                Evidence ev = extractEvidence(step.getToolName(), result);
                if (ev != null) evidences.add(ev);
            } else {
                step.setStatus(StepStatus.FAILED);
                step.setToolOutput(result.getError());
                evidences.add(new Evidence(step.getToolName(), "Tool failed: " + result.getError(), result.getError(), true));
            }
            stepRepository.save(step);
        }

        // Complete diagnosis
        stateMachine.transition(taskId, TaskStatus.DIAGNOSING, "Synthesizing evidence after approval");
        List<Hypothesis> hypotheses = hypothesisEngine.analyze(evidences);
        task = stateMachine.transition(taskId, TaskStatus.SUCCESS, "Approved execution completed");
        String report = reportBuilder.buildReport(task, pendingSteps, evidences, hypotheses);
        task.setDiagnosisReport(report);
        return taskRepository.save(task);
    }

    private Evidence extractEvidence(String toolName, ToolResult result) {
        String out = result.getOutput() != null ? result.getOutput() : "";
        boolean isAnomaly = false;

        if ("cpu_inspector".equalsIgnoreCase(toolName)) {
            isAnomaly = out.contains("ALERT") || out.contains("High CPU");
            return new Evidence(toolName, out, out, isAnomaly);
        } else if ("memory_inspector".equalsIgnoreCase(toolName)) {
            isAnomaly = out.contains("CRITICAL") || out.contains("WARNING");
            return new Evidence(toolName, out, out, isAnomaly);
        } else if ("disk_usage".equalsIgnoreCase(toolName)) {
            isAnomaly = out.contains("CRITICAL") || out.contains("WARNING");
            return new Evidence(toolName, out, out, isAnomaly);
        } else if ("port_check".equalsIgnoreCase(toolName)) {
            isAnomaly = out.contains("CLOSED");
            return new Evidence(toolName, out, out, isAnomaly);
        } else if ("service_status".equalsIgnoreCase(toolName)) {
            isAnomaly = out.contains("NOT running");
            return new Evidence(toolName, out, out, isAnomaly);
        } else if ("system_log_search".equalsIgnoreCase(toolName)) {
            isAnomaly = out.toLowerCase().contains("exception") || out.toLowerCase().contains("error");
            return new Evidence(toolName, out, out, isAnomaly);
        } else if ("server_info".equalsIgnoreCase(toolName)) {
            return new Evidence(toolName, out, out, false);
        }

        return new Evidence(toolName, out, out, false);
    }

    private Map<String, Object> parseParams(String json) {
        if (json == null || json.trim().isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private List<PlanStep> generatePlan(String prompt) {
        List<PlanStep> steps = new ArrayList<>();
        String lower = prompt.toLowerCase();

        // 1. Always inspect server info
        steps.add(new PlanStep("Collect Server Host Telemetry", "server_info", Collections.emptyMap()));

        // 2. CPU or Performance Inspection
        if (lower.contains("cpu") || lower.contains("load") || lower.contains("performance") || lower.contains("slow") || lower.contains("卡顿") || lower.contains("高")) {
            steps.add(new PlanStep("Inspect CPU Utilization", "cpu_inspector", Collections.emptyMap()));
            Map<String, Object> topParams = new HashMap<>();
            topParams.put("limit", 10);
            steps.add(new PlanStep("Inspect Top Resource Consuming Processes", "process_top", topParams));
        }

        // 3. Memory or OOM Inspection
        if (lower.contains("mem") || lower.contains("oom") || lower.contains("outofmemory") || lower.contains("内存") || lower.contains("leak")) {
            steps.add(new PlanStep("Inspect Physical & JVM Memory", "memory_inspector", Collections.emptyMap()));
            Map<String, Object> topParams = new HashMap<>();
            topParams.put("limit", 10);
            steps.add(new PlanStep("Inspect Top Memory Processes", "process_top", topParams));
        }

        // 4. Disk Inspection
        if (lower.contains("disk") || lower.contains("storage") || lower.contains("space") || lower.contains("磁盘") || lower.contains("空间")) {
            steps.add(new PlanStep("Inspect Disk Partition Usage", "disk_usage", Collections.emptyMap()));
        }

        // 5. Port / Network Inspection
        Pattern portPattern = Pattern.compile("(port|端口)\\s*[:=]?\\s*(\\d+)");
        Matcher portMatcher = portPattern.matcher(lower);
        if (portMatcher.find()) {
            int port = Integer.parseInt(portMatcher.group(2));
            Map<String, Object> pParams = new HashMap<>();
            pParams.put("port", port);
            pParams.put("host", "127.0.0.1");
            steps.add(new PlanStep("Check Port " + port + " Accessibility", "port_check", pParams));
        }

        // 6. Service Status Inspection
        Pattern svcPattern = Pattern.compile("(service|服务|process|进程)\\s*[:=]?\\s*([a-zA-Z0-9_-]+)");
        Matcher svcMatcher = svcPattern.matcher(lower);
        if (svcMatcher.find()) {
            String serviceName = svcMatcher.group(2);
            Map<String, Object> sParams = new HashMap<>();
            sParams.put("service", serviceName);
            steps.add(new PlanStep("Check Service Status: " + serviceName, "service_status", sParams));
        }

        // 7. Default full diagnostic suite if general inquiry
        if (steps.size() == 1) { // Only server_info was added
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
