package com.mes.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注册请求
 */
@Data
public class RegisterDTO {

    /** 用户编码 */
    @NotBlank(message = "用户编码不能为空")
    private String userCode;

    /** 姓名 */
    @NotBlank(message = "姓名不能为空")
    private String userName;

    /** 密码 */
    @NotBlank(message = "密码不能为空")
    private String password;
}
