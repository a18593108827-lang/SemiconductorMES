package com.mes.recipe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Step×Eqp 配方绑定 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_recipe_binding")
public class MesRecipeBinding extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long stepId;
    /** 具体设备；与 eqpType 不可同时空 */
    private Long eqpId;
    /** 设备类型绑定 */
    private String eqpType;
    private Long recipeId;
    /** 指定版本；空=跟随 recipe 当前 active */
    private Long recipeVersionId;
    /** 1启用 0停用 */
    private Integer enabled;

    private Long createBy;
    private Long updateBy;
}
