package com.mes.alarm.facade;

import com.mes.alarm.dto.AlarmCodeUpdateDTO;
import com.mes.alarm.dto.AlarmQuery;
import com.mes.alarm.vo.AlarmCodeVO;
import com.mes.alarm.vo.AlarmVO;
import com.mes.common.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * 告警查询与人工处置（确认 / 关闭）；码表维护。
 * raise 仍走 AlarmService；本门面不鉴权，鉴权在 Controller。
 */
public interface AlarmFacade {

    /** 分页列表；可按状态 / 级别 / 码 / 时间筛 */
    PageResult<AlarmVO> list(AlarmQuery query);

    /** 详情；没有抛业务异常 */
    AlarmVO get(Long id);

    /** OPEN → ACK */
    AlarmVO ack(Long id, String remark);

    /** OPEN 或 ACK → CLEARED */
    AlarmVO clear(Long id, String remark);

    /** 顶栏：未关闭的严重告警（OPEN/ACK + CRITICAL） */
    List<AlarmVO> listActiveCritical();

    /** 未关闭数：OPEN + ACK（看板 KPI） */
    long countUncleared();

    /** 最近未关闭，按 lastRaiseAt 倒序（看板报警流） */
    List<AlarmVO> listUncleared(int limit);

    /** 告警码表全量（含停用） */
    List<AlarmCodeVO> listCodes();

    /** 更新码表行；不改 code 主键 */
    AlarmCodeVO updateCode(String code, AlarmCodeUpdateDTO dto);

    /**
     * 派工 Lot 闸：该批次是否存在未关闭 CRITICAL（OPEN/ACK）。
     * 非法 lotId 视为 false。
     */
    boolean hasBlockingCriticalForLot(Long lotId);

    /**
     * 派工机台闸：该设备是否存在未关闭 CRITICAL。
     * 非法 eqpId 视为 false。
     */
    boolean hasBlockingCriticalForEqp(Long eqpId);

    /**
     * 派工候选过滤：在给定设备集合中，返回挂有未关闭 CRITICAL 的 eqpId。
     * 空入参返回空集；一次 IN 查询，避免 N+1。
     */
    Set<Long> listEqpIdsWithBlockingCritical(Collection<Long> eqpIds);

    /**
     * 派工拒绝文案：该批次上最近一条挡派 CRITICAL；没有则 null
     */
    AlarmVO findFirstBlockingCriticalForLot(Long lotId);

    /**
     * 给定 Lot 集合上未关闭告警条数（OPEN+ACK 且 entity=LOT）
     */
    long countUnclearedForLots(Collection<Long> lotIds);

    /**
     * 查找 Lot 集合上未关闭告警列表（OPEN+ACK 且 entity=LOT）
     */
    List<AlarmVO> listUnclearedForLots(Collection<Long> lotIds);
}
