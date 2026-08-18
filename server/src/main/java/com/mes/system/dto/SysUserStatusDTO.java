package com.mes.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户启停
 */
@Data
public class SysUserStatusDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
