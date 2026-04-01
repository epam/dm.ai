// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.file;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SourceCodeReader {

    private final List<String> fileExtensions;
    private final Path rootFolder;
    private static final Logger logger = Logger.getLogger(SourceCodeReader.class.getName());

    // Default root folder
    private static final Path DEFAULT_ROOT_FOLDER = Paths.get("src");

    public SourceCodeReader(List<String> extensions) {
        this(extensions, DEFAULT_ROOT_FOLDER);
    }

    public SourceCodeReader(List<String> extensions, Path rootFolder) {
        this.fileExtensions = new ArrayList<>(extensions);
        this.rootFolder = rootFolder != null ? rootFolder : DEFAULT_ROOT_FOLDER;
    }

    public void readSourceFiles(FileContentListener listener) {
        readSourceFiles(rootFolder, listener);
    }

    public void readSourceFiles(Path directory, FileContentListener listener) {
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    for (String extension : fileExtensions) {
                        if (fileName.endsWith(extension)) {
                            try {
                                String content = new String(Files.readAllBytes(file));
                                String folderPath = rootFolder.relativize(file.getParent()).toString();
                                String packageName = extractPackageName(content);

                                listener.onFileRead(folderPath, packageName, fileName, content);
                            } catch (IOException e) {
                                logger.warning("Failed to read file: " + file + " due to: " + e.getMessage());
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            logger.severe("Failed to read source files: " + e.getMessage());
        }
    }

    private String extractPackageName(String content) {
        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.startsWith("package ") && line.endsWith(";")) {
                return line.substring(8, line.length() - 1).trim(); // Extract package name and remove semicolon
            }
        }
        return ""; // Default to an empty string if no package is declared
    }
}