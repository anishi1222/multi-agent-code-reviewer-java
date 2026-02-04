package dev.logicojp.reviewer.service;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.AgentConfigLoader;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for loading and managing agent configurations.
 */
@Singleton
public class AgentService {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);
    
    /**
     * Builds the list of agent directories to search.
     * @param additionalDirs Additional directories specified via CLI
     * @return List of directories to search for agents
     */
    public List<Path> buildAgentDirectories(List<Path> additionalDirs) {
        List<Path> dirs = new ArrayList<>();
        
        // Default directories
        Path defaultAgentsDir = Paths.get("./agents");
        Path githubAgentsDir = Paths.get("./.github/agents");
        
        if (Files.exists(defaultAgentsDir)) {
            dirs.add(defaultAgentsDir);
        }
        if (Files.exists(githubAgentsDir)) {
            dirs.add(githubAgentsDir);
        }
        
        // Additional directories specified via CLI
        if (additionalDirs != null) {
            dirs.addAll(additionalDirs);
        }
        
        // If no directories found, add default for error message
        if (dirs.isEmpty()) {
            dirs.add(defaultAgentsDir);
        }
        
        return dirs;
    }
    
    /**
     * Loads all agents from the specified directories.
     */
    public Map<String, AgentConfig> loadAllAgents(List<Path> agentDirs) throws IOException {
        AgentConfigLoader loader = new AgentConfigLoader(agentDirs);
        return loader.loadAllAgents();
    }
    
    /**
     * Loads specific agents by name from the specified directories.
     */
    public Map<String, AgentConfig> loadAgents(List<Path> agentDirs, List<String> agentNames) throws IOException {
        AgentConfigLoader loader = new AgentConfigLoader(agentDirs);
        return loader.loadAgents(agentNames);
    }
    
    /**
     * Lists all available agent names from the specified directories.
     */
    public List<String> listAvailableAgents(List<Path> agentDirs) throws IOException {
        AgentConfigLoader loader = new AgentConfigLoader(agentDirs);
        return loader.listAvailableAgents();
    }
}
