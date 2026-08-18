package com.mes.track.support;

import com.mes.alarm.service.AlarmService;
import com.mes.hold.dto.MesHoldCreateDTO;
import com.mes.hold.service.HoldService;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Queue Time 超时处置：独立事务 Hold + 清窗，避免被 TrackIn 回滚。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueTimeExpireHandler {

    private final QueueTimeSupport queueTimeSupport;
    private final HoldService holdService;
    private final AlarmService alarmService;
    private final MesLotMapper mesLotMapper;

    /**
     * HOLD / HOLD_ALARM 到期：告警（若需要）、锁批、清窗。
     * ALARM 不处理。已有 active Hold 则只清窗。
     *
     * @return 是否已按 HOLD 策略处置
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean enforceIfExpired(Long lotId) {
        if (lotId == null) {
            return false;
        }
        MesLot lot = mesLotMapper.selectById(lotId);
        if (lot == null || !queueTimeSupport.isEnabled() || !queueTimeSupport.hasWindow(lot)
                || !queueTimeSupport.isExpired(lot)) {
            return false;
        }
        String policy = queueTimeSupport.resolvePolicy(lot.getQtimeOnViolate());
        boolean needHold = QueueTimeSupport.HOLD.equals(policy) || QueueTimeSupport.HOLD_ALARM.equals(policy);
        if (!needHold) {
            return false;
        }
        long elapsed = queueTimeSupport.elapsedMin(lot);
        Map<String, Object> payload = new HashMap<>();
        payload.put("lotId", lot.getId());
        payload.put("lotNo", lot.getLotNo());
        payload.put("fromSortNo", lot.getQtimeFromSort());
        payload.put("toSortNo", lot.getQtimeToSort());
        payload.put("elapsedMin", elapsed);
        payload.put("maxMin", lot.getQtimeMaxMin());
        alarmService.raise(QueueTimeSupport.REASON_QTIME_EXCEED,
                "Queue Time 超时: " + elapsed + "/" + lot.getQtimeMaxMin() + " 分钟", payload);
        if (!holdService.hasActive(lot.getId())) {
            MesHoldCreateDTO dto = new MesHoldCreateDTO();
            dto.setLotId(lot.getId());
            dto.setReasonCode(QueueTimeSupport.REASON_QTIME_EXCEED);
            dto.setRemark(String.format("QTime %d→%d 超时 elapsed=%d max=%d",
                    lot.getQtimeFromSort(), lot.getQtimeToSort(), elapsed, lot.getQtimeMaxMin()));
            try {
                holdService.create(dto);
            } catch (Exception ex) {
                if (holdService.hasActive(lot.getId())) {
                    log.warn("QueueTime 锁批并发 lotId={}", lot.getId());
                } else {
                    throw ex;
                }
            }
        }
        MesLot fresh = mesLotMapper.selectById(lot.getId());
        if (fresh != null) {
            queueTimeSupport.clearPersisted(fresh);
        }
        return true;
    }
}
