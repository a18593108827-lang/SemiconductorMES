package com.mes.recipe.dto;

import lombok.Data;

/** 新建草稿；可基于已有版本复制 body */
@Data
public class MesRecipeVersionCreateDTO {
    /** 源版本；空则空白草稿 */
    private Long fromVersionId;
    private String remark;
}
