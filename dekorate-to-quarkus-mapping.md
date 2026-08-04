# Dekorate Usage in Quarkus Helm Extension — Migration Mapping

This document inventories every Dekorate dependency, class, and utility used in the `quarkus-helm` extension, and maps each to its Quarkus-native or Fabric8/Kubernetes-client equivalent.

## 1. Dependencies

Dekorate enters the project in two ways:

| Module | Dekorate Dependency | Scope | How it arrives |
|---|---|---|---|
| `deployment` | `io.dekorate:*` (multiple packages) | compile | Transitive via `io.quarkus:quarkus-kubernetes-deployment` |
| 8 integration-test modules | `io.dekorate:dekorate-core` | test | Direct dependency in each test `pom.xml` |

### Dependency Replacement

| Dekorate | Quarkus / Fabric8 Equivalent | Notes |
|---|---|---|
| `io.dekorate:dekorate-core` (test scope) | `io.fabric8:kubernetes-client` + `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` | Tests only use `Serialization` (YAML parsing) and `Strings` (file reading). Replace with Jackson `ObjectMapper` / `YAMLFactory` and standard `java.nio.file.Files` or Fabric8 utils. |
| Transitive via `io.quarkus:quarkus-kubernetes-deployment` | Remains until Quarkus itself removes Dekorate internally. Extension code can stop *importing* Dekorate classes by switching to the equivalents below. | No action needed on the dependency itself — it is managed by Quarkus. |

---

## 2. Serialization & YAML Utilities

These are the most heavily used Dekorate classes (every integration test + the main deployment module).

| Dekorate Class | Method Used | Quarkus / Fabric8 Equivalent | Replacement |
|---|---|---|---|
| `io.dekorate.utils.Serialization` | `yamlMapper()` | `com.fasterxml.jackson.databind.ObjectMapper` + `com.fasterxml.jackson.dataformat.yaml.YAMLFactory` | `new ObjectMapper(new YAMLFactory())` or `io.fabric8.kubernetes.client.utils.Serialization.unmarshal(...)` |
| `io.dekorate.utils.Serialization` | `unmarshal(InputStream)` | `io.fabric8.kubernetes.client.utils.Serialization.unmarshal(InputStream)` | Direct drop-in; Fabric8 provides the same method signature |
| `io.dekorate.utils.Serialization` | `asYaml(Object)` | Jackson `ObjectMapper(YAMLFactory).writeValueAsString(obj)` | Or `io.fabric8.kubernetes.client.utils.Serialization.asYaml(obj)` |
| `io.dekorate.utils.Serialization` | `asJson(Object)` | Jackson `ObjectMapper().writeValueAsString(obj)` | Or `io.fabric8.kubernetes.client.utils.Serialization.asJson(obj)` |

**Files affected:** `HelmProcessor.java`, `QuarkusHelmWriterSessionListener.java`, `ValuesSchemaUtils.java`, and all 8 integration test classes.

---

## 3. String & File Utilities

| Dekorate Class | Method Used | Quarkus / Fabric8 Equivalent | Replacement |
|---|---|---|---|
| `io.dekorate.utils.Strings` | `read(InputStream)` | `java.nio.file.Files.readString(Path)` or `new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)` | Standard JDK — no library needed |
| `io.dekorate.utils.Strings` | `defaultIfEmpty(String, String)` | `org.apache.commons.lang3.StringUtils.defaultIfEmpty(str, def)` | Already in the project's dependencies (commons-lang3) |
| `io.dekorate.utils.Maps` | `merge(Map, Map)` | Manual `Map.putAll()` / `Map.merge()` | Or write a small utility; no Fabric8 equivalent |
| `io.dekorate.utils.Exec` | `inPath(Path).commands(...)` | `java.lang.ProcessBuilder` | Standard JDK process execution |

**Files affected:** `KubernetesFullIT.java`, `KubernetesWithTemplatesIT.java`, `SystemPropertiesUtils.java`, `QuarkusHelmWriterSessionListener.java`.

---

## 4. Session & Configuration Model

These classes form the Dekorate session lifecycle used to intercept and configure Kubernetes resource generation.

| Dekorate Class | Usage in This Project | Quarkus / Fabric8 Equivalent | Replacement |
|---|---|---|---|
| `io.dekorate.Session` | Cast from `DekorateOutputBuildItem.getSession()` to access decorators and their `ConfigReference` lists | `io.quarkus.kubernetes.spi.DekorateOutputBuildItem` (Quarkus SPI) | The SPI is the Quarkus abstraction. If Quarkus removes Dekorate internally, a new SPI will be needed. |
| `io.dekorate.WithSession` | Implemented by `DisableDefaultHelmListener` to call `getSession().addPropertyConfiguration(...)` | No direct equivalent | Would need a Quarkus build step (`@BuildStep`) that produces a `KubernetesConfigBuildItem` or similar |
| `io.dekorate.kubernetes.config.Configurator<BaseConfigFluent<?>>` | Extended by `DisableDefaultHelmListener` to visit and mutate config | No direct equivalent | Replace with a `@BuildStep` that produces/consumes appropriate Quarkus `BuildItem`s |
| `io.dekorate.kubernetes.config.BaseConfigFluent<?>` | Visited in `DisableDefaultHelmListener.visit(...)` to disable helm via property | No direct equivalent | Part of the `Configurator` pattern — replaced alongside it |
| `io.dekorate.project.Project` | Obtained from `DekorateOutputBuildItem.getProject()` — provides `getBuildInfo().getVersion()` | Project version from `io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem` or Maven model | Use Quarkus build items or `org.eclipse.aether` model for project metadata |
| `io.dekorate.project.BuildInfo` | Used in tests to create `Project` instances with app name, version, packaging | No direct equivalent (test-only) | Replace with a simple POJO or record in test code |
| `io.dekorate.Logger` / `io.dekorate.LoggerFactory` | `LoggerFactory.getLogger()` for logging in `QuarkusHelmWriterSessionListener` | `org.jboss.logging.Logger` | Already used elsewhere in the project (`HelmProcessor.java` line 32) — just switch |

**Files affected:** `HelmProcessor.java`, `DisableDefaultHelmListener.java`, `QuarkusHelmWriterSessionListener.java`, test classes.

---

## 5. ConfigReference (Helm Values Mapping)

`ConfigReference` is the central Dekorate type used to map Kubernetes resource fields to Helm `values.yaml` entries. It is the most deeply embedded Dekorate dependency.

| Dekorate Class | Method / Field Used | Quarkus / Fabric8 Equivalent | Replacement |
|---|---|---|---|
| `io.dekorate.ConfigReference` | `new ConfigReference.Builder(property, path).withValue(v).build()` | No direct equivalent | Create a project-local `ConfigReference` record/class with `property`, `path`, `value`, `profile`, `description`, `expression` fields |
| `io.dekorate.ConfigReference` | `getProperty()`, `getValue()`, `getProfile()`, `getDescription()`, `getExpression()`, `getPaths()` | No direct equivalent | Methods on the replacement class |
| `io.dekorate.ConfigReference` | `joinProperties(String...)` (static) | No direct equivalent | Simple `String.join(".", parts)` utility |

**Files affected:** `HelmProcessor.java`, `QuarkusHelmWriterSessionListener.java`, `LowPriorityAddEnvVarDecorator.java`, `ValuesHolder.java`, `YamlExpressionParserUtils.java`, `ValuesSchemaUtils.java`, `ConfigReferenceStrategy.java`, `ConfigReferenceStrategyManager.java`, `HttpGetPortConfigReferenceStrategy.java`.

---

## 6. Kubernetes Decorators

Decorators are the Dekorate pattern for mutating generated Kubernetes resources before serialization.

| Dekorate Class | Usage in This Project | Quarkus / Fabric8 Equivalent | Replacement |
|---|---|---|---|
| `io.dekorate.kubernetes.decorator.AddEnvVarDecorator` | Superclass of `LowPriorityAddEnvVarDecorator` | No direct Quarkus equivalent | Use Fabric8 model directly: `container.getEnv().add(new EnvVarBuilder().withName(n).withValue(v).build())` within a `@BuildStep` |
| `io.dekorate.kubernetes.decorator.AddInitContainerDecorator` | Used in `HelmProcessor` to add init containers | `io.quarkus.kubernetes.spi.KubernetesInitContainerBuildItem` | Produce this build item from a `@BuildStep` |
| `io.dekorate.kubernetes.decorator.AddSidecarDecorator` | Referenced for ordering in `LowPriorityAddEnvVarDecorator.after()` | No direct equivalent | Ordering concern disappears when using Quarkus build items (priority via `@BuildStep` ordering) |
| `io.dekorate.kubernetes.decorator.ApplyApplicationContainerDecorator` | Referenced for ordering | No direct equivalent | Same as above |
| `io.dekorate.kubernetes.decorator.Decorator` | Base type reference for ordering | No direct equivalent | Same as above |
| `io.dekorate.kubernetes.decorator.ResourceProvidingDecorator` | Referenced for ordering | No direct equivalent | Same as above |

**Files affected:** `HelmProcessor.java`, `LowPriorityAddEnvVarDecorator.java`.

---

## 7. Kubernetes Config Builders

| Dekorate Class | Usage in This Project | Quarkus / Fabric8 Equivalent | Replacement |
|---|---|---|---|
| `io.dekorate.kubernetes.config.ContainerBuilder` | Build init container spec in `HelmProcessor` | `io.fabric8.kubernetes.api.model.ContainerBuilder` | Drop-in replacement — same builder pattern, richer API |
| `io.dekorate.kubernetes.config.EnvBuilder` | Build env var spec in `LowPriorityAddEnvVarDecorator` | `io.fabric8.kubernetes.api.model.EnvVarBuilder` | Drop-in replacement — `.withName(n).withValue(v).build()` |

**Files affected:** `HelmProcessor.java`, `LowPriorityAddEnvVarDecorator.java`.

---

## 8. Summary: Replacement Strategy by Category

| Category | Dekorate Classes | Recommended Replacement | Effort |
|---|---|---|---|
| **YAML/Serialization** | `Serialization` | Fabric8 `Serialization` or Jackson directly | Low — method signatures are similar |
| **String/File utils** | `Strings`, `Maps`, `Exec` | JDK standard library + commons-lang3 | Low — trivial rewrites |
| **Logging** | `Logger`, `LoggerFactory` | `org.jboss.logging.Logger` | Low — already used elsewhere in project |
| **K8s model builders** | `ContainerBuilder`, `EnvBuilder` | Fabric8 `ContainerBuilder`, `EnvVarBuilder` | Low — near drop-in |
| **ConfigReference** | `ConfigReference`, `ConfigReference.Builder` | New project-local class | Medium — deeply embedded, but self-contained |
| **Decorators** | `AddEnvVarDecorator`, `AddInitContainerDecorator`, etc. | Quarkus `@BuildStep` + Fabric8 model manipulation | Medium — requires restructuring `LowPriorityAddEnvVarDecorator` |
| **Session/Config** | `Session`, `WithSession`, `Configurator`, `BaseConfigFluent` | Quarkus build items + `@BuildStep` | High — architectural change tied to how Quarkus exposes Dekorate internals |
| **Project metadata** | `Project`, `BuildInfo` | Quarkus build items (`CurateOutcomeBuildItem`) | Medium — need to map fields |

---

## 9. Files Requiring Changes

| File | Dekorate Classes Used | Change Scope |
|---|---|---|
| `deployment/.../HelmProcessor.java` | `ConfigReference`, `Session`, `ContainerBuilder`, `AddInitContainerDecorator`, `Project`, `Serialization` | High |
| `deployment/.../QuarkusHelmWriterSessionListener.java` | `ConfigReference`, `Logger`, `LoggerFactory`, `Project`, `Exec`, `Maps`, `Serialization` | High |
| `deployment/.../DisableDefaultHelmListener.java` | `WithSession`, `BaseConfigFluent`, `Configurator` | Medium (small file, but architectural) |
| `deployment/.../decorators/LowPriorityAddEnvVarDecorator.java` | `ConfigReference`, `EnvBuilder`, `AddEnvVarDecorator`, 5 decorator classes | High (extends Dekorate class) |
| `deployment/.../utils/ValuesHolder.java` | `ConfigReference` | Low (type reference only) |
| `deployment/.../utils/YamlExpressionParserUtils.java` | `ConfigReference` | Low (type reference only) |
| `deployment/.../utils/ValuesSchemaUtils.java` | `ConfigReference`, `Serialization` | Low |
| `deployment/.../utils/SystemPropertiesUtils.java` | `Strings.defaultIfEmpty` | Low (one static import) |
| `deployment/.../rules/ConfigReferenceStrategy.java` | `ConfigReference` | Low (interface parameter type) |
| `deployment/.../rules/ConfigReferenceStrategyManager.java` | `ConfigReference` | Low (parameter type) |
| `deployment/.../rules/HttpGetPortConfigReferenceStrategy.java` | `ConfigReference` | Low (parameter type) |
| `deployment/.../test/.../QuarkusHelmWriterSessionListenerTest.java` | `BuildInfo`, `Project` | Low |
| `deployment/.../test/.../QuarkusHelmWriterSessionListenerReplacedResourceTest.java` | `BuildInfo`, `Project` | Low |
| 8 integration test classes | `Serialization`, `Strings` | Low |