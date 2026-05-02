// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dev;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;

public class LegacyCodeGeneratorSourceCleanupTest {

    @Test
    public void testLegacyCodeGeneratorSourceFilesAreRemoved() {
        Path repositoryRoot = findRepositoryRoot(Path.of("").toAbsolutePath().normalize());

        assertFalse(Files.exists(repositoryRoot.resolve(
                "dmtools-core/src/main/java/com/github/istin/dmtools/dev/CodeGenerator.java")));
        assertFalse(Files.exists(repositoryRoot.resolve(
                "dmtools-core/src/main/java/com/github/istin/dmtools/dev/CodeGeneratorParams.java")));
    }

    private Path findRepositoryRoot(Path start) {
        Path current = start;
        while (current != null && !Files.exists(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("Unable to locate repository root from " + start);
        }
        return current;
    }
}
