package io.quarkiverse.helm.deployment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import io.dekorate.Session;
import io.dekorate.helm.config.HelmChartConfigBuilder;
import io.dekorate.helm.listener.HelmWriterSessionListener;
import io.dekorate.project.Project;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.kubernetes.spi.ConfiguratorBuildItem;
import io.quarkus.kubernetes.spi.DekorateOutputBuildItem;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;

public class HelmProcessor {

    private static final String FEATURE = "helm";

    @BuildStep(onlyIf = HelmEnabled.class)
    FeatureBuildItem feature(ApplicationInfoBuildItem app, OutputTargetBuildItem outputTarget,
            DekorateOutputBuildItem dekorateOutput,
            List<GeneratedKubernetesResourceBuildItem> generatedResources,
            HelmChartConfig config) {
        // Dekorate session writer
        final HelmWriterSessionListener helmWriter = new HelmWriterSessionListener();
        helmWriter.writeHelmFiles((Session) dekorateOutput.getSession(), (Project) dekorateOutput.getProject(),
                toDekorateHelmChartConfig(app, config),
                outputTarget.getOutputDirectory(),
                toFiles(dekorateOutput.getGeneratedFiles(), generatedResources));

        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = HelmEnabled.class)
    void disableDefaultHelmListener(BuildProducer<ConfiguratorBuildItem> helmConfiguration) {
        helmConfiguration.produce(new ConfiguratorBuildItem(new DisableDefaultHelmListener()));
    }

    private Collection<File> toFiles(List<String> generatedFiles,
            List<GeneratedKubernetesResourceBuildItem> generatedResources) {
        Set<File> files = new HashSet<>();
        for (String generatedFile : generatedFiles) {
            File file = new File(generatedFile);
            if (!file.exists()) {
                Optional<byte[]> content = generatedResources.stream()
                        .filter(resource -> file.getName().equals(resource.getName()))
                        .map(GeneratedKubernetesResourceBuildItem::getContent)
                        .findFirst();
                if (content.isPresent()) {
                    // The dekorate output generated files are sometimes not persisted yet, so we need to workaround it by
                    // creating a temp file with the content from generatedResources.
                    try {
                        File tempFile = File.createTempFile("tmp", file.getName());
                        tempFile.deleteOnExit();
                        Files.write(tempFile.toPath(), content.get());
                        files.add(tempFile);
                    } catch (IOException ignored) {
                        // if we could not create the temp file, we add the one from
                        files.add(file);
                    }
                }
            } else {
                files.add(file);
            }
        }

        return files;
    }

    private io.dekorate.helm.config.HelmChartConfig toDekorateHelmChartConfig(ApplicationInfoBuildItem app,
            HelmChartConfig config) {
        HelmChartConfigBuilder builder = new HelmChartConfigBuilder()
                .withEnabled(config.enabled)
                .withName(config.name.orElse(app.getName()))
                .withCreateTarFile(config.createTarFile)
                .withVersion(config.version.orElse(app.getVersion()))
                .withExtension(config.extension)
                .withValuesRootAlias(config.valuesRootAlias)
                .withNotes(config.notes);
        config.description.ifPresent(builder::withDescription);
        config.keywords.ifPresent(builder::addAllToKeywords);
        config.icon.ifPresent(builder::withIcon);
        config.home.ifPresent(builder::withHome);
        config.sources.ifPresent(builder::addAllToSources);
        config.maintainers.values().forEach(
                m -> builder.addNewMaintainer(m.name, defaultString(m.email), defaultString(m.url)));
        config.dependencies.values()
                .forEach(d -> builder.addNewDependency(d.name,
                        defaultString(d.alias, d.name),
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
