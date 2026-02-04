package dev.logicojp.reviewer.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing skill definitions.
 * Skills can be registered and retrieved by ID.
 */
public class SkillRegistry {

    private static final Logger logger = LoggerFactory.getLogger(SkillRegistry.class);

    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();

    /**
     * Registers a skill definition.
     */
    public void register(SkillDefinition skill) {
        skills.put(skill.id(), skill);
        logger.info("Registered skill: {} ({})", skill.id(), skill.name());
    }

    /**
     * Registers multiple skill definitions.
     */
    public void registerAll(Collection<SkillDefinition> skillDefinitions) {
        for (SkillDefinition skill : skillDefinitions) {
            register(skill);
        }
    }

    /**
     * Gets a skill by ID.
     */
    public Optional<SkillDefinition> get(String skillId) {
        return Optional.ofNullable(skills.get(skillId));
    }

    /**
     * Gets all registered skills.
     */
    public List<SkillDefinition> getAll() {
        return List.copyOf(skills.values());
    }

    /**
     * Gets all skill IDs.
     */
    public Set<String> getSkillIds() {
        return Set.copyOf(skills.keySet());
    }

    /**
     * Checks if a skill is registered.
     */
    public boolean hasSkill(String skillId) {
        return skills.containsKey(skillId);
    }

    /**
     * Removes a skill by ID.
     */
    public void unregister(String skillId) {
        skills.remove(skillId);
        logger.info("Unregistered skill: {}", skillId);
    }

    /**
     * Clears all registered skills.
     */
    public void clear() {
        skills.clear();
        logger.info("Cleared all skills");
    }

    /**
     * Returns the number of registered skills.
     */
    public int size() {
        return skills.size();
    }
}
