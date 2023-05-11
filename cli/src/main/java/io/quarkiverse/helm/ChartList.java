package io.quarkiverse.helm;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "list", sortOptions = false, mixinStandardHelpOptions = false, header = "List Helm charts in the project")
public class ChartList implements Callable<Integer> {

    @Spec
    protected CommandSpec spec;

    @CommandLine.Option(names = { "--platform" }, description = "Select target platform (kubernetes or openshift)")
    public Platform platform;

    @CommandLine.Option(names = { "-h", "--help" }, usageHelp = true, description = "Display this help message.")
    public boolean help;

    @Override
    public Integer call() {
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        try {
            List<Platform> platforms = platform != null ? List.of(platform) : List.of(Platform.values());
            List<Path> chartDirectories = HelmUtil.listGeneratedCharts(currentDir, platforms);
            if (chartDirectories.isEmpty()) {
                System.out.println("No generated helm charts where found under: " + currentDir.toAbsolutePath()
                        + " for platforms: "
                        + platforms.stream().map(Platform::name).collect(Collectors.joining(",", "[", "]")));
                return CommandLine.ExitCode.OK;
            }

            String format = getFormat(chartDirectories);
            System.out.println(String.format(format, "Name", "Platform", "Path"));
            for (Path chartDir : chartDirectories) {
                System.out.println(
                        String.format(format, chartDir.getFileName().toString(), HelmUtil.platformOf(chartDir).name(),
                                chartDir.toAbsolutePath().toString()));
            }
            return CommandLine.ExitCode.OK;
        } catch (Exception e) {
            System.err.println("Failed to list charts under: " + currentDir.toAbsolutePath());
            return CommandLine.ExitCode.SOFTWARE;
        }
    }

    private static String getFormat(Collection<Path> items) {
        int maxNameLength = items.stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .map(String::length)
                .max(Comparator.naturalOrder())
                .orElse(0);

        int maxPlatformLength = items.stream()
                .map(HelmUtil::platformOf)
                .map(Platform::name)
                .map(String::length)
                .max(Comparator.naturalOrder())
                .orElse(0);
        return "%-" + maxNameLength + "s\t %-" + maxPlatformLength + "s\t%s";
    }
}
