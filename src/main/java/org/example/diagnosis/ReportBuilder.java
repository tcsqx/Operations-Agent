package org.example.diagnosis;

import org.example.entity.TaskEntity;
import org.example.entity.TaskStepEntity;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ReportBuilder {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String buildReport(TaskEntity task, List<TaskStepEntity> steps, List<Evidence> evidences, List<Hypothesis> hypotheses) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("# 🚨 OpsPilot SRE Incident Diagnosis Report\n\n");
        sb.append("**Task ID**: `").append(task.getTaskId()).append("` | ");
        sb.append("**Status**: `").append(task.getStatus()).append("` | ");
        sb.append("**Risk Level**: `").append(task.getRiskLevel() != null ? task.getRiskLevel() : "LOW").append("` | ");
        sb.append("**Generated At**: ").append(task.getUpdatedAt().format(FORMATTER)).append("\n\n");

        sb.append("---\n\n");

        // 1. Incident Overview
        sb.append("## 1. 📋 Incident Overview\n\n");
        sb.append("- **Title / Intent**: ").append(task.getTitle() != null ? task.getTitle() : task.getIntent()).append("\n");
        sb.append("- **Trigger Description**: ").append(task.getRawPrompt()).append("\n");
        sb.append("- **Diagnosis Summary**: ");
        if (hypotheses != null && !hypotheses.isEmpty()) {
            Hypothesis top = hypotheses.get(0);
            sb.append("**").append(top.getName()).append("** (Confidence: **")
              .append(String.format("%.0f%%", top.getConfidenceScore() * 100))
              .append("**)\n\n");
        } else {
            sb.append("Analysis concluded with nominal telemetry metrics.\n\n");
        }

        // 2. Telemetry Evidence
        sb.append("## 2. 🔍 Gathered Telemetry & Evidence\n\n");
        if (evidences == null || evidences.isEmpty()) {
            sb.append("*No specific probe telemetry recorded.*\n\n");
        } else {
            sb.append("| Source Tool | Status | Observation Details | Time |\n");
            sb.append("| :--- | :---: | :--- | :--- |\n");
            for (Evidence ev : evidences) {
                String flag = ev.isAnomaly() ? "⚠️ **Anomaly**" : "✅ Normal";
                String cleanObs = ev.getObservation().replace("\n", "<br/>");
                sb.append(String.format("| `%s` | %s | %s | %s |\n",
                    ev.getToolName(), flag, cleanObs, ev.getTimestamp().format(FORMATTER)));
            }
            sb.append("\n");
        }

        // 3. Root Cause Analysis (RCA) & Hypotheses
        sb.append("## 3. 🧠 Root Cause Analysis & Ranked Hypotheses\n\n");
        if (hypotheses == null || hypotheses.isEmpty()) {
            sb.append("*No hypotheses evaluated.*\n\n");
        } else {
            int rank = 1;
            for (Hypothesis hyp : hypotheses) {
                sb.append(String.format("### %d. %s (Confidence: **%.0f%%**)\n\n",
                    rank++, hyp.getName(), hyp.getConfidenceScore() * 100));
                sb.append("- **Root Cause Mechanism**: ").append(hyp.getRootCause()).append("\n");
                if (!hyp.getSupportingEvidences().isEmpty()) {
                    sb.append("- **Supporting Evidence**:\n");
                    for (String ev : hyp.getSupportingEvidences()) {
                        sb.append("  - ").append(ev.replace("\n", " ")).append("\n");
                    }
                }
                if (!hyp.getSuggestedMitigations().isEmpty()) {
                    sb.append("- **Recommended Actions / Fixes**:\n");
                    for (String mit : hyp.getSuggestedMitigations()) {
                        sb.append("  - 💡 ").append(mit).append("\n");
                    }
                }
                sb.append("\n");
            }
        }

        // 4. Execution Step Trail
        sb.append("## 4. ⚙️ Execution Step Trail\n\n");
        if (steps == null || steps.isEmpty()) {
            sb.append("*No execution steps recorded.*\n\n");
        } else {
            sb.append("| # | Step | Tool | Status | Duration |\n");
            sb.append("| :---: | :--- | :--- | :---: | :---: |\n");
            for (TaskStepEntity st : steps) {
                sb.append(String.format("| %d | %s | `%s` | %s | %d ms |\n",
                    st.getStepIndex(),
                    st.getStepName() != null ? st.getStepName() : "-",
                    st.getToolName() != null ? st.getToolName() : "-",
                    st.getStatus(),
                    st.getCostMs() != null ? st.getCostMs() : 0));
            }
            sb.append("\n");
        }

        sb.append("---\n*Generated automatically by OpsPilot SRE AI Agent.*");
        return sb.toString();
    }
}
