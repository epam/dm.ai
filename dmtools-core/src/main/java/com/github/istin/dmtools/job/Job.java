package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;

public interface Job<Params> {

    /**
     * Runs the job with the given parameters
     * @param params The parameters
     * @throws Exception If an error occurs
     */
    void runJob(Params params) throws Exception;

    /**
     * Gets the name of the job
     * @return The job name
     */
    String getName();

    /**
     * Gets the parameter class
     * @return The parameter class
     */
    Class<Params> getParamsClass();

    /**
     * Gets the AI instance used by this job
     * @return The AI instance, or null if not used
     */
    AI getAi();
}
