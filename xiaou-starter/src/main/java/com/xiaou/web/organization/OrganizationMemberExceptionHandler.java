package com.xiaou.web.organization;

import com.xiaou.aecp.identity.organization.OrganizationMemberError;
import com.xiaou.web.auth.InvalidSessionException;
import com.xiaou.web.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OrganizationMemberController.class)
public class OrganizationMemberExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OrganizationMemberExceptionHandler.class);

    @ExceptionHandler(InvalidSessionException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidSession(
            InvalidSessionException exception, HttpServletRequest request) {
        log.warn("uri={} exception={}", request.getRequestURI(), exception.getClass().getSimpleName());
        return failure(HttpStatus.UNAUTHORIZED, "认证信息无效");
    }

    @ExceptionHandler(OrganizationMemberError.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainError(
            OrganizationMemberError exception, HttpServletRequest request) {
        log.warn("uri={} exception={} reason={}",
                request.getRequestURI(), exception.getClass().getSimpleName(), exception.reason());
        return switch (exception.reason()) {
            case UNAUTHENTICATED -> failure(HttpStatus.UNAUTHORIZED, "认证信息无效");
            case FORBIDDEN -> failure(HttpStatus.FORBIDDEN, "无权执行该操作");
            case ORGANIZATION_NOT_FOUND -> failure(HttpStatus.NOT_FOUND, "组织不存在");
            case USER_NOT_FOUND -> failure(HttpStatus.NOT_FOUND, "用户不存在");
            case MEMBER_NOT_FOUND -> failure(HttpStatus.NOT_FOUND, "成员不存在");
            case ALREADY_ACTIVE -> failure(HttpStatus.CONFLICT, "成员已存在");
            case LAST_ADMINISTRATOR -> failure(HttpStatus.CONFLICT, "至少保留一名组织管理员");
        };
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(
            Exception exception, HttpServletRequest request) {
        log.warn("uri={} exception={}", request.getRequestURI(), exception.getClass().getSimpleName());
        return failure(HttpStatus.BAD_REQUEST, "请求参数错误");
    }

    @ExceptionHandler({RedisException.class, DataAccessResourceFailureException.class, CannotAcquireLockException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnavailable(
            Exception exception, HttpServletRequest request) {
        log.error("uri={} exception={}", request.getRequestURI(), exception.getClass().getSimpleName());
        return failure(HttpStatus.SERVICE_UNAVAILABLE, "服务暂时不可用");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabaseFailure(
            DataAccessException exception, HttpServletRequest request) {
        log.error("uri={} exception={}", request.getRequestURI(), exception.getClass().getSimpleName());
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedFailure(
            Exception exception, HttpServletRequest request) {
        log.error("uri={} exception={}", request.getRequestURI(), exception.getClass().getSimpleName());
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");
    }

    private static ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(status.value(), message));
    }
}
