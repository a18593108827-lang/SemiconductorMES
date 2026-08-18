package com.mes.common;

/**
 * 统一业务状态码
 */
public final class ResultCode {

    private ResultCode() {
    }

    /** 成功 */
    public static final int SUCCESS = 200;

    /** 业务失败 */
    public static final int FAIL = 500;

    /** 参数错误 */
    public static final int BAD_REQUEST = 400;

    /** 未授权 */
    public static final int UNAUTHORIZED = 401;

    /** 无权限 */
    public static final int FORBIDDEN = 403;

    /** 资源不存在 */
    public static final int NOT_FOUND = 404;
}
