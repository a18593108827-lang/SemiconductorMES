package com.mes.route.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.route.entity.MesRouteEdge;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MesRouteEdgeMapper extends BaseMapper<MesRouteEdge> {

    @Delete("DELETE FROM mes_route_edge WHERE version_id = #{versionId}")
    int physicalDeleteByVersionId(@Param("versionId") Long versionId);

    @Delete("DELETE FROM mes_route_edge WHERE version_id = #{versionId} AND edge_type = 'normal'")
    int physicalDeleteNormalByVersionId(@Param("versionId") Long versionId);
}
