package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.agent.AgentConfig;
import dev.logicojp.reviewer.agent.ReviewContext;

@FunctionalInterface
interface AgentReviewerFactory {
    AgentReviewer create(AgentConfig config, ReviewContext context);
}