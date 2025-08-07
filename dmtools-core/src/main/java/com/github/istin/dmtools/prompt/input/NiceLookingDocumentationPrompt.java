package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.common.utils.HtmlCleaner;

public class NiceLookingDocumentationPrompt extends TextInputPrompt {

    private String existingContent = "";

    public String getExistingContent() {
        return existingContent;
    }

    public NiceLookingDocumentationPrompt(String basePath, ToText textInput, String existingContent) {
        super(basePath, textInput);
        this.existingContent = HtmlCleaner.cleanAllHtmlTags(basePath, existingContent);
    }
}
