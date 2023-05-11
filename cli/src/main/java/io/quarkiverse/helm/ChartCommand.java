package io.quarkiverse.helm;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

public abstract class ChartCommand implements Callable<Integer> {

    @Spec
    protected CommandSpec spec;

    @Option(order = 1, names = {
            "--platform" }, description = "Select target platform (kubernetes or openshift, default to kubernetes).")
    public Platform platform;

    @Option(order = 2, names = { "-h", "--help" }, usageHelp = true, description = "Display this help message.")
    public boolean help;

    @Option(order = 3, names = { "--dry-run" }, description = "Show actions that would be taken.")
    boolean dryRun = false;

    @Parameters(arity = "0..1", paramLabel = "CHART_NAME", description = "The chart name to")
    String chartName;

    /**
     * The helm subcommand (e.g. install, uninstall, upgrade)
     *
     * @retun the subcommand
     */
    public abstract String getAction();

    /**
     * Additional arguments to pass to helm.
     *
     * @param path the path of the cart.
     * @retun a list containing additonal arugments
     */
    public abstract List<String> getAdditionalArguments(Path path);

    @Override
    public Integer call() {
        Path currentDir = Paths.get(System.getProperty("user.dir"));

        try {
            List<Platform> platforms = platform != null ? List.of(platform) : List.of(Platform.kubernetes);
            List<Path> chartDirectories = HelmUtil.listGeneratedCharts(currentDir, platforms).stream()
                    .filter(c -> chartName == null || chartName.equals(c.getFileName().toString()))
                    .collect(Collectors.toList());

            if (chartDirectories.size() == 0) {
                System.out.println("No generated helm charts where found under: " + currentDir.toAbsolutePath().toString()
                        + " for platforms: "
                        + platforms.stream().map(Platform::name).collect(Collectors.joining(",", "[", "]")));
                return CommandLine.ExitCode.USAGE;
            }

            System.out.println("Dry run:");
            for (Path chartDirectory : chartDirectories) {

                List<String> arguments = new ArrayList<>();
                arguments.add(getAction());
                arguments.addAll(getAdditionalArguments(chartDirectory));

                if (dryRun) {
                    System.out.print("  helm " + String.join(" ", arguments));
                    continue;
                }

                boolean success = ExecUtil.execWithTimeout(currentDir.toFile(),
                        i -> new HelmCommandOutput(i),
                        Duration.ofSeconds(10),
                        "helm",
                        arguments);

                if (!success) {
                    return CommandLine.ExitCode.USAGE;
                }
            }

            return CommandLine.ExitCode.OK;
        } catch (Exception e) {
            System.err.println("Failed to " + getAction() + " charts under: " + currentDir.toAbsolutePath()
                    + ", due to: " + e.getMessage());
            return CommandLine.ExitCode.SOFTWARE;
        }
    }
}
