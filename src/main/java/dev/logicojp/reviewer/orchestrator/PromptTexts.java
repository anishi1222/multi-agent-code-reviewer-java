package dev.logicojp.reviewer.orchestrator;

public record PromptTexts(
    String focusAreasGuidance,
    String localSourceHeader,
    String localReviewResultRequest
) {
    public PromptTexts {
        focusAreasGuidance = focusAreasGuidance != null ? focusAreasGuidance : "";
        localSourceHeader = localSourceHeader != null ? localSourceHeader : "";
        localReviewResultRequest = localReviewResultRequest != null ? localReviewResultRequest : "";
    }
}