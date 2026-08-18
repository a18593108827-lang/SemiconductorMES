package com.mes.wip.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mes.wip.entity.MesWipLot;
import com.mes.wip.vo.MesWipStepSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MesWipLotMapper extends BaseMapper<MesWipLot> {

    @Select("""
            SELECT current_sort_no AS sortNo,
                   current_step_id AS stepId,
                   SUM(CASE WHEN status = 'wait' THEN 1 ELSE 0 END) AS waitCount,
                   SUM(CASE WHEN status = 'processing' THEN 1 ELSE 0 END) AS processingCount,
                   SUM(CASE WHEN status = 'held' THEN 1 ELSE 0 END) AS heldCount,
                   COUNT(*) AS total
            FROM mes_wip_lot
            GROUP BY current_sort_no, current_step_id
            ORDER BY current_sort_no ASC, current_step_id ASC
            """)
    List<MesWipStepSummaryVO> summaryByStep();
}

