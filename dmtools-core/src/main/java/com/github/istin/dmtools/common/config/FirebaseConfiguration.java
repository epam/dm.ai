package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for Firebase settings.
 */
public interface FirebaseConfiguration {
    /**
     * Gets the Firebase project ID
     * @return The Firebase project ID
     */
    String getFirebaseProjectId();

    /**
     * Gets the Firebase service account JSON authentication
     * @return The Firebase service account JSON authentication
     */
    String getFirebaseServiceAccountJsonAuth();
} 