package com.mes.recipe.dto;

import lombok.Data;

/** 配方绑定分页查询 */
@Data
public class MesRecipeBindingQuery {
    private Long stepId;
    private Long recipeId;
    private Long eqpId;
    private String eqpType;
    /** 1启用 0停用；空则全部 */
    private Integer enabled;
    private long page = 1;
    private long size = 20;
}
