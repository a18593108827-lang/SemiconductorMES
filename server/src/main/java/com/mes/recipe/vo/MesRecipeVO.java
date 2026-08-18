package com.mes.recipe.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 配方主数据 */
@Data
public class MesRecipeVO {
    private Long id;
    private String recipeCode;
    private String recipeName;
    private Integer enabled;
    private String remark;
    private Integer version;
    /** 当前生效版本 */
    private Long activeVersionId;
    private Integer activeVersionNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
