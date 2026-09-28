package com.fc.v2.common.exception;

/**
 * 业务异常：工单状态不满足流转条件（不存在、已封存、跳级操作等）。
 * 由全局异常处理直接回写消息，不打印系统异常栈。
 *
 * @author fuce
 * @date 2026-09-28
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessException(String message) {
        super(message);
    }
}
