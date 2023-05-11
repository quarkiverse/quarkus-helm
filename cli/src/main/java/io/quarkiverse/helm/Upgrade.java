package io.quarkiverse.helm;

import static io.quarkiverse.helm.HelmUtil.chartOf;
import static io.quarkiverse.helm.HelmUtil.chartPathOf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "upgrade", sortOptions = false, mixinStandardHelpOptions = false, header = "Helm Upgrade")
public class Upgrade extends ChartCommand {

    @Option(names = { "-s", "--set" }, description = "Set values")
    java.util.List<String> values = new ArrayList<>();

    @Override
    public String getAction() {
        return "upgrade";
    }

    @Override
    public List<String> getAdditionalArguments(Path path) {
        List<String> arguments = new ArrayList<>();
        arguments.add(chartOf(path));
        arguments.add(chartPathOf(path));
        for (String val : values) {
            arguments.add("--set");
            arguments.add(val);
        }
        return arguments;
    }
}
