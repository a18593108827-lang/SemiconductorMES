package com.mes.common;

import cn.hutool.crypto.digest.BCrypt;

/**
 * 密码工具（BCrypt 加解密）
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    /** 加密密码 */
    public static String encode(String rawPassword) {
        return BCrypt.hashpw(rawPassword);
    }

    /** 校验密码 */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return BCrypt.checkpw(rawPassword, encodedPassword);
    }
}
