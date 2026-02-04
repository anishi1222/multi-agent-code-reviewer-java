package dev.logicojp.reviewer.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads agent configurations from external files.
 * Supports two formats:
 * 1. YAML files (.yaml, .yml) - Traditional format
 * 2. Markdown files (.agent.md) - GitHub Copilot agent definition format
 * 
 * Files can be placed in:
 * - agents/ directory (YAML or Markdown)
 * - .github/agents/ directory (GitHub Copilot format)
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
     * Supports both YAML (.yaml, .yml) and Markdown (.agent.md) formats.
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
                    AgentConfig config = loadAgentFile(file);
                    if (config != null) {
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
        return filename.endsWith(".yaml") || 
               filename.endsWith(".yml") || 
               filename.endsWith(".agent.md");
    }
    
    private AgentConfig loadAgentFile(Path file) throws IOException {
        String filename = file.getFileName().toString().toLowerCase();
        
        if (filename.endsWith(".agent.md")) {
            return markdownParser.parse(file);
        } else if (filename.endsWith(".yaml") || filename.endsWith(".yml")) {
            return loadYamlAgent(file);
        }
        
        return null;
    }
    
    private AgentConfig loadYamlAgent(Path yamlFile) throws IOException {
        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(AgentConfigYaml.class, options));

        try (InputStream is = Files.newInputStream(yamlFile)) {
            AgentConfigYaml yamlConfig = yaml.load(is);
            AgentConfig config = yamlConfig != null ? yamlConfig.toAgentConfig() : null;
            if (config != null) {
                config.validateRequired();
            }
            return config;
        }
    }

    private static class AgentConfigYaml {
        private String name;
        private String displayName;
        private String model;
        private String systemPrompt;
        private String reviewPrompt;
        private String outputFormat;
        private List<String> focusAreas;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getSystemPrompt() {
            return systemPrompt;
        }

        public void setSystemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
        }

        public String getReviewPrompt() {
            return reviewPrompt;
        }

        public void setReviewPrompt(String reviewPrompt) {
            this.reviewPrompt = reviewPrompt;
        }

        public String getOutputFormat() {
            return outputFormat;
        }

        public void setOutputFormat(String outputFormat) {
            this.outputFormat = outputFormat;
        }

        public List<String> getFocusAreas() {
            return focusAreas;
        }

        public void setFocusAreas(List<String> focusAreas) {
            this.focusAreas = focusAreas;
        }

        public AgentConfig toAgentConfig() {
            return new AgentConfig(
                name,
                displayName,
                model,
                systemPrompt,
                reviewPrompt,
                outputFormat,
                focusAreas
            );
        }
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
        } else if (filename.endsWith(".yaml")) {
            return filename.substring(0, filename.length() - ".yaml".length());
        } else if (filename.endsWith(".yml")) {
            return filename.substring(0, filename.length() - ".yml".length());
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
