package io.quarkiverse.helm.deployment;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import io.dekorate.Session;
import io.dekorate.helm.config.HelmChartConfigBuilder;
import io.dekorate.project.Project;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.spi.ConfiguratorBuildItem;
import io.quarkus.kubernetes.spi.DekorateOutputBuildItem;

public class HelmProcessor {

    private static final String FEATURE = "helm";

    @BuildStep
    FeatureBuildItem feature(ApplicationInfoBuildItem app, OutputTargetBuildItem outputTarget,
            Optional<DekorateOutputBuildItem> optDekorateOutput, HelmChartConfig config) {
        if (config.enabled && optDekorateOutput.isEmpty()) {
            throw new IllegalStateException("The Quarkus Helm extension is only compatible with either Quarkus Kubernetes, "
                    + "Quarkus OpenShift or Quarkus Knative extensions");
        }

        // Dekorate session writer
        final DekorateOutputBuildItem dekorateOutput = optDekorateOutput.get();
        final HelmWriterSessionListener helmWriter = new HelmWriterSessionListener();
        helmWriter.writeHelmFiles((Session) dekorateOutput.getSession(), (Project) dekorateOutput.getProject(),
                toDekorateHelmChartConfig(app, config),
                outputTarget.getOutputDirectory(),
                toFiles(dekorateOutput.getGeneratedFiles()));

        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void disableDefaultHelmListener(BuildProducer<ConfiguratorBuildItem> helmConfiguration) {
        helmConfiguration.produce(new ConfiguratorBuildItem(new DisableDefaultHelmListener()));
    }

    private Collection<File> toFiles(List<String> generatedFiles) {
        return generatedFiles.stream().map(File::new).filter(File::exists).collect(Collectors.toSet());
    }

    private io.dekorate.helm.config.HelmChartConfig toDekorateHelmChartConfig(ApplicationInfoBuildItem app,
            HelmChartConfig config) {
        HelmChartConfigBuilder builder = new HelmChartConfigBuilder()
                .withEnabled(config.enabled)
                .withName(config.name.orElse(app.getName()))
                // Available in Dekorate 2.10.0
                // .withCreateTarFile(config.createTarFile)
                .withVersion(config.version.orElse(app.getVersion()))
                .withExtension(config.extension);
        config.description.ifPresent(builder::withDescription);
        config.keywords.ifPresent(builder::addAllToKeywords);
        config.icon.ifPresent(builder::withIcon);
        config.home.ifPresent(builder::withHome);
        config.sources.ifPresent(builder::addAllToSources);
        config.maintainers.values().forEach(
                m -> builder.addNewMaintainer(m.name, defaultString(m.email), defaultString(m.url)));
        config.dependencies.values()
                .forEach(d -> builder.addNewDependency(d.name,
                        // Available in Dekorate 2.10.0
                        // defaultString(d.alias, d.name),
                        d.version,
                        d.repository));
        config.values.values().forEach(v -> builder.addNewValue(v.property, v.jsonPaths.toArray(new String[v.jsonPaths.size()]),
                defaultString(v.profile), defaultString(v.value)));

        return builder.build();
    }

    private String defaultString(Optional<String> value) {
        return defaultString(value, null);
    }

    private String defaultString(Optional<String> value, String defaultStr) {
        if (value.isEmpty() || StringUtils.isEmpty(value.get())) {
            return defaultStr;
        }

        return value.get();
    }
}
