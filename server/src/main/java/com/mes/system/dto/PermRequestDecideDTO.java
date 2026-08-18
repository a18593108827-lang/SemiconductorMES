package com.mes.system.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PermRequestDecideDTO {

    @Size(max = 512, message = "审批意见过长")
    private String opinion;
}
