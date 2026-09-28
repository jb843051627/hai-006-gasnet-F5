package com.fc.v2.common.exception;

/**
 * 单据状态机非法流转异常：跳级操作、终态单据被改删时抛出，
 * 由全局异常解析为 AjaxResult 错误信息返回前台。
 *
 * @author fuce
 * @date 2026-09-27
 */
public class StateFlowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StateFlowException(String message) {
        super(message);
    }
}
