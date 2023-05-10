package io.quarkiverse.helm;

import static io.quarkiverse.helm.HelmUtil.chartOf;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import picocli.CommandLine.Command;

@Command(name = "uninstall", sortOptions = false, mixinStandardHelpOptions = false, header = "Helm Uninstall")
public class Uninstall extends ChartCommand {

    @Override
    public String getAction() {
        return "uninstall";
    }

    @Override
    public List<String> getAdditionaArguments(Path path) {
        List<String> arguments = new ArrayList<>();
        arguments.add(chartOf(path));
        return arguments;
    }
}
