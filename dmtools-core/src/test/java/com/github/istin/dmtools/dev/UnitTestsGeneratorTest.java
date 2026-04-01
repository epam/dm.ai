// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.dev;

import com.github.istin.dmtools.ai.JAssistant;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class UnitTestsGeneratorTest {


    @Test
    public void testCreateTestFileSkeleton() throws Exception {
        UnitTestsGenerator generator = new UnitTestsGenerator();
        String fileContent = "public class Example {}";
        Path testFilePath = Paths.get("src/test/java/com/github/istin/dmtools/ExampleTest.java");
        String className = "Example";
        String packageName = "com.github.istin.dmtools";
        UnitTestsGeneratorParams params = mock(UnitTestsGeneratorParams.class);
        JAssistant jAssistant = mock(JAssistant.class);

        generator.createTestFileSkeleton(fileContent, testFilePath, className, packageName, params, jAssistant);

        assertTrue(Files.exists(testFilePath));
    }

    @Test
    public void testGenerateTestClassSkeleton() throws Exception {
        UnitTestsGenerator generator = new UnitTestsGenerator();
        String fileContent = "public class Example {}";
        String className = "Example";
        String packageName = "com.github.istin.dmtools";
        UnitTestsGeneratorParams params = mock(UnitTestsGeneratorParams.class);
        JAssistant jAssistant = mock(JAssistant.class);

        String result = generator.generateTestClassSkeleton(fileContent, className, packageName, params, jAssistant);

        assertNotNull(result);
        assertTrue(result.contains("public class ExampleTest"));
    }
}