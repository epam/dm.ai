# Thread-Safe Agent Configuration System

This package provides a thread-safe configuration system for DMTools Agents, allowing multiple agent instances to run in parallel with different configurations.

## Overview

The system consists of several key components:

1. **AgentContext** - A thread-local context for agent execution that holds agent-specific configuration and attributes
2. **AbstractSimpleAgent** - Updated to use AgentContext for thread-safe configuration
3. **IAgent** - Interface defining the contract for all agents

## Key Features

- **Thread Isolation** - Each thread has its own agent configuration context, preventing cross-thread contamination
- **Dynamic Configuration** - Agent configurations can be changed at runtime without affecting other threads
- **Context Attributes** - Additional attributes can be stored in the context for sharing data within an agent execution

## Usage Examples

### Basic Usage

```java
// Create an agent with default configuration
RequestDecompositionAgent agent = new RequestDecompositionAgent();
agent.run(params);
```

### Custom Configuration

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

### Parallel Execution

```java
// Create different configurations
InMemoryConfiguration config1 = new InMemoryConfiguration();
config1.setProperty("OPEN_AI_MODEL", "gpt-3.5-turbo");

InMemoryConfiguration config2 = new InMemoryConfiguration();
config2.setProperty("OPEN_AI_MODEL", "gpt-4");

// Run agents in parallel with different configurations
ExecutorService executor = Executors.newFixedThreadPool(2);

executor.submit(() -> {
    AgentContext.withContext(AgentContext.create(config1), () -> {
        RequestDecompositionAgent agent = new RequestDecompositionAgent();
        return agent.run(params1);
    });
});

executor.submit(() -> {
    AgentContext.withContext(AgentContext.create(config2), () -> {
        RequestDecompositionAgent agent = new RequestDecompositionAgent();
        return agent.run(params2);
    });
});
```

## Implementation Details

### AgentContext

The `AgentContext` class provides a thread-local storage for agent execution contexts. Each context includes:

- A unique agent ID
- Agent-specific configuration
- Additional attributes for sharing data within the agent execution

Key methods:
- `AgentContext.current()` - Gets the current agent context
- `AgentContext.create(config)` - Creates a new agent context with the given configuration
- `AgentContext.withContext(context, task)` - Executes a task within a specific agent context

### AbstractSimpleAgent

The `AbstractSimpleAgent` class has been updated to:

1. Use `AgentContext` to access configuration
2. Execute agent logic within an agent context
3. Maintain backward compatibility with existing agent implementations

### Thread Safety

`AgentContext` uses `ThreadLocal` storage to ensure that each thread has its own isolated context. This prevents configuration changes in one thread from affecting other threads.

## Testing

Unit tests for `AgentContext` verify thread safety by running parallel tasks with different configurations and ensuring that each task sees only its own configuration. 