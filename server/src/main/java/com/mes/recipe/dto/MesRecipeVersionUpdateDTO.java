package com.mes.recipe.dto;

import lombok.Data;

/** 编辑草稿版本（仅 draft） */
@Data
public class MesRecipeVersionUpdateDTO {
    private String bodyJson;
    private String bodyObjectKey;
    private String remark;
}
