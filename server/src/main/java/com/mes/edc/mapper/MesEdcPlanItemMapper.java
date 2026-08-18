package com.mes.edc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.edc.entity.MesEdcPlanItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 计划项表 */
@Mapper
public interface MesEdcPlanItemMapper extends BaseMapper<MesEdcPlanItem> {

    /** 整表替换前物理清掉，避免软删撞唯一键 (plan_id, param_id) */
    @Delete("DELETE FROM mes_edc_plan_item WHERE plan_id = #{planId}")
    int physicalDeleteByPlanId(@Param("planId") Long planId);
}
