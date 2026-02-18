package io.quarkiverse.helm.deployment;

import static org.apache.commons.lang3.StringUtils.*;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.nio.charset.Charset;

import org.jboss.logging.Logger;

public final class HelmChartUploader {

    private static final Logger LOGGER = Logger.getLogger(HelmProcessor.class);

    private static final String APPLICATION_GZIP = "application/gzip";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String POST = "POST";
    private static final String PUT = "PUT";

    private HelmChartUploader() {

    }

    static void pushToHelmRepository(File tarball, HelmRepository helmRepository) {
        validate(helmRepository);
        try {
            LOGGER.info(
                    "Pushing the Helm Chart at '" + tarball.getName() + "' to the repository: " + helmRepository.url().get());
            HttpURLConnection connection = deductConnectionByRepositoryType(tarball, helmRepository);

            writeFileOnConnection(tarball, connection);

            if (connection.getResponseCode() >= HttpURLConnection.HTTP_MULT_CHOICE) {
                String response;
                if (connection.getErrorStream() != null) {
                    response = inputStreamToString(connection.getErrorStream(), Charset.defaultCharset());
                } else if (connection.getInputStream() != null) {
                    response = inputStreamToString(connection.getInputStream(), Charset.defaultCharset());
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

    private static void writeFileOnConnection(File file, HttpURLConnection connection) throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            copyToOutputStream(fileInputStream, connection.getOutputStream());
        }
    }

    private static HttpURLConnection deductConnectionByRepositoryType(File tarball, HelmRepository repository)
            throws IOException {
        if (repository.type().get() == HelmRepositoryType.NEXUS) {
            String url = formatRepositoryURL(tarball, repository);
            if (url.endsWith(".tar.gz")) {
                url = url.replaceAll("tar.gz$", "tgz");
            }
            final HttpURLConnection connection = createConnection(repository, url);
            connection.setRequestMethod(PUT);
            return connection;
        } else if (repository.type().get() == HelmRepositoryType.ARTIFACTORY) {
            final HttpURLConnection connection = createConnection(repository, formatRepositoryURL(tarball, repository));
            connection.setRequestMethod(PUT);
            return connection;
        }

        // chartmuseum
        return createConnection(repository, repository.url().get());
    }

    private static String formatRepositoryURL(File file, HelmRepository repository) {
        return String.format("%s%s", appendIfMissing(repository.url().get(), "/"), file.getName());
    }

    private static HttpURLConnection createConnection(HelmRepository repository, String url) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setDoOutput(true);
        connection.setRequestMethod(POST);
        connection.setRequestProperty(CONTENT_TYPE, APPLICATION_GZIP);
        verifyAndSetAuthentication(repository);
        return connection;
    }

    private static void verifyAndSetAuthentication(HelmRepository helmRepository) {
        if (isNotEmpty(helmRepository.getUsername()) && isNotEmpty(helmRepository.getPassword())) {
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

    private static String inputStreamToString(InputStream input, Charset charset) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Reader reader = new BufferedReader(new InputStreamReader(input, charset))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                sb.append((char) c);
            }
        }

        return sb.toString();
    }

    private static void copyToOutputStream(InputStream source, OutputStream target) throws IOException {
        byte[] buf = new byte[8192];
        int length;
        while ((length = source.read(buf)) != -1) {
            target.write(buf, 0, length);
        }
    }

}
