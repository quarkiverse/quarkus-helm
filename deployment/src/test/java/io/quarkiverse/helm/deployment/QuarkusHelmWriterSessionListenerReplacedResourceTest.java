package io.quarkiverse.helm.deployment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.dekorate.project.BuildInfo;
import io.dekorate.project.Project;
import io.quarkiverse.helm.spi.AdditionalHelmTemplateBuildItem.ReplacedResource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Verifies that a base Kubernetes resource marked as {@link ReplacedResource} is actually left out of the
 * generated Helm chart, in favor of whichever additional template is meant to replace it
 * (quarkiverse/quarkus-operator-sdk#1390).
 */
class QuarkusHelmWriterSessionListenerReplacedResourceTest {

    private static final String KUBERNETES_YML = """
            ---
            apiVersion: rbac.authorization.k8s.io/v1
            kind: RoleBinding
            metadata:
              name: foo-role-binding
              namespace: default
            roleRef:
              kind: ClusterRole
              apiGroup: rbac.authorization.k8s.io
              name: foo-cluster-role
            subjects:
              - kind: ServiceAccount
                name: foo
            ---
            apiVersion: rbac.authorization.k8s.io/v1
            kind: RoleBinding
            metadata:
              name: bar-role-binding
              namespace: default
            roleRef:
              kind: ClusterRole
              apiGroup: rbac.authorization.k8s.io
              name: bar-cluster-role
            subjects:
              - kind: ServiceAccount
                name: bar
            """;

    // mirrors the real quarkus-operator-sdk "watch specific namespaces" case: the same RoleBinding
    // name is emitted once per watched namespace, so multiple documents share the same kind+name
    private static final String KUBERNETES_YML_WITH_DUPLICATE_NAMES = """
            ---
            apiVersion: rbac.authorization.k8s.io/v1
            kind: RoleBinding
            metadata:
              name: foo-role-binding
              namespace: ns1
            roleRef:
              kind: ClusterRole
              apiGroup: rbac.authorization.k8s.io
              name: foo-cluster-role
            subjects:
              - kind: ServiceAccount
                name: foo
            ---
            apiVersion: rbac.authorization.k8s.io/v1
            kind: RoleBinding
            metadata:
              name: foo-role-binding
              namespace: ns2
            roleRef:
              kind: ClusterRole
              apiGroup: rbac.authorization.k8s.io
              name: foo-cluster-role
            subjects:
              - kind: ServiceAccount
                name: foo
            ---
            apiVersion: rbac.authorization.k8s.io/v1
            kind: RoleBinding
            metadata:
              name: bar-role-binding
              namespace: ns1
            roleRef:
              kind: ClusterRole
              apiGroup: rbac.authorization.k8s.io
              name: bar-cluster-role
            subjects:
              - kind: ServiceAccount
                name: bar
            """;

    private final QuarkusHelmWriterSessionListener listener = new QuarkusHelmWriterSessionListener();

    @Test
    void replacedResourceIsExcludedWhileOthersOfTheSameKindAreKept(@TempDir Path tempDir) throws IOException {
        Path roleBindingFile = writeChart(tempDir, KUBERNETES_YML,
                List.of(new ReplacedResource("RoleBinding", "foo-role-binding")));

        assertTrue(Files.exists(roleBindingFile), "rolebinding.yaml should still exist because of 'bar-role-binding'");
        String content = Files.readString(roleBindingFile);
        assertFalse(content.contains("foo-role-binding"), "the replaced resource must not be rendered");
        assertTrue(content.contains("bar-role-binding"), "unrelated resources of the same kind must be kept");
    }

    @Test
    void nothingIsExcludedWhenNoResourceIsReplaced(@TempDir Path tempDir) throws IOException {
        Path roleBindingFile = writeChart(tempDir, KUBERNETES_YML, List.of());

        String content = Files.readString(roleBindingFile);
        assertTrue(content.contains("foo-role-binding"));
        assertTrue(content.contains("bar-role-binding"));
    }

    @Test
    void allDocumentsSharingTheReplacedNameAreExcluded(@TempDir Path tempDir) throws IOException {
        Path roleBindingFile = writeChart(tempDir, KUBERNETES_YML_WITH_DUPLICATE_NAMES,
                List.of(new ReplacedResource("RoleBinding", "foo-role-binding")));

        assertTrue(Files.exists(roleBindingFile), "rolebinding.yaml should still exist because of 'bar-role-binding'");
        String content = Files.readString(roleBindingFile);
        assertFalse(content.contains("foo-role-binding"), "no copy of the replaced resource must be rendered");
        assertTrue(content.contains("bar-role-binding"), "unrelated resources of the same kind must be kept");
    }

    private Path writeChart(Path tempDir, String kubernetesYml, List<ReplacedResource> replacedResources)
            throws IOException {
        SmallRyeConfig smallRyeConfig = new SmallRyeConfigBuilder()
                .withMapping(HelmChartConfig.class)
                .build();
        HelmChartConfig helmConfig = smallRyeConfig.getConfigMapping(HelmChartConfig.class);

        Path inputDir = tempDir.resolve("input");
        Path outputDir = tempDir.resolve("output");
        Map<String, byte[]> generatedFiles = Map.of("kubernetes.yml", KUBERNETES_YML.getBytes(StandardCharsets.UTF_8));

        // satisfy the notes-template lookup with a real file, since the classpath-relative default
        // ("/NOTES.template.txt") only resolves inside the real Quarkus augmentation classloader
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("NOTES.txt"), "notes");

        BuildInfo buildInfo = new BuildInfo("test-chart", "1.0.0", "jar", "generic", null, null, null, null);
        Project project = new Project(null, buildInfo, null);

        listener.writeHelmFiles("test-chart", project, helmConfig, List.of(), inputDir, outputDir,
                generatedFiles, Map.of(), Map.of(), replacedResources);

        return outputDir.resolve("test-chart").resolve("templates").resolve("rolebinding.yaml");
    }
}
