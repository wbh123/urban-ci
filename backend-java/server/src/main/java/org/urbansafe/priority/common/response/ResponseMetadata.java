package org.urbansafe.priority.common.response;

import java.time.OffsetDateTime;

/** 所有成功和失败响应共享的元数据。 */
public record ResponseMetadata(
        boolean success,
        String requestId,
        OffsetDateTime timestamp) {
}
