package com.github.istin.dmtools.common.config;

/**
 * Configuration interface for metrics settings.
 */
public interface MetricsConfiguration {
    /**
     * Gets the default ticket weight if no story points are specified
     * @return The default ticket weight
     */
    Integer getDefaultTicketWeightIfNoSPs();

    /**
     * Gets the lines of code divider
     * @return The lines of code divider
     */
    Double getLinesOfCodeDivider();

    /**
     * Gets the time spent divider
     * @return The time spent divider
     */
    Double getTimeSpentOnDivider();

    /**
     * Gets the ticket fields changed divider for a specific field
     * @param fieldName The name of the field
     * @return The ticket fields changed divider
     */
    Double getTicketFieldsChangedDivider(String fieldName);
} 