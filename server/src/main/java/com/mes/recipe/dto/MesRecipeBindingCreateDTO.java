package com.mes.recipe.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 新建 Step×Eqp 配方绑定 */
@Data
public class MesRecipeBindingCreateDTO {

    @NotNull(message = "工序不能为空")
    private Long stepId;

    /** 与 eqpType 至少填一个 */
    private Long eqpId;
    private String eqpType;

    @NotNull(message = "配方不能为空")
    private Long recipeId;

    /** 空=跟随当前 active */
    private Long recipeVersionId;

    /** 默认 1 */
    private Integer enabled;
}
