package com.mes.recipe.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 配方绑定 */
@Data
public class MesRecipeBindingVO {
    private Long id;
    private Long stepId;
    private String stepCode;
    private String stepName;
    private Long eqpId;
    private String eqpCode;
    private String eqpType;
    private Long recipeId;
    private String recipeCode;
    private String recipeName;
    private Long recipeVersionId;
    private Integer recipeVersionNo;
    private Integer enabled;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
