package com.mes.recipe.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 配方版本摘要 */
@Data
public class MesRecipeVersionVO {
    private Long id;
    private Long recipeId;
    private Integer versionNo;
    private String status;
    private String remark;
    private LocalDateTime publishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
