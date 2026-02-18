package io.quarkiverse.helm;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class HelmUtil {

    private HelmUtil() {
        //Utility Class
    }

    /**
     * List all chart directories found under the specified {@link Path} for the
     * specified platforms.
     *
     * @param path The specified path
     * @param platforms The specified platofrms
     * @return a {@link List} of {@link Path} items corresponding ot the chart
     *         directories found.
     **/
    public static List<Path> listGeneratedCharts(Path path, List<Platform> platforms) throws IOException {
        List<Path> result = new ArrayList<>();
        for (Platform platform : platforms) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path file, BasicFileAttributes attrs) throws IOException {
                    Path target = file.resolve("target"); // Maven output folder
                    Path targetHelm = target.resolve("helm");
                    Path targetHelmPlatform = targetHelm.resolve(platform.name().toLowerCase());

                    if (targetHelmPlatform.toFile().exists()) {
                        result.addAll(listDirectories(targetHelmPlatform));
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    Path build = file.resolve("build"); // Gradle output folder
                    Path buildHelm = build.resolve("helm");
                    Path buildHelmPlatform = buildHelm.resolve(platform.name().toLowerCase());

                    if (buildHelmPlatform.toFile().exists()) {
                        result.addAll(listDirectories(buildHelmPlatform));
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return result;
    }

    /**
     * List all directories found under the specified {@link Path}
     *
     * @param path The specified path
     * @return a {@link List} of {@link Path} items corresponding ot the directories
     *         found.
     **/
    private static List<Path> listDirectories(Path path) throws IOException {
        if (!path.toFile().isDirectory()) {
            throw new IllegalArgumentException(path.toAbsolutePath().toString() + " is not a directoy!");
        }
        List<Path> result = new ArrayList<>();
        Iterator<Path> iterator = Files.newDirectoryStream(path).iterator();
        while (iterator.hasNext()) {
            Path candidate = iterator.next();
            if (candidate.toFile().isDirectory()) {
                result.add(candidate);
            }
        }
        return result;
    }

    public static String chartOf(Path path) {
        return path.getFileName().toString();
    }

    public static String chartPathOf(Path path) {
        return path.toAbsolutePath().toString();
    }

    public static Platform platformOf(Path path) {
        String absolutePath = path.toAbsolutePath().toString();
        for (Platform p : Platform.values()) {
            if (absolutePath.contains("helm" + File.separatorChar + p.name())) {
                return p;
            }
        }
        throw new IllegalArgumentException(
                "Path: " + absolutePath + " does not contain helm charts for any of the known platforms");
    }
}
