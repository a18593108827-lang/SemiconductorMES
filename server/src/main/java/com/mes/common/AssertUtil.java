package com.mes.common;

import java.util.Collection;
import java.util.Map;

/**
 * 业务断言工具：条件不满足时抛出 {@link BusinessException}
 */
public final class AssertUtil {

    private AssertUtil() {
    }

    /** 表达式必须为 true */
    public static void isTrue(boolean expression, String msg) {
        if (!expression) {
            throw new BusinessException(msg);
        }
    }

    /** 表达式必须为 false */
    public static void isFalse(boolean expression, String msg) {
        if (expression) {
            throw new BusinessException(msg);
        }
    }

    /** 对象不能为 null */
    public static void notNull(Object obj, String msg) {
        if (obj == null) {
            throw new BusinessException(msg);
        }
    }

    /** 对象必须为 null */
    public static void isNull(Object obj, String msg) {
        if (obj != null) {
            throw new BusinessException(msg);
        }
    }

    /** 字符串不能为空白 */
    public static void notBlank(String str, String msg) {
        if (str == null || str.isBlank()) {
            throw new BusinessException(msg);
        }
    }

    /** 集合不能为空 */
    public static void notEmpty(Collection<?> collection, String msg) {
        if (collection == null || collection.isEmpty()) {
            throw new BusinessException(msg);
        }
    }

    /** Map 不能为空 */
    public static void notEmpty(Map<?, ?> map, String msg) {
        if (map == null || map.isEmpty()) {
            throw new BusinessException(msg);
        }
    }
}
