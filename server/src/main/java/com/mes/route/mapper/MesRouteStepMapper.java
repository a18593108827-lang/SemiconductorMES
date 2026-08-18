package com.mes.route.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.route.entity.MesRouteStep;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 路线版本步骤 Mapper */
@Mapper
public interface MesRouteStepMapper extends BaseMapper<MesRouteStep> {

    /** 物理删除某版本下全部步骤（覆盖保存草稿时用，避免逻辑删除撞 uk_ver_sort） */
    @Delete("DELETE FROM mes_route_step WHERE version_id = #{versionId}")
    int physicalDeleteByVersionId(@Param("versionId") Long versionId);
}
