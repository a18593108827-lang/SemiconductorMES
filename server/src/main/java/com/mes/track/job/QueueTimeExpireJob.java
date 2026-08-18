package com.mes.track.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.track.support.QueueTimeExpireHandler;
import com.mes.track.support.QueueTimeSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 扫描已开窗且到期的 Lot，自动 Hold（ALARM 策略跳过）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueTimeExpireJob {

    private final QueueTimeSupport queueTimeSupport;
    private final QueueTimeExpireHandler expireHandler;
    private final MesLotMapper mesLotMapper;

    @Scheduled(fixedDelayString = "${mes.qtime.scan-ms:30000}")
    public void scanExpired() {
        if (!queueTimeSupport.isEnabled()) {
            return;
        }
        List<MesLot> rows = mesLotMapper.selectList(new LambdaQueryWrapper<MesLot>()
                .isNotNull(MesLot::getQtimeToSort)
                .isNotNull(MesLot::getQtimeStartedAt)
                .isNotNull(MesLot::getQtimeMaxMin)
                .in(MesLot::getStatus, "wait", "processing"));
        for (MesLot lot : rows) {
            try {
                if (!queueTimeSupport.isExpired(lot)) {
                    continue;
                }
                String policy = queueTimeSupport.resolvePolicy(lot.getQtimeOnViolate());
                if (QueueTimeSupport.ALARM.equals(policy)) {
                    continue;
                }
                expireHandler.enforceIfExpired(lot.getId());
            } catch (Exception ex) {
                log.error("QueueTime 到期扫描失败 lotId={}", lot.getId(), ex);
            }
        }
    }
}
