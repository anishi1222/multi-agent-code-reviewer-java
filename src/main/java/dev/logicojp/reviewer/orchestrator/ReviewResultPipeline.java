package dev.logicojp.reviewer.orchestrator;

import dev.logicojp.reviewer.report.ReviewResult;
import dev.logicojp.reviewer.report.ReviewResultMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/// Finalizes orchestrated review results: collect, filter, log, and optional merge.
final class ReviewResultPipeline {

    private static final Logger logger = LoggerFactory.getLogger(ReviewResultPipeline.class);

    ReviewResultPipeline() {
    }

    List<ReviewResult> collectFromFutures(List<CompletableFuture<ReviewResult>> futures) {
        List<ReviewResult> results = new ArrayList<>(futures.size());
        for (CompletableFuture<ReviewResult> future : futures) {
            try {
                ReviewResult result = future.getNow(null);
                if (result == null) {
                    logger.warn("Review future completed without a result (null)");
                    continue;
                }
                results.add(result);
            } catch (Exception e) {
                logger.error("Error collecting review result: {}", e.getMessage(), e);
            }
        }
        return results;
    }

    List<ReviewResult> finalizeResults(List<ReviewResult> results, int reviewPasses) {
        List<ReviewResult> filtered = filterNonNull(results);
        logCompletionSummary(filtered);

        if (!shouldMerge(reviewPasses)) {
            return filtered;
        }

        return mergeAndLog(filtered);
    }

    private List<ReviewResult> filterNonNull(List<ReviewResult> results) {
        return results.stream()
            .filter(Objects::nonNull)
            .toList();
    }

    private void logCompletionSummary(List<ReviewResult> results) {
        long successCount = countSuccessful(results);
        logger.info(completionSummaryMessage(), results.size(), successCount, results.size() - successCount);
    }

    private long countSuccessful(List<ReviewResult> results) {
        return results.stream().filter(ReviewResult::success).count();
    }

    private String completionSummaryMessage() {
        return "Completed {} reviews (success: {}, failed: {})";
    }

    private boolean shouldMerge(int reviewPasses) {
        return reviewPasses > 1;
    }

    private List<ReviewResult> mergeAndLog(List<ReviewResult> filtered) {
        List<ReviewResult> merged = ReviewResultMerger.mergeByAgent(filtered);
        logger.info("Merged {} pass results into {} agent results", filtered.size(), merged.size());
        return merged;
    }
}