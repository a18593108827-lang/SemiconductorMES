package com.mes.history.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.history.vo.HistoryDailyCountVO;
import com.mes.history.vo.HistoryStepCountVO;
import com.mes.track.entity.MesTxLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** 履历只读 Mapper，表仍是 mes_tx_log。约定禁止 insert/update/delete，写只走 Track。 */
@Mapper
public interface HistoryTxLogMapper extends BaseMapper<MesTxLog> {

    @Select("""
            SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS day, COUNT(*) AS count
            FROM mes_tx_log
            WHERE tx_type = #{txType}
              AND create_time >= #{from}
              AND create_time < #{toExclusive}
            GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d')
            ORDER BY day
            """)
    List<HistoryDailyCountVO> countDailyByTxType(@Param("txType") String txType,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("toExclusive") LocalDateTime toExclusive);

    @Select("""
            SELECT step_id AS stepId, COUNT(*) AS count
            FROM mes_tx_log
            WHERE tx_type = #{txType}
              AND create_time >= #{from}
              AND create_time < #{toExclusive}
            GROUP BY step_id
            ORDER BY count DESC
            """)
    List<HistoryStepCountVO> countByStepAndTxType(@Param("txType") String txType,
                                                  @Param("from") LocalDateTime from,
                                                  @Param("toExclusive") LocalDateTime toExclusive);

    /**
     * 批量：给定 Lot 集合每批最近 limit 条（按 create_time DESC, id DESC）。
     * 用 ROW_NUMBER 窗口避免单条 IN 拉全量；空入参调用方自行处理。
     */
    @Select("""
            SELECT id, lot_id, lot_no, tx_type, from_status, to_status,
                   from_sort_no, to_sort_no, step_id, eqp_id, recipe_id, recipe_version_id,
                   route_version_id, remark, ext_json, oper_user_id, oper_user_name, create_time
            FROM (
                SELECT t.*, ROW_NUMBER() OVER (PARTITION BY lot_id ORDER BY create_time DESC, id DESC) AS rn
                FROM mes_tx_log t
                WHERE lot_id IN
                <foreach collection="lotIds" item="id" open="(" close=")" separator=",">
                    #{id}
                </foreach>
            ) ranked
            WHERE rn <= #{limit}
            """)
    List<MesTxLog> selectRecentByLots(@Param("lotIds") Collection<Long> lotIds,
                                       @Param("limit") int limit);
}
