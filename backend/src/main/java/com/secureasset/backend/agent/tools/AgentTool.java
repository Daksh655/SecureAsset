package com.secureasset.backend.agent.tools;

public interface AgentTool<I, O> {
    
    /**
     * @return A stable, unique name for the tool.
     */
    String getName();
    
    /**
     * @return A short description of what the tool does and when it should be used.
     */
    String getDescription();
    
    /**
     * @return The class representing the input schema for the tool.
     */
    Class<I> getInputSchema();
    
    /**
     * Executes the tool with the given input.
     *
     * @param input the input payload
     * @return the result of the tool execution
     */
    O execute(I input);
}
