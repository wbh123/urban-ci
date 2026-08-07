package org.urbansafe.priority.controller;

import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.model.api.SystemHealthApi;
import org.urbansafe.priority.model.dto.SystemHealthData;
import org.urbansafe.priority.model.dto.SystemHealthResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** 系统健康检查专用 Controller，仅实现 SystemHealthApi。 */
@RestController
public class SystemHealthController implements SystemHealthApi {

    @Override
    public ResponseEntity<SystemHealthResponse> getSystemHealth() {
        SystemHealthData data = new SystemHealthData();
        data.setService("urban-safe-priority-server");
        data.setStatus(SystemHealthData.StatusEnum.UP);
        data.setVersion("0.1.0");

        ResponseMetadata metadata = ResponseMetadataFactory.success();
        SystemHealthResponse response = new SystemHealthResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(data);
        response.setError(null);

        return ResponseEntity.ok(response);
    }
}
