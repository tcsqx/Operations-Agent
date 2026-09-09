package org.example.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class CommandGuard {

    private static final Logger log = LoggerFactory.getLogger(CommandGuard.class);

    // Critical destructive command patterns that must NEVER be executed
    private static final List<Pattern> BLACKLIST_PATTERNS = Arrays.asList(
        Pattern.compile("rm\\s+(-[a-zA-Z]*[rf][a-zA-Z]*\\s+.*(/|\\*|~|\\.\\.)|--no-preserve-root)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("rm\\s+(-[a-zA-Z]*r[a-zA-Z]*\\s+-[a-zA-Z]*f[a-zA-Z]*\\s+.*(/|\\*))", Pattern.CASE_INSENSITIVE),
        Pattern.compile("mkfs(\\.[a-zA-Z0-9]+)?\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("dd\\s+.*(of=/dev/(sd[a-z]|nvme|hd[a-z]|vd[a-z]))", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(shutdown|reboot|poweroff|halt|init\\s+[06])(\\s+.*)?$", Pattern.CASE_INSENSITIVE),
        Pattern.compile(":\\(\\)\\s*\\{\\s*:\\|:&\\s*\\};:", Pattern.CASE_INSENSITIVE), // Fork bomb
        Pattern.compile("chmod\\s+(-R\\s+)?(777|000)\\s+/", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(fdisk|parted|sfdisk|wipefs)\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile(">\\s*/dev/(sd[a-z]|nvme|mem|kmem)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("DROP\\s+(DATABASE|SCHEMA)\\s+", Pattern.CASE_INSENSITIVE)
    );

    // High risk command patterns that require Human-In-The-Loop approval
    private static final List<Pattern> HIGH_RISK_PATTERNS = Arrays.asList(
        Pattern.compile("systemctl\\s+(restart|stop|reload)\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("service\\s+\\S+\\s+(restart|stop|reload)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("docker\\s+(stop|kill|rm|restart|prune)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("kill\\s+-9\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("killall\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("iptables\\s+", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ufw\\s+(disable|reset)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("pkill\\s+", Pattern.CASE_INSENSITIVE)
    );

    public static class ValidationResult {
        private final boolean allowed;
        private final boolean requiresApproval;
        private final String reason;

        public ValidationResult(boolean allowed, boolean requiresApproval, String reason) {
            this.allowed = allowed;
            this.requiresApproval = requiresApproval;
            this.reason = reason;
        }

        public static ValidationResult ok() {
            return new ValidationResult(true, false, "Safe to execute");
        }

        public static ValidationResult blocked(String reason) {
            return new ValidationResult(false, false, reason);
        }

        public static ValidationResult requiresApproval(String reason) {
            return new ValidationResult(true, true, reason);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public boolean isRequiresApproval() {
            return requiresApproval;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * Validates command safety.
     * Rejects blacklisted commands unconditionally.
     * Marks high-risk commands as requiring human approval.
     */
    public ValidationResult validate(String command) {
        if (command == null || command.trim().isEmpty()) {
            return ValidationResult.blocked("Empty command is not allowed");
        }

        String trimmed = command.trim();

        // 1. Check blacklist
        for (Pattern pattern : BLACKLIST_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                log.warn("[CommandGuard] CRITICAL: Blocked dangerous command matching '{}': {}", pattern.pattern(), trimmed);
                return ValidationResult.blocked("Command violates security blacklist policy: destructive operations prohibited");
            }
        }

        // 2. Check high-risk commands requiring approval
        for (Pattern pattern : HIGH_RISK_PATTERNS) {
            if (pattern.matcher(trimmed).find()) {
                log.info("[CommandGuard] HIGH_RISK: Command requires human approval: {}", trimmed);
                return ValidationResult.requiresApproval("Command modifies system state or services, human approval required");
            }
        }

        return ValidationResult.ok();
    }
}
