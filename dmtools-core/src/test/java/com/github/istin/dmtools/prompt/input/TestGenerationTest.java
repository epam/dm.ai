package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.dev.UnitTestsGeneratorParams;
import com.github.istin.dmtools.prompt.input.TestGeneration;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;

public class TestGenerationTest {

    @Test
    public void testGetParams() {
        UnitTestsGeneratorParams mockParams = Mockito.mock(UnitTestsGeneratorParams.class);
        TestGeneration testGeneration = new TestGeneration("fileContent", "className", "packageName", "testTemplate", mockParams);

        UnitTestsGeneratorParams result = testGeneration.getParams();

        assertEquals(mockParams, result);
    }

    @Test
    public void testGetConverter() throws IOException {
        String fileContent = "fileContent";
        String className = "className";
        String packageName = "packageName";
        String testTemplate = "testTemplate";

        TestGeneration testGeneration = new TestGeneration(fileContent, className, packageName, testTemplate, null);
        ToText converter = testGeneration.getConverter();

        String expected = className + "\n" + fileContent + "\n" + packageName + "\n" + testTemplate;
        String actual = converter.toText();

        assertEquals(expected, actual);
    }
}