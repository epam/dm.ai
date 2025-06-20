# Thread-Safe Configuration System for Jobs and Agents

This package provides a thread-safe configuration system for DMTools Jobs and Agents, allowing multiple instances to run in parallel with different configurations.

## Overview

The system consists of several key components:

1. **JobContext** - A thread-local context for job execution that holds job-specific configuration and attributes
2. **AgentContext** - A thread-local context for agent execution that holds agent-specific configuration and attributes
3. **AbstractJob** - Updated to use JobContext for thread-safe configuration
4. **AbstractSimpleAgent** - Updated to use AgentContext for thread-safe configuration

## Key Features

- **Thread Isolation** - Each thread has its own configuration context, preventing cross-thread contamination
- **Dynamic Configuration** - Configurations can be changed at runtime without affecting other threads
- **Backward Compatibility** - Existing job and agent implementations continue to work without changes
- **Context Attributes** - Additional attributes can be stored in the context for sharing data within a thread

## Usage Examples

### Jobs

```java
// Create a job with default configuration
Expert expert = new Expert();

// Create a job with custom configuration
InMemoryConfiguration config = new InMemoryConfiguration();
config.setProperty("OPEN_AI_API_KEY", "custom-key");
Expert expertWithCustomConfig = new Expert(config);

// Run jobs in parallel with different configurations
ExecutorService executor = Executors.newFixedThreadPool(2);
executor.submit(() -> expert.runJob(params1));
executor.submit(() -> expertWithCustomConfig.runJob(params2));
```

### Agents

```java
// Create an agent with custom configuration
InMemoryConfiguration config = new InMemoryConfiguration();
config.setProperty("OPEN_AI_MODEL", "gpt-4");

// Run the agent with the custom configuration
AgentContext.withContext(AgentContext.create(config), () -> {
    RequestDecompositionAgent agent = new RequestDecompositionAgent();
    return agent.run(params);
});
```

## Implementation Details

### JobContext

The `JobContext` class provides a thread-local storage for job execution contexts. Each context includes:

- A unique job ID
- Job-specific configuration
- Additional attributes for sharing data within the job execution

Key methods:
- `JobContext.current()` - Gets the current job context
- `JobContext.create(config)` - Creates a new job context with the given configuration
- `JobContext.withContext(context, task)` - Executes a task within a specific job context

### AgentContext

The `AgentContext` class provides similar functionality for agent execution:

- A unique agent ID
- Agent-specific configuration
- Additional attributes for sharing data within the agent execution

Key methods:
- `AgentContext.current()` - Gets the current agent context
- `AgentContext.create(config)` - Creates a new agent context with the given configuration
- `AgentContext.withContext(context, task)` - Executes a task within a specific agent context

### Thread Safety

Both `JobContext` and `AgentContext` use `ThreadLocal` storage to ensure that each thread has its own isolated context. This prevents configuration changes in one thread from affecting other threads.

## Testing

Unit tests for both `JobContext` and `AgentContext` verify thread safety by running parallel tasks with different configurations and ensuring that each task sees only its own configuration. 