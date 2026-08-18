package com.mes.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 配方主数据 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_recipe")
public class MesRecipe extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 配方编码（短名，唯一） */
    private String recipeCode;
    private String recipeName;
    /** 1启用 0停用 */
    private Integer enabled;
    private String remark;

    /** 乐观锁 */
    @Version
    private Integer version;

    private Long createBy;
    private Long updateBy;
}
