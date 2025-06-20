package com.github.istin.dmtools.ai.agent;

import com.github.istin.dmtools.ai.ChunkPreparation;
import com.github.istin.dmtools.common.config.ApplicationConfiguration;
import java.io.IOException;

public interface IAgent<Params, Result> {

    /**
     * Executes the agent with the given parameters
     * @param params The parameters
     * @return The result
     * @throws Exception If an error occurs
     */
    Result run(Params params) throws Exception;

    /**
     * Transforms the AI response into the result type
     * @param params The parameters
     * @param response The AI response
     * @return The transformed result
     * @throws Exception If an error occurs during transformation
     */
    Result transformAIResponse(Params params, String response) throws Exception;

    /**
     * Gets the parameter class
     * @return The parameter class
     */
    Class<Params> getParamsClass();
}
