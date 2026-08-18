package com.mes.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 配方版本：draft 可编辑，active 生效，obsolete 停用 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_recipe_version")
public class MesRecipeVersion extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long recipeId;
    /** 同配方内自 1 递增 */
    private Integer versionNo;
    /** draft / active / obsolete */
    private String status;
    /** 参数 JSON（一期可选） */
    private String bodyJson;
    /** MinIO 对象键 */
    private String bodyObjectKey;
    private String remark;
    private LocalDateTime publishedAt;

    private Long createBy;
    private Long updateBy;
}
