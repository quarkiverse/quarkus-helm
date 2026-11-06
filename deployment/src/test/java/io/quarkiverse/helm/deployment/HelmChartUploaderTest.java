package io.quarkiverse.helm.deployment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

class HelmChartUploaderTest {

    private static final byte[] CHART = "chart-content".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private HttpServer server;
    private volatile ReceivedRequest received;
    private volatile int responseCode = 201;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            received = new ReceivedRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestBody().readAllBytes());
            byte[] body = "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseCode, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void chartMuseumPostsToRepositoryUrl() throws IOException {
        push(HelmRepositoryType.CHARTMUSEUM, "/api/charts", "myapp-1.0.0.tar.gz", null, null);

        assertEquals("POST", received.method());
        assertEquals("/api/charts", received.path());
        assertEquals("application/gzip", received.contentType());
        assertNull(received.authorization());
        assertArrayEquals(CHART, received.body());
    }

    @Test
    void artifactoryPutsTarballUnderRepositoryUrl() throws IOException {
        push(HelmRepositoryType.ARTIFACTORY, "/artifactory/helm", "myapp-1.0.0.tar.gz", null, null);

        assertEquals("PUT", received.method());
        assertEquals("/artifactory/helm/myapp-1.0.0.tar.gz", received.path());
        assertArrayEquals(CHART, received.body());
    }

    @Test
    void nexusPutsTarballWithTgzExtension() throws IOException {
        push(HelmRepositoryType.NEXUS, "/repository/helm/", "myapp-1.0.0.tar.gz", null, null);

        assertEquals("PUT", received.method());
        assertEquals("/repository/helm/myapp-1.0.0.tgz", received.path());
        assertArrayEquals(CHART, received.body());
    }

    @Test
    void sendsBasicAuthenticationWhenCredentialsAreSet() throws IOException {
        push(HelmRepositoryType.CHARTMUSEUM, "/api/charts", "myapp-1.0.0.tgz", "user", "secret");

        assertEquals("Basic dXNlcjpzZWNyZXQ=", received.authorization());
    }

    @Test
    void failsWithResponseBodyWhenRepositoryRejectsTheChart() {
        responseCode = 409;

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> push(HelmRepositoryType.CHARTMUSEUM, "/api/charts", "myapp-1.0.0.tgz", null, null));
        assertTrue(e.getMessage().contains("boom"), e.getMessage());
    }

    @Test
    void failsWhenUrlIsMissing() {
        HelmRepository repository = repository(HelmRepositoryType.CHARTMUSEUM, null, null, null);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> HelmChartUploader.pushToHelmRepository(new File("chart.tgz"), repository));
        assertTrue(e.getMessage().contains("quarkus.helm.repository.url"), e.getMessage());
    }

    @Test
    void failsWhenTypeIsMissing() {
        HelmRepository repository = repository(null, "http://localhost", null, null);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> HelmChartUploader.pushToHelmRepository(new File("chart.tgz"), repository));
        assertTrue(e.getMessage().contains("quarkus.helm.repository.type"), e.getMessage());
    }

    @Test
    void failsWhenOnlyUsernameIsSet() {
        HelmRepository repository = repository(HelmRepositoryType.CHARTMUSEUM, "http://localhost", "user", null);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> HelmChartUploader.pushToHelmRepository(new File("chart.tgz"), repository));
        assertTrue(e.getMessage().contains("quarkus.helm.repository.password"), e.getMessage());
    }

    private void push(HelmRepositoryType type, String path, String tarballName, String username, String password)
            throws IOException {
        File tarball = Files.write(tempDir.resolve(tarballName), CHART).toFile();
        String url = "http://localhost:" + server.getAddress().getPort() + path;
        HelmChartUploader.pushToHelmRepository(tarball, repository(type, url, username, password));
    }

    private static HelmRepository repository(HelmRepositoryType type, String url, String username, String password) {
        return new HelmRepository() {
            @Override
            public boolean push() {
                return true;
            }

            @Override
            public Optional<String> deploymentTarget() {
                return Optional.empty();
            }

            @Override
            public Optional<HelmRepositoryType> type() {
                return Optional.ofNullable(type);
            }

            @Override
            public Optional<String> url() {
                return Optional.ofNullable(url);
            }

            @Override
            public Optional<String> username() {
                return Optional.ofNullable(username);
            }

            @Override
            public Optional<String> password() {
                return Optional.ofNullable(password);
            }
        };
    }

    private record ReceivedRequest(String method, String path, String contentType, String authorization, byte[] body) {
    }
}
