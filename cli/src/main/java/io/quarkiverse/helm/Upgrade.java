package io.quarkiverse.helm;

import static io.quarkiverse.helm.HelmUtil.chartOf;
import static io.quarkiverse.helm.HelmUtil.chartPathOf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Unmatched;

@Command(name = "upgrade", sortOptions = false, mixinStandardHelpOptions = false, header = "Helm Upgrade")
public class Upgrade extends ChartCommand {

    @Option(names = { "-s", "--set" }, description = "Set values")
    java.util.List<String> values = new ArrayList<>();

    @Option(arity = "0..1", names = { "-n", "--namespace" }, description = "The namespace to use for install")
    Optional<String> namespace = Optional.empty();

    @Option(arity = "0..1", names = { "--devel" }, description = "Use development version, too")
    boolean devel;

    @Parameters(index = "0", arity = "0..1", paramLabel = "NAME", description = "The chart name of the chart")
    String name;

    @Parameters(arity = "0..1", paramLabel = "CHART", description = "The chart")
    String chart;

    @Unmatched
    List<String> unmatched;

    @Override
    public String getAction() {
        return "upgrade";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<String> getAdditionalArguments(Path path) {
        List<String> arguments = new ArrayList<>();
        arguments.add(name != null ? name : chartOf(path));
        arguments.add(chart != null ? chart : chartPathOf(path));
        for (String val : values) {
            arguments.add("--set");
            arguments.add(val);
        }
        namespace.ifPresent(n -> {
            arguments.add("-n");
            arguments.add(n);
        });
        if (devel) {
            arguments.add("--devel");
        }
        if (unmatched != null) {
            arguments.addAll(unmatched);
        }
        return arguments;
    }
}
