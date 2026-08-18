package com.mes.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 编辑用户
 */
@Data
public class SysUserUpdateDTO {

    @NotBlank(message = "姓名不能为空")
    private String userName;
}
