package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.dev.UnitTestsGeneratorParams;

import java.io.IOException;

public class TestGeneration {


    private final String fileContent;
    private final String className;
    private final String packageName;
    private final String testTemplate;
    private final UnitTestsGeneratorParams params;

    public TestGeneration(String fileContent, String className, String packageName, String testTemplate, UnitTestsGeneratorParams params) {
        this.fileContent = fileContent;
        this.className = className;
        this.packageName = packageName;
        this.testTemplate = testTemplate;
        this.params = params;
    }

    public UnitTestsGeneratorParams getParams() {
        return params;
    }

    public ToText getConverter() {
        return new ToText() {
            @Override
            public String toText() throws IOException {
                return className + "\n" + fileContent + "\n" + packageName + "\n" + testTemplate;
            }
        };
    }

}
