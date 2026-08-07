package org.urbansafe.priority.common.response;

import org.urbansafe.priority.model.dto.ApiError;
import org.urbansafe.priority.model.dto.ErrorResponse;

public final class UnifiedResponse {

    private UnifiedResponse() {
    }

    public static ErrorResponse error(String code, String message) {
        ResponseMetadata metadata = ResponseMetadataFactory.failure();

        ApiError apiError = new ApiError();
        apiError.setCode(code);
        apiError.setMessage(message);

        ErrorResponse response = new ErrorResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(null);
        response.setError(apiError);
        return response;
    }

}
