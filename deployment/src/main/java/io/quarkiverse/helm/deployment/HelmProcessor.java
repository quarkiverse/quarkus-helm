package io.quarkiverse.helm.deployment;

import static io.quarkiverse.helm.deployment.HelmChartUploader.pushToHelmRepository;
import static io.quarkiverse.helm.deployment.utils.SystemPropertiesUtils.getPropertyFromSystem;
import static io.quarkiverse.helm.deployment.utils.SystemPropertiesUtils.getSystemProperties;
import static io.quarkiverse.helm.deployment.utils.SystemPropertiesUtils.hasSystemProperties;
import static io.quarkus.deployment.Capability.OPENSHIFT;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.ConfigValue;
import org.jboss.logging.Logger;

import io.dekorate.ConfigReference;
import io.dekorate.Session;
import io.dekorate.kubernetes.config.ContainerBuilder;
import io.dekorate.kubernetes.decorator.AddInitContainerDecorator;
import io.dekorate.project.Project;
import io.dekorate.utils.Serialization;
import io.quarkiverse.helm.deployment.decorators.LowPriorityAddEnvVarDecorator;
import io.quarkiverse.helm.deployment.rules.ConfigReferenceStrategyManager;
import io.quarkiverse.helm.deployment.utils.HelmConfigUtils;
import io.quarkiverse.helm.model.Chart;
import io.quarkiverse.helm.model.ValuesSchema;
import io.quarkiverse.helm.spi.AdditionalHelmTemplateBuildItem;
import io.quarkiverse.helm.spi.CustomHelmOutputDirBuildItem;
import io.quarkiverse.helm.spi.HelmChartBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.IsNormal;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationInfoBuildItem;
import io.quarkus.deployment.builditem.GeneratedFileSystemResourceBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.deployment.util.FileUtil;
import io.quarkus.kubernetes.deployment.DeploymentTargetEntry;
import io.quarkus.kubernetes.deployment.EnabledKubernetesDeploymentTargetsBuildItem;
import io.quarkus.kubernetes.spi.ConfiguratorBuildItem;
import io.quarkus.kubernetes.spi.CustomKubernetesOutputDirBuildItem;
import io.quarkus.kubernetes.spi.DecoratorBuildItem;
import io.quarkus.kubernetes.spi.DekorateOutputBuildItem;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;

public class HelmProcessor {
    private static final Logger LOGGER = Logger.getLogger(HelmProcessor.class);

    private static final String NAME_FORMAT_REG_EXP = "[a-z0-9]([-a-z0-9]*[a-z0-9])?(\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*";
    private static final List<String> HELM_INVALID_CHARACTERS = Arrays.asList("-");
    private static final String BUILD_TIME_PROPERTIES = "/build-time-list";
    private static final String INIT_CONTAINER_CONDITION_FORMAT = "$(env | grep %s | grep -q false) && exit 0; %s";

    private static final String QUARKUS_KUBERNETES_NAME = "quarkus.kubernetes.name";
    private static final String QUARKUS_KNATIVE_NAME = "quarkus.knative.name";
    private static final String QUARKUS_OPENSHIFT_NAME = "quarkus.openshift.name";
    private static final String QUARKUS_CONTAINER_IMAGE_NAME = "quarkus.container-image.name";
    private static final String SERVICE_NAME_PLACEHOLDER = "::service-name";
    private static final String SERVICE_PORT_PLACEHOLDER = "::service-port";
    private static final String SPLIT = ":";
    private static final String PROPERTIES_CONFIG_SOURCE = "PropertiesConfigSource";
    private static final String YAML_CONFIG_SOURCE = "YamlConfigSource";
    private static final HelmChartBuildItem.Deserializer DESERIALIZER = path -> {
        final Class<?> type = switch (path.getFileName().toString()) {
            case "Chart.yaml" -> Chart.class;
            case "values.yaml" -> Map.class;
            case "values.schema.json" -> ValuesSchema.class;
            default -> throw new IllegalArgumentException("Unsupported path: " + path);
        };
        return Serialization.unmarshal(Files.readString(path), type);
    };
    // Lazy loaded when calling `isBuildTimeProperty(xxx)`.
    private static Set<String> buildProperties;

    @BuildStep(onlyIf = { HelmEnabled.class, IsNormal.class })
    void mapSystemPropertiesIfEnabled(Capabilities capabilities, ApplicationInfoBuildItem info, HelmChartConfig helmConfig,
            BuildProducer<DecoratorBuildItem> decorators) {
        if (helmConfig.mapSystemProperties()) {
            String deploymentName = getDeploymentName(capabilities, info);
            Config config = ConfigProvider.getConfig();
            Map<String, String> propertiesFromConfigSource = new HashMap<>();
            for (String propName : config.getPropertyNames()) {
                ConfigValue propValue = config.getConfigValue(propName);
                if (isPropertiesConfigSource(propValue.getSourceName())) {
                    propertiesFromConfigSource.put(propName, propValue.getRawValue());
                }
            }

            for (Map.Entry<String, String> entry : propertiesFromConfigSource.entrySet()) {
                if (!isBuildTimeProperty(entry.getKey())) {
                    mapProperty(deploymentName, decorators, entry.getValue(), propertiesFromConfigSource);
                }
            }
        }
    }

    @BuildStep(onlyIf = { HelmEnabled.class, IsNormal.class })
    void configureHelmDependencyOrder(Capabilities capabilities, ApplicationInfoBuildItem info, HelmChartConfig config,
            BuildProducer<DecoratorBuildItem> decorators) {
        if (config.dependencies() == null || config.dependencies().isEmpty()) {
            return;
        }

        String deploymentName = getDeploymentName(capabilities, info);

        for (Map.Entry<String, HelmDependencyConfig> entry : config.dependencies().entrySet()) {
            HelmDependencyConfig dependency = entry.getValue();
            if (dependency.waitForService().isPresent()) {
                String containerName = "wait-for-" + defaultString(dependency.name(), entry.getKey());
                ContainerBuilder container = new ContainerBuilder()
                        .withName(containerName)
                        .withImage(dependency.waitForServiceImage())
                        .withCommand("sh");

                String argument = null;

                String service = dependency.waitForService().get();
                if (service.contains(SPLIT)) {
                    // it's service name and service port
                    String[] parts = service.split(SPLIT);
                    String serviceName = parts[0];
                    String servicePort = parts[1];
                    argument = dependency.waitForServicePortCommandTemplate()
                            .replaceAll(SERVICE_NAME_PLACEHOLDER, serviceName)
                            .replaceAll(SERVICE_PORT_PLACEHOLDER, servicePort);

                } else {
                    argument = dependency.waitForServiceOnlyCommandTemplate()
                            .replaceAll(SERVICE_NAME_PLACEHOLDER, service);
                }

                // if the condition is set, we need to map it as env property as well
                if (dependency.condition().isPresent()) {
                    String property = HelmConfigUtils.deductProperty(config, dependency.condition().get());
                    decorators.produce(new DecoratorBuildItem(
                            new LowPriorityAddEnvVarDecorator(deploymentName, containerName, property, "true")));

                    argument = String.format(INIT_CONTAINER_CONDITION_FORMAT, property, argument);
                }

                decorators.produce(new DecoratorBuildItem(
                        new AddInitContainerDecorator(deploymentName, container.withArguments("-c", argument).build())));
            }
        }
    }

    @BuildStep(onlyIf = { HelmEnabled.class, IsNormal.class })
    void generateResources(ApplicationInfoBuildItem app, OutputTargetBuildItem outputTarget,
            Optional<DekorateOutputBuildItem> dekorateOutput,
            EnabledKubernetesDeploymentTargetsBuildItem kubernetesDeploymentTargets,
            List<GeneratedKubernetesResourceBuildItem> generatedResources,
            // this is added to ensure that the build step will be run
            BuildProducer<HelmChartBuildItem> helmChartBuildItems,
            BuildProducer<GeneratedFileSystemResourceBuildItem> dummy,
            Optional<CustomKubernetesOutputDirBuildItem> customKubernetesOutputDir,
            Optional<CustomHelmOutputDirBuildItem> customHelmOutputDir,
            List<AdditionalHelmTemplateBuildItem> additionalHelmTemplateBuildItems,
            HelmChartConfig config) {

        if (dekorateOutput.isPresent()) {
            helmChartBuildItems.produce(doGenerateResources(app, outputTarget, dekorateOutput.get(),
                    kubernetesDeploymentTargets,
                    generatedResources,
                    customKubernetesOutputDir,
                    customHelmOutputDir,
                    additionalHelmTemplateBuildItems,
                    config));
        } else if (config.enabled()) {
            LOGGER.warn("Quarkus Helm extension is skipped since no Quarkus Kubernetes extension is configured. ");
        }
    }

    @BuildStep
    void disableDefaultHelmListener(BuildProducer<ConfiguratorBuildItem> helmConfiguration) {
        helmConfiguration.produce(new ConfiguratorBuildItem(new DisableDefaultHelmListener()));
    }

    private List<HelmChartBuildItem> doGenerateResources(ApplicationInfoBuildItem app,
            OutputTargetBuildItem outputTarget,
            DekorateOutputBuildItem dekorateOutput,
            EnabledKubernetesDeploymentTargetsBuildItem kubernetesDeploymentTargets,
            List<GeneratedKubernetesResourceBuildItem> generatedResources,
            Optional<CustomKubernetesOutputDirBuildItem> customKubernetesOutputDir,
            Optional<CustomHelmOutputDirBuildItem> customHelmOutputDir,
            List<AdditionalHelmTemplateBuildItem> additionalHelmTemplateBuildItems,
            HelmChartConfig config) {
        validate(config);
        Project project = (Project) dekorateOutput.getProject();

        Set<String> enabledDeploymentTargets = kubernetesDeploymentTargets.getEntriesSortedByPriority().stream()
                .map(DeploymentTargetEntry::getName)
                .collect(Collectors.toSet());

        // Deduct folders
        Path inputFolder = getInputDirectory(config, project);
        Path outputFolder = getOutputDirectory(config, customHelmOutputDir, outputTarget);

        // Dekorate session writer
        final QuarkusHelmWriterSessionListener helmWriter = new QuarkusHelmWriterSessionListener();
        final Map<String, Map<String, byte[]>> deploymentTargets = toDeploymentTargets(generatedResources,
                enabledDeploymentTargets);

        // Deduct deployment target to push
        String deploymentTargetToPush = deductDeploymentTarget(config, deploymentTargets);

        List<HelmChartBuildItem> helmCharts = new ArrayList<>();
        // separate generated helm charts into the deployment targets
        for (Map.Entry<String, Map<String, byte[]>> filesInDeploymentTarget : deploymentTargets.entrySet()) {
            String deploymentTarget = filesInDeploymentTarget.getKey();
            Path chartOutputFolder = outputFolder.resolve(deploymentTarget);
            deleteOutputHelmFolderIfExists(chartOutputFolder);
            String name = config.name().orElse(app.getName());
            Path appChartDir = chartOutputFolder.resolve(name);
            Map<String, byte[]> additionalTemplates = additionalHelmTemplateBuildItems.stream()
                    .filter(t -> t.getDeploymentTarget() == null || t.getDeploymentTarget().equals(deploymentTarget))
                    .collect(Collectors.toMap(AdditionalHelmTemplateBuildItem::getName,
                            AdditionalHelmTemplateBuildItem::getContent));

            Map<String, String> generated = helmWriter.writeHelmFiles(
                    name,
                    project,
                    config,
                    getConfigReferencesFromSession(deploymentTarget, dekorateOutput),
                    inputFolder,
                    chartOutputFolder,
                    filesInDeploymentTarget.getValue(),
                    additionalTemplates);

            if (!generated.isEmpty()) {
                helmCharts.add(read(appChartDir));
            }

            // Push to Helm repository if enabled
            if (config.repository().push() && deploymentTargetToPush.equals(deploymentTarget)) {
                String tarball = generated.keySet().stream()
                        .filter(file -> file.endsWith(config.extension()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Couldn't find the tarball file. There should have "
                                + "been generated when pushing to a Helm repository is enabled."));
                pushToHelmRepository(new File(tarball), config.repository());
            }
        }
        return helmCharts;
    }

    private void validate(HelmChartConfig config) {
        if (config.name().isPresent()) {
            if (!config.name().get().matches(NAME_FORMAT_REG_EXP)) {
                throw new IllegalStateException(String.format("Wrong name '%s'. Regular expression used for validation "
                        + "is '%s'", config.name().get(), NAME_FORMAT_REG_EXP));
            }
        }

        for (Map.Entry<String, AddIfStatementConfig> addIfStatement : config.addIfStatement().entrySet()) {
            String name = addIfStatement.getValue().property().orElse(addIfStatement.getKey());
            if (addIfStatement.getValue().onResourceKind().isEmpty() && addIfStatement.getValue().onResourceName().isEmpty()) {
                throw new IllegalStateException(String.format("Either 'quarkus.helm.add-if-statement.%s.on-resource-kind' "
                        + "or 'quarkus.helm.add-if-statement.%s.on-resource-kind' must be provided.",
                        addIfStatement.getKey(), addIfStatement.getKey()));
            }

            if (!config.disableNamingValidation() && HELM_INVALID_CHARACTERS.stream().anyMatch(name::contains)) {
                throw new RuntimeException(
                        String.format("The property of the `add-if-statement` '%s' is invalid. Can't use '-' characters."
                                + "You can disable the naming validation using "
                                + "`quarkus.helm.disable-naming-validation=true`", name));
            }
        }

        if (!config.disableNamingValidation()) {
            for (Map.Entry<String, HelmDependencyConfig> dependency : config.dependencies().entrySet()) {
                String name = dependency.getValue().name().orElse(dependency.getKey());
                if (dependency.getValue().condition().isPresent()
                        && HELM_INVALID_CHARACTERS.stream().anyMatch(dependency.getValue().condition().get()::contains)) {
                    throw new RuntimeException(
                            String.format("Condition of the dependency '%s' is invalid. Can't use '-' characters."
                                    + "You can disable the naming validation using "
                                    + "`quarkus.helm.disable-naming-validation=true`", name));
                }
            }

            for (Map.Entry<String, ValueReferenceConfig> value : config.values().entrySet()) {
                String name = value.getValue().property().orElse(value.getKey());
                if (HELM_INVALID_CHARACTERS.stream().anyMatch(name::contains)) {
                    throw new RuntimeException(
                            String.format("Property of the value '%s' is invalid. Can't use '-' characters."
                                    + "You can disable the naming validation using "
                                    + "`quarkus.helm.disable-naming-validation=true`", name));
                }
            }
        }
    }

    private String deductDeploymentTarget(HelmChartConfig config, Map<String, Map<String, byte[]>> deploymentTargets) {
        if (config.repository().push()) {
            // if enabled, use the deployment target from the user if set
            if (config.repository().deploymentTarget().isPresent()) {
                return config.repository().deploymentTarget().get();
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
        Path path = Paths.get(config.inputDirectory());
        if (!path.isAbsolute()) {
            return project.getRoot().resolve(path);
        }

        return path;
    }

    private Path getOutputDirectory(HelmChartConfig config, Optional<CustomHelmOutputDirBuildItem> customHelmOutputDir,
            OutputTargetBuildItem outputTarget) {
        return customHelmOutputDir
                .map(CustomHelmOutputDirBuildItem::getOutputDir)
                .orElse(outputTarget.getOutputDirectory().resolve(config.outputDirectory()));
    }

    private Map<String, Map<String, byte[]>> toDeploymentTargets(
            List<GeneratedKubernetesResourceBuildItem> generatedResources, Set<String> enabledDeploymentTargets) {
        Map<String, Map<String, byte[]>> resourceByDeploymentTarget = new HashMap<>();
        for (GeneratedKubernetesResourceBuildItem generatedResource : generatedResources) {
            if (generatedResource.getName().toLowerCase(Locale.ROOT).endsWith(".json")) {
                // skip json files
                continue;
            }

            String deploymentTarget = generatedResource.getName().substring(0, generatedResource.getName().indexOf("."));
            if (!enabledDeploymentTargets.contains(deploymentTarget)) {
                continue;
            }

            if (resourceByDeploymentTarget.containsKey(deploymentTarget)) {
                // It's already included.
                continue;
            }

            Map<String, byte[]> resourcesByName = new HashMap<>();
            resourcesByName.put(generatedResource.getName(), generatedResource.getContent());
            resourceByDeploymentTarget.put(deploymentTarget, resourcesByName);
        }

        return resourceByDeploymentTarget;
    }

    private String defaultString(Optional<String> value, String defaultStr) {
        if (value.isEmpty() || StringUtils.isEmpty(value.get())) {
            return defaultStr;
        }

        return value.get();
    }

    private String mapProperty(String deploymentName, BuildProducer<DecoratorBuildItem> decorators, String property,
            Map<String, String> propertiesFromConfigSource) {
        if (!hasSystemProperties(property)) {
            return property;
        }

        String lastPropertyValue = property;
        for (String systemProperty : getSystemProperties(property)) {
            String defaultValue = EMPTY;
            if (systemProperty.contains(SPLIT)) {
                int splitPosition = systemProperty.indexOf(SPLIT);
                defaultValue = systemProperty.substring(splitPosition + SPLIT.length());
                systemProperty = systemProperty.substring(0, splitPosition);

                if (hasSystemProperties(defaultValue)) {
                    defaultValue = mapProperty(deploymentName, decorators, defaultValue, propertiesFromConfigSource);
                }
            }

            // Incorporate if and only if the system property name is valid in Helm
            // and it's not already defined in the application properties
            // and it's not a build time property from quarkus
            if (!propertiesFromConfigSource.containsKey(systemProperty)
                    && HELM_INVALID_CHARACTERS.stream().noneMatch(systemProperty::contains)
                    && !isBuildTimeProperty(systemProperty)) {
                // Check whether the system property is provided:
                defaultValue = getPropertyFromSystem(systemProperty, defaultValue);

                decorators.produce(new DecoratorBuildItem(
                        new LowPriorityAddEnvVarDecorator(deploymentName, systemProperty, defaultValue)));

                lastPropertyValue = defaultValue;
            }
        }

        return lastPropertyValue;
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

    private boolean isPropertiesConfigSource(String sourceName) {
        return StringUtils.isNotEmpty(sourceName)
                && (sourceName.startsWith(PROPERTIES_CONFIG_SOURCE) || sourceName.startsWith(YAML_CONFIG_SOURCE));
    }

    private boolean isBuildTimeProperty(String name) {
        if (buildProperties == null) {
            buildProperties = new HashSet<>();
            try {
                Scanner scanner = new Scanner(HelmProcessor.class.getResourceAsStream(BUILD_TIME_PROPERTIES));
                while (scanner.hasNextLine()) {
                    buildProperties.add(scanner.nextLine());
                }
            } catch (Exception e) {
                LOGGER.debugf("Can't read the build time properties file at '%s'. Caused by: %s",
                        BUILD_TIME_PROPERTIES,
                        e.getMessage());
            }
        }

        return buildProperties.stream().anyMatch(build -> name.matches(build) // It's a regular expression
                || (build.endsWith(".") && name.startsWith(build)) // contains with
                || name.equals(build)); // or it's equal to
    }

    private List<ConfigReference> getConfigReferencesFromSession(String deploymentTarget,
            DekorateOutputBuildItem dekorateOutput) {
        List<ConfigReference> configReferencesFromDecorators = ((Session) dekorateOutput.getSession())
                .getResourceRegistry()
                .getConfigReferences(deploymentTarget)
                .stream()
                .flatMap(decorator -> decorator.getConfigReferences().stream())
                // This should not be necessary, but sometimes config references from the session are not well-defined.
                .map(ConfigReferenceStrategyManager::visit)
                .collect(Collectors.toList());

        Collections.reverse(configReferencesFromDecorators);
        return configReferencesFromDecorators;
    }

    private static HelmChartBuildItem read(Path dir) {
        final var helmChartBuilder = HelmChartBuildItem.readAsBuilder(dir, DESERIALIZER);
        Path targetDir = dir.getParent();
        Path helmDir = targetDir.getParent();
        String deploymentTarget = helmDir.relativize(targetDir).getFileName().toString();
        return helmChartBuilder.withDeploymentTarget(deploymentTarget).build();
    }

}
