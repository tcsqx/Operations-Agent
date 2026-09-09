package org.example.controller;

import org.example.dto.v2.AlertmanagerWebhookPayload;
import org.example.dto.v2.ApiResponse;
import org.example.engine.AlertDeduplicator;
import org.example.engine.OpsPilotAgentEngine;
import org.example.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/webhook")
@CrossOrigin(origins = "*")
public class AlertWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AlertWebhookController.class);

    private final OpsPilotAgentEngine agentEngine;
    private final AlertDeduplicator alertDeduplicator;

    public AlertWebhookController(OpsPilotAgentEngine agentEngine, AlertDeduplicator alertDeduplicator) {
        this.agentEngine = agentEngine;
        this.alertDeduplicator = alertDeduplicator;
    }

    @PostMapping("/alertmanager")
    public ApiResponse<Map<String, Object>> handleAlertmanagerWebhook(@RequestBody AlertmanagerWebhookPayload payload) {
        if (payload == null || payload.getAlerts() == null || payload.getAlerts().isEmpty()) {
            return ApiResponse.ok("No alerts in webhook payload", Collections.emptyMap());
        }

        List<String> triggeredTaskIds = new ArrayList<>();
        int suppressedCount = 0;

        for (AlertmanagerWebhookPayload.AlertItem alert : payload.getAlerts()) {
            // Only trigger investigation for firing alerts
            if (!"firing".equalsIgnoreCase(alert.getStatus())) {
                continue;
            }

            String fingerprint = alert.getFingerprint();
            if (fingerprint == null && alert.getLabels() != null) {
                fingerprint = alert.getLabels().getOrDefault("alertname", "unknown") + "_" +
                              alert.getLabels().getOrDefault("instance", "default");
            }

            if (alertDeduplicator.isDuplicate(fingerprint)) {
                suppressedCount++;
                continue;
            }

            String alertName = alert.getLabels() != null ? alert.getLabels().getOrDefault("alertname", "Alert") : "Alert";
            String instance = alert.getLabels() != null ? alert.getLabels().getOrDefault("instance", "127.0.0.1") : "127.0.0.1";
            String severity = alert.getLabels() != null ? alert.getLabels().getOrDefault("severity", "warning") : "warning";
            String description = alert.getAnnotations() != null ? alert.getAnnotations().getOrDefault("description", alertName) : alertName;

            String prompt = String.format("Alertmanager alert triggered: %s (Severity: %s, Instance: %s). Description: %s. Please perform diagnosis.",
                alertName, severity, instance, description);

            TaskEntity task = agentEngine.createTask(prompt, "ALERT_TRIGGERED");
            agentEngine.executeTaskAsync(task.getTaskId());
            triggeredTaskIds.add(task.getTaskId());

            log.info("[AlertWebhook] Created auto-diagnosis task {} for alert '{}' on instance '{}'",
                task.getTaskId(), alertName, instance);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("triggered_tasks", triggeredTaskIds);
        resp.put("suppressed_duplicates", suppressedCount);
        resp.put("status", "PROCESSED");

        return ApiResponse.ok(resp);
    }
}
