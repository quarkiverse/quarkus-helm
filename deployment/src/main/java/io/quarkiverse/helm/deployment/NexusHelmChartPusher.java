package io.quarkiverse.helm.deployment;

import java.io.File;

/**
 * Same as Artifactory, but Nexus only accepts the `.tgz` extension.
 */
final class NexusHelmChartPusher extends ArtifactoryHelmChartPusher {

    @Override
    String url(File tarball, HelmRepository repository) {
        String url = super.url(tarball, repository);
        if (url.endsWith(".tar.gz")) {
            url = url.replaceAll("tar.gz$", "tgz");
        }
        return url;
    }
}
