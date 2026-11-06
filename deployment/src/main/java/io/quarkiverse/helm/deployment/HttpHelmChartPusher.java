package io.quarkiverse.helm.deployment;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

import org.jboss.logging.Logger;

/**
 * Base class for Helm repositories that accept the chart tarball as the body of a single HTTP request.
 */
abstract class HttpHelmChartPusher implements HelmChartPusher {

    private static final Logger LOGGER = Logger.getLogger(HttpHelmChartPusher.class);

    private static final String APPLICATION_GZIP = "application/gzip";
    private static final String AUTHORIZATION = "Authorization";
    private static final String CONTENT_TYPE = "Content-Type";

    /**
     * @return the HTTP method used to upload the chart.
     */
    abstract String method();

    /**
     * @return the URL the chart is uploaded to.
     */
    abstract String url(File tarball, HelmRepository repository);

    @Override
    public void push(File tarball, HelmRepository repository) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url(tarball, repository)).toURL().openConnection();
        try {
            connection.setDoOutput(true);
            connection.setRequestMethod(method());
            connection.setRequestProperty(CONTENT_TYPE, APPLICATION_GZIP);
            if (isNotEmpty(repository.getUsername()) && isNotEmpty(repository.getPassword())) {
                connection.setRequestProperty(AUTHORIZATION,
                        basicAuthentication(repository.getUsername(), repository.getPassword()));
            }

            try (OutputStream output = connection.getOutputStream()) {
                Files.copy(tarball.toPath(), output);
            }

            if (connection.getResponseCode() >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new RuntimeException("Couldn't upload the Helm chart to the Helm repository: " + responseOf(connection));
            }
            LOGGER.info("Helm chart was successfully uploaded to the Helm repository.");
        } finally {
            connection.disconnect();
        }
    }

    static String basicAuthentication(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String responseOf(HttpURLConnection connection) throws IOException {
        InputStream response = connection.getErrorStream();
        if (response == null) {
            response = connection.getInputStream();
        }
        if (response == null) {
            return "No details provided";
        }
        try (InputStream input = response) {
            return new String(input.readAllBytes(), Charset.defaultCharset());
        }
    }
}
