package org.urbansafe.priority.common.exception;

import org.springframework.http.HttpStatus;

/** 编码重复、状态冲突或幂等冲突时返回 409。 */
public class ResourceConflictException extends BusinessException {

    public ResourceConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}
