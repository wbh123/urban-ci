package org.urbansafe.priority.common.exception;

import org.springframework.http.HttpStatus;

/** 资源不存在或已经逻辑删除时返回 404。 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String errorCode, String message) {
        super(HttpStatus.NOT_FOUND, errorCode, message);
    }
}
