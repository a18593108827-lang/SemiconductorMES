package com.mes.history.facade;

import com.mes.common.PageResult;
import com.mes.history.dto.HistoryQuery;
import com.mes.history.vo.HistoryDailyCountVO;
import com.mes.history.vo.HistoryStepCountVO;
import com.mes.history.vo.HistoryTxVO;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 履历对外唯一门面：只回答「这批 / 这台机发生过什么」。
 * 写仍只在 Track；别人禁止直查 mes_tx_log。
 */
public interface HistoryFacade {

    /** 现场侧栏兼容：时间正序，最多最近 500 条；Lot 不存在抛 404 */
    List<HistoryTxVO> listByLot(Long lotId);

    /**
     * 批量：给定 Lot 集合，每批最近 limit 条（时间正序）。
     * 一次 IN + 窗口函数；空入参 → 空列表。不重复校验 Lot 存在。
     * 调用方（ComplaintPackageAssembler）按需截更短的尾端。
     */
    List<HistoryTxVO> listByLots(Collection<Long> lotIds, int limit);

    /** 调查台分页：默认新→旧；lotId、eqpId 至少要一个 */
    PageResult<HistoryTxVO> query(HistoryQuery query);

    /** 抽屉单行；没有就 404 */
    HistoryTxVO getByTxId(Long txId);

    /**
     * 统计某事务类型每日数量
     */
    List<HistoryDailyCountVO> countDailyByTxType(String txType, LocalDate from, LocalDate toInclusive);

    /**
     * 统计某事务类型按工序分组的数量
     * step_id 为空归入 stepId=null 桶
     */
    List<HistoryStepCountVO> countByStepAndTxType(String txType, LocalDate from, LocalDate toInclusive);
}
