package io.quarkiverse.helm.deployment;

import java.io.File;

/**
 * Uploads the chart by POSTing it to the ChartMuseum API endpoint (e.g. `http://host/api/charts`).
 */
final class ChartMuseumHelmChartPusher extends HttpHelmChartPusher {

    @Override
    String method() {
        return "POST";
    }

    @Override
    String url(File tarball, HelmRepository repository) {
        return repository.url().get();
    }
}
