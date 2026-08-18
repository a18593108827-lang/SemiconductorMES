package com.mes.hold.service;

import com.mes.common.PageResult;
import com.mes.hold.dto.MesFutureHoldCreateDTO;
import com.mes.hold.dto.MesFutureHoldQuery;
import com.mes.hold.vo.MesFutureHoldVO;
import com.mes.lot.entity.MesLot;

import java.util.List;

/** 预约锁批：Set / Cancel / 到站激活；激活复用 HoldService.create */
public interface FutureHoldService {

    /** 分页；默认查 pending */
    PageResult<MesFutureHoldVO> page(MesFutureHoldQuery query);

    MesFutureHoldVO get(Long id);

    /** 某批全部预约（含 activated / cancelled） */
    List<MesFutureHoldVO> listByLot(Long lotId);

    /** 某批未生效预约（现场台 context） */
    List<MesFutureHoldVO> listPendingByLot(Long lotId);

    /** 设置预约；写 FUTURE_HOLD_SET 履历 */
    MesFutureHoldVO create(MesFutureHoldCreateDTO dto);

    /** 取消 pending；写 FUTURE_HOLD_CANCEL 履历 */
    MesFutureHoldVO cancel(Long id, String remark);

    /**
     * Track 钩子：命中 pending（同 version + sort + timing）则激活为 active Hold。
     * 激活走 HoldService.create；已有 active 则失败当前事务。
     *
     * @param timing PRE=进站前 / POST=出站后
     * @return 是否激活了一条
     */
    boolean tryActivate(MesLot lot, Integer sortNo, String timing);
}
