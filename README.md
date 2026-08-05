# Quarkus Helm
<!-- ALL-CONTRIBUTORS-BADGE:START - Do not remove or modify this section -->
[![All Contributors](https://img.shields.io/badge/all_contributors-1-orange.svg?style=flat-square)](#contributors-)
<!-- ALL-CONTRIBUTORS-BADGE:END -->

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.helm/quarkus-helm?logo=apache-maven&style=flat-square)](https://search.maven.org/artifact/io.quarkiverse.helm/quarkus-helm)

## Introduction

This Quarkus extension enables to generate the [Helm Chart](https://helm.sh/) resources in Quarkus.

It provides configuration properties to configure the chart metadata and values.

## Documentation

The documentation for this extension can be found [here](https://quarkiverse.github.io/quarkiverse-docs/quarkus-helm/dev/index.html).

## Compatibility with Quarkus

| Quarkus Helm Version | Quarkus Version |
|----------------------|-----------------|
| 1.4.x                | Quarkus 3.33+   |
| 1.3.x                | Quarkus 3.21+   |
| 1.2.6+               | Quarkus 3.16+   |
| 1.2.0 - 1.2.5        | Quarkus 3.5.0+  |
| 1.1.0                | Quarkus 3.3.0+  |
| 1.0.0 - 1.0.9        | Quarkus 3.0.0+  |
| 0.2.0 - 0.2.9        | Quarkus 2.14+   |
| 0.0.6 - 0.1.2        | Quarkus 2.12+   |

## Running integration tests locally

You can run the Kubernetes integration tests locally without waiting for CI, using a [kind](https://kind.sigs.k8s.io/), minikube cluster and a local container registry.

### Prerequisites

- [kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [Helm](https://helm.sh/docs/intro/install/)
- A container engine: [Docker](https://docs.docker.com/get-docker/) or [Podman](https://podman.io/docs/installation)

### 1. Create a kind cluster with a local registry

Use the [snowdrop k8s-infra](https://github.com/snowdrop/k8s-infra/tree/main/kind) scripts to create a kind cluster with a local registry:

**Local registry**

```shell
curl -sL https://raw.githubusercontent.com/snowdrop/k8s-infra/main/kind/registry.sh | bash -s install --registry-name registry.localtest.me
```

**With Docker:**

```bash
curl -sL https://raw.githubusercontent.com/snowdrop/k8s-infra/main/kind/kind.sh | bash -s install --cluster-name helm-it --registry-name registry.localtest.me
```

**With Podman (rootless):**

```bash
curl -sL https://raw.githubusercontent.com/snowdrop/k8s-infra/main/kind/kind.sh | bash -s install --cluster-name helm-it --port-map "30080:30080" --registry-name registry.localtest.me --provider podman
```

This creates a kind cluster with a local registry at `registry.localtest.me:5000`.

To delete the cluster:

```bash
curl -sL https://raw.githubusercontent.com/snowdrop/k8s-infra/main/kind/kind.sh | bash -s remove
curl -sL https://raw.githubusercontent.com/snowdrop/k8s-infra/main/kind/registry.sh | bash -s remove
```

### 2. Build the project

```bash
mvn clean install -DskipTests
```

### 3. Run an integration test

Use the `scripts/verify.sh` script to build, push, and deploy an integration test module:

```bash
# Default: helm-kubernetes-minimal with podman
./scripts/verify.sh

# Specify a test module
./scripts/verify.sh helm-kubernetes-with-templates

# Use docker instead of podman
./scripts/verify.sh helm-kubernetes-minimal docker
```

Available test modules:

| Module | Description |
|--------|-------------|
| `helm-kubernetes-minimal` | Minimal Kubernetes deployment |
| `helm-kubernetes-minimal-yaml` | Minimal with YAML config source |
| `helm-kubernetes-config` | With Kubernetes config |
| `helm-kubernetes-full` | Full-featured (health, probes, env vars) |
| `helm-kubernetes-with-dependency` | With Helm chart dependencies |
| `helm-kubernetes-with-templates` | With custom Helm templates |

The script will build the container image, push it to the local registry, lint and install the Helm chart, and wait for the pod to be running.

You can override the registry address via the `LOCAL_REGISTRY` environment variable:

```bash
LOCAL_REGISTRY=localhost:5001 ./scripts/verify.sh helm-kubernetes-minimal podman
```

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tr>
    <td align="center"><a href="https://github.com/Sgitario"><img src="https://avatars.githubusercontent.com/u/6310047?v=4&s=100" width="100px;" alt=""/><br /><sub><b>Jose Carvajal</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-helm/commits?author=Sgitario" title="Code">💻</a> <a href="#maintenance-sgitario" title="Maintenance">🚧</a></td>
  </tr>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification. Contributions of any kind welcome!
