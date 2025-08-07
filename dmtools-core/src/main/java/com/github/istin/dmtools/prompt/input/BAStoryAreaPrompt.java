package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.common.model.ToText;

public class BAStoryAreaPrompt extends TextInputPrompt {

    private String areas;

    public BAStoryAreaPrompt(String basePath, ToText toText, String areas) {
        super(basePath, toText);
        this.areas = areas;
    }

    public String getAreas() {
        return areas;
    }
}
