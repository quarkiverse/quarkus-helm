package io.quarkiverse.helm.deployment;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.File;
import java.io.IOException;

import org.jboss.logging.Logger;

public final class HelmChartUploader {

    private static final Logger LOGGER = Logger.getLogger(HelmChartUploader.class);

    private HelmChartUploader() {

    }

    static void pushToHelmRepository(File tarball, HelmRepository helmRepository) {
        validate(helmRepository);
        LOGGER.info("Pushing the Helm Chart at '" + tarball.getName() + "' to the repository: " + helmRepository.url().get());
        try {
            pusherFor(helmRepository.type().get()).push(tarball, helmRepository);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static HelmChartPusher pusherFor(HelmRepositoryType type) {
        return switch (type) {
            case CHARTMUSEUM -> new ChartMuseumHelmChartPusher();
            case ARTIFACTORY -> new ArtifactoryHelmChartPusher();
            case NEXUS -> new NexusHelmChartPusher();
        };
    }

    private static void validate(HelmRepository repository) {
        if (repository.url().isEmpty() || isEmpty(repository.url().get())) {
            throw new RuntimeException("The push to a Helm repository is enabled (the property `quarkus.helm.repository.push` "
                    + "is true), but the repository URL was not provided (the property `quarkus.helm.repository.url`).");
        }

        if (repository.type().isEmpty()) {
            throw new RuntimeException("The push to a Helm repository is enabled (the property `quarkus.helm.repository.push` "
                    + "is true), but the repository type was not provided (the property `quarkus.helm.repository.type`).");
        }

        if ((isNotEmpty(repository.getUsername()) && isEmpty(repository.getPassword()))
                || (isNotEmpty(repository.getPassword()) && isEmpty(repository.getUsername()))) {
            throw new RuntimeException("The push to a Helm repository is enabled (the property `quarkus.helm.repository.push` "
                    + "is true), but either the username (the property `quarkus.helm.repository.username`) "
                    + "or the password (the property `quarkus.helm.repository.password`) was not set.");
        }
    }
}
