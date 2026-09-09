package org.example.diagnosis;

import org.example.entity.TaskEntity;
import org.example.entity.TaskStepEntity;
import org.example.model.enums.RiskLevel;
import org.example.model.enums.StepStatus;
import org.example.model.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportBuilderTest {

    @Test
    @DisplayName("Should generate structured SRE Incident Markdown report")
    void testBuildReport() {
        ReportBuilder reportBuilder = new ReportBuilder();

        TaskEntity task = new TaskEntity("task-test-01", "CPU负载异常排查", "CPU_LOAD", TaskStatus.SUCCESS, RiskLevel.LOW, "服务器负载偏高，请排查");
        TaskStepEntity step1 = new TaskStepEntity("task-test-01", 1, "探测CPU负载", "cpu_inspector", "{}");
        step1.setStatus(StepStatus.SUCCESS);
        step1.setCostMs(120L);

        Evidence ev1 = new Evidence("cpu_inspector", "CPU Load: 92%, ALERT: High CPU", "raw", true);

        Hypothesis hyp1 = new Hypothesis("CPU Starvation", "High thread contention", 0.88);
        hyp1.addEvidence("CPU Load > 90%");
        hyp1.addMitigation("Run jstack to identify hot loops");

        String report = reportBuilder.buildReport(task, List.of(step1), List.of(ev1), List.of(hyp1));

        assertNotNull(report);
        assertTrue(report.contains("# 🚨 OpsPilot SRE Incident Diagnosis Report"));
        assertTrue(report.contains("task-test-01"));
        assertTrue(report.contains("## 1. 📋 Incident Overview"));
        assertTrue(report.contains("## 2. 🔍 Gathered Telemetry & Evidence"));
        assertTrue(report.contains("## 3. 🧠 Root Cause Analysis & Ranked Hypotheses"));
        assertTrue(report.contains("CPU Starvation"));
        assertTrue(report.contains("## 4. ⚙️ Execution Step Trail"));
        assertTrue(report.contains("cpu_inspector"));
    }
}
