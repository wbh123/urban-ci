package org.urbansafe.priority.ai.orchestration;

import org.apache.ibatis.type.Alias;

/** 人工智能能力类型。 */
@Alias("Phase7AiCapabilityType")
public enum AiCapabilityType {
    VISION_INFERENCE,
    WORKFLOW,
    TEXT_GENERATION
}
