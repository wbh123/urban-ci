package org.urbansafe.priority.common.exception;

import jakarta.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;
import org.urbansafe.priority.model.dto.ApiError;
import org.urbansafe.priority.model.dto.ErrorResponse;
import org.urbansafe.priority.model.dto.FieldError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 将异常统一转换为 OpenAPI 定义的错误响应，并使用标准 HTTP 状态码。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LogManager.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
        return ResponseEntity
                .status(exception.getHttpStatus())
                .body(createErrorResponse(
                        exception.getErrorCode(),
                        exception.getMessage(),
                        Collections.emptyList()));
    }

    /**
     * 方法级权限校验发生在控制器调用阶段，不会经过 SecurityFilterChain 的 AccessDeniedHandler，
     * 因此需要在统一异常处理器中显式转换为稳定的 403 响应。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(createErrorResponse(
                "AUTH_ACCESS_DENIED",
                "无权访问该资源",
                Collections.emptyList()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception) {
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> {
                    FieldError fieldError = new FieldError();
                    fieldError.setField(error.getField());
                    fieldError.setRejectedValue(null);
                    fieldError.setMessage(error.getDefaultMessage() == null
                            ? "字段值不符合要求"
                            : error.getDefaultMessage());
                    return fieldError;
                })
                .toList();

        return ResponseEntity
                .badRequest()
                .body(createErrorResponse(
                        "VALIDATION_FAILED",
                        "请求参数校验失败",
                        fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception) {
        List<FieldError> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> {
                    FieldError fieldError = new FieldError();
                    fieldError.setField(violation.getPropertyPath().toString());
                    fieldError.setRejectedValue(null);
                    fieldError.setMessage(violation.getMessage());
                    return fieldError;
                })
                .toList();

        return ResponseEntity
                .badRequest()
                .body(createErrorResponse(
                        "VALIDATION_FAILED",
                        "请求参数校验失败",
                        fieldErrors));
    }

    /**
     * 将 JSON 语法错误、枚举越界和字段类型错误转换为稳定的 400 响应。
     *
     * <p>异常原文可能包含 Java 类型名或反序列化细节，因此仅记录服务端日志，
     * 对客户端返回不泄漏内部实现的固定错误码和中文说明。</p>
     *
     * @param exception Spring MVC 请求体反序列化异常
     * @return 统一格式的请求体无效响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception) {
        ResponseMetadata metadata = ResponseMetadataFactory.failure();
        LOGGER.warn("请求体无法解析，requestId={}", metadata.requestId(), exception);
        return ResponseEntity.badRequest().body(createErrorResponse(
                metadata,
                "REQUEST_BODY_INVALID",
                "请求体格式错误或字段值不受支持",
                Collections.emptyList()));
    }

    /**
     * 将未匹配到 API 的路径转换为 404，避免 Spring MVC 静态资源兜底异常被误报为 500。
     *
     * @param exception 未找到控制器或静态资源时产生的异常
     * @return 统一格式的资源不存在响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(createErrorResponse(
                "API_PATH_NOT_FOUND",
                "请求的接口路径不存在",
                Collections.emptyList()));
    }

    /**
     * 将 PostgreSQL 完整性异常翻译为稳定的业务状态码，避免并发唯一冲突泄漏为 500。
     *
     * <p>约束名只用于选择更精确的公开错误码，响应中不会暴露 SQL、表名或原始数据库异常文本。
     *
     * @param exception Spring 统一封装的数据库完整性异常
     * @return 符合统一响应结构的 400 或 409 响应
     */
    @ExceptionHandler({DataIntegrityViolationException.class, DuplicateKeyException.class})
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception) {
        String sqlState = findSqlState(exception);
        String constraintName = findConstraintName(exception);

        if ("23505".equals(sqlState)) {
            String errorCode = constraintName.contains("community_code")
                    ? "COMMUNITY_CODE_CONFLICT"
                    : constraintName.contains("building_code")
                            ? "BUILDING_CODE_CONFLICT"
                            : "RESOURCE_UNIQUE_CONFLICT";
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(createErrorResponse(errorCode, "资源编码或唯一字段已存在", Collections.emptyList()));
        }
        if ("23503".equals(sqlState)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(createErrorResponse(
                            "RESOURCE_REFERENCE_CONFLICT", "资源仍被其他数据引用", Collections.emptyList()));
        }
        if ("23514".equals(sqlState)) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse(
                            "DATABASE_CHECK_CONSTRAINT_FAILED", "请求数据不符合业务约束", Collections.emptyList()));
        }
        if ("23502".equals(sqlState)) {
            return ResponseEntity.badRequest()
                    .body(createErrorResponse(
                            "DATABASE_REQUIRED_FIELD_MISSING", "请求缺少数据库必填字段", Collections.emptyList()));
        }

        LOGGER.error("无法识别的数据库完整性异常，sqlState={}，constraint={}，requestId={}",
                sqlState, constraintName, ResponseMetadataFactory.failure().requestId(), exception);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(createErrorResponse(
                        "DATABASE_INTEGRITY_CONFLICT", "数据库完整性约束冲突", Collections.emptyList()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        ResponseMetadata metadata = ResponseMetadataFactory.failure();
        LOGGER.error("未处理的服务端异常，requestId={}", metadata.requestId(), exception);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse(
                        metadata,
                        "INTERNAL_SERVER_ERROR",
                        "服务器内部错误",
                        Collections.emptyList()));
    }

    private ErrorResponse createErrorResponse(
            String code,
            String message,
            List<FieldError> fieldErrors) {
        return createErrorResponse(
                ResponseMetadataFactory.failure(),
                code,
                message,
                fieldErrors);
    }

    private ErrorResponse createErrorResponse(
            ResponseMetadata metadata,
            String code,
            String message,
            List<FieldError> fieldErrors) {
        ApiError apiError = new ApiError();
        apiError.setCode(code);
        apiError.setMessage(message);
        apiError.setFieldErrors(fieldErrors);

        ErrorResponse response = new ErrorResponse();
        response.setSuccess(metadata.success());
        response.setRequestId(metadata.requestId());
        response.setTimestamp(metadata.timestamp());
        response.setData(null);
        response.setError(apiError);
        return response;
    }

    /**
     * 沿异常因果链查找 JDBC SQLState。
     *
     * @param exception 顶层异常
     * @return SQLState；未找到时返回空字符串
     */
    private String findSqlState(Throwable exception) {
        Throwable cursor = exception;
        while (cursor != null) {
            if (cursor instanceof java.sql.SQLException sqlException
                    && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
            cursor = cursor.getCause();
        }
        return "";
    }

    /**
     * 从 PostgreSQL 异常消息中提取约束名，仅用于内部错误码分类。
     *
     * @param exception 顶层异常
     * @return 小写异常消息；未找到时返回空字符串
     */
    private String findConstraintName(Throwable exception) {
        Throwable cursor = exception;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null && message.toLowerCase(java.util.Locale.ROOT).contains("constraint")) {
                return message.toLowerCase(java.util.Locale.ROOT);
            }
            cursor = cursor.getCause();
        }
        return "";
    }
}
