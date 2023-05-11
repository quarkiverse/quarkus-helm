package io.quarkiverse.helm;

import static io.quarkiverse.helm.HelmUtil.chartOf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Unmatched;

@Command(name = "uninstall", sortOptions = false, mixinStandardHelpOptions = false, header = "Helm Uninstall")
public class Uninstall extends ChartCommand {

    @Option(arity = "0..1", names = { "-n", "--namespace" }, description = "The namespace to use for install")
    Optional<String> namespace = Optional.empty();

    @Parameters(index = "0", arity = "0..1", paramLabel = "NAME", description = "The chart name of the chart")
    String name;

    @Unmatched
    List<String> unmatched;

    @Override
    public String getAction() {
        return "uninstall";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<String> getAdditionalArguments(Path path) {
        List<String> arguments = new ArrayList<>();
        arguments.add(name != null ? name : chartOf(path));
        namespace.ifPresent(n -> {
            arguments.add("-n");
            arguments.add(n);
        });
        if (unmatched != null) {
            arguments.addAll(unmatched);
        }
        return arguments;
    }
}
