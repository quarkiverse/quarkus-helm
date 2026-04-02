package io.quarkiverse.helm.spi;

import java.nio.file.Path;

import io.quarkus.builder.item.SimpleBuildItem;

public final class CustomHelmOutputDirBuildItem extends SimpleBuildItem {

    private final Path outputDir;

    public CustomHelmOutputDirBuildItem(Path outputDir) {
        this.outputDir = outputDir;
    }

    public Path getOutputDir() {
        return outputDir;
    }
}
