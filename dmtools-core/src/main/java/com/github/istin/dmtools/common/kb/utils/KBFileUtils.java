package com.github.istin.dmtools.common.kb.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * General purpose file counting helpers used by KB tooling.
 */
public class KBFileUtils {

    /**
     * Count regular files in a directory that match the provided filter.
     *
     * @param dir directory to scan
     * @param filter optional filter to apply (may be null)
     * @return number of matching files
     * @throws IOException if directory cannot be read
     */
    public int countFiles(Path dir, java.util.function.Predicate<Path> filter) throws IOException {
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            Stream<Path> filtered = files.filter(Files::isRegularFile);
            if (filter != null) {
                filtered = filtered.filter(filter);
            }
            return (int) filtered.count();
        }
    }

    /**
     * Count subdirectories in a directory.
     */
    public int countDirectories(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return (int) files.filter(Files::isDirectory).count();
        }
    }
}
