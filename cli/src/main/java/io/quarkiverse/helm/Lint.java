package io.quarkiverse.helm;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import picocli.CommandLine.Command;

@Command(name = "lint", sortOptions = false, mixinStandardHelpOptions = false, header = "Helm Lint")
public class Lint extends ChartCommand {

    @Override
    public String getAction() {
        return "lint";
    }

    @Override
    public List<String> getAdditionalArguments(Path path) {
        List<String> arguments = new ArrayList<>();
        arguments.add(path.toAbsolutePath().toString());
        if (unmatched != null) {
            arguments.addAll(unmatched);
        }
        return arguments;
    }
}
