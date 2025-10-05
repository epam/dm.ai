package com.github.istin.dmtools.bridge;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DMToolsBridgeAPIProviderTest {

    @Test
    void testGenerateAPIDescription_EmptyPermissions() {
        Set<DMToolsBridge.Permission> permissions = new HashSet<>();
        String result = DMToolsBridgeAPIProvider.generateAPIDescription(permissions);
        
        assertNotNull(result);
        assertTrue(result.contains("DMToolsBridge API Reference"));
        assertTrue(result.contains("Available Permissions"));
    }

    @Test
    void testGenerateAPIDescription_WithPermissions() {
        Set<DMToolsBridge.Permission> permissions = new HashSet<>();
        permissions.add(DMToolsBridge.Permission.LOGGING_INFO);
        permissions.add(DMToolsBridge.Permission.LOGGING_ERROR);
        
        String result = DMToolsBridgeAPIProvider.generateAPIDescription(permissions);
        
        assertNotNull(result);
        assertTrue(result.contains("LOGGING_INFO"));
        assertTrue(result.contains("LOGGING_ERROR"));
        assertTrue(result.contains("DMToolsBridge API Reference"));
    }

    @Test
    void testGenerateAPIDescription_ContainsMethodSections() {
        Set<DMToolsBridge.Permission> permissions = Set.of(
            DMToolsBridge.Permission.LOGGING_INFO,
            DMToolsBridge.Permission.HTTP_GET_REQUESTS
        );
        
        String result = DMToolsBridgeAPIProvider.generateAPIDescription(permissions);
        
        assertNotNull(result);
        assertTrue(result.contains("Available Methods"));
        assertTrue(result.contains("Usage Notes"));
    }

    @Test
    void testGenerateSimpleAPIDescription_EmptyPermissions() {
        Set<DMToolsBridge.Permission> permissions = new HashSet<>();
        String result = DMToolsBridgeAPIProvider.generateSimpleAPIDescription(permissions);
        
        assertNotNull(result);
        assertTrue(result.contains("bridge.") || result.isEmpty());
    }

    @Test
    void testGenerateSimpleAPIDescription_WithPermissions() {
        Set<DMToolsBridge.Permission> permissions = Set.of(
            DMToolsBridge.Permission.LOGGING_INFO,
            DMToolsBridge.Permission.HTTP_POST_REQUESTS
        );
        
        String result = DMToolsBridgeAPIProvider.generateSimpleAPIDescription(permissions);
        
        assertNotNull(result);
        // Should contain method signatures with bridge prefix
    }

    @Test
    void testGenerateAPIDescription_AllPermissions() {
        Set<DMToolsBridge.Permission> allPermissions = new HashSet<>();
        for (DMToolsBridge.Permission perm : DMToolsBridge.Permission.values()) {
            allPermissions.add(perm);
        }
        
        String result = DMToolsBridgeAPIProvider.generateAPIDescription(allPermissions);
        
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.length() > 100); // Should have substantial content
    }

    @Test
    void testGenerateSimpleAPIDescription_AllPermissions() {
        Set<DMToolsBridge.Permission> allPermissions = new HashSet<>();
        for (DMToolsBridge.Permission perm : DMToolsBridge.Permission.values()) {
            allPermissions.add(perm);
        }
        
        String result = DMToolsBridgeAPIProvider.generateSimpleAPIDescription(allPermissions);
        
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }
}
