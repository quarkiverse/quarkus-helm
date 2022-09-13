package io.quarkiverse.helm.deployment;

import static io.quarkiverse.helm.deployment.HelmChartUploader.pushToHelmRepository;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import io.dekorate.Session;
import io.dekorate.helm.config.HelmChartConfigBuilder;
import io.dekorate.project.Project;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.util.FileUtil;
import io.quarkus.kubernetes.spi.ConfiguratorBuildItem;
import io.quarkus.kubernetes.spi.DekorateOutputBuildItem;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;

public class HelmProcessor {

    private static final String FEATURE = "helm";

    @BuildStep(onlyIf = { HelmEnabled.class, IsNormal.class })
    void generateResources(ApplicationInfoBuildItem app, OutputTargetBuildItem outputTarget,
            DekorateOutputBuildItem dekorateOutput,
            List<GeneratedKubernetesResourceBuildItem> generatedResources,
            // this is added to ensure that the build step will be run
            BuildProducer<ArtifactResultBuildItem> dummy,
            HelmChartConfig config) {

        Project project = (Project) dekorateOutput.getProject();

        // Deduct folders
        Path inputFolder = getInputDirectory(config, project);
        Path outputFolder = getOutputDirectory(config, outputTarget);

        // Dekorate session writer
        final QuarkusHelmWriterSessionListener helmWriter = new QuarkusHelmWriterSessionListener();
        final Map<String, Set<File>> deploymentTargets = toDeploymentTargets(dekorateOutput.getGeneratedFiles(),
                generatedResources);
        // separate generated helm charts into the deployment targets
        for (Map.Entry<String, Set<File>> filesInDeploymentTarget : deploymentTargets.entrySet()) {
            Path chartOutputFolder = outputFolder.resolve(filesInDeploymentTarget.getKey());
            deleteOutputHelmFolderIfExists(chartOutputFolder);
            Map<String, String> generated = helmWriter.writeHelmFiles((Session) dekorateOutput.getSession(), project,
                    toDekorateHelmChartConfig(app, config),
                    inputFolder,
                    chartOutputFolder,
                    filesInDeploymentTarget.getValue(),
                    config.apiVersion);

            // Push to Helm repository if enabled
            if (config.repository.push) {
                String tarball = generated.keySet().stream()
                        .filter(file -> file.endsWith(config.extension))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Couldn't find the tarball file. There should have "
                                + "been generated when pushing to a Helm repository is enabled."));
                pushToHelmRepository(new File(tarball), config.repository);
            }
        }
    }

    @BuildStep(onlyIf = { HelmEnabled.class, IsNormal.class })
    void disableDefaultHelmListener(BuildProducer<ConfiguratorBuildItem> helmConfiguration) {
        helmConfiguration.produce(new ConfiguratorBuildItem(new DisableDefaultHelmListener()));
    }

    private void deleteOutputHelmFolderIfExists(Path outputFolder) {
        try {
            FileUtil.deleteIfExists(outputFolder);
        } catch (IOException ignored) {

        }
    }

    private Path getInputDirectory(HelmChartConfig config, Project project) {
        Path path = Paths.get(config.inputDirectory);
        if (!path.isAbsolute()) {
            return project.getRoot().resolve(path);
        }

        return path;
    }

    private Path getOutputDirectory(HelmChartConfig config, OutputTargetBuildItem outputTarget) {
        Path path = Paths.get(config.outputDirectory);
        if (!path.isAbsolute()) {
            return outputTarget.getOutputDirectory().resolve(path);
        }

        return path;
    }

    private Map<String, Set<File>> toDeploymentTargets(List<String> generatedFiles,
            List<GeneratedKubernetesResourceBuildItem> generatedResources) {
        Map<String, Set<File>> filesByDeploymentTarget = new HashMap<>();
        for (String generatedFile : generatedFiles) {
            if (generatedFile.toLowerCase(Locale.ROOT).endsWith(".json")) {
                // skip json files
                continue;
            }

            File file = new File(generatedFile);
            String deploymentTarget = file.getName().substring(0, file.getName().indexOf("."));
            if (filesByDeploymentTarget.containsKey(deploymentTarget)) {
                // It's already included.
                continue;
            }

            Set<File> files = new HashSet<>();
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

            filesByDeploymentTarget.put(deploymentTarget, files);
        }

        return filesByDeploymentTarget;
    }

    private io.dekorate.helm.config.HelmChartConfig toDekorateHelmChartConfig(ApplicationInfoBuildItem app,
            HelmChartConfig config) {
        HelmChartConfigBuilder builder = new HelmChartConfigBuilder()
                .withEnabled(config.enabled)
                .withName(config.name.orElse(app.getName()))
                .withCreateTarFile(config.createTarFile || config.repository.push)
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
        config.values.values().forEach(v -> builder.addNewValue(v.property,
                v.paths.map(l -> l.toArray(new String[0])).orElse(new String[0]),
                defaultString(v.profile),
                defaultString(v.value)));

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
