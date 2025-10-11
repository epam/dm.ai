package com.github.istin.dmtools.common.kb;

import com.github.istin.dmtools.common.kb.model.SourceConfig;
import com.github.istin.dmtools.common.kb.model.SourceInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SourceConfigManager
 */
public class SourceConfigManagerTest {
    
    @Test
    void testLoadConfig_FileDoesNotExist(@TempDir Path tempDir) throws Exception {
        SourceConfigManager manager = new SourceConfigManager();
        
        SourceConfig config = manager.loadConfig(tempDir);
        
        assertNotNull(config);
        assertTrue(config.getSources().isEmpty());
    }
    
    @Test
    void testSaveAndLoadConfig(@TempDir Path tempDir) throws Exception {
        SourceConfigManager manager = new SourceConfigManager();
        
        // Create config
        SourceConfig config = new SourceConfig();
        SourceInfo info = new SourceInfo();
        info.setLastSyncDate("2024-10-10T12:00:00Z");
        info.setUpdatedAt("2024-10-10T12:05:00Z");
        config.getSources().put("test_source", info);
        
        // Save
        manager.saveConfig(config, tempDir);
        
        // Verify file exists
        Path configFile = tempDir.resolve("inbox/source_config.json");
        assertTrue(Files.exists(configFile));
        
        // Load and verify
        SourceConfig loaded = manager.loadConfig(tempDir);
        assertNotNull(loaded);
        assertEquals(1, loaded.getSources().size());
        assertTrue(loaded.getSources().containsKey("test_source"));
        
        SourceInfo loadedInfo = loaded.getSources().get("test_source");
        assertEquals("2024-10-10T12:00:00Z", loadedInfo.getLastSyncDate());
        assertEquals("2024-10-10T12:05:00Z", loadedInfo.getUpdatedAt());
    }
    
    @Test
    void testUpdateLastSyncDate(@TempDir Path tempDir) throws Exception {
        SourceConfigManager manager = new SourceConfigManager();
        
        // Update for new source
        manager.updateLastSyncDate("new_source", "2024-10-10T15:00:00Z", tempDir);
        
        // Load and verify
        SourceConfig config = manager.loadConfig(tempDir);
        assertNotNull(config);
        assertTrue(config.getSources().containsKey("new_source"));
        
        SourceInfo info = config.getSources().get("new_source");
        assertEquals("2024-10-10T15:00:00Z", info.getLastSyncDate());
        assertNotNull(info.getUpdatedAt());
    }
    
    @Test
    void testUpdateExistingSource(@TempDir Path tempDir) throws Exception {
        SourceConfigManager manager = new SourceConfigManager();
        
        // First update
        manager.updateLastSyncDate("existing_source", "2024-10-10T10:00:00Z", tempDir);
        
        // Second update
        manager.updateLastSyncDate("existing_source", "2024-10-10T12:00:00Z", tempDir);
        
        // Load and verify
        SourceConfig config = manager.loadConfig(tempDir);
        SourceInfo info = config.getSources().get("existing_source");
        assertEquals("2024-10-10T12:00:00Z", info.getLastSyncDate());
    }
}


