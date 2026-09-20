package com.mes.hold.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.hold.entity.MesHold;
import com.mes.hold.vo.HoldReasonAggVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MesHoldMapper extends BaseMapper<MesHold> {

    /**
     * 按 hold_time 落入 [from, toExclusive) 的发生笔数，按 reason_code 聚合。
     * 时长：每笔 TIMESTAMPDIFF(MINUTE, hold_time, COALESCE(release_time, now))，再 AVG。
     */
    @Select("""
            SELECT reason_code AS reasonCode,
                   COUNT(*) AS holdCount,
                   SUM(CASE WHEN status = 'active' THEN 1 ELSE 0 END) AS activeCount,
                   ROUND(AVG(TIMESTAMPDIFF(MINUTE, hold_time, COALESCE(release_time, #{now})))) AS avgDurationMinutes
            FROM mes_hold
            WHERE hold_time >= #{from}
              AND hold_time < #{toExclusive}
            GROUP BY reason_code
            ORDER BY holdCount DESC
            """)
    List<HoldReasonAggVO> aggregateByReasonInHoldTimeRange(@Param("from") LocalDateTime from,
                                                           @Param("toExclusive") LocalDateTime toExclusive,
                                                           @Param("now") LocalDateTime now);
}
