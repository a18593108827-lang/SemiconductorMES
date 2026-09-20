package com.mes.alarm.service.impl;

import com.mes.hold.dto.MesHoldCreateDTO;
import com.mes.hold.service.HoldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Alarm → Hold：独立事务挂锁，失败只回滚本事务，不连带 mes_alarm OPEN。
 * 异步执行：raise 常在 TrackOut 未提交时调用，同步 Hold 会跟 Lot 行锁互相等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmHoldOnRaiseExecutor {

    private final HoldService holdService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void holdLotAsync(Long lotId, String reasonCode, String remark) {
        try {
            if (holdService.hasActive(lotId)) {
                log.info("[ALARM] HOLD_LOT 跳过：批次已有生效锁批 lotId={}", lotId);
                return;
            }
            MesHoldCreateDTO dto = new MesHoldCreateDTO();
            dto.setLotId(lotId);
            dto.setReasonCode(reasonCode);
            dto.setRemark(remark);
            holdService.create(dto);
        } catch (Exception e) {
            log.error("[ALARM] HOLD_LOT 失败 lotId={} reasonCode={}", lotId, reasonCode, e);
        }
    }
}
