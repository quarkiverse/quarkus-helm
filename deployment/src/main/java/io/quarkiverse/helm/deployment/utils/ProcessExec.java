package io.quarkiverse.helm.deployment.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;

// Replaced Dekorate's io.dekorate.utils.Exec with project-local ProcessExec
public final class ProcessExec {

    private final Path workingDir;
    private final OutputStream out;

    private ProcessExec(Path workingDir, OutputStream out) {
        this.workingDir = workingDir;
        this.out = out;
    }

    public static ProcessExec inPath(Path path) {
        return new ProcessExec(path, null);
    }

    public ProcessExec redirectingOutput(OutputStream out) {
        return new ProcessExec(workingDir, out);
    }

    public boolean commands(String... commands) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder()
                    .directory(workingDir.toFile())
                    .command(commands)
                    .redirectErrorStream(true);
            processBuilder.environment().remove("MAVEN_DEBUG_OPTS");
            Process process = processBuilder.start();

            try (InputStreamReader isr = new InputStreamReader(process.getInputStream());
                    BufferedReader reader = new BufferedReader(isr)) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    if (out != null) {
                        out.write(line.getBytes());
                        out.write(System.lineSeparator().getBytes());
                    }
                }
            }

            process.waitFor();
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
