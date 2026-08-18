package com.mes.recipe.vo;

import lombok.Data;

/** 配方解析结果 */
@Data
public class RecipeResolveVO {
    private Long recipeId;
    private String recipeCode;
    private String recipeName;
    private Long versionId;
    private Integer versionNo;
    private Long stepId;
    private Long eqpId;
}
