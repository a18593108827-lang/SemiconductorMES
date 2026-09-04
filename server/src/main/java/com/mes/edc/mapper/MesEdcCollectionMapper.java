package com.mes.edc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.edc.entity.MesEdcCollection;
import com.mes.edc.vo.EdcSeriesPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 采集头表 */
@Mapper
public interface MesEdcCollectionMapper extends BaseMapper<MesEdcCollection> {

    /** 某站某特性最近的点，再按时间从早到晚排。软删的头和项都不要。 */
    @Select("""
            <script>
            SELECT t.itemId,
                   t.collectionId,
                   t.lotId,
                   t.lotNo,
                   t.eqpId,
                   t.collectedAt,
                   t.valueNum,
                   t.itemResult,
                   t.uslSnap,
                   t.lslSnap
            FROM (
                SELECT i.id AS itemId,
                       i.collection_id AS collectionId,
                       c.lot_id AS lotId,
                       c.lot_no AS lotNo,
                       c.eqp_id AS eqpId,
                       c.collected_at AS collectedAt,
                       i.value_num AS valueNum,
                       i.item_result AS itemResult,
                       i.usl_snap AS uslSnap,
                       i.lsl_snap AS lslSnap
                FROM mes_edc_collection_item i
                INNER JOIN mes_edc_collection c ON c.id = i.collection_id AND c.deleted = 0
                WHERE i.deleted = 0
                  AND i.param_id = #{paramId}
                  AND c.step_id = #{stepId}
                  <if test="eqpId != null">AND c.eqp_id = #{eqpId}</if>
                  <if test="fromTime != null">AND c.collected_at &gt;= #{fromTime}</if>
                  <if test="toTime != null">AND c.collected_at &lt;= #{toTime}</if>
                ORDER BY c.collected_at DESC, i.id DESC
                LIMIT #{limit}
            ) t
            ORDER BY t.collectedAt ASC, t.itemId ASC
            </script>
            """)
    List<EdcSeriesPoint> listSeries(@Param("paramId") Long paramId,
                                    @Param("stepId") Long stepId,
                                    @Param("eqpId") Long eqpId,
                                    @Param("fromTime") LocalDateTime fromTime,
                                    @Param("toTime") LocalDateTime toTime,
                                    @Param("limit") int limit);
}
