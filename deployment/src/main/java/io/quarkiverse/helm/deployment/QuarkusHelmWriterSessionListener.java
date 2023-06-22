/**
 * Copyright 2018 The original authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **/
package io.quarkiverse.helm.deployment;

import static io.dekorate.helm.util.HelmTarArchiver.createTarBall;
import static io.dekorate.utils.Strings.isNullOrEmpty;
import static io.quarkiverse.helm.deployment.utils.HelmConfigUtils.deductProperty;
import static io.quarkiverse.helm.deployment.utils.MapUtils.toMultiValueUnsortedMap;
import static io.quarkiverse.helm.deployment.utils.MapUtils.toPlainMap;
import static io.quarkiverse.helm.deployment.utils.ValuesSchemaUtils.createSchema;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.EMPTY;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.END_EXPRESSION_TOKEN;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.END_TAG;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.SEPARATOR_QUOTES;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.SEPARATOR_TOKEN;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.START_EXPRESSION_TOKEN;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.START_TAG;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.read;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.readAndSet;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.set;
import static io.quarkiverse.helm.deployment.utils.YamlExpressionParserUtils.toExpression;

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

import com.fasterxml.jackson.core.type.TypeReference;

import io.dekorate.ConfigReference;
import io.dekorate.Logger;
import io.dekorate.LoggerFactory;
import io.dekorate.helm.config.AddIfStatement;
import io.dekorate.helm.config.Annotation;
import io.dekorate.helm.config.HelmChartConfig;
import io.dekorate.helm.config.HelmExpression;
import io.dekorate.helm.listener.HelmWriterSessionListener;
import io.dekorate.helm.model.Chart;
import io.dekorate.helm.model.HelmDependency;
import io.dekorate.helm.model.Maintainer;
import io.dekorate.helm.util.HelmConfigUtils;
import io.dekorate.helm.util.MapUtils;
import io.dekorate.project.Project;
import io.dekorate.utils.Exec;
import io.dekorate.utils.Maps;
import io.dekorate.utils.Serialization;
import io.dekorate.utils.Strings;
import io.github.yamlpath.YamlExpressionParser;
import io.github.yamlpath.YamlPath;
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
    public Map<String, String> writeHelmFiles(Project project,
            io.dekorate.helm.config.HelmChartConfig helmConfig,
            List<ConfigReference> valueReferencesFromUser,
            List<ConfigReference> valueReferencesFromDecorators,
            Path inputDir,
            Path outputDir,
            Collection<File> generatedFiles,
            String valuesProfileSeparator) {
        Map<String, String> artifacts = new HashMap<>();
        if (helmConfig.isEnabled()) {
            validateHelmConfig(helmConfig);

            try {
                LOGGER.info(String.format("Creating Helm Chart \"%s\"", helmConfig.getName()));
                ValuesHolder values = populateValuesFromConfig(helmConfig, inputDir);
                List<Map<Object, Object>> resources = populateValuesFromConfigReferences(helmConfig, generatedFiles, values,
                        valueReferencesFromUser, valueReferencesFromDecorators);
                artifacts.putAll(processTemplates(helmConfig, helmConfig.getAddIfStatements(), inputDir, outputDir, resources));
                artifacts.putAll(createChartYaml(helmConfig, project, inputDir, outputDir));
                artifacts.putAll(createValuesYaml(helmConfig, inputDir, outputDir, values, valuesProfileSeparator));

                // To follow Helm file structure standards:
                artifacts.putAll(createEmptyChartFolder(helmConfig, outputDir));
                artifacts.putAll(addNotesIntoTemplatesFolder(helmConfig, inputDir, outputDir));
                artifacts.putAll(addAdditionalResources(helmConfig, inputDir, outputDir));

                // Final step: packaging
                if (helmConfig.isCreateTarFile()) {
                    fetchDependencies(helmConfig, outputDir);
                    artifacts.putAll(createTarball(helmConfig, project, outputDir, artifacts));
                }

            } catch (IOException e) {
                throw new RuntimeException("Error writing resources", e);
            }
        }

        return artifacts;
    }

    private Map<String, String> addAdditionalResources(HelmChartConfig helmConfig, Path inputDir, Path outputDir)
            throws IOException {
        if (inputDir == null || !inputDir.toFile().exists()) {
            return Collections.emptyMap();
        }

        Map<String, String> artifacts = new HashMap<>();
        for (File source : inputDir.toFile().listFiles()) {
            if (ADDITIONAL_CHART_FILES.stream().anyMatch(source.getName()::equalsIgnoreCase)) {
                artifacts.putAll(addAdditionalResource(helmConfig, outputDir, source));
            }
        }

        return artifacts;
    }

    private Map<String, String> addAdditionalResource(HelmChartConfig helmConfig, Path outputDir, File source)
            throws IOException {
        if (!source.exists()) {
            return Collections.emptyMap();
        }

        Path destination = getChartOutputDir(helmConfig, outputDir).resolve(source.getName());
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

    private void fetchDependencies(io.dekorate.helm.config.HelmChartConfig helmConfig, Path outputDir) {
        if (helmConfig.getDependencies() != null && helmConfig.getDependencies().length > 0) {
            Path chartFolder = getChartOutputDir(helmConfig, outputDir);
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

    private void validateHelmConfig(io.dekorate.helm.config.HelmChartConfig helmConfig) {
        if (isNullOrEmpty(helmConfig.getName())) {
            throw new RuntimeException("Helm Chart name is required!");
        }
    }

    private Map<String, String> addNotesIntoTemplatesFolder(io.dekorate.helm.config.HelmChartConfig helmConfig, Path inputDir,
            Path outputDir)
            throws IOException {
        InputStream notesInputStream;

        File notesInInputDir = inputDir.resolve(NOTES).toFile();
        if (notesInInputDir.exists()) {
            notesInputStream = new FileInputStream(notesInInputDir);
        } else {
            if (isNullOrEmpty(helmConfig.getNotes())) {
                return Collections.emptyMap();
            }

            notesInputStream = getResourceFromClasspath(helmConfig.getNotes());
        }

        if (notesInputStream == null) {
            throw new RuntimeException("Could not find the notes template file in the classpath at " + helmConfig.getNotes());
        }
        Path chartOutputDir = getChartOutputDir(helmConfig, outputDir).resolve(TEMPLATES).resolve(NOTES);
        Files.copy(notesInputStream, chartOutputDir);
        return Collections.singletonMap(chartOutputDir.toString(), EMPTY);
    }

    private InputStream getResourceFromClasspath(String notes) {
        // Try to locate the file from the context class loader
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(notes);
        if (is == null) {
            // if not found, try to find it in the current classpath.
            is = HelmWriterSessionListener.class.getResourceAsStream(notes);
        }

        return is;
    }

    private Map<String, String> createEmptyChartFolder(io.dekorate.helm.config.HelmChartConfig helmConfig, Path outputDir)
            throws IOException {
        Path emptyChartsDir = getChartOutputDir(helmConfig, outputDir).resolve(CHARTS);
        Files.createDirectories(emptyChartsDir);
        return Collections.singletonMap(emptyChartsDir.toString(), EMPTY);
    }

    private Map<String, String> createValuesYaml(io.dekorate.helm.config.HelmChartConfig helmConfig,
            Path inputDir, Path outputDir, ValuesHolder valuesHolder, String valuesProfileSeparator)
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
            artifacts.putAll(writeFileAsYaml(mergeWithFileIfExists(inputDir, VALUES + YAML, toValuesMap(values)),
                    getChartOutputDir(helmConfig, outputDir)
                            .resolve(VALUES + valuesProfileSeparator + profile + YAML)));
        }

        // Next, we process the prod profile
        artifacts.putAll(writeFileAsYaml(mergeWithFileIfExists(inputDir, VALUES + YAML, toValuesMap(prodValues)),
                getChartOutputDir(helmConfig, outputDir).resolve(VALUES + YAML)));

        // Next, the "values.schema.json" file
        if (helmConfig.isCreateValuesSchemaFile()) {
            Map<String, Object> schemaAsMap = createSchema(helmConfig, prodValues);
            artifacts.putAll(
                    writeFileAsJson(mergeWithFileIfExists(inputDir, VALUES_SCHEMA, MapUtils.toMultiValueSortedMap(schemaAsMap)),
                            getChartOutputDir(helmConfig, outputDir).resolve(VALUES_SCHEMA)));
        } else {
            artifacts.putAll(addAdditionalResource(helmConfig, outputDir, inputDir.resolve(VALUES_SCHEMA).toFile()));
        }

        // Next, the "README.md" file
        if (helmConfig.isCreateReadmeFile()) {
            String readmeContent = ReadmeBuilder.build(helmConfig, prodValues);
            artifacts.putAll(writeFile(readmeContent, getChartOutputDir(helmConfig, outputDir).resolve(README)));
        } else {
            artifacts.putAll(addAdditionalResource(helmConfig, outputDir, inputDir.resolve(README).toFile()));
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

    private Map<String, String> createTarball(io.dekorate.helm.config.HelmChartConfig helmConfig, Project project,
            Path outputDir,
            Map<String, String> artifacts) throws IOException {

        File tarballFile = outputDir.resolve(String.format("%s-%s%s.%s",
                helmConfig.getName(),
                getVersion(helmConfig, project),
                isNullOrEmpty(helmConfig.getTarFileClassifier()) ? EMPTY : "-" + helmConfig.getTarFileClassifier(),
                helmConfig.getExtension()))
                .toFile();

        LOGGER.debug(String.format("Creating Helm configuration Tarball: '%s'", tarballFile));

        Path helmSources = getChartOutputDir(helmConfig, outputDir);

        List<File> files = new ArrayList<>();
        for (String filePath : artifacts.keySet()) {
            File file = new File(filePath);
            if (file.isDirectory()) {
                files.addAll(Arrays.asList(file.listFiles()));
            } else {
                files.add(file);
            }
        }

        createTarBall(tarballFile, helmSources.toFile(), files, helmConfig.getExtension(),
                tae -> tae.setName(String.format("%s/%s", helmConfig.getName(), tae.getName())));

        return Collections.singletonMap(tarballFile.toString(), null);
    }

    private String getVersion(io.dekorate.helm.config.HelmChartConfig helmConfig, Project project) {
        if (isNullOrEmpty(helmConfig.getVersion())) {
            return project.getBuildInfo().getVersion();
        }

        return helmConfig.getVersion();
    }

    private Map<String, String> processTemplates(io.dekorate.helm.config.HelmChartConfig helmConfig,
            AddIfStatement[] addIfStatements,
            Path inputDir,
            Path outputDir,
            List<Map<Object, Object>> resources) throws IOException {

        Map<String, String> templates = new HashMap<>();
        Path templatesDir = getChartOutputDir(helmConfig, outputDir).resolve(TEMPLATES);
        Files.createDirectories(templatesDir);

        Map<String, String> functionsByResource = processUserDefinedTemplates(inputDir, templates, templatesDir);
        // Split yamls in separated files by kind
        for (Map<Object, Object> resource : resources) {
            // Add user defined expressions
            if (helmConfig.getExpressions() != null) {
                YamlExpressionParser parser = new YamlExpressionParser(Arrays.asList(resource));
                for (HelmExpression expressionConfig : helmConfig.getExpressions()) {
                    if (expressionConfig.getPath() != null && expressionConfig.getExpression() != null) {
                        readAndSet(parser, expressionConfig.getPath(), expressionConfig.getExpression());
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
            for (AddIfStatement addIfStatement : addIfStatements) {
                if ((isNullOrEmpty(addIfStatement.getOnResourceKind()) || addIfStatement.getOnResourceKind().equals(kind))
                        && (isNullOrEmpty(addIfStatement.getOnResourceName())
                                || addIfStatement.getOnResourceName().equals(getNameFromResource(resource)))) {

                    String property = deductProperty(helmConfig, addIfStatement.getProperty());

                    adaptedString = String.format(IF_STATEMENT_START_TAG, property)
                            + System.lineSeparator()
                            + adaptedString
                            + System.lineSeparator()
                            + TEMPLATE_FUNCTION_END_TAG
                            + System.lineSeparator();
                }
            }

            adaptedString = adaptedString
                    .replaceAll(Pattern.quote("\"" + START_TAG), START_TAG)
                    .replaceAll(Pattern.quote(END_TAG + "\""), END_TAG)
                    .replaceAll("\"" + START_EXPRESSION_TOKEN, EMPTY)
                    .replaceAll(END_EXPRESSION_TOKEN + "\"", EMPTY)
                    .replaceAll(SEPARATOR_QUOTES, "\"")
                    .replaceAll(SEPARATOR_TOKEN, System.lineSeparator())
                    // replace randomly escape characters that is entered by Jackson readTree method:
                    .replaceAll("\\\\\\n(\\s)*\\\\", EMPTY);

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
                    String[] userResource = Strings.read(new FileInputStream(userTemplateFile)).split(System.lineSeparator());

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

    private ValuesHolder populateValuesFromConfig(io.dekorate.helm.config.HelmChartConfig helmConfig, Path inputDir) {
        ValuesHolder values = new ValuesHolder();

        // Populate expressions from conditions
        for (io.dekorate.helm.config.HelmDependency dependency : helmConfig.getDependencies()) {
            if (Strings.isNotNullOrEmpty(dependency.getCondition())) {
                String propertyName = HelmConfigUtils.deductProperty(helmConfig, dependency.getCondition());
                ConfigReference configReference = new ConfigReference.Builder(propertyName, new String[0])
                        .withDescription("Flag to enable/disable the dependency '" + dependency.getName() + "'")
                        .build();
                values.put(propertyName, configReference, true);
            }
        }

        // Populate if statements expressions
        for (AddIfStatement addIfStatement : helmConfig.getAddIfStatements()) {
            String propertyName = deductProperty(helmConfig, addIfStatement.getProperty());
            ConfigReference configReference = new ConfigReference.Builder(propertyName, new String[0])
                    .withDescription(addIfStatement.getDescription())
                    .withValue(addIfStatement.getWithDefaultValue())
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
                ConfigReference configReference = new ConfigReference.Builder(entry.getKey(), new String[0])
                        .withValue(entry.getValue())
                        .build();
                values.put(entry.getKey(), configReference);
            }
        }

        return values;
    }

    private List<Map<Object, Object>> populateValuesFromConfigReferences(io.dekorate.helm.config.HelmChartConfig helmConfig,
            Collection<File> generatedFiles,
            ValuesHolder values,
            List<ConfigReference> valuesReferencesFromUser,
            List<ConfigReference> valuesReferencesFromDecorators) throws IOException {
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
                            seen, paths);
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
                            paths);
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
            YamlExpressionParser parser, Map<String, Object> seen, Set<String> paths) {

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
                    set(parser, path, toExpression(property, value, found, valueReference));
                    values.putIfAbsent(property, valueReference, actualValue, profile);
                    if (isNullOrEmpty(profile)) {
                        seen.putIfAbsent(property, actualValue);
                    }
                }
            }
        } else {
            values.putIfAbsent(property, valueReference, value, profile);
            if (isNullOrEmpty(profile)) {
                seen.putIfAbsent(property, value);
            }
        }
    }

    private Map<String, String> createChartYaml(io.dekorate.helm.config.HelmChartConfig helmConfig, Project project,
            Path inputDir, Path outputDir)
            throws IOException {
        final Chart chart = new Chart();
        chart.setName(helmConfig.getName());
        chart.setVersion(getVersion(helmConfig, project));
        chart.setDescription(helmConfig.getDescription());
        chart.setHome(helmConfig.getHome());
        chart.setSources(Arrays.asList(helmConfig.getSources()));
        chart.setMaintainers(Arrays.stream(helmConfig.getMaintainers())
                .map(m -> new Maintainer(m.getName(), m.getEmail(), m.getUrl()))
                .collect(Collectors.toList()));
        chart.setIcon(helmConfig.getIcon());
        chart.setApiVersion(helmConfig.getApiVersion());
        chart.setCondition(helmConfig.getCondition());
        chart.setTags(helmConfig.getTags());
        chart.setAppVersion(helmConfig.getAppVersion());
        if (helmConfig.isDeprecated()) {
            chart.setDeprecated(helmConfig.isDeprecated());
        }
        chart.setAnnotations(Arrays.stream(helmConfig.getAnnotations())
                .collect(Collectors.toMap(Annotation::getKey, Annotation::getValue)));
        chart.setKubeVersion(helmConfig.getKubeVersion());
        chart.setKeywords(Arrays.asList(helmConfig.getKeywords()));
        chart.setDependencies(Arrays.stream(helmConfig.getDependencies())
                .map(d -> new HelmDependency(d.getName(),
                        Strings.defaultIfEmpty(d.getAlias(), d.getName()),
                        d.getVersion(),
                        d.getRepository(),
                        d.getCondition(),
                        d.getTags(),
                        d.isEnabled()))
                .collect(Collectors.toList()));
        chart.setType(helmConfig.getType());

        Path yml = getChartOutputDir(helmConfig, outputDir).resolve(CHART_FILENAME).normalize();
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
        return writeFile(value, file);
    }

    private Map<String, String> writeFileAsJson(Object data, Path file) throws IOException {
        String value = Serialization.asJson(data);
        return writeFile(value, file);
    }

    private Map<String, String> writeFile(String value, Path file) throws IOException {
        try (FileWriter writer = new FileWriter(file.toFile(), APPEND)) {
            writer.write(value);
            return Collections.singletonMap(file.toString(), value);
        }
    }

    private Path getChartOutputDir(HelmChartConfig helmConfig, Path outputDir) {
        return outputDir.resolve(helmConfig.getName());
    }
}
