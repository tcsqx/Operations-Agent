package org.example.engine;

import io.milvus.client.MilvusServiceClient;
import org.example.entity.AuditLogEntity;
import org.example.entity.TaskEntity;
import org.example.entity.TaskStepEntity;
import org.example.model.enums.TaskStatus;
import org.example.repository.AuditLogRepository;
import org.example.repository.TaskRepository;
import org.example.repository.TaskStepRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OpsPilotE2ETest {

    @MockBean
    private MilvusServiceClient milvusClient;

    @Autowired
    private OpsPilotAgentEngine agentEngine;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private TaskStepRepository stepRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("E2E: Should execute full automated diagnosis lifecycle successfully")
    void testFullDiagnosisLifecycle() {
        // 1. Create task
        String prompt = "诊断当前主机服务器环境、CPU利用率与磁盘分区水位";
        TaskEntity createdTask = agentEngine.createTask(prompt, "AUTO_DIAGNOSIS");
        assertNotNull(createdTask.getTaskId());
        assertEquals(TaskStatus.CREATED, createdTask.getStatus());

        // 2. Execute task synchronously
        TaskEntity finishedTask = agentEngine.executeTask(createdTask.getTaskId());

        // 3. Verify final status
        assertEquals(TaskStatus.SUCCESS, finishedTask.getStatus());
        assertNotNull(finishedTask.getPlanJson());
        assertNotNull(finishedTask.getDiagnosisReport());
        assertTrue(finishedTask.getDiagnosisReport().contains("告警分析") || finishedTask.getDiagnosisReport().contains("诊断报告"));
        assertTrue(finishedTask.getDiagnosisReport().contains("server_info"));
        assertTrue(finishedTask.getDiagnosisReport().contains("cpu_inspector"));

        // 4. Verify persisted steps
        List<TaskStepEntity> steps = stepRepository.findByTaskIdOrderByStepIndexAsc(finishedTask.getTaskId());
        assertFalse(steps.isEmpty());
        for (TaskStepEntity step : steps) {
            assertNotNull(step.getToolName());
            assertNotNull(step.getToolOutput());
            assertTrue(step.getCostMs() >= 0);
        }

        // 5. Verify audit logs recorded
        List<AuditLogEntity> audits = auditLogRepository.findByTaskIdOrderByCreatedAtDesc(finishedTask.getTaskId());
        assertFalse(audits.isEmpty());
        assertTrue(audits.stream().anyMatch(a -> "TOOL_EXECUTE".equals(a.getAction())));
    }
}
