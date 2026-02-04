package dev.logicojp.reviewer.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads agent configurations from external files.
 * Supports GitHub Copilot agent definition format (.agent.md).
 * 
 * Files can be placed in:
 * - agents/ directory
 * - .github/agents/ directory
 */
public class AgentConfigLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentConfigLoader.class);
    
    private final List<Path> agentDirectories;
    private final AgentMarkdownParser markdownParser;
    
    /**
     * Creates a loader with a single agents directory.
     */
    public AgentConfigLoader(Path agentsDirectory) {
        this(List.of(agentsDirectory));
    }
    
    /**
     * Creates a loader with multiple agent directories.
     * Directories are searched in order; later directories override earlier ones.
     */
    public AgentConfigLoader(List<Path> agentDirectories) {
        this.agentDirectories = new ArrayList<>(agentDirectories);
        this.markdownParser = new AgentMarkdownParser();
    }
    
    /**
     * Loads all agent configurations from all configured directories.
     * @return Map of agent name to AgentConfig
     */
    public Map<String, AgentConfig> loadAllAgents() throws IOException {
        Map<String, AgentConfig> agents = new HashMap<>();
        
        for (Path directory : agentDirectories) {
            if (!Files.exists(directory)) {
                logger.debug("Agents directory does not exist: {}", directory);
                continue;
            }
            
            logger.info("Loading agents from: {}", directory);
            loadAgentsFromDirectory(directory, agents);
        }
        
        if (agents.isEmpty()) {
            logger.warn("No agents found in any configured directory");
        }
        
        return agents;
    }
    
    private void loadAgentsFromDirectory(Path directory, Map<String, AgentConfig> agents) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            List<Path> files = paths
                .filter(this::isAgentFile)
                .collect(Collectors.toList());
            
            for (Path file : files) {
                try {
                    AgentConfig config = markdownParser.parse(file);
                    if (config != null) {
                        config.validateRequired();
                        agents.put(config.getName(), config);
                        logger.info("Loaded agent: {} from {}", config.getName(), file.getFileName());
                    }
                } catch (Exception e) {
                    logger.error("Failed to load agent from {}: {}", file, e.getMessage());
                }
            }
        }
    }
    
    private boolean isAgentFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        return filename.endsWith(".agent.md");
    }
    
    /**
     * Loads specific agents by name.
     * @param agentNames List of agent names to load
     * @return Map of agent name to AgentConfig
     */
    public Map<String, AgentConfig> loadAgents(List<String> agentNames) throws IOException {
        Map<String, AgentConfig> allAgents = loadAllAgents();
        Map<String, AgentConfig> selectedAgents = new HashMap<>();
        
        for (String name : agentNames) {
            if (allAgents.containsKey(name)) {
                selectedAgents.put(name, allAgents.get(name));
            } else {
                logger.warn("Agent not found: {}", name);
            }
        }
        
        return selectedAgents;
    }
    
    /**
     * Lists all available agent names from all configured directories.
     */
    public List<String> listAvailableAgents() throws IOException {
        Set<String> agentNames = new TreeSet<>();
        
        for (Path directory : agentDirectories) {
            if (!Files.exists(directory)) {
                continue;
            }
            
            try (Stream<Path> paths = Files.list(directory)) {
                paths.filter(this::isAgentFile)
                    .map(this::extractAgentName)
                    .forEach(agentNames::add);
            }
        }
        
        return new ArrayList<>(agentNames);
    }
    
    private String extractAgentName(Path file) {
        String filename = file.getFileName().toString();
        
        if (filename.endsWith(".agent.md")) {
            return filename.substring(0, filename.length() - ".agent.md".length());
        }
        
        return filename;
    }
    
    /**
     * Gets the list of configured agent directories.
     */
    public List<Path> getAgentDirectories() {
        return Collections.unmodifiableList(agentDirectories);
    }
}
