package io.quarkiverse.helm.deployment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.charset.Charset;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.jboss.logging.Logger;

import io.dekorate.utils.Strings;

public final class HelmChartUploader {

    private static Logger LOGGER = Logger.getLogger(HelmProcessor.class);

    private static String APPLICATION_GZIP = "application/gzip";
    private static String POST = "POST";
    private static String PUT = "PUT";

    private HelmChartUploader() {

    }

    static void pushToHelmRepository(File tarball, HelmRepository helmRepository) {
        validate(helmRepository);
        try {
            LOGGER.info("Pushing the Helm Chart at '" + tarball.getName() + "' to the repository: " + helmRepository.url.get());
            HttpURLConnection connection = deductConnectionByRepositoryType(tarball, helmRepository);

            writeFileOnConnection(tarball, connection);

            if (connection.getResponseCode() >= HttpURLConnection.HTTP_MULT_CHOICE) {
                String response;
                if (connection.getErrorStream() != null) {
                    response = IOUtils.toString(connection.getErrorStream(), Charset.defaultCharset());
                } else if (connection.getInputStream() != null) {
                    response = IOUtils.toString(connection.getInputStream(), Charset.defaultCharset());
                } else {
                    response = "No details provided";
                }
                throw new RuntimeException("Couldn't upload the Helm chart to the Helm repository: " + response);
            } else {
                LOGGER.info("Helm chart was successfully uploaded to the Helm repository.");
            }
            connection.disconnect();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void validate(HelmRepository repository) {
        if (repository.url.isEmpty() || Strings.isNullOrEmpty(repository.url.get())) {
            throw new RuntimeException("The push to a Helm repository is enabled (the property `quarkus.helm.repository.push` "
                    + "is true), but the repository URL was not provided (the property `quarkus.helm.repository.url`).");
        }

        if (repository.type.isEmpty()) {
            throw new RuntimeException("The push to a Helm repository is enabled (the property `quarkus.helm.repository.push` "
                    + "is true), but the repository type was not provided (the property `quarkus.helm.repository.type`).");
        }

        if ((Strings.isNotNullOrEmpty(repository.getUsername()) && Strings.isNullOrEmpty(repository.getPassword()))
                || (Strings.isNotNullOrEmpty(repository.getPassword()) && Strings.isNullOrEmpty(repository.getUsername()))) {
            throw new RuntimeException("The push to a Helm repository is enabled (the property `quarkus.helm.repository.push` "
                    + "is true), but either the username (the property `quarkus.helm.repository.username`) "
                    + "or the password (the property `quarkus.helm.repository.password`) was not set.");
        }
    }

    private static void writeFileOnConnection(File file, HttpURLConnection connection) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            IOUtils.copy(fileInputStream, connection.getOutputStream());
        }
    }

    private static HttpURLConnection deductConnectionByRepositoryType(File tarball, HelmRepository repository)
            throws IOException {
        if (repository.type.get() == HelmRepositoryType.NEXUS) {
            String url = formatRepositoryURL(tarball, repository);
            if (url.endsWith(".tar.gz")) {
                url = url.replaceAll("tar.gz$", "tgz");
            }
            final HttpURLConnection connection = createConnection(repository, url);
            connection.setRequestMethod(PUT);
            return connection;
        } else if (repository.type.get() == HelmRepositoryType.ARTIFACTORY) {
            final HttpURLConnection connection = createConnection(repository, formatRepositoryURL(tarball, repository));
            connection.setRequestMethod(PUT);
            return connection;
        }

        // chartmuseum
        return createConnection(repository, repository.url.get());
    }

    private static String formatRepositoryURL(File file, HelmRepository repository) {
        return String.format("%s%s", StringUtils.appendIfMissing(repository.url.get(), "/"), file.getName());
    }

    private static HttpURLConnection createConnection(HelmRepository repository, String url) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod(POST);
        connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, APPLICATION_GZIP);
        verifyAndSetAuthentication(repository);
        return connection;
    }

    private static void verifyAndSetAuthentication(HelmRepository helmRepository) {
        if (Strings.isNotNullOrEmpty(helmRepository.getUsername()) && Strings.isNotNullOrEmpty(helmRepository.getPassword())) {
            PasswordAuthentication authentication = new PasswordAuthentication(helmRepository.getUsername(),
                    helmRepository.getPassword().toCharArray());

            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return authentication;
                }
            });
        }
    }
}
