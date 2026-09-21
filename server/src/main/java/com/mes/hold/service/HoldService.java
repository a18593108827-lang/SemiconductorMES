package com.mes.hold.service;

import com.mes.common.PageResult;
import com.mes.hold.dto.MesHoldCreateDTO;
import com.mes.hold.dto.MesHoldQuery;
import com.mes.hold.entity.MesHold;
import com.mes.hold.vo.HoldReasonAggVO;
import com.mes.hold.vo.MesHoldVO;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** 锁批业务 */
public interface HoldService {

    /** 分页；默认查 active */
    PageResult<MesHoldVO> page(MesHoldQuery query);

    /**
     * 活跃锁批条数：与 page 默认口径一致（status=active），只 COUNT，不组装列表。
     */
    long countActive();

    /**
     * 报表：hold_time 落在 [from, to]（含）内按原因聚合。
     * 含已释放；时长用 release_time，仍 active 则用当前时刻。
     */
    List<HoldReasonAggVO> summarizeByReason(LocalDate from, LocalDate toInclusive);

    MesHoldVO get(Long id);

    /** 某批 Hold 历史（含已解锁） */
    List<MesHoldVO> listByLot(Long lotId);

    /**
     * 给定 Lot 集合的 Hold 历史（含已解锁），一次 IN；按 hold_time 倒序。
     * 空入参 → 空列表。不重复校验 Lot 存在（调用方已校验）。
     */
    List<MesHoldVO> listByLots(Collection<Long> lotIds);

    /** contain skip 判据；改文案须同步客诉包 CP-5 */
    String MSG_ALREADY_HELD = "该批次已存在生效中的锁批";

    /** 发起锁批 */
    MesHoldVO create(MesHoldCreateDTO dto);

    /** 校验原因码是否存在且启用 */
    void assertReasonUsable(String code);

    /** 解锁 */
    MesHoldVO release(Long id, String remark);

    /** 是否存在 active Hold */
    boolean hasActive(Long lotId);

    /** 查当前 active Hold，无则 null */
    MesHold findActive(Long lotId);

    /** Track 钩子：有 active 则抛「批次已锁批：{原因}」 */
    void assertNoActive(Long lotId);
}
