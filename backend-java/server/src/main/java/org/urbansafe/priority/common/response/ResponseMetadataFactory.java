package org.urbansafe.priority.common.response;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.urbansafe.priority.common.request.RequestContext;

/** 创建全局统一响应体所需的公共元数据。 */
public final class ResponseMetadataFactory {

    private static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("Asia/Shanghai");

    private ResponseMetadataFactory() {
    }

    public static ResponseMetadata success() {
        return create(true);
    }

    public static ResponseMetadata failure() {
        return create(false);
    }

    private static ResponseMetadata create(boolean success) {
        return new ResponseMetadata(
                success,
                RequestContext.getRequestId(),
                OffsetDateTime.now(DEFAULT_ZONE_ID));
    }
}
