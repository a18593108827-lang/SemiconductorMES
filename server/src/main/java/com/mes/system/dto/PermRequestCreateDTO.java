package com.mes.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PermRequestCreateDTO {

    @NotNull(message = "目标角色不能为空")
    private Long roleId;

    @NotBlank(message = "申请原因不能为空")
    @Size(max = 512, message = "申请原因过长")
    private String reason;
}
