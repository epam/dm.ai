package com.github.istin.dmtools.common.utils;

import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Generic cache manager that provides in-memory TTL caching and simple in-memory caching capabilities.
 * 
 * This class can be used by any component that needs memory caching functionality for keys, 
 * field mappings, and other business logic data.
 */
public class CacheManager {
    
    private Logger logger;
    
    // In-memory cache for objects with TTL
    private final Map<String, CacheEntry<Object>> memoryCache = new ConcurrentHashMap<>();
    
    // Simple in-memory cache for objects without TTL
    private final Map<String, Object> simpleCache = new ConcurrentHashMap<>();
    
    // Default TTL: 24 hours in milliseconds
    private static final long DEFAULT_TTL = 24 * 60 * 60 * 1000L;

    /**
     * Constructor for CacheManager
     * 
     * @param logger Logger instance for debug output
     */
    public CacheManager(Logger logger) {
        if (new PropertyReader().isCacheManagerLoggingEnabled()) {
            this.logger = logger;
        }
    }
    

    
    /**
     * Get or compute value with TTL-based memory caching
     * 
     * @param key Cache key
     * @param supplier Function to execute if cache miss or expired
     * @param ttlMillis Time-to-live in milliseconds (0 = no expiration)
     * @return Cached or freshly computed result
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrComputeWithTTL(String key, Supplier<T> supplier, long ttlMillis) {
        long currentTime = System.currentTimeMillis();
        
        // Check if cache entry exists and is not expired
        CacheEntry<Object> entry = memoryCache.get(key);
        if (entry != null) {
            if (ttlMillis <= 0 || (currentTime - entry.timestamp) <= ttlMillis) {
                if (logger != null) {
                    logger.debug("TTL cache hit for key: " + key);
                }
                return (T) entry.value;
            } else {
                // Entry expired, remove it
                memoryCache.remove(key);
                if (logger != null) {
                    logger.debug("TTL cache expired for key: " + key);
                }
            }
        }
        
        // Cache miss or expired, compute new value
        T result = supplier.get();
        if (result != null) {
            memoryCache.put(key, new CacheEntry<>(result, currentTime));
            if (logger != null) {
                logger.debug("Stored in TTL cache for key: " + key);
            }
        }
        
        return result;
    }
    
    /**
     * Get or compute value with default TTL (24 hours)
     * 
     * @param key Cache key
     * @param supplier Function to execute if cache miss or expired
     * @return Cached or freshly computed result
     */
    public <T> T getOrComputeWithDefaultTTL(String key, Supplier<T> supplier) {
        return getOrComputeWithTTL(key, supplier, DEFAULT_TTL);
    }
    
    /**
     * Simple memory cache operations without TTL
     */
    @SuppressWarnings("unchecked")
    public <T> T getFromSimpleCache(String key) {
        Object value = simpleCache.get(key);
        if (value != null && logger != null) {
            logger.debug("Simple cache hit for key: " + key);
        }
        return (T) value;
    }
    
    public <T> void putInSimpleCache(String key, T value) {
        simpleCache.put(key, value);
        if (logger != null) {
            logger.debug("Stored in simple cache for key: " + key);
        }
    }
    
    public <T> T getOrComputeSimple(String key, Supplier<T> supplier) {
        T cachedValue = getFromSimpleCache(key);
        if (cachedValue != null) {
            return cachedValue;
        }
        
        T result = supplier.get();
        if (result != null) {
            putInSimpleCache(key, result);
        }
        return result;
    }
    
    /**
     * Cache management operations
     */
    public void clearAllMemoryCache() {
        memoryCache.clear();
        simpleCache.clear();
        if (logger != null) {
            logger.debug("Cleared all memory caches");
        }
    }
    
    public void clearTTLCache() {
        memoryCache.clear();
        if (logger != null) {
            logger.debug("Cleared TTL cache");
        }
    }
    
    public void clearSimpleCache() {
        simpleCache.clear();
        if (logger != null) {
            logger.debug("Cleared simple cache");
        }
    }
    
    public void removeFromCache(String key) {
        memoryCache.remove(key);
        simpleCache.remove(key);
        if (logger != null) {
            logger.debug("Removed from caches for key: " + key);
        }
    }
    
    /**
     * Cache statistics and information
     */
    public int getTTLCacheSize() {
        return memoryCache.size();
    }
    
    public int getSimpleCacheSize() {
        return simpleCache.size();
    }
    
    /**
     * Internal cache entry class for TTL cache
     */
    private static class CacheEntry<T> {
        final T value;
        final long timestamp;
        
        CacheEntry(T value, long timestamp) {
            this.value = value;
            this.timestamp = timestamp;
        }
    }
} 