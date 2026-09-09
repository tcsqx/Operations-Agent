package org.example.controller;

import org.example.dto.v2.ApiResponse;
import org.example.dto.v2.ApprovalRequestDto;
import org.example.dto.v2.TaskCreateRequest;
import org.example.engine.HitlApprovalManager;
import org.example.engine.OpsPilotAgentEngine;
import org.example.entity.HitlApprovalEntity;
import org.example.entity.TaskEntity;
import org.example.entity.TaskStepEntity;
import org.example.model.enums.TaskStatus;
import org.example.repository.TaskRepository;
import org.example.repository.TaskStepRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v2/task")
@CrossOrigin(origins = "*")
public class OpsTaskController {

    private static final Logger log = LoggerFactory.getLogger(OpsTaskController.class);

    private final OpsPilotAgentEngine agentEngine;
    private final TaskRepository taskRepository;
    private final TaskStepRepository stepRepository;
    private final HitlApprovalManager approvalManager;

    public OpsTaskController(OpsPilotAgentEngine agentEngine,
                             TaskRepository taskRepository,
                             TaskStepRepository stepRepository,
                             HitlApprovalManager approvalManager) {
        this.agentEngine = agentEngine;
        this.taskRepository = taskRepository;
        this.stepRepository = stepRepository;
        this.approvalManager = approvalManager;
    }

    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> createTask(@RequestBody TaskCreateRequest request) {
        if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            return ApiResponse.error(400, "Prompt must not be empty");
        }

        TaskEntity task = agentEngine.createTask(request.getPrompt(), request.getIntent());

        Map<String, Object> resp = new HashMap<>();
        resp.put("taskId", task.getTaskId());
        resp.put("status", task.getStatus());
        resp.put("title", task.getTitle());

        if (request.isAutoExecute()) {
            // Trigger asynchronous execution
            agentEngine.executeTaskAsync(task.getTaskId());
            resp.put("execution", "ASYNC_TRIGGERED");
        }

        return ApiResponse.ok(resp);
    }

    @PostMapping("/execute/{taskId}")
    public ApiResponse<TaskEntity> executeSync(@PathVariable String taskId) {
        TaskEntity task = agentEngine.executeTask(taskId);
        return ApiResponse.ok(task);
    }

    @GetMapping("/{taskId}")
    public ApiResponse<Map<String, Object>> getTaskDetails(@PathVariable String taskId) {
        TaskEntity task = taskRepository.findById(taskId)
            .orElse(null);

        if (task == null) {
            return ApiResponse.error(404, "Task not found: " + taskId);
        }

        List<TaskStepEntity> steps = stepRepository.findByTaskIdOrderByStepIndexAsc(taskId);

        Map<String, Object> result = new HashMap<>();
        result.put("task", task);
        result.put("steps", steps);

        return ApiResponse.ok(result);
    }

    @GetMapping("/list")
    public ApiResponse<List<TaskEntity>> listTasks() {
        return ApiResponse.ok(taskRepository.findAllByOrderByCreatedAtDesc());
    }

    @GetMapping(value = "/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTaskProgress(@PathVariable String taskId) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout

        CompletableFuture.runAsync(() -> {
            try {
                TaskEntity task = taskRepository.findById(taskId).orElse(null);
                if (task == null) {
                    emitter.send(SseEmitter.event().name("error").data("Task not found"));
                    emitter.complete();
                    return;
                }

                // Poll task state until terminal state
                TaskStatus lastStatus = null;
                int maxPolls = 60; // 60 * 1s = 60s
                while (maxPolls-- > 0) {
                    task = taskRepository.findById(taskId).orElse(null);
                    if (task == null) break;

                    if (task.getStatus() != lastStatus) {
                        lastStatus = task.getStatus();
                        emitter.send(SseEmitter.event().name("status").data(Map.of(
                            "taskId", taskId,
                            "status", task.getStatus().name()
                        )));
                    }

                    if (task.getStatus() == TaskStatus.SUCCESS) {
                        emitter.send(SseEmitter.event().name("report").data(Map.of(
                            "report", task.getDiagnosisReport() != null ? task.getDiagnosisReport() : ""
                        )));
                        emitter.send(SseEmitter.event().name("complete").data("Done"));
                        break;
                    } else if (task.getStatus() == TaskStatus.FAILED || task.getStatus() == TaskStatus.CANCELLED) {
                        emitter.send(SseEmitter.event().name("failed").data(Map.of(
                            "error", task.getErrorMsg() != null ? task.getErrorMsg() : "Task terminated"
                        )));
                        break;
                    } else if (task.getStatus() == TaskStatus.WAITING_APPROVAL) {
                        emitter.send(SseEmitter.event().name("approval_required").data(Map.of(
                            "message", "Task paused waiting for human approval"
                        )));
                        break;
                    }

                    Thread.sleep(1000);
                }

                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                }
            }
        });

        return emitter;
    }

    @PostMapping("/approve")
    public ApiResponse<Map<String, Object>> handleApproval(@RequestBody ApprovalRequestDto request) {
        if (request.getApprovalId() == null || request.getAction() == null) {
            return ApiResponse.error(400, "Missing approvalId or action");
        }

        String action = request.getAction().toUpperCase();
        HitlApprovalEntity approval;

        if ("APPROVE".equals(action)) {
            approval = approvalManager.approve(request.getApprovalId(), request.getOperator(), request.getComment());
            // Resume task execution
            agentEngine.executeTaskAsync(approval.getTaskId());
        } else if ("REJECT".equals(action)) {
            approval = approvalManager.reject(request.getApprovalId(), request.getOperator(), request.getComment());
        } else {
            return ApiResponse.error(400, "Unsupported action: " + action);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("approvalId", approval.getApprovalId());
        resp.put("status", approval.getStatus());
        resp.put("taskId", approval.getTaskId());

        return ApiResponse.ok(resp);
    }

    @GetMapping("/approvals/pending")
    public ApiResponse<List<HitlApprovalEntity>> getPendingApprovals() {
        return ApiResponse.ok(approvalManager.getPendingApprovals());
    }
}
