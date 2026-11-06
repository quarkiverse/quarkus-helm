package io.quarkiverse.helm.deployment;

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
        String url = repository.url().get();
        return (url.endsWith("/") ? url : url + "/") + tarball.getName();
    }
}
