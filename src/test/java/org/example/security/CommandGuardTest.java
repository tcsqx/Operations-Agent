package org.example.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandGuardTest {

    private CommandGuard commandGuard;

    @BeforeEach
    void setUp() {
        commandGuard = new CommandGuard();
    }

    @Test
    @DisplayName("Should block destructive rm -rf commands")
    void testBlockDestructiveRm() {
        CommandGuard.ValidationResult r1 = commandGuard.validate("rm -rf /");
        assertFalse(r1.isAllowed());
        assertTrue(r1.getReason().contains("destructive"));

        CommandGuard.ValidationResult r2 = commandGuard.validate("rm -rf /*");
        assertFalse(r2.isAllowed());

        CommandGuard.ValidationResult r3 = commandGuard.validate("rm --no-preserve-root -rf /");
        assertFalse(r3.isAllowed());
    }

    @Test
    @DisplayName("Should block disk formatting and device overwrites")
    void testBlockFormattingAndDeviceOverwrites() {
        CommandGuard.ValidationResult r1 = commandGuard.validate("mkfs.ext4 /dev/sda1");
        assertFalse(r1.isAllowed());

        CommandGuard.ValidationResult r2 = commandGuard.validate("dd if=/dev/zero of=/dev/sda bs=1M");
        assertFalse(r2.isAllowed());

        CommandGuard.ValidationResult r3 = commandGuard.validate("fdisk /dev/sdb");
        assertFalse(r3.isAllowed());
    }

    @Test
    @DisplayName("Should block system shutdown and fork bomb")
    void testBlockShutdownAndForkBomb() {
        CommandGuard.ValidationResult r1 = commandGuard.validate("shutdown -h now");
        assertFalse(r1.isAllowed());

        CommandGuard.ValidationResult r2 = commandGuard.validate("reboot");
        assertFalse(r2.isAllowed());

        CommandGuard.ValidationResult r3 = commandGuard.validate(":(){ :|:& };:");
        assertFalse(r3.isAllowed());
    }

    @Test
    @DisplayName("Should mark service restarts as requiring human approval")
    void testRequireApprovalForServiceRestart() {
        CommandGuard.ValidationResult r1 = commandGuard.validate("systemctl restart nginx");
        assertTrue(r1.isAllowed());
        assertTrue(r1.isRequiresApproval());

        CommandGuard.ValidationResult r2 = commandGuard.validate("docker restart mysql-server");
        assertTrue(r2.isAllowed());
        assertTrue(r2.isRequiresApproval());

        CommandGuard.ValidationResult r3 = commandGuard.validate("kill -9 1234");
        assertTrue(r3.isAllowed());
        assertTrue(r3.isRequiresApproval());
    }

    @Test
    @DisplayName("Should allow safe read-only commands without approval")
    void testAllowSafeCommands() {
        CommandGuard.ValidationResult r1 = commandGuard.validate("ps aux");
        assertTrue(r1.isAllowed());
        assertFalse(r1.isRequiresApproval());

        CommandGuard.ValidationResult r2 = commandGuard.validate("df -h");
        assertTrue(r2.isAllowed());
        assertFalse(r2.isRequiresApproval());

        CommandGuard.ValidationResult r3 = commandGuard.validate("cat /var/log/nginx/access.log | grep 500");
        assertTrue(r3.isAllowed());
        assertFalse(r3.isRequiresApproval());
    }
}
