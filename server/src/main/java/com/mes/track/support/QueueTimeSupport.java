package com.mes.track.support;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mes.alarm.service.AlarmService;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.route.entity.MesRouteEdge;
import com.mes.route.mapper.MesRouteEdgeMapper;
import com.mes.route.support.RouteEdgeTypes;
import com.mes.route.support.RouteTrackOutDecision;
import com.mes.track.entity.MesTxLog;
import com.mes.track.mapper.MesTxLogMapper;
import com.mes.track.vo.TrackQueueTimeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Queue Time：TrackOut 开窗 / TrackIn 结算 / context。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueTimeSupport {

    public static final String HOLD = "HOLD";
    public static final String ALARM = "ALARM";
    public static final String HOLD_ALARM = "HOLD_ALARM";
    public static final String REASON_QTIME_EXCEED = "QTIME_EXCEED";
    public static final String TX_QTIME_SUPERSEDED = "QTIME_SUPERSEDED";
    public static final String TX_QTIME_CLEARED = "QTIME_CLEARED";

    private final MesLotMapper mesLotMapper;
    private final MesRouteEdgeMapper mesRouteEdgeMapper;
    private final MesTxLogMapper mesTxLogMapper;
    private final AlarmService alarmService;
    private final ObjectProvider<QueueTimeExpireHandler> expireHandlerProvider;

    @Value("${mes.qtime.enabled:true}")
    private boolean enabled;

    @Value("${mes.qtime.default-on-violate:HOLD}")
    private String defaultOnViolate;

    @Value("${mes.qtime.alarm-only-block:true}")
    private boolean alarmOnlyBlock;

    @Value("${mes.qtime.fail-open:true}")
    private boolean failOpen;

    /** Queue Time 总开关是否开启 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 仅清空内存中 Lot 的开窗字段，不落库（updateById 默认不写 null） */
    public void clear(MesLot lot) {
        lot.setQtimeFromSort(null);
        lot.setQtimeToSort(null);
        lot.setQtimeStartedAt(null);
        lot.setQtimeMaxMin(null);
        lot.setQtimeOnViolate(null);
    }

    /** 清空内存并 Wrapper 落库清窗，同步刷新乐观锁 version */
    public void clearPersisted(MesLot lot) {
        clear(lot);
        if (lot.getId() == null) {
            return;
        }
        mesLotMapper.update(null, new LambdaUpdateWrapper<MesLot>()
                .eq(MesLot::getId, lot.getId())
                .set(MesLot::getQtimeFromSort, null)
                .set(MesLot::getQtimeToSort, null)
                .set(MesLot::getQtimeStartedAt, null)
                .set(MesLot::getQtimeMaxMin, null)
                .set(MesLot::getQtimeOnViolate, null));
        MesLot fresh = mesLotMapper.selectById(lot.getId());
        if (fresh != null) {
            lot.setVersion(fresh.getVersion());
        }
    }

    /**
     * 是否存在活跃开窗（站间等待计时中）。
     * 条件：目标站、开始时刻、上限分钟均非空。
     */
    public boolean hasWindow(MesLot lot) {
        return lot.getQtimeToSort() != null && lot.getQtimeStartedAt() != null && lot.getQtimeMaxMin() != null;
    }

    /** 开窗已超过固化上限 */
    public boolean isExpired(MesLot lot) {
        return hasWindow(lot) && elapsedMin(lot) > lot.getQtimeMaxMin();
    }

    public long elapsedMin(MesLot lot) {
        if (lot.getQtimeStartedAt() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(lot.getQtimeStartedAt(), LocalDateTime.now()).toMinutes());
    }

    /** 组装现场台 context 的 queueTime；无开窗返回 null */
    public TrackQueueTimeVO toContextVo(MesLot lot) {
        if (!hasWindow(lot)) {
            return null;
        }
        long elapsed = elapsedMin(lot);
        long remain = lot.getQtimeMaxMin() - elapsed;
        TrackQueueTimeVO vo = new TrackQueueTimeVO();
        vo.setFromSortNo(lot.getQtimeFromSort());
        vo.setToSortNo(lot.getQtimeToSort());
        vo.setStartedAt(lot.getQtimeStartedAt());
        vo.setMaxQueueMin(lot.getQtimeMaxMin());
        vo.setElapsedMin(elapsed);
        vo.setRemainMin(remain);
        vo.setOnViolate(lot.getQtimeOnViolate());
        vo.setViolated(isExpired(lot));
        return vo;
    }

    /**
     * TrackOut 成功后开窗：按走出边 / time_link 写入 Lot 计时。
     * 中间站无新约束时保留未结算开窗（跨站 time_link 全程可见倒计时）。
     * 开窗失败时 fail-open=true 不回滚过站，仅告警。
     */
    public void openAfterTrackOut(MesLot lot, Integer fromSortNo, RouteTrackOutDecision decision) {
        if (!enabled || decision == null || decision.isCompleted() || fromSortNo == null) {
            if (enabled && (decision == null || decision.isCompleted())) {
                clearPersisted(lot);
            }
            return;
        }
        try {
            MesRouteEdge constraint = resolveConstraint(lot.getRouteVersionId(), fromSortNo, decision.getEdge());
            if (constraint == null || constraint.getMaxQueueMin() == null) {
                // 无新约束：保留进行中的跨站开窗，勿在中间站清掉
                return;
            }
            if (hasWindow(lot)) {
                writeMetaTx(lot, TX_QTIME_SUPERSEDED, fromSortNo, lot.getQtimeToSort(), "QueueTime 开窗被覆盖");
            }
            String policy = resolvePolicy(constraint.getOnViolate());
            lot.setQtimeFromSort(fromSortNo);
            lot.setQtimeToSort(constraint.getToSortNo());
            lot.setQtimeStartedAt(LocalDateTime.now());
            lot.setQtimeMaxMin(constraint.getMaxQueueMin());
            lot.setQtimeOnViolate(policy);
            int rows = mesLotMapper.updateById(lot);
            AssertUtil.isTrue(rows > 0, "QueueTime 开窗写入失败");
        } catch (Exception ex) {
            log.error("QueueTime 开窗失败 lotId={} fromSort={}", lot.getId(), fromSortNo, ex);
            if (!failOpen) {
                throw ex instanceof RuntimeException re ? re : new BusinessException("QueueTime 开窗失败");
            }
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("lotId", lot.getId());
                payload.put("fromSortNo", fromSortNo);
                alarmService.raise("QTIME_OPEN_FAIL", "QueueTime 开窗失败", payload);
            } catch (Exception ignore) {
                // ignore
            }
        }
    }

    /**
     * TrackIn 前结算开窗：未超时则目标站关窗放行；超时 HOLD 走独立事务锁批清窗后拒进站。
     */
    public void assertAndSettleOnTrackIn(MesLot lot) {
        if (!enabled || !hasWindow(lot)) {
            return;
        }
        if (isExpired(lot)) {
            String policy = resolvePolicy(lot.getQtimeOnViolate());
            boolean needAlarm = ALARM.equals(policy) || HOLD_ALARM.equals(policy);
            boolean needHold = HOLD.equals(policy) || HOLD_ALARM.equals(policy);
            boolean block = needHold || (ALARM.equals(policy) && alarmOnlyBlock);
            if (needHold) {
                expireHandlerProvider.getObject().enforceIfExpired(lot.getId());
                MesLot fresh = mesLotMapper.selectById(lot.getId());
                if (fresh != null) {
                    lot.setStatus(fresh.getStatus());
                    lot.setVersion(fresh.getVersion());
                    lot.setQtimeFromSort(fresh.getQtimeFromSort());
                    lot.setQtimeToSort(fresh.getQtimeToSort());
                    lot.setQtimeStartedAt(fresh.getQtimeStartedAt());
                    lot.setQtimeMaxMin(fresh.getQtimeMaxMin());
                    lot.setQtimeOnViolate(fresh.getQtimeOnViolate());
                }
            } else if (needAlarm) {
                long elapsed = elapsedMin(lot);
                Map<String, Object> payload = new HashMap<>();
                payload.put("lotId", lot.getId());
                payload.put("lotNo", lot.getLotNo());
                payload.put("fromSortNo", lot.getQtimeFromSort());
                payload.put("toSortNo", lot.getQtimeToSort());
                payload.put("elapsedMin", elapsed);
                payload.put("maxMin", lot.getQtimeMaxMin());
                alarmService.raise(REASON_QTIME_EXCEED,
                        "Queue Time 超时: " + elapsed + "/" + lot.getQtimeMaxMin() + " 分钟", payload);
            }
            if (block) {
                throw new BusinessException("Queue Time 已超时，禁止开工");
            }
            clearPersisted(lot);
            return;
        }
        if (!lot.getQtimeToSort().equals(lot.getCurrentSortNo())) {
            return;
        }
        clearPersisted(lot);
    }

    /**
     * Skip 后处理开窗：落到目标站则按 TrackIn 规则结算；跳过/越过目标站则清窗并记履历。
     */
    public void onSkip(MesLot lot, Integer fromSortNo, Integer toSortNo, List<Integer> skipped) {
        if (!enabled || !hasWindow(lot)) {
            return;
        }
        Integer target = lot.getQtimeToSort();
        if (target.equals(toSortNo)) {
            lot.setCurrentSortNo(toSortNo);
            assertAndSettleOnTrackIn(lot);
            return;
        }
        boolean skippedTarget = skipped != null && skipped.contains(target);
        boolean jumpedOver = fromSortNo != null && target != null && toSortNo != null
                && ((fromSortNo < target && target < toSortNo) || (fromSortNo > target && target > toSortNo));
        if (skippedTarget || jumpedOver) {
            writeMetaTx(lot, TX_QTIME_CLEARED, lot.getQtimeFromSort(), target, "Skip 清除 QueueTime");
            clearPersisted(lot);
        }
    }

    /** 清窗并写 QTIME_CLEARED 履历（如 Rework）；无窗也强制落库清空 */
    public void clearWithLog(MesLot lot, String remark) {
        if (!hasWindow(lot)) {
            clearPersisted(lot);
            return;
        }
        writeMetaTx(lot, TX_QTIME_CLEARED, lot.getQtimeFromSort(), lot.getQtimeToSort(), remark);
        clearPersisted(lot);
    }

    /**
     * 解析开窗约束：优先取本笔 TrackOut 导航边的 maxQueueMin；否则取 from 站 time_link 中上限最小者。
     */
    public MesRouteEdge resolveConstraint(Long versionId, Integer fromSortNo, MesRouteEdge takenEdge) {
        if (versionId == null || fromSortNo == null) {
            return null;
        }
        if (takenEdge != null && takenEdge.getMaxQueueMin() != null && takenEdge.getMaxQueueMin() >= 1) {
            return takenEdge;
        }
        List<MesRouteEdge> links = mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId)
                .eq(MesRouteEdge::getFromSortNo, fromSortNo)
                .eq(MesRouteEdge::getEdgeType, RouteEdgeTypes.TIME_LINK)
                .isNotNull(MesRouteEdge::getMaxQueueMin)
                .orderByAsc(MesRouteEdge::getMaxQueueMin));
        return links.stream()
                .filter(e -> e.getMaxQueueMin() != null && e.getMaxQueueMin() >= 1)
                .min(Comparator.comparing(MesRouteEdge::getMaxQueueMin))
                .orElse(null);
    }

    /** 归一超时策略：边配置优先，空则用 mes.qtime.default-on-violate */
    public String resolvePolicy(String onViolate) {
        if (StringUtils.hasText(onViolate)) {
            String p = onViolate.trim().toUpperCase();
            AssertUtil.isTrue(HOLD.equals(p) || ALARM.equals(p) || HOLD_ALARM.equals(p),
                    "非法 onViolate: " + onViolate);
            return p;
        }
        String d = defaultOnViolate == null ? HOLD : defaultOnViolate.trim().toUpperCase();
        if (!HOLD.equals(d) && !ALARM.equals(d) && !HOLD_ALARM.equals(d)) {
            return HOLD;
        }
        return d;
    }

    /** 写 Queue Time 元事务履历（覆盖/清除等，不改变 Lot 站位状态） */
    private void writeMetaTx(MesLot lot, String txType, Integer fromSort, Integer toSort, String remark) {
        JSONObject ext = new JSONObject();
        if (fromSort != null) {
            ext.set("fromSortNo", fromSort);
        }
        if (toSort != null) {
            ext.set("toSortNo", toSort);
        }
        if (lot.getQtimeMaxMin() != null) {
            ext.set("maxQueueMin", lot.getQtimeMaxMin());
        }
        MesTxLog row = new MesTxLog();
        row.setLotId(lot.getId());
        row.setLotNo(lot.getLotNo());
        row.setTxType(txType);
        row.setFromStatus(lot.getStatus());
        row.setToStatus(lot.getStatus());
        row.setFromSortNo(fromSort);
        row.setToSortNo(toSort);
        row.setStepId(lot.getCurrentStepId());
        row.setEqpId(lot.getCurrentEqpId());
        row.setRouteVersionId(lot.getRouteVersionId());
        row.setRemark(remark);
        row.setExtJson(ext.toString());
        try {
            row.setOperUserId(StpUtil.getLoginIdAsLong());
        } catch (Exception ignore) {
            // 无登录上下文
        }
        row.setCreateTime(LocalDateTime.now());
        mesTxLogMapper.insert(row);
    }
}
