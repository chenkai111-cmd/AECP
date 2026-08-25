package com.xiaou.common.exception;

import com.xiaou.common.domain.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常捕捉类
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public R<?> handleServiceException(ServiceException e, HttpServletRequest request) {
        log.error("请求地址: {}, 业务异常: {}", request.getRequestURI(), e.getMessage());
        Integer code = e.getCode();
        return code != null ? R.fail(code, e.getMessage()) : R.fail(e.getMessage());
    }

    /**
     * 请求方式不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<?> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.error("请求地址: {}, 不支持 {} 请求", request.getRequestURI(), e.getMethod());
        return R.fail(HttpStatus.METHOD_NOT_ALLOWED.value(), "不支持 " + e.getMethod() + " 请求");
    }

    /**
     * 请求路径中缺少必需的路径变量
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public R<?> handleMissingPathVariableException(MissingPathVariableException e, HttpServletRequest request) {
        log.error("请求地址: {}, 路径缺少必需参数: {}", request.getRequestURI(), e.getVariableName());
        return R.fail(HttpStatus.BAD_REQUEST.value(), "路径缺少必需参数: " + e.getVariableName());
    }

    /**
     * 请求参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<?> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.error("请求地址: {}, 参数类型不匹配: {}", request.getRequestURI(), e.getMessage());
        return R.fail(HttpStatus.BAD_REQUEST.value(), "参数类型不匹配");
    }

    /**
     * 自定义验证异常 (@RequestBody 参数校验)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.error("参数校验失败: {}", message);
        return R.fail(HttpStatus.BAD_REQUEST.value(), message);
    }

    /**
     * 自定义验证异常 (表单参数绑定)
     */
    @ExceptionHandler(BindException.class)
    public R<?> handleBindException(BindException e) {
        String message = e.getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.error("参数绑定失败: {}", message);
        return R.fail(HttpStatus.BAD_REQUEST.value(), message);
    }

    /**
     * 约束违反异常 (@RequestParam 参数校验)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<?> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.error("约束违反: {}", message);
        return R.fail(HttpStatus.BAD_REQUEST.value(), message);
    }

    /**
     * 其他异常兜底
     */
    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception e, HttpServletRequest request) {
        log.error("请求地址: {}, 系统异常:", request.getRequestURI(), e);
        return R.fail("系统异常，请稍后重试");
    }
}
