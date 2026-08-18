package com.mes.recipe.dto;

import lombok.Data;

/** 配方分页查询 */
@Data
public class MesRecipeQuery {
    /** 编码 / 名称 */
    private String keyword;
    /** 1启用 0停用；空则全部 */
    private Integer enabled;
    private long page = 1;
    private long size = 20;
}
