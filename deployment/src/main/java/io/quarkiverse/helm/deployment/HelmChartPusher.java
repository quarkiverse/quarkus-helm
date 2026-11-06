package io.quarkiverse.helm.deployment;

import java.io.File;
import java.io.IOException;

/**
 * Pushes a packaged Helm chart to a given type of Helm repository.
 */
interface HelmChartPusher {

    void push(File tarball, HelmRepository repository) throws IOException;
}
