package com.zeta.firewall.exception;

// 自定义异常类
public  class EmptyResultException extends RuntimeException {
    public EmptyResultException(String message) {
        super(message);
    }
}
