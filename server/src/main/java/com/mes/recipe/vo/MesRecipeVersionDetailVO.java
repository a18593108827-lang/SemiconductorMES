package com.mes.recipe.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 配方版本详情 */
@Data
public class MesRecipeVersionDetailVO {
    private Long id;
    private Long recipeId;
    private String recipeCode;
    private String recipeName;
    private Integer versionNo;
    private String status;
    private String bodyJson;
    private String bodyObjectKey;
    private String remark;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
