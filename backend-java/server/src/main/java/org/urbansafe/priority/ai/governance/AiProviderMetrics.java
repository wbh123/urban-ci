package org.urbansafe.priority.ai.governance;

import org.apache.ibatis.type.Alias;

/** 受控人工智能任务统计，不包含密钥、原始响应或业务图片。 */
@Alias("Phase7AiProviderMetrics")
public record AiProviderMetrics(
        long totalTasks,
        long succeededTasks,
        long failedTasks,
        long reviewedTasks,
        long pendingReviewTasks,
        long averageDurationMs,
        double successRate) {

    public static AiProviderMetrics empty() {
        return new AiProviderMetrics(0, 0, 0, 0, 0, 0, 0d);
    }

    public AiProviderMetrics plus(AiProviderMetrics other) {
        if (other == null) {
            return this;
        }
        long combinedTotal = totalTasks + other.totalTasks;
        long weightedDuration = combinedTotal == 0
                ? 0
                : Math.round(
                        ((double) averageDurationMs * totalTasks
                                + (double) other.averageDurationMs * other.totalTasks)
                                / combinedTotal);
        long combinedSucceeded = succeededTasks + other.succeededTasks;
        return new AiProviderMetrics(
                combinedTotal,
                combinedSucceeded,
                failedTasks + other.failedTasks,
                reviewedTasks + other.reviewedTasks,
                pendingReviewTasks + other.pendingReviewTasks,
                weightedDuration,
                combinedTotal == 0 ? 0d : combinedSucceeded * 100d / combinedTotal);
    }
}
