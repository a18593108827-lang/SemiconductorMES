package com.mes.dispatch.service;

import com.mes.dispatch.dto.DispatchReserveCreateDTO;
import com.mes.dispatch.vo.DispatchCandidatesVO;
import com.mes.dispatch.vo.DispatchReserveVO;

import java.util.List;

public interface DispatchService {

    /** 按 Lot 当前站过滤排序，返回候选机 + 推荐机 */
    DispatchCandidatesVO listCandidates(Long lotId);

    /** 预约设备；默认超时 30 分钟 */
    DispatchReserveVO reserve(DispatchReserveCreateDTO dto);

    /** 人工释约 */
    DispatchReserveVO release(Long id, String remark);

    /** 查预约；lotId / eqpId 至少传一个；默认只看 active（含惰性过期） */
    List<DispatchReserveVO> listReserves(Long lotId, Long eqpId, String status);

    /**
     * 开工前校验
     * 该批没有有效预约 → 放行（不强制预约）
     * 有有效预约 → 必须用预约的那台机，否则报错
     */
    void assertReserveMatch(Long lotId, Long eqpId);

    /**
     *  开工成功后
     * 有匹配的有效预约 → 改成 consumed，记下 consumeTxId
     * 没有 / 机台对不上 → 什么都不做
     */
    void consumeOnTrackIn(Long lotId, Long eqpId, Long txLogId);

    /**
     * 设备是否被其他批次 Off-Flow 锚点占用（processing 进旁路后逻辑占台）。
     * excludeLotId 为当前批时可排除自身。
     */
    void assertNotOffFlowAnchored(Long eqpId, Long excludeLotId);

    /**
     * Abort 时：若这批还挂着有效预约（正常开工会已消费，一般碰不到），顺手释掉，别假占机台。
     *
     * @return 被释约的预约 id；没有 active 则 null
     */
    Long releaseActiveOnAbort(Long lotId, Long abortTxId);
}
