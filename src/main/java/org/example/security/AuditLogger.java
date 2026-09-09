package org.example.security;

import org.example.entity.AuditLogEntity;
import org.example.model.enums.RiskLevel;
import org.example.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger("AUDIT_LOGGER");

    private final AuditLogRepository auditLogRepository;
    private final DataMasker dataMasker;

    public AuditLogger(AuditLogRepository auditLogRepository, DataMasker dataMasker) {
        this.auditLogRepository = auditLogRepository;
        this.dataMasker = dataMasker;
    }

    public void logAction(String taskId, String operator, String action, String toolName,
                          String parameters, RiskLevel riskLevel, String approvedBy,
                          String executionResult, String ipAddress) {
        try {
            String maskedParams = dataMasker.mask(parameters);
            String maskedResult = dataMasker.mask(executionResult);

            AuditLogEntity audit = new AuditLogEntity(
                taskId,
                operator != null ? operator : "system",
                action,
                toolName,
                maskedParams,
                riskLevel != null ? riskLevel : RiskLevel.LOW,
                approvedBy,
                maskedResult,
                ipAddress != null ? ipAddress : "127.0.0.1"
            );

            auditLogRepository.save(audit);

            log.info("[AUDIT] TaskId={} Action={} Tool={} Risk={} Operator={} ApprovedBy={}",
                taskId, action, toolName, riskLevel, operator, approvedBy);
        } catch (Exception e) {
            log.error("[AUDIT ERROR] Failed to record audit log: {}", e.getMessage(), e);
        }
    }
}
