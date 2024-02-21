package io.quarkiverse.helm.deployment;

import static io.quarkiverse.helm.deployment.utils.HelmConfigUtils.deductProperty;
import static io.quarkiverse.helm.deployment.utils.HelmTarArchiver.createTarBall;
import static io.quarkiverse.helm.deployment.utils.MapUtils.toMultiValueUnsortedMap;
import static io.quarkiverse.helm.deployment.utils.MapUtils.toPlainMap;
import static io.quarkiverse.helm.deployment.utils.ValuesSchemaUtils.createSchema;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.EMPTY;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.END_EXPRESSION_TOKEN;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.END_TAG;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.QUOTE_CONVERSION;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.SEPARATOR_QUOTES;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.SEPARATOR_TOKEN;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.START_EXPRESSION_TOKEN;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.START_TAG;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.read;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.readAndSet;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.set;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.toExpression;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;

import io.dekorate.ConfigReference;
import io.dekorate.Logger;
import io.dekorate.LoggerFactory;
import io.dekorate.project.Project;
import io.dekorate.utils.Exec;
import io.dekorate.utils.Maps;
import io.dekorate.utils.Serialization;
import io.github.yamlpath.YamlExpressionParser;
import io.github.yamlpath.YamlPath;
import io.quarkiverse.helm.deployment.model.Chart;
import io.quarkiverse.helm.deployment.model.HelmDependency;
import io.quarkiverse.helm.deployment.model.Maintainer;
import io.quarkiverse.helm.deployment.utils.FileUtils;
import io.quarkiverse.helm.deployment.utils.MapUtils;
import io.quarkiverse.helm.deployment.utils.ReadmeBuilder;
import io.quarkiverse.helm.deployment.utils.ValuesHolder;

public class QuarkusHelmWriterSessionListener {
    private static final String YAML = ".yaml";
    private static final String YAML_REG_EXP = ".*?\\.ya?ml$";
    private static final String CHART_FILENAME = "Chart" + YAML;
    private static final String VALUES = "values";
    private static final String TEMPLATES = "templates";
    private static final String CHARTS = "charts";
    private static final String NOTES = "NOTES.txt";
    private static final String VALUES_SCHEMA = "values.schema.json";
    private static final String README = "README.md";
    private static final List<String> ADDITIONAL_CHART_FILES = Arrays.asList("LICENSE", "app-readme.md",
            "questions.yml", "questions.yaml", "requirements.yml", "requirements.yaml", "crds");
    private static final String KIND = "kind";
    private static final String METADATA = "metadata";
    private static final String NAME = "name";
    private static final String ENVIRONMENT_PROPERTY_GROUP = "envs.";
    private static final String IF_STATEMENT_START_TAG = "{{- if .Values.%s }}";
    private static final String TEMPLATE_FUNCTION_START_TAG = "{{- define";
    private static final String TEMPLATE_FUNCTION_END_TAG = "{{- end }}";
    private static final String HELM_HELPER_PREFIX = "_";
    private static final boolean APPEND = true;
    private static final Logger LOGGER = LoggerFactory.getLogger();

    /**
     * Needs to be public in order to be called from outside the session context.
     *
     * @return the list of the Helm generated files.
     */
    public Map<String, String> writeHelmFiles(String name,
            Project project,
            HelmChartConfig helmConfig,
            List<ConfigReference> valueReferencesFromDecorators,
            Path inputDir,
            Path outputDir,
            Collection<File> generatedFiles) {
        Map<String, String> artifacts = new HashMap<>();
        if (helmConfig.enabled()) {

            try {
                LOGGER.info(String.format("Creating Helm Chart \"%s\"", name));
                ValuesHolder values = populateValuesFromConfig(helmConfig, inputDir);
                List<Map<Object, Object>> resources = populateValuesFromConfigReferences(helmConfig, generatedFiles, values,
                        valueReferencesFromDecorators);
                artifacts.putAll(processTemplates(name, helmConfig, inputDir, outputDir, resources));
                artifacts.putAll(createChartYaml(name, helmConfig, project, inputDir, outputDir));
                artifacts.putAll(createValuesYaml(name, helmConfig, inputDir, outputDir, values));

                // To follow Helm file structure standards:
                artifacts.putAll(createEmptyChartFolder(name, outputDir));
                artifacts.putAll(addNotesIntoTemplatesFolder(name, helmConfig, inputDir, outputDir));
                artifacts.putAll(addAdditionalResources(name, inputDir, outputDir));

                // Final step: packaging
                if (helmConfig.createTarFile() || helmConfig.repository().push()) {
                    fetchDependencies(name, helmConfig, outputDir);
                    artifacts.putAll(createTarball(name, helmConfig, project, outputDir, artifacts));
                }

            } catch (IOException e) {
                throw new RuntimeException("Error writing resources", e);
            }
        }

        return artifacts;
    }

    private Map<String, String> addAdditionalResources(String name, Path inputDir, Path outputDir)
            throws IOException {
        if (inputDir == null || !inputDir.toFile().exists()) {
            return Collections.emptyMap();
        }

        Map<String, String> artifacts = new HashMap<>();
        for (File source : inputDir.toFile().listFiles()) {
            if (ADDITIONAL_CHART_FILES.stream().anyMatch(source.getName()::equalsIgnoreCase)) {
                artifacts.putAll(addAdditionalResource(name, outputDir, source));
            }
        }

        return artifacts;
    }

    private Map<String, String> addAdditionalResource(String name, Path outputDir, File source)
            throws IOException {
        if (!source.exists()) {
            return Collections.emptyMap();
        }

        Path destination = getChartOutputDir(name, outputDir).resolve(source.getName());
        if (source.isDirectory()) {
            Files.createDirectory(destination);
            for (File file : source.listFiles()) {
                Files.copy(new FileInputStream(file), destination.resolve(file.getName()));
            }
        } else {
            Files.copy(new FileInputStream(source), destination);
        }

        return Collections.singletonMap(destination.toString(), EMPTY);
    }

    private void fetchDependencies(String name, HelmChartConfig helmConfig, Path outputDir) {
        if (helmConfig.dependencies() != null && !helmConfig.dependencies().isEmpty()) {
            Path chartFolder = getChartOutputDir(name, outputDir);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            boolean success = Exec.inPath(chartFolder)
                    .redirectingOutput(out)
                    .commands("helm", "dependency", "build");

            if (success) {
                LOGGER.info("Dependencies successfully fetched");
            } else {
                throw new RuntimeException("Error fetching Helm dependencies. Cause: " + new String(out.toByteArray()));
            }
        }
    }

    private Map<String, String> addNotesIntoTemplatesFolder(String name, HelmChartConfig helmConfig, Path inputDir,
            Path outputDir)
            throws IOException {
        InputStream notesInputStream;

        File notesInInputDir = inputDir.resolve(NOTES).toFile();
        if (notesInInputDir.exists()) {
            notesInputStream = new FileInputStream(notesInInputDir);
        } else {
            if (isEmpty(helmConfig.notes())) {
                return Collections.emptyMap();
            }

            notesInputStream = getResourceFromClasspath(helmConfig.notes());
        }

        if (notesInputStream == null) {
            throw new RuntimeException("Could not find the notes template file in the classpath at " + helmConfig.notes());
        }
        Path chartOutputDir = getChartOutputDir(name, outputDir).resolve(TEMPLATES).resolve(NOTES);
        Files.copy(notesInputStream, chartOutputDir);
        return Collections.singletonMap(chartOutputDir.toString(), EMPTY);
    }

    private InputStream getResourceFromClasspath(String notes) {
        // Try to locate the file from the context class loader
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(notes);
    }

    private Map<String, String> createEmptyChartFolder(String name, Path outputDir)
            throws IOException {
        Path emptyChartsDir = getChartOutputDir(name, outputDir).resolve(CHARTS);
        Files.createDirectories(emptyChartsDir);
        return Collections.singletonMap(emptyChartsDir.toString(), EMPTY);
    }

    private Map<String, String> createValuesYaml(String name, HelmChartConfig helmConfig,
            Path inputDir, Path outputDir, ValuesHolder valuesHolder)
            throws IOException {
        Map<String, ValuesHolder.HelmValueHolder> prodValues = valuesHolder.getProdValues();
        Map<String, Map<String, ValuesHolder.HelmValueHolder>> valuesByProfile = valuesHolder.getValuesByProfile();

        Map<String, String> artifacts = new HashMap<>();

        // first, we process the values in each profile
        for (Map.Entry<String, Map<String, ValuesHolder.HelmValueHolder>> valuesInProfile : valuesByProfile.entrySet()) {
            String profile = valuesInProfile.getKey();
            Map<String, ValuesHolder.HelmValueHolder> values = valuesInProfile.getValue();
            // Populate the profiled values with the one from prod if the key does not exist
            for (Map.Entry<String, ValuesHolder.HelmValueHolder> prodValue : prodValues.entrySet()) {
                if (!values.containsKey(prodValue.getKey())) {
                    values.put(prodValue.getKey(), prodValue.getValue());
                }
            }

            // Create the values.<profile>.yaml file
            artifacts.putAll(writeFileAsYaml(
                    mergeWithFileIfExists(inputDir, VALUES + helmConfig.valuesProfileSeparator() + profile + YAML,
                            toValuesMap(values)),
                    getChartOutputDir(name, outputDir)
                            .resolve(VALUES + helmConfig.valuesProfileSeparator() + profile + YAML)));
        }

        // Next, we process the prod profile
        artifacts.putAll(writeFileAsYaml(toValuesMap(prodValues),
                getChartOutputDir(name, outputDir).resolve(VALUES + YAML)));

        // Next, the "values.schema.json" file
        if (helmConfig.createValuesSchemaFile()) {
            Map<String, Object> schemaAsMap = createSchema(helmConfig, prodValues);
            artifacts.putAll(
                    writeFileAsJson(mergeWithFileIfExists(inputDir, VALUES_SCHEMA, MapUtils.toMultiValueSortedMap(schemaAsMap)),
                            getChartOutputDir(name, outputDir).resolve(VALUES_SCHEMA)));
        } else {
            artifacts.putAll(addAdditionalResource(name, outputDir, inputDir.resolve(VALUES_SCHEMA).toFile()));
        }

        // Next, the "README.md" file
        if (helmConfig.createReadmeFile()) {
            String readmeContent = ReadmeBuilder.build(name, helmConfig, prodValues);
            artifacts.putAll(writeFile(readmeContent, getChartOutputDir(name, outputDir).resolve(README)));
        } else {
            artifacts.putAll(addAdditionalResource(name, outputDir, inputDir.resolve(README).toFile()));
        }

        return artifacts;
    }

    private Map<String, Object> toValuesMap(Map<String, ValuesHolder.HelmValueHolder> holder) {
        Map<String, Object> values = new HashMap<>();
        for (Map.Entry<String, ValuesHolder.HelmValueHolder> value : holder.entrySet()) {
            values.put(value.getKey(), value.getValue().value);
        }

        return MapUtils.toMultiValueSortedMap(values);
    }

    private Map<String, Object> mergeWithFileIfExists(Path inputDir, String file, Map<String, Object> valuesAsMultiValueMap) {
        File templateValuesFile = inputDir.resolve(file).toFile();
        if (templateValuesFile.exists()) {
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> yaml = Serialization.unmarshal(templateValuesFile,
                    new TypeReference<Map<String, Object>>() {
                    });
            result.putAll(yaml);
            // first, incorporate the properties from the file
            Maps.merge(valuesAsMultiValueMap, result);
            // then, merge it with the generated data
            Maps.merge(result, valuesAsMultiValueMap);
            return result;
        }

        return valuesAsMultiValueMap;
    }

    private Map<String, String> createTarball(String name, HelmChartConfig helmConfig, Project project,
            Path outputDir,
            Map<String, String> artifacts) throws IOException {

        File tarballFile = outputDir.resolve(String.format("%s-%s%s.%s",
                name,
                getVersion(helmConfig, project),
                helmConfig.tarFileClassifier().map(c -> "-" + c).orElse(EMPTY),
                helmConfig.extension()))
                .toFile();

        LOGGER.debug(String.format("Creating Helm configuration Tarball: '%s'", tarballFile));

        Path helmSources = getChartOutputDir(name, outputDir);

        List<File> files = new ArrayList<>();
        for (String filePath : artifacts.keySet()) {
            File file = new File(filePath);
            if (file.isDirectory()) {
                files.addAll(Arrays.asList(file.listFiles()));
            } else {
                files.add(file);
            }
        }

        createTarBall(tarballFile, helmSources.toFile(), files, helmConfig.extension(),
                tae -> tae.setName(String.format("%s/%s", name, tae.getName())));

        return Collections.singletonMap(tarballFile.toString(), null);
    }

    private String getVersion(HelmChartConfig helmConfig, Project project) {
        return helmConfig.version().orElse(project.getBuildInfo().getVersion());
    }

    private Map<String, String> processTemplates(String name, HelmChartConfig helmConfig,
            Path inputDir,
            Path outputDir,
            List<Map<Object, Object>> resources) throws IOException {

        Map<String, String> templates = new HashMap<>();
        Path templatesDir = getChartOutputDir(name, outputDir).resolve(TEMPLATES);
        Files.createDirectories(templatesDir);

        Map<String, String> functionsByResource = processUserDefinedTemplates(inputDir, templates, templatesDir);
        // Split yamls in separated files by kind
        for (Map<Object, Object> resource : resources) {
            // Add user defined expressions
            if (helmConfig.expressions() != null) {
                YamlExpressionParser parser = new YamlExpressionParser(Arrays.asList(resource));
                for (ExpressionConfig expressionConfig : helmConfig.expressions().values()) {
                    if (expressionConfig.path() != null && expressionConfig.expression() != null) {
                        readAndSet(parser, expressionConfig.path(), expressionConfig.expression());
                    }
                }
            }

            String kind = (String) resource.get(KIND);
            Path targetFile = templatesDir.resolve(kind.toLowerCase() + YAML);
            String functions = functionsByResource.get(kind.toLowerCase() + YAML);

            // Adapt the values tag to Helm standards:
            String adaptedString = Serialization.yamlMapper().writeValueAsString(resource);
            if (functions != null) {
                adaptedString = functions + System.lineSeparator() + adaptedString;
            }

            // Add if statements at resource level
            for (Map.Entry<String, AddIfStatementConfig> addIfStatement : helmConfig.addIfStatement().entrySet()) {
                AddIfStatementConfig addIfStatementConfig = addIfStatement.getValue();
                if ((addIfStatementConfig.onResourceKind().isEmpty()
                        || addIfStatementConfig.onResourceKind().get().equals(kind))
                        && (addIfStatementConfig.onResourceName().isEmpty()
                                || addIfStatementConfig.onResourceName().get().equals(getNameFromResource(resource)))) {
                    String propertyName = addIfStatementConfig.property().orElse(addIfStatement.getKey());
                    String property = deductProperty(helmConfig, propertyName);

                    adaptedString = String.format(IF_STATEMENT_START_TAG, property)
                            + System.lineSeparator()
                            + adaptedString
                            + System.lineSeparator()
                            + TEMPLATE_FUNCTION_END_TAG
                            + System.lineSeparator();
                }
            }

            adaptedString = applyKnownPatterns(adaptedString);

            writeFile(adaptedString, targetFile);
            templates.put(targetFile.toString(), adaptedString);
        }

        return templates;
    }

    private String getNameFromResource(Map<Object, Object> resource) {
        Object metadata = resource.get(METADATA);
        if (metadata != null && metadata instanceof Map) {
            Object name = ((Map) metadata).get(NAME);
            if (name != null) {
                return name.toString();
            }
        }

        return null;
    }

    private Map<String, String> processUserDefinedTemplates(Path inputDir, Map<String, String> templates, Path templatesDir)
            throws IOException {
        Map<String, String> functionsByResource = new HashMap<>();

        File inputTemplates = inputDir.resolve(TEMPLATES).toFile();
        if (inputTemplates.exists()) {
            File[] userTemplates = inputTemplates.listFiles();
            for (File userTemplateFile : userTemplates) {
                if (userTemplateFile.getName().startsWith(HELM_HELPER_PREFIX)) {
                    // it's a helper Helm file, include as it is
                    Path output = templatesDir.resolve(userTemplateFile.getName());
                    Files.copy(new FileInputStream(userTemplateFile), output);
                    templates.put(output.toString(), EMPTY);
                } else {
                    // it's a resource template, let's extract only the template functions and include
                    // it into the generated file later.
                    String[] userResource = FileUtils.lines(userTemplateFile);

                    StringBuilder sb = new StringBuilder();
                    boolean isFunction = false;
                    for (String lineUserResource : userResource) {
                        if (lineUserResource.contains(TEMPLATE_FUNCTION_START_TAG) || isFunction) {
                            isFunction = !lineUserResource.contains(TEMPLATE_FUNCTION_END_TAG);
                            sb.append(lineUserResource + System.lineSeparator());
                        }
                    }

                    functionsByResource.put(userTemplateFile.getName(), sb.toString());
                }
            }
        }
        return functionsByResource;
    }

    private ValuesHolder populateValuesFromConfig(HelmChartConfig helmConfig, Path inputDir) {
        ValuesHolder values = new ValuesHolder();

        // Populate expressions from conditions
        for (Map.Entry<String, HelmDependencyConfig> dependency : helmConfig.dependencies().entrySet()) {
            dependency.getValue().condition().ifPresent(condition -> {
                String dependencyName = dependency.getValue().name().orElse(dependency.getKey());
                String propertyName = deductProperty(helmConfig, condition);
                ConfigReference configReference = new ConfigReference.Builder(propertyName, new String[0])
                        .withDescription("Flag to enable/disable the dependency '" + dependencyName + "'")
                        .build();
                values.put(propertyName, configReference, true);
            });
        }

        // Populate if statements expressions
        for (Map.Entry<String, AddIfStatementConfig> addIfStatement : helmConfig.addIfStatement().entrySet()) {
            String property = addIfStatement.getValue().property().orElse(addIfStatement.getKey());
            String propertyName = deductProperty(helmConfig, property);
            ConfigReference configReference = new ConfigReference.Builder(propertyName, new String[0])
                    .withDescription(addIfStatement.getValue().description())
                    .withValue(addIfStatement.getValue().withDefaultValue())
                    .build();
            values.put(propertyName, configReference);
        }

        // Populate from custom `values.yaml` file if exists
        File templateValuesFile = inputDir.resolve(VALUES + YAML).toFile();
        if (templateValuesFile.exists()) {
            Map<String, Object> yaml = toPlainMap(Serialization.unmarshal(templateValuesFile,
                    new TypeReference<Map<String, Object>>() {
                    }));

            for (Map.Entry<String, Object> entry : yaml.entrySet()) {
                Object value = entry.getValue();
                // when there are spaces, we need to preserve the double quotes.
                if (entry.getValue() instanceof String str && str.contains(" ")) {
                    value = SEPARATOR_QUOTES + str + SEPARATOR_QUOTES;
                }

                ConfigReference configReference = new ConfigReference.Builder(entry.getKey(), new String[0])
                        .withValue(value)
                        .build();
                values.put(entry.getKey(), configReference);
            }
        }

        return values;
    }

    private List<Map<Object, Object>> populateValuesFromConfigReferences(HelmChartConfig helmConfig,
            Collection<File> generatedFiles,
            ValuesHolder values,
            List<ConfigReference> valuesReferencesFromDecorators) throws IOException {
        List<ConfigReference> valuesReferencesFromUser = helmConfig.values().entrySet().stream()
                .map(e -> new ConfigReference.Builder(e.getValue().property().orElse(e.getKey()),
                        e.getValue().paths().map(l -> l.toArray(new String[0])).orElse(new String[0]))
                        .withValue(toValue(e.getValue()))
                        .withDescription(e.getValue().description().orElse(EMPTY))
                        .withExpression(e.getValue().expression().orElse(null))
                        .withProfile(e.getValue().profile().orElse(null))
                        .withRequired(e.getValue().required())
                        .withPattern(e.getValue().pattern().orElse(null))
                        .withMaximum(e.getValue().maximum().orElse(Integer.MAX_VALUE))
                        .withMinimum(e.getValue().minimum().orElse(Integer.MIN_VALUE))
                        .build())
                .collect(Collectors.toList());

        List<Map<Object, Object>> allResources = new LinkedList<>();
        for (File generatedFile : generatedFiles) {
            if (!generatedFile.getName().toLowerCase().matches(YAML_REG_EXP)) {
                continue;
            }

            // Read helm expression parsers
            YamlExpressionParser parser = YamlPath.from(new FileInputStream(generatedFile));

            // Seen lookup by default values.yaml file.
            Map<String, Object> seen = new HashMap<>();
            Set<String> paths = new HashSet<>();

            // Merge all values references in order: first the users' and then the decorators'.
            List<ConfigReference> valuesReferences = new ArrayList<>();
            valuesReferences.addAll(valuesReferencesFromUser);
            valuesReferences.addAll(valuesReferencesFromDecorators);

            // First, process the non-environmental properties
            for (ConfigReference valueReference : valuesReferences) {
                if (!valueIsEnvironmentProperty(valueReference)) {
                    String valueReferenceProperty = deductProperty(helmConfig, valueReference.getProperty());

                    processValueReference(valueReferenceProperty, valueReference.getValue(), valueReference, values, parser,
                            seen, paths, EMPTY);
                }
            }

            // Next, process the environmental properties, so we can decide if it's a property coming from values.yaml or not.
            for (ConfigReference valueReference : valuesReferences) {
                if (valueIsEnvironmentProperty(valueReference)) {
                    String valueReferenceProperty = deductProperty(helmConfig, valueReference.getProperty());
                    Object valueReferenceValue = valueReference.getValue();
                    String environmentProperty = getEnvironmentPropertyName(valueReference);

                    // Try to find the value from the current values
                    Map<String, ValuesHolder.HelmValueHolder> current = values.get(valueReference.getProfile());
                    for (Map.Entry<String, ValuesHolder.HelmValueHolder> currentValue : current.entrySet()) {
                        if (currentValue.getKey().endsWith(environmentProperty)) {
                            // found, we use this value instead of generating an additional envs.xxx=yyy property
                            valueReferenceProperty = currentValue.getKey();
                            valueReferenceValue = currentValue.getValue().value;
                            break;
                        }
                    }

                    processValueReference(valueReferenceProperty, valueReferenceValue, valueReference, values, parser, seen,
                            paths, QUOTE_CONVERSION);
                }
            }

            allResources.addAll(parser.getResources());
        }

        return allResources;
    }

    private boolean valueIsEnvironmentProperty(ConfigReference valueReference) {
        return valueReference.getProperty().contains(ENVIRONMENT_PROPERTY_GROUP);
    }

    private String getEnvironmentPropertyName(ConfigReference valueReference) {
        String property = valueReference.getProperty();
        int index = valueReference.getProperty().indexOf(ENVIRONMENT_PROPERTY_GROUP);
        if (index >= 0) {
            property = property.substring(index + ENVIRONMENT_PROPERTY_GROUP.length());
        }

        return property;
    }

    private void processValueReference(String property, Object value, ConfigReference valueReference, ValuesHolder values,
            YamlExpressionParser parser, Map<String, Object> seen, Set<String> paths, String defaultConversion) {

        String profile = valueReference.getProfile();
        if (valueReference.getPaths() != null && valueReference.getPaths().length > 0) {
            for (String path : valueReference.getPaths()) {
                Object found = seen.get(property);

                if (paths.contains(path) && found != null) {
                    // path was already processed. Skipping.
                    continue;
                }

                if (found == null) {
                    found = read(parser, path);
                    paths.add(path);
                }

                Object actualValue = Optional.ofNullable(value).orElse(found);

                if (actualValue != null) {
                    set(parser, path, toExpression(property, value, found, valueReference, defaultConversion));
                    values.putIfAbsent(property, valueReference, actualValue, profile);
                    if (StringUtils.isEmpty(profile)) {
                        seen.putIfAbsent(property, actualValue);
                    }
                }
            }
        } else {
            values.putIfAbsent(property, valueReference, value, profile);
            if (StringUtils.isEmpty(profile)) {
                seen.putIfAbsent(property, value);
            }
        }
    }

    private Map<String, String> createChartYaml(String name, HelmChartConfig helmConfig, Project project,
            Path inputDir, Path outputDir)
            throws IOException {
        final Chart chart = new Chart();
        chart.setName(name);
        chart.setVersion(getVersion(helmConfig, project));
        helmConfig.description().ifPresent(chart::setDescription);
        helmConfig.home().ifPresent(chart::setHome);
        helmConfig.sources().ifPresent(chart::setSources);
        chart.setMaintainers(helmConfig.maintainers().entrySet()
                .stream()
                .map(e -> new Maintainer(e.getValue().name().orElse(e.getKey()), e.getValue().email().orElse(EMPTY),
                        e.getValue().url().orElse(EMPTY)))
                .collect(Collectors.toList()));
        helmConfig.icon().ifPresent(chart::setIcon);
        chart.setApiVersion(helmConfig.apiVersion());
        helmConfig.condition().ifPresent(chart::setCondition);
        helmConfig.tags().ifPresent(chart::setTags);
        helmConfig.appVersion().ifPresent(chart::setAppVersion);
        helmConfig.deprecated().ifPresent(chart::setDeprecated);
        chart.setAnnotations(helmConfig.annotations());
        helmConfig.kubeVersion().ifPresent(chart::setKubeVersion);
        helmConfig.keywords().ifPresent(chart::setKeywords);
        chart.setDependencies(helmConfig.dependencies().entrySet().stream()
                .map(d -> new HelmDependency(d.getValue().name().orElse(d.getKey()),
                        d.getValue().alias().orElse(d.getValue().name().orElse(d.getKey())),
                        d.getValue().version(),
                        d.getValue().repository(),
                        d.getValue().condition().orElse(EMPTY),
                        d.getValue().tags().map(l -> l.toArray(new String[0])).orElse(new String[0]),
                        d.getValue().enabled().orElse(true)))
                .collect(Collectors.toList()));
        helmConfig.type().ifPresent(chart::setType);

        Path yml = getChartOutputDir(name, outputDir).resolve(CHART_FILENAME).normalize();
        File userChartFile = inputDir.resolve(CHART_FILENAME).toFile();
        Object chartContent = chart;
        if (userChartFile.exists()) {
            chartContent = mergeWithFileIfExists(inputDir, CHART_FILENAME,
                    toMultiValueUnsortedMap(Serialization.yamlMapper().readValue(Serialization.asYaml(chart), Map.class)));
        }

        return writeFileAsYaml(chartContent, yml);
    }

    private Map<String, String> writeFileAsYaml(Object data, Path file) throws IOException {
        String value = Serialization.asYaml(data);
        return writeFile(applyKnownPatterns(value), file);
    }

    private Map<String, String> writeFileAsJson(Object data, Path file) throws IOException {
        String value = Serialization.asJson(data);
        return writeFile(applyKnownPatterns(value), file);
    }

    private Map<String, String> writeFile(String value, Path file) throws IOException {
        try (FileWriter writer = new FileWriter(file.toFile(), APPEND)) {
            writer.write(value);
            return Collections.singletonMap(file.toString(), value);
        }
    }

    private Path getChartOutputDir(String name, Path outputDir) {
        return outputDir.resolve(name);
    }

    private Object toValue(ValueReferenceConfig v) {
        if (v.valueAsInt().isPresent()) {
            return v.valueAsInt().get();
        } else if (v.valueAsBool().isPresent()) {
            return v.valueAsBool().get();
        } else if (!v.valueAsMap().isEmpty()) {
            return v.valueAsMap();
        } else if (v.valueAsList().isPresent()) {
            return v.valueAsList().get();
        }

        return v.value().orElse(null);
    }

    private static String applyKnownPatterns(String adaptedString) {
        adaptedString = adaptedString
                .replaceAll(Pattern.quote("\"" + START_TAG), START_TAG)
                .replaceAll(Pattern.quote(END_TAG + "\""), END_TAG)
                .replaceAll("\"" + START_EXPRESSION_TOKEN, EMPTY)
                .replaceAll(END_EXPRESSION_TOKEN + "\"", EMPTY)
                .replaceAll(SEPARATOR_QUOTES, "\"")
                .replaceAll(SEPARATOR_TOKEN, System.lineSeparator())
                // replace randomly escape characters that is entered by Jackson readTree method:
                .replaceAll("\\\\\\n(\\s)*\\\\", EMPTY);
        return adaptedString;
    }
}
