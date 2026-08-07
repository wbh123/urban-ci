package org.urbansafe.priority.phase2.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;

/** 第二阶段手写控制器复用的统一响应工厂。 */
public final class Phase2ResponseFactory {

    private Phase2ResponseFactory() {
    }

    public static Map<String, Object> success(Object data) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", metadata.success());
        body.put("data", data);
        body.put("error", null);
        body.put("requestId", metadata.requestId());
        body.put("timestamp", metadata.timestamp());
        return body;
    }
}
