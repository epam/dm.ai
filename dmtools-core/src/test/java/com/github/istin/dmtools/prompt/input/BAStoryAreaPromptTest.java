package com.github.istin.dmtools.prompt.input;

import com.github.istin.dmtools.common.model.ToText;
import com.github.istin.dmtools.prompt.input.BAStoryAreaPrompt;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

public class BAStoryAreaPromptTest {

    @Test
    public void testGetAreas() {
        String basePath = "base/path";
        ToText toText = mock(ToText.class);
        String expectedAreas = "some areas";

        BAStoryAreaPrompt prompt = new BAStoryAreaPrompt(basePath, toText, expectedAreas);
        String actualAreas = prompt.getAreas();

        assertEquals(expectedAreas, actualAreas);
    }
}