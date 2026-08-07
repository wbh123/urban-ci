package org.urbansafe.priority.ai.command;

import java.util.UUID;

/**
 * 提交人工复核命令。
 *
 * @param inferenceId 推理任务编号
 * @param reviewStatus 复核状态 CONFIRMED、CORRECTED 或 REJECTED
 * @param comment     复核备注
 * @param reviewedBy  复核人编号
 */
public record ReviewCommand(
        UUID inferenceId,
        String reviewStatus,
        String comment,
        UUID reviewedBy) {
}
