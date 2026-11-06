package io.quarkiverse.helm.deployment;

import static org.apache.commons.lang3.StringUtils.appendIfMissing;

import java.io.File;

/**
 * Uploads the chart by PUTting it at `<repository url>/<tarball name>`.
 */
class ArtifactoryHelmChartPusher extends HttpHelmChartPusher {

    @Override
    String method() {
        return "PUT";
    }

    @Override
    String url(File tarball, HelmRepository repository) {
        return appendIfMissing(repository.url().get(), "/") + tarball.getName();
    }
}
