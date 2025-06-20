package com.github.istin.dmtools.job;

import com.github.istin.dmtools.ai.AI;
import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class AbstractJob<Params> implements Job<Params>{

    @Getter
    @Setter
    protected AI ai;

    /**
     * Default constructor
     */
    public AbstractJob() {
    }
    
    /**
     * Constructor with AI
     * @param ai The AI instance to use
     */
    public AbstractJob(AI ai) {
        this.ai = ai;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public Class<Params> getParamsClass() {
        return (Class<Params>) getTemplateParameterClass(getClass());
    }

    private Class<?> getTemplateParameterClass(Class<?> clazz) {
        Type genericSuperclass = clazz.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericSuperclass;
            Type[] typeArguments = parameterizedType.getActualTypeArguments();
            if (typeArguments.length > 0) {
                return (Class<?>) typeArguments[0];
            }
        }
        throw new IllegalArgumentException("Class does not have a template parameter.");
    }
    
    @Override
    public void runJob(Params params) throws Exception {
        executeJob(params);
    }
    
    /**
     * Executes the job with the given parameters.
     * @param params The job parameters
     * @throws Exception If an error occurs
     */
    protected void executeJob(Params params) throws Exception {
        // Default implementation calls the old runJob method for backwards compatibility
        // This allows existing job implementations to continue working without changes
        runJobImpl(params);
    }
    
    /**
     * Legacy implementation of runJob.
     * This method is called by executeJob for backwards compatibility.
     * @param params The job parameters
     * @throws Exception If an error occurs
     */
    protected void runJobImpl(Params params) throws Exception {
        // This method should be overridden by subclasses that don't implement executeJob
        throw new UnsupportedOperationException("Either executeJob or runJobImpl must be implemented");
    }
}
