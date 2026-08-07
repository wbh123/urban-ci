package org.urbansafe.priority.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 表示请求值在跨字段或排序白名单校验中不合法。
 *
 * <p>该异常与数据库资源冲突分离：请求本身错误固定返回 HTTP 400，便于前端准确提示用户修正输入。
 */
public class InvalidRequestException extends BusinessException {

    /**
     * 创建请求错误异常。
     *
     * @param errorCode 对外稳定错误码
     * @param message 不包含敏感数据的中文错误说明
     */
    public InvalidRequestException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}
