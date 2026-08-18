package com.mes.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理员重置密码
 */
@Data
public class SysUserResetPwdDTO {

    /** 临时密码 */
    @NotBlank(message = "临时密码不能为空")
    private String password;

    /** 是否强制下次改密，默认 true */
    private Boolean mustChangePwd = true;
}
