package com.mes.hold.service;

import com.mes.common.PageResult;
import com.mes.hold.dto.MesHoldCreateDTO;
import com.mes.hold.dto.MesHoldQuery;
import com.mes.hold.entity.MesHold;
import com.mes.hold.vo.MesHoldVO;

import java.util.List;

/** 锁批业务 */
public interface HoldService {

    /** 分页；默认查 active */
    PageResult<MesHoldVO> page(MesHoldQuery query);

    MesHoldVO get(Long id);

    /** 某批 Hold 历史（含已解锁） */
    List<MesHoldVO> listByLot(Long lotId);

    /** 发起锁批 */
    MesHoldVO create(MesHoldCreateDTO dto);

    /** 解锁 */
    MesHoldVO release(Long id, String remark);

    /** 是否存在 active Hold */
    boolean hasActive(Long lotId);

    /** 查当前 active Hold，无则 null */
    MesHold findActive(Long lotId);

    /** Track 钩子：有 active 则抛「批次已锁批：{原因}」 */
    void assertNoActive(Long lotId);
}
