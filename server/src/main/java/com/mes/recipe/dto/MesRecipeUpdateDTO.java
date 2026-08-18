package com.mes.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 编辑配方主数据（不含编码） */
@Data
public class MesRecipeUpdateDTO {

    @NotBlank(message = "配方名称不能为空")
    private String recipeName;

    private String remark;
}
