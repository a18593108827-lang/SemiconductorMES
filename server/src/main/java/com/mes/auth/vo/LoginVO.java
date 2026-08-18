package com.mes.auth.vo;

import lombok.Data;

/**
 * 登录成功返回
 */
@Data
public class LoginVO {

    /** Token 值 */
    private String token;

    /** 请求头名称 */
    private String tokenName;
}
