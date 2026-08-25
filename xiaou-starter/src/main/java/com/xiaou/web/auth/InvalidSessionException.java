package com.xiaou.web.auth;

public final class InvalidSessionException extends RuntimeException {

    public InvalidSessionException() {
        super("认证信息无效");
    }
}
