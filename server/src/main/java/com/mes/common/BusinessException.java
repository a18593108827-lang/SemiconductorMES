package com.mes.common;

import lombok.Getter;

/**
 * 业务异常，由全局处理器捕获并转为统一返回
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    public BusinessException(String msg) {
        this(ResultCode.FAIL, msg);
    }

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
