package io.quarkiverse.helm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public final class ExecUtil {

    private static final int PROCESS_CHECK_INTERVAL = 500;

    private ExecUtil() {
        // Utility Class
    }

    public static boolean execWithTimeout(File directory, Function<InputStream, Runnable> outputFilterFunction,
            Duration timeout, String command, Collection<String> args) {
        try {
            Process process = startProcess(directory, command, args);
            Thread t = new Thread(outputFilterFunction.apply(process.getInputStream()));
            t.setName("Process stdout");
            t.setDaemon(true);
            t.start();
            process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            destroyProcess(process);
            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Start a process executing given command with arguments within the specified directory.
     *
     * @param directory The directory
     * @param command The command
     * @param args The command arguments
     * @return the process
     */
    public static Process startProcess(File directory, String command, Collection<String> args) {
        try {
            String[] cmd = new String[args.size() + 1];
            cmd[0] = command;
            if (args.size() > 0) {
                System.arraycopy(args.toArray(new String[0]), 0, cmd, 1, args.size());
            }
            return new ProcessBuilder()
                    .directory(directory)
                    .command(cmd)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException e) {
            throw new RuntimeException("Input/Output error while executing command.", e);
        }
    }

    /**
     * Kill the process, if still alive, kill it forcibly
     *
     * @param process the process to kill
     */
    public static void destroyProcess(Process process) {
        process.destroy();
        int i = 0;
        while (process.isAlive() && i++ < 10) {
            try {
                process.waitFor(PROCESS_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }
}
