package com.mes.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 新建配方主数据 */
@Data
public class MesRecipeCreateDTO {

    @NotBlank(message = "配方编码不能为空")
    private String recipeCode;

    @NotBlank(message = "配方名称不能为空")
    private String recipeName;

    private String remark;
}
