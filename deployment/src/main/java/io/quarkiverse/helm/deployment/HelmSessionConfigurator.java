package io.quarkiverse.helm.deployment;

import java.nio.file.Path;
import java.util.function.BiConsumer;

import io.dekorate.Session;
import io.dekorate.config.ConfigurationSupplier;
import io.dekorate.project.Project;

public class HelmSessionConfigurator implements BiConsumer<Object, Object> {

    private final HelmChartConfig config;
    private final Path outputDir;

    public HelmSessionConfigurator(HelmChartConfig config, Path outputDir) {
        this.config = config;
        this.outputDir = outputDir;
    }

    @Override
    public void accept(Object project, Object session) {
        doAccept((Project) project, (Session) session);
    }

    private void doAccept(Project project, Session session) {
        session.getConfigurationRegistry()
                .add(new ConfigurationSupplier<>(io.dekorate.helm.config.HelmChartConfig
                        .newHelmChartConfigBuilderFromDefaults()
                        .withName(config.name)));

    }
}
