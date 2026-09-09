package org.example.tools;

import org.example.security.CommandGuard;
import org.example.security.DataMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Component
public class SafeCommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(SafeCommandExecutor.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 15;
    private static final int MAX_OUTPUT_BYTES = 64 * 1024; // 64KB limit

    private final CommandGuard commandGuard;
    private final DataMasker dataMasker;
    private final boolean isWindows;

    public SafeCommandExecutor(CommandGuard commandGuard, DataMasker dataMasker) {
        this.commandGuard = commandGuard;
        this.dataMasker = dataMasker;
        this.isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    }

    public static class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;
        private final long durationMs;
        private final boolean timedOut;

        public CommandResult(int exitCode, String stdout, String stderr, long durationMs, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.durationMs = durationMs;
            this.timedOut = timedOut;
        }

        public boolean isSuccess() {
            return !timedOut && exitCode == 0;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public boolean isTimedOut() {
            return timedOut;
        }
    }

    public CommandResult execute(String command) {
        return execute(command, DEFAULT_TIMEOUT_SECONDS);
    }

    public CommandResult execute(String command, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();

        // Security check
        CommandGuard.ValidationResult val = commandGuard.validate(command);
        if (!val.isAllowed()) {
            return new CommandResult(-1, "", "Execution denied: " + val.getReason(), 0, false);
        }

        ProcessBuilder processBuilder;
        if (isWindows) {
            processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
        }

        processBuilder.redirectErrorStream(false);

        try {
            Process process = processBuilder.start();
            StringBuilder stdoutSb = new StringBuilder();
            StringBuilder stderrSb = new StringBuilder();

            Thread stdoutThread = new Thread(() -> readStream(process.getInputStream(), stdoutSb));
            Thread stderrThread = new Thread(() -> readStream(process.getErrorStream(), stderrSb));

            stdoutThread.start();
            stderrThread.start();

            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - startTime;

            if (!completed) {
                process.destroyForcibly();
                log.warn("[SafeCommandExecutor] Command timed out after {}s: {}", timeoutSeconds, command);
                return new CommandResult(-1, stdoutSb.toString(), "Execution timed out after " + timeoutSeconds + "s", duration, true);
            }

            stdoutThread.join(1000);
            stderrThread.join(1000);

            int exitCode = process.exitValue();
            String stdout = dataMasker.mask(stdoutSb.toString());
            String stderr = dataMasker.mask(stderrSb.toString());

            return new CommandResult(exitCode, stdout, stderr, duration, false);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[SafeCommandExecutor] Command execution error: {}", e.getMessage(), e);
            return new CommandResult(-1, "", "Process execution error: " + e.getMessage(), duration, false);
        }
    }

    private void readStream(java.io.InputStream is, StringBuilder sb) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, isWindows ? StandardCharsets.UTF_8 : StandardCharsets.UTF_8))) {
            char[] buffer = new char[1024];
            int read;
            int totalBytes = 0;
            while ((read = reader.read(buffer)) != -1) {
                if (totalBytes < MAX_OUTPUT_BYTES) {
                    sb.append(buffer, 0, read);
                    totalBytes += read * 2;
                } else {
                    sb.append("\n[OUTPUT TRUNCATED - EXCEEDED 64KB LIMIT]");
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }
}
