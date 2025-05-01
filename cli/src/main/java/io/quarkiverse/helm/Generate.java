package io.quarkiverse.helm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.quarkiverse.helm.spi.HelmChartBuildItem;
import io.quarkus.bootstrap.BootstrapAppModelFactory;
import io.quarkus.bootstrap.BootstrapException;
import io.quarkus.bootstrap.app.AugmentAction;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.deployment.builditem.GeneratedFileSystemResourceBuildItem;
import io.quarkus.devtools.project.BuildTool;
import io.quarkus.devtools.project.QuarkusProjectHelper;
import io.quarkus.kubernetes.spi.GeneratedKubernetesResourceBuildItem;
import io.quarkus.maven.dependency.ArtifactDependency;
import io.quarkus.maven.dependency.Dependency;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Parameters;

@Command(name = "generate", sortOptions = false, mixinStandardHelpOptions = false, header = "Generate Helm Chart for the current Quarkus project.", headerHeading = "%n", commandListHeading = "%nCommands:%n", synopsisHeading = "%nUsage: ", optionListHeading = "%nOptions:%n")
public class Generate<T> implements Callable<Integer> {

    private static final ArtifactDependency QUARKUS_HELM = new ArtifactDependency("io.quarkiverse.helm", "quarkus-helm", null,
            "jar", Generate.getVersion());

    @Parameters(arity = "0..1", paramLabel = "GENERATION_PATH", description = " The path to generate the Helm Charts")
    Optional<String> generationPath = Optional.of(".helm");

    public String[] getRequiredBuildItems() {
        return new String[] {
                GeneratedKubernetesResourceBuildItem.class.getName(),
                HelmChartBuildItem.class.getName(),
                GeneratedFileSystemResourceBuildItem.class.getName(),
        };
    };

    public Properties getBuildSystemProperties(Path outputDir) {
        Properties buildSystemProperties = new Properties();
        Path projectRoot = getWorkingDirectory();
        Path applicationPropertiesPath = projectRoot.resolve("src").resolve("main").resolve("resources")
                .resolve("application.properties");
        if (Files.exists(applicationPropertiesPath)) {
            try {
                buildSystemProperties.load(Files.newBufferedReader(applicationPropertiesPath));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        buildSystemProperties.put("quarkus.helm.enabled", "true");
        buildSystemProperties.put("quarkus.helm.output-directory", outputDir.toAbsolutePath().toString());
        return buildSystemProperties;
    }

    public List<Dependency> getProjectDependencies() {
        List<Dependency> dependencies = new ArrayList<>();
        dependencies.add(QUARKUS_HELM);
        try {
            BootstrapAppModelFactory.newInstance()
                    .setProjectRoot(getWorkingDirectory())
                    .setLocalProjectsDiscovery(true)
                    .resolveAppModel()
                    .getApplicationModel()
                    .getDependencies().forEach(d -> {
                        dependencies.add(new ArtifactDependency(d.getGroupId(), d.getArtifactId(), d.getClassifier(),
                                d.getType(), d.getVersion()));
                    });
        } catch (BootstrapException e) {
            //Ignore, as it's currently broken for gradle
        }

        // Also add quarkus-kubernetes if it does not exists
        if (dependencies.stream()
                .noneMatch(d -> d.getGroupId().equals("io.quarkus") && d.getArtifactId().equals("quarkus-kubernetes"))) {
            Optional<Dependency> quarkusCore = dependencies.stream()
                    .filter(d -> d.getGroupId().equals("io.quarkus") && d.getArtifactId().equals("quarkus-core")).findFirst();
            quarkusCore.ifPresent(d -> {
                dependencies.add(
                        new ArtifactDependency("io.quarkus", "quarkus-kubernetes", d.getClassifier(), d.getType(),
                                d.getVersion()));
            });
        }
        return dependencies;
    }

    public Integer call() {
        Path projectRoot = getWorkingDirectory();
        Path outputDir = generationPath.map(Paths::get).orElse(projectRoot.resolve(".helm"));

        if (outputDir.toFile().exists() && !outputDir.toFile().isDirectory()) {
            System.err.println("Output directory is not a directory: " + outputDir);
            return ExitCode.SOFTWARE;
        }
        if (!outputDir.toFile().exists() && !outputDir.toFile().mkdirs()) {
            System.err.println("Failed to create output directory: " + outputDir);
            return ExitCode.SOFTWARE;
        }

        BuildTool buildTool = QuarkusProjectHelper.detectExistingBuildTool(projectRoot);
        Path targetDirecotry = projectRoot.resolve(buildTool.getBuildDirectory());
        QuarkusBootstrap quarkusBootstrap = QuarkusBootstrap.builder()
                .setMode(QuarkusBootstrap.Mode.PROD)
                .setBuildSystemProperties(getBuildSystemProperties(outputDir))
                .setApplicationRoot(getWorkingDirectory())
                .setProjectRoot(getWorkingDirectory())
                .setTargetDirectory(targetDirecotry)
                .setIsolateDeployment(false)
                .setRebuild(true)
                .setTest(false)
                .setLocalProjectDiscovery(true)
                .setBaseClassLoader(ClassLoader.getSystemClassLoader())
                .setForcedDependencies(getProjectDependencies())
                .build();

        // Checking
        try (CuratedApplication curatedApplication = quarkusBootstrap.bootstrap()) {
            AugmentAction action = curatedApplication.createAugmentor();

            action.performCustomBuild(HelmHandler.class.getName(), new Consumer<List<HelmChartBuildItem>>() {
                @Override
                public void accept(List<HelmChartBuildItem> list) {
                }
            }, getRequiredBuildItems());

        } catch (BootstrapException e) {
            throw new RuntimeException(e);
        }
        return ExitCode.OK;
    }

    protected void writeStringSafe(Path p, String content) {
        try {
            Files.writeString(p, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Path getWorkingDirectory() {
        return Paths.get(System.getProperty("user.dir"));
    }

    private static String getVersion() {
        return read(Generate.class.getClassLoader().getResourceAsStream("version"));
    }

    private static String read(InputStream is) {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(is))) {
            return buffer.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
