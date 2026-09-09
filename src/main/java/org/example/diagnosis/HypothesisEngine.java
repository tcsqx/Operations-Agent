package org.example.diagnosis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class HypothesisEngine {

    private static final Logger log = LoggerFactory.getLogger(HypothesisEngine.class);

    /**
     * Synthesizes gathered evidence and generates ranked diagnostic hypotheses.
     */
    public List<Hypothesis> analyze(List<Evidence> evidences) {
        List<Hypothesis> hypotheses = new ArrayList<>();
        if (evidences == null || evidences.isEmpty()) {
            Hypothesis unknown = new Hypothesis("Indeterminate State", "No telemetry or probe evidence collected yet", 0.1);
            unknown.addMitigation("Run server_info, cpu_inspector, and memory_inspector tools to collect telemetry facts.");
            hypotheses.add(unknown);
            return hypotheses;
        }

        // Diagnostic rules evaluation
        evaluateMemoryIssues(evidences, hypotheses);
        evaluateCpuIssues(evidences, hypotheses);
        evaluateDiskIssues(evidences, hypotheses);
        evaluatePortServiceIssues(evidences, hypotheses);
        evaluateLogErrors(evidences, hypotheses);

        if (hypotheses.isEmpty()) {
            Hypothesis normal = new Hypothesis("System Healthy / Transient Anomaly", "All executed telemetry probes reported metrics within nominal thresholds", 0.85);
            normal.addMitigation("Continue normal monitoring; inspect upstream traffic or network load balancer if user reports persist.");
            hypotheses.add(normal);
        }

        // Sort descending by confidence
        hypotheses.sort((h1, h2) -> Double.compare(h2.getConfidenceScore(), h1.getConfidenceScore()));
        log.info("[HypothesisEngine] Evaluated {} evidences, generated {} hypotheses (top: {})",
            evidences.size(), hypotheses.size(), hypotheses.get(0).getName());
        return hypotheses;
    }

    private void evaluateMemoryIssues(List<Evidence> evidences, List<Hypothesis> hypotheses) {
        boolean highMem = false;
        boolean oomLog = false;
        String memDetail = "";

        for (Evidence e : evidences) {
            if ("memory_inspector".equalsIgnoreCase(e.getToolName()) && e.isAnomaly()) {
                highMem = true;
                memDetail = e.getObservation();
            }
            if ("system_log_search".equalsIgnoreCase(e.getToolName()) && e.getObservation().toUpperCase().contains("OUTOFMEMORY")) {
                oomLog = true;
            }
        }

        if (highMem && oomLog) {
            Hypothesis h = new Hypothesis("JVM OutOfMemoryError / Memory Leak",
                "Severe memory pressure combined with OutOfMemory errors detected in application logs.", 0.95);
            h.addEvidence(memDetail);
            h.addEvidence("Found OutOfMemoryError in application logs");
            h.addMitigation("Capture JVM heap dump (jmap -dump:live,format=b,file=heap.hprof <pid>) and analyze via Eclipse Memory Analyzer (MAT).");
            h.addMitigation("Check JVM -Xmx heap parameters and consider expanding heap or restarting the affected instance.");
            hypotheses.add(h);
        } else if (highMem) {
            Hypothesis h = new Hypothesis("High Memory Saturation",
                "Host physical memory or JVM heap usage exceeds 85-90% safe operating threshold.", 0.80);
            h.addEvidence(memDetail);
            h.addMitigation("Inspect process list (process_top) to identify resident set size (RSS) top consumers.");
            h.addMitigation("Verify if garbage collection (GC) pause times have increased using JVM GC logs.");
            hypotheses.add(h);
        }
    }

    private void evaluateCpuIssues(List<Evidence> evidences, List<Hypothesis> hypotheses) {
        boolean highCpu = false;
        String cpuDetail = "";

        for (Evidence e : evidences) {
            if ("cpu_inspector".equalsIgnoreCase(e.getToolName()) && e.isAnomaly()) {
                highCpu = true;
                cpuDetail = e.getObservation();
            }
        }

        if (highCpu) {
            Hypothesis h = new Hypothesis("CPU Starvation / Infinite Loop or Runaway Process",
                "Host CPU load exceeds 85% operating threshold, leading to request queuing and thread contention.", 0.82);
            h.addEvidence(cpuDetail);
            h.addMitigation("Run 'process_top' to identify the process ID consuming peak CPU time.");
            h.addMitigation("Capture thread dumps (jstack <pid>) to inspect hot threads or deadlocks.");
            hypotheses.add(h);
        }
    }

    private void evaluateDiskIssues(List<Evidence> evidences, List<Hypothesis> hypotheses) {
        boolean diskAlert = false;
        String diskDetail = "";

        for (Evidence e : evidences) {
            if ("disk_usage".equalsIgnoreCase(e.getToolName()) && e.isAnomaly()) {
                diskAlert = true;
                diskDetail = e.getObservation();
            }
        }

        if (diskAlert) {
            Hypothesis h = new Hypothesis("Disk Storage Exhaustion",
                "One or more mounted storage partitions have exceeded 90% capacity, causing potential I/O write failures.", 0.90);
            h.addEvidence(diskDetail);
            h.addMitigation("Inspect large log files and temporary directories (/var/log, /tmp, ./logs).");
            h.addMitigation("Purge rotated uncompressed logs and clean Docker image/layer caches (docker system prune).");
            hypotheses.add(h);
        }
    }

    private void evaluatePortServiceIssues(List<Evidence> evidences, List<Hypothesis> hypotheses) {
        for (Evidence e : evidences) {
            if ("port_check".equalsIgnoreCase(e.getToolName()) && e.getObservation().contains("CLOSED")) {
                Hypothesis h = new Hypothesis("Downstream Service Port Unreachable",
                    "Target TCP service port is closed or connection timed out: " + e.getObservation(), 0.88);
                h.addEvidence(e.getObservation());
                h.addMitigation("Verify whether target service process is active using service_status tool.");
                h.addMitigation("Inspect host firewall rules (iptables/ufw/security group) allowing inbound traffic on that port.");
                hypotheses.add(h);
            }
            if ("service_status".equalsIgnoreCase(e.getToolName()) && e.getObservation().contains("NOT running")) {
                Hypothesis h = new Hypothesis("Target Service Process Inactive / Crashed",
                    "The specified system service or process is not running: " + e.getObservation(), 0.92);
                h.addEvidence(e.getObservation());
                h.addMitigation("Check service crash logs via system_log_search or journalctl/event viewer.");
                h.addMitigation("Request human approval (HITL) to restart the critical service.");
                hypotheses.add(h);
            }
        }
    }

    private void evaluateLogErrors(List<Evidence> evidences, List<Hypothesis> hypotheses) {
        for (Evidence e : evidences) {
            if ("system_log_search".equalsIgnoreCase(e.getToolName()) && e.isAnomaly()) {
                Hypothesis h = new Hypothesis("Application Exception / Error Surge",
                    "Log analysis detected anomalous error clusters in recent logs.", 0.75);
                h.addEvidence(e.getObservation());
                h.addMitigation("Examine stack traces in detail for NullPointerException, ConnectionRefused, or Timeout exceptions.");
                h.addMitigation("Verify if upstream dependencies, database connection pools, or external APIs are experiencing degradation.");
                hypotheses.add(h);
            }
        }
    }
}
