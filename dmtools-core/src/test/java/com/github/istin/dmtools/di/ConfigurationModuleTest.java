package com.github.istin.dmtools.di;

import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.config.PropertyReaderConfiguration;
import com.github.istin.dmtools.common.utils.PropertyReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationModuleTest {

    @Test
    void testDefaultConstructor() {
        ConfigurationModule module = new ConfigurationModule();
        assertNotNull(module);
        
        ApplicationConfiguration config = module.provideConfiguration();
        assertNotNull(config);
        assertTrue(config instanceof PropertyReaderConfiguration);
    }

    @Test
    void testConstructorWithConfiguration() {
        ApplicationConfiguration mockConfig = mock(ApplicationConfiguration.class);
        ConfigurationModule module = new ConfigurationModule(mockConfig);
        assertNotNull(module);
        
        ApplicationConfiguration config = module.provideConfiguration();
        assertNotNull(config);
        assertSame(mockConfig, config);
    }

    @Test
    void testProvideConfiguration() {
        ApplicationConfiguration mockConfig = mock(ApplicationConfiguration.class);
        ConfigurationModule module = new ConfigurationModule(mockConfig);
        
        ApplicationConfiguration result = module.provideConfiguration();
        assertNotNull(result);
        assertSame(mockConfig, result);
    }

    @Test
    void testProvidePropertyReader() {
        ConfigurationModule module = new ConfigurationModule();
        
        PropertyReader reader = module.providePropertyReader();
        assertNotNull(reader);
    }

    @Test
    void testSingletonBehavior_Configuration() {
        ApplicationConfiguration mockConfig = mock(ApplicationConfiguration.class);
        ConfigurationModule module = new ConfigurationModule(mockConfig);
        
        ApplicationConfiguration result1 = module.provideConfiguration();
        ApplicationConfiguration result2 = module.provideConfiguration();
        
        assertSame(result1, result2, "Should return same instance for singleton");
    }

    @Test
    void testProvidePropertyReader_CreatesNewInstance() {
        ConfigurationModule module = new ConfigurationModule();
        
        PropertyReader reader1 = module.providePropertyReader();
        PropertyReader reader2 = module.providePropertyReader();
        
        assertNotNull(reader1);
        assertNotNull(reader2);
        // Both should be PropertyReader instances
    }
}
