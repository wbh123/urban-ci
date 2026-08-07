package org.urbansafe.priority.common.exception;

import org.springframework.http.HttpStatus;

/** PostgreSQL、MinIO 或人工智能服务不可用时返回 503。 */
public class DependencyUnavailableException extends BusinessException {

    public DependencyUnavailableException(String errorCode, String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, errorCode, message);
    }
}
