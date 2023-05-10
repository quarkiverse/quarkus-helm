package io.quarkiverse.helm;

import static io.quarkiverse.helm.HelmUtil.chartOf;
import static io.quarkiverse.helm.HelmUtil.chartPathOf;

import java.nio.file.Path;
import java.util.ArrayList;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "install", sortOptions = false, mixinStandardHelpOptions = false, header = "Helm Install")
public class Install extends ChartCommand {

    @Option(names = { "-s", "--set" }, description = "Set values")
    java.util.List<String> values = new ArrayList<>();

    @Override
    public String getAction() {
        return "install";
    }

    @Override
    public java.util.List<String> getAdditionaArguments(Path path) {
        java.util.List<String> arguments = new java.util.ArrayList<>();
        arguments.add(chartOf(path));
        arguments.add(chartPathOf(path));
        for (String val : values) {
            arguments.add("--set");
            arguments.add(val);
        }
        return arguments;
    }
}
