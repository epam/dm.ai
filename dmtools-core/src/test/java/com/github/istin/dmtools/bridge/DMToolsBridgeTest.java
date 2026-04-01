// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2024 EPAM Systems, Inc.

package com.github.istin.dmtools.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Calendar;

public class DMToolsBridgeTest {

    @Test
    void testPermissionSystem() {
        // Test bridge with limited permissions
        DMToolsBridge limitedBridge = DMToolsBridge.withPermissions(
            "TestClient",
            DMToolsBridge.Permission.LOGGING_INFO,
            DMToolsBridge.Permission.LOGGING_WARN
        );

        // These should work
        limitedBridge.jsLogInfo("This should work");
        limitedBridge.jsLogWarn("This should also work");

        // This should fail
        assertThrows(SecurityException.class, () -> {
            limitedBridge.jsLogError("This should fail");
        }, "Should throw SecurityException for unauthorized permission");

        // Test bridge with all permissions
        DMToolsBridge fullBridge = DMToolsBridge.withAllPermissions("AdminClient");
        
        // All of these should work
        assertDoesNotThrow(() -> {
            fullBridge.jsLogInfo("Info log");
            fullBridge.jsLogWarn("Warn log");
            fullBridge.jsLogError("Error log");
        });
    }

    @Test
    void testPermissionChecking() {
        DMToolsBridge bridge = DMToolsBridge.withPermissions(
            "TestClient",
            DMToolsBridge.Permission.LOGGING_INFO,
            DMToolsBridge.Permission.PRESENTATION_HTML_GENERATION
        );

        assertTrue(bridge.hasPermission("LOGGING_INFO"));
        assertTrue(bridge.hasPermission("PRESENTATION_HTML_GENERATION"));
        assertFalse(bridge.hasPermission("HTTP_POST_REQUESTS"));
        assertFalse(bridge.hasPermission("INVALID_PERMISSION"));
    }

    @Test
    void testBridgeInfo() {
        DMToolsBridge bridge = DMToolsBridge.withPermissions(
            "TestClient",
            DMToolsBridge.Permission.LOGGING_INFO,
            DMToolsBridge.Permission.LOGGING_WARN
        );

        String info = bridge.getBridgeInfo();
        assertTrue(info.contains("TestClient"));
        assertTrue(info.contains("2 permissions"));
    }

    @Test
    void testDateParsing() throws Exception {
        DMToolsBridge bridge = DMToolsBridge.withAllPermissions("TestClient");
        
        // Use reflection to access the private parseCalendarFromString method
        Method parseMethod = DMToolsBridge.class.getDeclaredMethod("parseCalendarFromString", String.class);
        parseMethod.setAccessible(true);
        
        // Test null/empty strings
        assertNull((Calendar) parseMethod.invoke(bridge, (String) null));
        assertNull((Calendar) parseMethod.invoke(bridge, ""));
        assertNull((Calendar) parseMethod.invoke(bridge, "  "));
        
        // Test "last_month" keyword
        Calendar lastMonth = (Calendar) parseMethod.invoke(bridge, "last_month");
        assertNotNull(lastMonth);
        Calendar expected = Calendar.getInstance();
        expected.add(Calendar.MONTH, -1);
        assertEquals(expected.get(Calendar.YEAR), lastMonth.get(Calendar.YEAR));
        assertEquals(expected.get(Calendar.MONTH), lastMonth.get(Calendar.MONTH));
        
        // Test epoch milliseconds
        Calendar epochCal = (Calendar) parseMethod.invoke(bridge, "1640995200000"); // 2022-01-01 00:00:00 UTC
        assertNotNull(epochCal);
        assertEquals(1640995200000L, epochCal.getTimeInMillis());
        
        // Test standard date format (YYYY-MM-DD) - this should be handled by DateUtils.smartParseDate
        Calendar standardDate = (Calendar) parseMethod.invoke(bridge, "2023-12-25");
        assertNotNull(standardDate);
        assertEquals(2023, standardDate.get(Calendar.YEAR));
        assertEquals(Calendar.DECEMBER, standardDate.get(Calendar.MONTH));
        assertEquals(25, standardDate.get(Calendar.DAY_OF_MONTH));
        
        // Test ISO format - should also be handled by DateUtils.smartParseDate
        Calendar isoDate = (Calendar) parseMethod.invoke(bridge, "2023-12-25T10:30:00Z");
        assertNotNull(isoDate);
        assertEquals(2023, isoDate.get(Calendar.YEAR));
        assertEquals(Calendar.DECEMBER, isoDate.get(Calendar.MONTH));
        assertEquals(25, isoDate.get(Calendar.DAY_OF_MONTH));
        
        // Test invalid date format
        assertThrows(Exception.class, () -> {
            parseMethod.invoke(bridge, "invalid-date-format");
        });
    }
} 