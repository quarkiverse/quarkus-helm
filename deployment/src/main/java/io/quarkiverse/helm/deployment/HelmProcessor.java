package io.quarkiverse.helm.deployment;

import static io.quarkiverse.helm.deployment.HelmChartUploader.pushToHelmRepository;
import static io.quarkus.deployment.Capability.OPENSHIFT;

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
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import io.dekorate.ConfigReference;
import io.dekorate.Session;
import io.dekorate.helm.config.HelmChartConfigBuilder;
import io.dekorate.kubernetes.config.ContainerBuilder;
import io.dekorate.kubernetes.decorator.AddInitContainerDecorator;
import io.dekorate.project.Project;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.pkg.builditem.ArtifactResultBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.util.FileUtil;
import io.quarkus.kubernetes.spi.ConfiguratorBuildItem;
import io.quarkus.kubernetes.spi.DecoratorBuildItem;
import io.quarkus.kubernetes.spi.DekorateOutputBuildItem;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;

public class HelmProcessor {
    private static final String NAME_FORMAT_REG_EXP = "[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*";

    private static final String QUARKUS_KUBERNETES_NAME = "quarkus.kubernetes.name";
    private static final String QUARKUS_KNATIVE_NAME = "quarkus.knative.name";
    private static final String QUARKUS_OPENSHIFT_NAME = "quarkus.openshift.name";
    private static final String QUARKUS_CONTAINER_IMAGE_NAME = "quarkus.container-image.name";
    private static final String SERVICE_NAME_PLACEHOLDER = "::service-name";

    @BuildStep(onlyIf = { HelmEnabled.class, IsNormal.class })
    void configureHelmDependencyOrder(Capabilities capabilities, ApplicationInfoBuildItem info, HelmChartConfig config,
            BuildProducer<DecoratorBuildItem> decorators) {
        if (config.dependencies == null || config.dependencies.isEmpty()) {
            return;
        }

        for (HelmDependencyConfig dependency : config.dependencies.values()) {
            if (dependency.waitForService.isPresent()) {
                String serviceName = dependency.waitForService.get();
                decorators.produce(new DecoratorBuildItem(new AddInitContainerDecorator(getDeploymentName(capabilities, info),
                        new ContainerBuilder()
                                .withImage(dependency.waitForServiceImage)
                                .withCommand("-c", dependency.waitForServiceCommandTemplate
                                        .replaceAll(SERVICE_NAME_PLACEHOLDER, serviceName))
                                .build())));
            }
        }
    }

    @BuildStep(onlyIf = { HelmEnabled.class, IsNormal.class })
    void generateResources(ApplicationInfoBuildItem app, OutputTargetBuildItem outputTarget,
            DekorateOutputBuildItem dekorateOutput,
            List<GeneratedKubernetesResourceBuildItem> generatedResources,
            // this is added to ensure that the build step will be run
            BuildProducer<ArtifactResultBuildItem> dummy,
            HelmChartConfig config) {
        validate(config);
        Project project = (Project) dekorateOutput.getProject();

        // Deduct folders
        Path inputFolder = getInputDirectory(config, project);
        Path outputFolder = getOutputDirectory(config, outputTarget);

        // Dekorate session writer
        final QuarkusHelmWriterSessionListener helmWriter = new QuarkusHelmWriterSessionListener();
        final Map<String, Set<File>> deploymentTargets = toDeploymentTargets(dekorateOutput.getGeneratedFiles(),
                generatedResources);

        // Config
        io.dekorate.helm.config.HelmChartConfig dekorateHelmChartConfig = toDekorateHelmChartConfig(app, config);
        List<ConfigReference> valueReferencesFromConfig = toValueReferences(config);

        // Deduct deployment target to push
        String deploymentTargetToPush = deductDeploymentTarget(config, deploymentTargets);

        // separate generated helm charts into the deployment targets
        for (Map.Entry<String, Set<File>> filesInDeploymentTarget : deploymentTargets.entrySet()) {
            String deploymentTarget = filesInDeploymentTarget.getKey();
            Path chartOutputFolder = outputFolder.resolve(deploymentTarget);
            deleteOutputHelmFolderIfExists(chartOutputFolder);
            Map<String, String> generated = helmWriter.writeHelmFiles((Session) dekorateOutput.getSession(), project,
                    dekorateHelmChartConfig,
                    valueReferencesFromConfig,
                    inputFolder,
                    chartOutputFolder,
                    filesInDeploymentTarget.getValue());

            // Push to Helm repository if enabled
            if (config.repository.push && deploymentTargetToPush.equals(deploymentTarget)) {
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

    private void validate(HelmChartConfig config) {
        if (config.name.isPresent()) {
            if (!config.name.get().matches(NAME_FORMAT_REG_EXP)) {
                throw new IllegalStateException(String.format("Wrong name '%s'. Regular expression used for validation "
                        + "is '%s'", config.name.get(), NAME_FORMAT_REG_EXP));
            }
        }
    }

    private String deductDeploymentTarget(HelmChartConfig config, Map<String, Set<File>> deploymentTargets) {
        if (config.repository.push) {
            // if enabled, use the deployment target from the user if set
            if (config.repository.deploymentTarget.isPresent()) {
                return config.repository.deploymentTarget.get();
            } else {
                List<String> deploymentTargetNames = deploymentTargets.keySet().stream().collect(Collectors.toList());
                if (deploymentTargetNames.size() == 1) {
                    return deploymentTargetNames.get(0);
                } else {
                    throw new IllegalStateException("Multiple deployment target found: '"
                            + deploymentTargetNames.stream().collect(
                                    Collectors.joining(", "))
                            + "'. To push the Helm Chart to the repository, "
                            + "you need to select only one using the property `quarkus.helm.repository.deployment-target`");
                }
            }
        }

        return null;
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
                .withApiVersion(config.apiVersion)
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
                .forEach(d -> builder.addNewDependency()
                        .withName(d.name)
                        .withAlias(defaultString(d.alias, d.name))
                        .withVersion(d.version)
                        .withRepository(d.repository)
                        .withCondition(defaultString(d.condition))
                        .withTags(defaultArray(d.tags))
                        .endDependency());

        return builder.build();
    }

    private List<ConfigReference> toValueReferences(HelmChartConfig config) {
        return config.values.values().stream()
                .map(v -> new ConfigReference(v.property,
                        defaultArray(v.paths),
                        toValue(v),
                        defaultString(v.expression),
                        defaultString(v.profile)))
                .collect(Collectors.toList());
    }

    private Object toValue(ValueReferenceConfig v) {
        if (v.valueAsInt.isPresent()) {
            return v.valueAsInt.get();
        } else if (v.valueAsBool.isPresent()) {
            return v.valueAsBool.get();
        }

        return v.value.orElse(null);
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

    private static String[] defaultArray(Optional<List<String>> optional) {
        return optional.map(l -> l.toArray(new String[0])).orElse(new String[0]);
    }

    public static String getDeploymentName(Capabilities capabilities, ApplicationInfoBuildItem info) {
        Config config = ConfigProvider.getConfig();
        Optional<String> resourceName;
        if (capabilities.isPresent(OPENSHIFT)) {
            resourceName = config.getOptionalValue(QUARKUS_OPENSHIFT_NAME, String.class);
        } else {
            resourceName = config.getOptionalValue(QUARKUS_KNATIVE_NAME, String.class)
                    .or(() -> config.getOptionalValue(QUARKUS_KUBERNETES_NAME, String.class));
        }

        return resourceName
                .or(() -> config.getOptionalValue(QUARKUS_CONTAINER_IMAGE_NAME, String.class))
                .orElse(info.getName());
    }
}
