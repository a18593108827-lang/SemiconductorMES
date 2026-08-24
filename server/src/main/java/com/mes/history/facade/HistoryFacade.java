package com.mes.history.facade;

import com.mes.common.PageResult;
import com.mes.history.dto.HistoryQuery;
import com.mes.history.vo.HistoryTxVO;

import java.util.List;

/**
 * 履历对外唯一门面：只回答「这批 / 这台机发生过什么」。
 * 写仍只在 Track；别人禁止直查 mes_tx_log。
 */
public interface HistoryFacade {

    /** 现场侧栏兼容：时间正序，最多最近 500 条；Lot 不存在抛 404 */
    List<HistoryTxVO> listByLot(Long lotId);

    /** 调查台分页：默认新→旧；lotId、eqpId 至少要一个 */
    PageResult<HistoryTxVO> query(HistoryQuery query);

    /** 抽屉单行；没有就 404 */
    HistoryTxVO getByTxId(Long txId);
}
