package dev.logicojp.reviewer.agent;

import dev.logicojp.reviewer.skill.SkillDefinition;
import dev.logicojp.reviewer.skill.SkillMarkdownParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Loads agent configurations from external files.
/// Supports GitHub Copilot agent definition format (.agent.md).
///
/// Files can be placed in:
/// - agents/ directory
/// - .github/agents/ directory
///
/// Skills are loaded from (in priority order):
/// 1. .github/skills/ — Agent Skills spec (SKILL.md per directory)
/// 2. {agentsDir}/{agentName}/ — Legacy per-agent skill files (.skill.md)
public class AgentConfigLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentConfigLoader.class);

    /// Standard skills directory per Agent Skills spec
    private static final String GITHUB_SKILLS_DIR = ".github/skills";
    
    private final List<Path> agentDirectories;
    private final AgentMarkdownParser markdownParser;
    private final SkillMarkdownParser skillParser;
    
    /// Creates a loader with a single agents directory.
    public AgentConfigLoader(Path agentsDirectory) {
        this(List.of(agentsDirectory));
    }
    
    /// Creates a loader with multiple agent directories.
    /// Directories are searched in order; later directories override earlier ones.
    public AgentConfigLoader(List<Path> agentDirectories) {
        this.agentDirectories = new ArrayList<>(agentDirectories);
        this.markdownParser = new AgentMarkdownParser();
        this.skillParser = new SkillMarkdownParser();
    }
    
    /// Loads all agent configurations from all configured directories.
    /// Skills are loaded from .github/skills/ (Agent Skills spec) or
    /// from per-agent subdirectories (legacy format).
    /// @return Map of agent name to AgentConfig
    public Map<String, AgentConfig> loadAllAgents() throws IOException {
        Map<String, AgentConfig> agents = new HashMap<>();

        // Discover global skills from .github/skills/ (Agent Skills spec)
        List<SkillDefinition> globalSkills = loadGlobalSkills();

        for (Path directory : agentDirectories) {
            if (!Files.exists(directory)) {
                logger.debug("Agents directory does not exist: {}", directory);
                continue;
            }
            
            logger.info("Loading agents from: {}", directory);
            loadAgentsFromDirectory(directory, agents, globalSkills);
        }
        
        if (agents.isEmpty()) {
            logger.warn("No agents found in any configured directory");
        }
        
        return agents;
    }
    
    private void loadAgentsFromDirectory(Path directory, Map<String, AgentConfig> agents,
                                         List<SkillDefinition> globalSkills) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            List<Path> files = paths
                .filter(this::isAgentFile)
                .collect(Collectors.toList());
            
            for (Path file : files) {
                try {
                    AgentConfig config = markdownParser.parse(file);
                    if (config != null) {
                        // Collect skills for this agent:
                        // 1. Global skills from .github/skills/ matched by metadata.agent
                        // 2. Legacy per-agent subdirectory skills
                        List<SkillDefinition> agentSkills = collectSkillsForAgent(
                            directory, config.getName(), globalSkills);
                        if (!agentSkills.isEmpty()) {
                            config = config.withSkills(agentSkills);
                            logger.info("Loaded {} skills for agent: {}", agentSkills.size(), config.getName());
                        }
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

    /// Collects skills for a specific agent from global and legacy sources.
    ///
    /// Global skills (.github/skills/) are matched to agents via the `metadata.agent` field.
    /// Legacy skills ({agentsDir}/{agentName}/*.skill.md) are included unconditionally.
    /// Skills without an agent metadata field are available to all agents.
    private List<SkillDefinition> collectSkillsForAgent(Path agentsDir, String agentName,
                                                         List<SkillDefinition> globalSkills) {
        List<SkillDefinition> skills = new ArrayList<>();

        // Add global skills matching this agent (or with no agent restriction)
        for (SkillDefinition skill : globalSkills) {
            String skillAgent = skill.metadata().get("agent");
            if (skillAgent == null || skillAgent.equals(agentName)) {
                skills.add(skill);
            }
        }

        // Add legacy per-agent subdirectory skills
        List<SkillDefinition> legacySkills = loadLegacySkillsForAgent(agentsDir, agentName);
        skills.addAll(legacySkills);

        return skills;
    }

    /// Loads skills from .github/skills/ following the Agent Skills specification.
    /// Each skill is a directory containing a SKILL.md file.
    private List<SkillDefinition> loadGlobalSkills() {
        // Resolve .github/skills/ relative to the working directory
        Path skillsRoot = Path.of(GITHUB_SKILLS_DIR);
        if (!Files.isDirectory(skillsRoot)) {
            logger.debug("Global skills directory does not exist: {}", skillsRoot);
            return List.of();
        }

        List<Path> skillFiles = skillParser.discoverSkills(skillsRoot);
        if (skillFiles.isEmpty()) {
            return List.of();
        }

        List<SkillDefinition> skills = new ArrayList<>();
        for (Path skillFile : skillFiles) {
            try {
                SkillDefinition skill = skillParser.parse(skillFile);
                skills.add(skill);
                logger.info("Loaded global skill: {} from {}", skill.id(),
                    skillFile.getParent().getFileName());
            } catch (Exception e) {
                logger.error("Failed to load skill from {}: {}", skillFile, e.getMessage());
            }
        }

        logger.info("Loaded {} global skills from {}", skills.size(), skillsRoot);
        return List.copyOf(skills);
    }

    /// Loads skill definitions from legacy .skill.md files in the agent's subdirectory.
    /// Skills are expected in: {agentsDir}/{agentName}/*.skill.md
    private List<SkillDefinition> loadLegacySkillsForAgent(Path agentsDir, String agentName) {
        Path skillsDir = agentsDir.resolve(agentName);
        if (!Files.exists(skillsDir) || !Files.isDirectory(skillsDir)) {
            return List.of();
        }

        List<SkillDefinition> skills = new ArrayList<>();
        try (Stream<Path> paths = Files.list(skillsDir)) {
            List<Path> skillFiles = paths
                .filter(skillParser::isSkillFile)
                .sorted()
                .collect(Collectors.toList());

            for (Path skillFile : skillFiles) {
                try {
                    SkillDefinition skill = skillParser.parse(skillFile);
                    skills.add(skill);
                    logger.debug("Loaded skill: {} from {}", skill.id(), skillFile.getFileName());
                } catch (Exception e) {
                    logger.error("Failed to load skill from {}: {}", skillFile, e.getMessage());
                }
            }
        } catch (IOException e) {
            logger.error("Failed to list skills directory {}: {}", skillsDir, e.getMessage());
        }

        return skills;
    }
    
    private boolean isAgentFile(Path path) {
        String filename = path.getFileName().toString().toLowerCase();
        return filename.endsWith(".agent.md");
    }
    
    /// Loads specific agents by name.
    /// @param agentNames List of agent names to load
    /// @return Map of agent name to AgentConfig
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
    
    /// Lists all available agent names from all configured directories.
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
    
    /// Gets the list of configured agent directories.
    public List<Path> getAgentDirectories() {
        return Collections.unmodifiableList(agentDirectories);
    }
}
