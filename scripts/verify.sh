#!/usr/bin/env bash
set -euo pipefail

# Local equivalent of CI "Verify Integration Test" steps.
# Prerequisites: kind cluster + local registry: registry.localtest.me:5000
#
# Usage: ./verify.sh [integration-test-name] [container-builder]
#   e.g. ./verify.sh helm-kubernetes-minimal            (default: docker)
#        ./verify.sh helm-kubernetes-minimal podman
#        ./verify.sh helm-kubernetes-with-templates docker

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEST_NAME="${1:-helm-kubernetes-minimal}"
CONTAINER_BUILDER="${2:-podman}"
TEST_DIR="$PROJECT_ROOT/integration-tests/$TEST_NAME"

if [ ! -d "$TEST_DIR" ]; then
  echo "ERROR: integration test directory not found: $TEST_DIR"
  echo "Available tests:"
  ls -1 "$PROJECT_ROOT/integration-tests/" | grep '^helm-'
  exit 1
fi

if [[ "$CONTAINER_BUILDER" != "docker" && "$CONTAINER_BUILDER" != "podman" ]]; then
  echo "ERROR: container builder must be 'docker' or 'podman', got: $CONTAINER_BUILDER"
  exit 1
fi

LOCAL_REGISTRY="${LOCAL_REGISTRY:-registry.localtest.me:5000}"
KIND_REGISTRY_GROUP="local"
VERSION="latest"
K8S_NAMESPACE="helm"
ARTIFACT_NAME="quarkus-helm-it-${TEST_NAME#helm-}"
QUARKUS_HELM_VERSION=$(cd "$PROJECT_ROOT" && mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)

echo "==> Test: $TEST_NAME"
echo "==> Container engine: $CONTAINER_BUILDER"
echo "==> Artifact: $ARTIFACT_NAME"
echo "==> Project version: $QUARKUS_HELM_VERSION"
echo "==> Registry: $LOCAL_REGISTRY"
echo "==> Namespace: $K8S_NAMESPACE"

# Create namespace (ignore if it already exists)
kubectl create namespace "$K8S_NAMESPACE" 2>/dev/null || true

# Build, containerize, and push
cd "$TEST_DIR"
MVN_ARGS=(clean package -P "$CONTAINER_BUILDER"
  -Dquarkus.container-image.build=true
  -Dquarkus.container-image.push=true
  -Dquarkus.container-image.registry="$LOCAL_REGISTRY"
  -Dquarkus.container-image.group="$KIND_REGISTRY_GROUP"
  -Dquarkus.container-image.tag="$VERSION"
  -Dquarkus.container-image.insecure=true
)
if [[ "$CONTAINER_BUILDER" == "podman" ]]; then
  MVN_ARGS+=(-Dquarkus.podman.tls-verify=false)
fi
mvn "${MVN_ARGS[@]}"

# Lint the generated chart
echo "==> Linting chart..."
java -jar "$PROJECT_ROOT/cli/target/quarkus-helm-cli-${QUARKUS_HELM_VERSION}.jar" lint

# Install the chart
echo "==> Installing chart..."
java -jar "$PROJECT_ROOT/cli/target/quarkus-helm-cli-${QUARKUS_HELM_VERSION}.jar" install \
  -n "$K8S_NAMESPACE" \
  --set "app.image=${LOCAL_REGISTRY}/${KIND_REGISTRY_GROUP}/${ARTIFACT_NAME}:${VERSION}"

# Wait for the pod to be running
echo "==> Waiting for pod..."
bash "$PROJECT_ROOT/.github/scripts/waitFor.sh" \
  pod \
  "-l app.kubernetes.io/name=${ARTIFACT_NAME}" \
  "$K8S_NAMESPACE" \
  "Running"

echo "==> Integration test '$TEST_NAME' passed!"