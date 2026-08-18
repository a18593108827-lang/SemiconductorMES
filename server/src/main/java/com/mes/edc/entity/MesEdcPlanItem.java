package com.mes.edc.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 计划项：计划里要采的一条特性。
 * spec_id 空 = 现场按 lot 产品解析当前 active Spec。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_edc_plan_item")
public class MesEdcPlanItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long planId;
    private Long paramId;
    /** 指定规格；空=跟 active */
    private Long specId;
    /** 录入顺序，越小越靠前 */
    private Integer sortNo;
    /** 1必采；缺了提交直接 FAIL */
    private Integer mandatory;
}
