package com.github.istin.dmtools.di;

import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import com.github.istin.dmtools.common.config.PropertyReaderConfiguration;
import com.github.istin.dmtools.common.utils.PropertyReader;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/**
 * Dagger module for providing Configuration instances.
 */
@Module
public class ConfigurationModule {
    
    private final ApplicationConfiguration configuration;
    
    /**
     * Creates a new ConfigurationModule with the default PropertyReaderConfiguration
     */
    public ConfigurationModule() {
        this.configuration = new PropertyReaderConfiguration();
    }
    
    /**
     * Creates a new ConfigurationModule with the provided configuration
     * @param configuration The configuration to provide
     */
    public ConfigurationModule(ApplicationConfiguration configuration) {
        this.configuration = configuration;
    }
    
    /**
     * Provides the ApplicationConfiguration instance
     * @return The ApplicationConfiguration instance
     */
    @Provides
    @Singleton
    public ApplicationConfiguration provideConfiguration() {
        return configuration;
    }
    
    /**
     * Provides a PropertyReader instance for backward compatibility
     * @return The PropertyReader instance
     */
    @Provides
    @Singleton
    public PropertyReader providePropertyReader() {
        return new PropertyReader();
    }
} 