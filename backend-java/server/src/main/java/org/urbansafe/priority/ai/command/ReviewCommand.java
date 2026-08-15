package org.urbansafe.priority.ai.command;

import java.util.Map;
import java.util.UUID;

/**
 * 提交人工复核命令。
 *
 * @param inferenceId 推理任务编号
 * @param reviewStatus 复核状态 CONFIRMED、CORRECTED 或 REJECTED
 * @param comment     复核备注
 * @param reviewedBy  复核人编号
 * @param correctedData 人工修正后的结构化辅助数据；不得直接承载正式规则评分写入
 */
public record ReviewCommand(
        UUID inferenceId,
        String reviewStatus,
        String comment,
        UUID reviewedBy,
        Map<String, Object> correctedData) {

    public ReviewCommand(
            UUID inferenceId,
            String reviewStatus,
            String comment,
            UUID reviewedBy) {
        this(inferenceId, reviewStatus, comment, reviewedBy, Map.of());
    }

    public ReviewCommand {
        correctedData = correctedData == null ? Map.of() : Map.copyOf(correctedData);
    }
}
