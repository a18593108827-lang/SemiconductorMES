package com.mes.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求
 */
@Data
public class LoginDTO {

    /** 用户编码 */
    @NotBlank(message = "用户编码不能为空")
    private String userCode;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
