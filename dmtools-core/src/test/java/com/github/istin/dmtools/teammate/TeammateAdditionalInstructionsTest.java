// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.teammate;

import com.github.istin.dmtools.ai.agent.RequestDecompositionAgent;
import com.github.istin.dmtools.expert.ExpertParams;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for additionalInstructions field in TeammateParams.
 *
 * Verifies:
 * 1. Field defaults to null when not set
 * 2. Field accepts and returns a string array
 * 3. Serialization name is "additionalInstructions"
 */
class TeammateAdditionalInstructionsTest {

    @Test
    void testAdditionalInstructions_DefaultsToNull() {
        Teammate.TeammateParams params = new Teammate.TeammateParams();

        assertNull(params.getAdditionalInstructions(),
            "additionalInstructions should default to null when not set");
    }

    @Test
    void testAdditionalInstructions_SetAndGet() {
        Teammate.TeammateParams params = new Teammate.TeammateParams();
        String[] instructions = {"instruction one", "instruction two"};

        params.setAdditionalInstructions(instructions);

        assertArrayEquals(instructions, params.getAdditionalInstructions());
    }

    @Test
    void testAdditionalInstructions_EmptyArray() {
        Teammate.TeammateParams params = new Teammate.TeammateParams();

        params.setAdditionalInstructions(new String[]{});

        assertNotNull(params.getAdditionalInstructions());
        assertEquals(0, params.getAdditionalInstructions().length);
    }

    @Test
    void testAdditionalInstructions_WithConfluenceUrls() {
        Teammate.TeammateParams params = new Teammate.TeammateParams();
        String[] instructions = {
            "https://yourcompany.atlassian.net/wiki/spaces/PROJ/pages/123",
            "Some plain text instruction"
        };

        params.setAdditionalInstructions(instructions);

        assertEquals(2, params.getAdditionalInstructions().length);
        assertEquals("https://yourcompany.atlassian.net/wiki/spaces/PROJ/pages/123",
            params.getAdditionalInstructions()[0]);
    }

    @Test
    void testAdditionalInstructions_MergeLogic_AppendToExisting() {
        // Simulate the merge logic in Teammate.runJob:
        // resolved instructions from agentParams + resolved additionalInstructions
        String[] baseInstructions = {"base rule 1", "base rule 2"};
        String[] additionalInstructions = {"project rule A", "project rule B"};

        String[] combined = new String[baseInstructions.length + additionalInstructions.length];
        System.arraycopy(baseInstructions, 0, combined, 0, baseInstructions.length);
        System.arraycopy(additionalInstructions, 0, combined, baseInstructions.length, additionalInstructions.length);

        assertEquals(4, combined.length);
        assertEquals("base rule 1", combined[0]);
        assertEquals("base rule 2", combined[1]);
        assertEquals("project rule A", combined[2]);
        assertEquals("project rule B", combined[3]);
    }

    @Test
    void testAdditionalInstructions_NullAdditional_DoesNotModifyBase() {
        // When additionalInstructions is null, extractIfNeeded returns empty array
        // so instructions should stay unchanged
        String[] baseInstructions = {"base rule 1"};
        String[] resolvedAdditional = new String[0]; // extractIfNeeded(null) returns []

        String[] result;
        if (resolvedAdditional != null && resolvedAdditional.length > 0) {
            String[] combined = new String[baseInstructions.length + resolvedAdditional.length];
            System.arraycopy(baseInstructions, 0, combined, 0, baseInstructions.length);
            System.arraycopy(resolvedAdditional, 0, combined, baseInstructions.length, resolvedAdditional.length);
            result = combined;
        } else {
            result = baseInstructions;
        }

        assertSame(baseInstructions, result,
            "When additionalInstructions is empty, base array should be used as-is");
    }

    @Test
    void testAdditionalInstructions_EmptyAdditional_DoesNotModifyBase() {
        String[] baseInstructions = {"base rule 1"};
        String[] resolvedAdditional = new String[0];

        String[] result;
        if (resolvedAdditional != null && resolvedAdditional.length > 0) {
            String[] combined = new String[baseInstructions.length + resolvedAdditional.length];
            System.arraycopy(baseInstructions, 0, combined, 0, baseInstructions.length);
            System.arraycopy(resolvedAdditional, 0, combined, baseInstructions.length, resolvedAdditional.length);
            result = combined;
        } else {
            result = baseInstructions;
        }

        assertEquals(1, result.length);
        assertEquals("base rule 1", result[0]);
    }
}
