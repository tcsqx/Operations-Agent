package org.example.tools;

import org.example.security.CommandGuard;
import org.example.security.DataMasker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SafeCommandExecutorTest {

    private SafeCommandExecutor executor;

    @BeforeEach
    void setUp() {
        CommandGuard guard = new CommandGuard();
        DataMasker masker = new DataMasker();
        executor = new SafeCommandExecutor(guard, masker);
    }

    @Test
    @DisplayName("Should execute safe echo command successfully")
    void testExecuteSafeCommand() {
        SafeCommandExecutor.CommandResult res = executor.execute("echo OpsPilotSafeTest", 5);
        assertTrue(res.isSuccess());
        assertEquals(0, res.getExitCode());
        assertTrue(res.getStdout().contains("OpsPilotSafeTest"));
        assertFalse(res.isTimedOut());
    }

    @Test
    @DisplayName("Should deny blocked dangerous command before execution")
    void testDenyDangerousCommand() {
        SafeCommandExecutor.CommandResult res = executor.execute("rm -rf /", 5);
        assertFalse(res.isSuccess());
        assertEquals(-1, res.getExitCode());
        assertTrue(res.getStderr().contains("denied"));
    }
}
