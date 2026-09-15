// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.microsoft.sharepoint;

import com.github.istin.dmtools.common.utils.PropertyReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BasicSharePointClientTest {

    @AfterEach
    void tearDown() {
        PropertyReader.clearOverrides();
        BasicSharePointClient.reset();
    }

    @Test
    void validateClientIdRejectsNull() {
        assertThrows(IllegalStateException.class, () -> BasicSharePointClient.validateClientId(null));
    }

    @Test
    void validateClientIdRejectsEmptyAndBlank() {
        assertThrows(IllegalStateException.class, () -> BasicSharePointClient.validateClientId(""));
        assertThrows(IllegalStateException.class, () -> BasicSharePointClient.validateClientId("   "));
    }

    @Test
    void validateClientIdAcceptsValue() {
        assertDoesNotThrow(() -> BasicSharePointClient.validateClientId("some-client-id"));
    }

    /**
     * Regression guard: without TEAMS_CLIENT_ID configured, getInstance() must fail
     * fast with a clear error instead of letting the SharePointClient constructor
     * start an OAuth flow with a null client id (which opens a browser in
     * browser auth mode). The thread-local override (highest-priority source,
     * returned verbatim even when empty) makes this independent of the real
     * machine environment.
     */
    @Test
    void getInstanceFailsFastWhenClientIdMissing() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put(PropertyReader.TEAMS_CLIENT_ID, "");
        PropertyReader.setOverrides(overrides);
        BasicSharePointClient.reset();

        assertThrows(IllegalStateException.class, BasicSharePointClient::getInstance);
    }
}
