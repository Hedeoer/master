package com.zeta.firewall.subscirbe;

/**
 * Redis Stream消费者异常
 * 用于封装Stream消费过程中的异常
 */
public class StreamConsumerException extends RuntimeException {
    
    public StreamConsumerException(String message) {
        super(message);
    }
    
    public StreamConsumerException(String message, Throwable cause) {
        super(message, cause);
    }
}
