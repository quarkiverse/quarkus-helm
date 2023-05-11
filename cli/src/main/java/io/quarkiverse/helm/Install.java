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

@Command(name = "install", sortOptions = false, mixinStandardHelpOptions = false, header = "Helm Install")
public class Install extends ChartCommand {

    @Option(names = { "-s", "--set" }, description = "Set values")
    java.util.List<String> values = new ArrayList<>();

    @Option(arity = "0..1", names = { "-n", "--namespace" }, description = "The namespace to use for install")
    Optional<String> namespace = Optional.empty();

    @Option(arity = "0..1", names = { "--devel" }, description = "Use development version, too")
    boolean devel;

    @Option(arity = "0..1", names = { "--dependency-update" }, description = "Upadate dependencies before installing the chart")
    boolean dependencyUpdate;

    @Parameters(arity = "0..1", paramLabel = "NAME", description = "The chart name")
    String name;

    @Parameters(arity = "0..1", paramLabel = "CHART", description = "The chart")
    String chart;

    @Unmatched
    List<String> unmatched;

    @Override
    public String getAction() {
        return "install";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public java.util.List<String> getAdditionalArguments(Path path) {
        java.util.List<String> arguments = new java.util.ArrayList<>();
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
        if (dependencyUpdate) {
            arguments.add("--dependency-update");
        }
        if (unmatched != null) {
            arguments.addAll(unmatched);
        }
        return arguments;
    }
}
