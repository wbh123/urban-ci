package org.urbansafe.priority.ai.command;

import java.util.UUID;

/**
 * 重试失败推理任务命令。
 *
 * @param inferenceId 原推理任务编号
 * @param modelId     可选，指定另一个已批准模型；为空时沿用原模型
 * @param requestedBy 请求用户编号
 */
public record RetryCommand(
        UUID inferenceId,
        String modelId,
        UUID requestedBy) {
}
