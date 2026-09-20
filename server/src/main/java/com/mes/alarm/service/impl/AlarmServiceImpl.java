package com.mes.alarm.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.alarm.entity.MesAlarm;
import com.mes.alarm.entity.MesAlarmCode;
import com.mes.alarm.mapper.MesAlarmCodeMapper;
import com.mes.alarm.mapper.MesAlarmMapper;
import com.mes.alarm.service.AlarmService;
import com.mes.alarm.support.AlarmSelfHoldCodes;
import com.mes.alarm.ws.AlarmWsPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * raise：独立小事务落库；失败只打日志，不回滚调用方（采集 / 过站）。
 * P1：新 OPEN 且码表 on_raise=HOLD_LOT 时经 AlarmHoldOnRaiseExecutor 调 Hold（bump 不挂）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmServiceImpl implements AlarmService {

    /** 码表没有或停用时的默认级别 */
    private static final String DEFAULT_LEVEL = "WARNING";
    /** 严重：顶栏要盯 */
    private static final String LEVEL_CRITICAL = "CRITICAL";
    /** 警告：多数业务/质量告警 */
    private static final String LEVEL_WARNING = "WARNING";
    /** 提示：轻量告知，一般不用人立刻处理 */
    private static final String LEVEL_INFO = "INFO";

    private static final String ON_RAISE_HOLD_LOT = "HOLD_LOT";

    private final MesAlarmMapper mesAlarmMapper;
    private final MesAlarmCodeMapper mesAlarmCodeMapper;
    private final AlarmWsPublisher alarmWsPublisher;
    private final AlarmHoldOnRaiseExecutor alarmHoldOnRaiseExecutor;

    @Value("${mes.alarm.enabled:true}")
    private boolean enabled;

    @Value("${mes.alarm.hold-on-raise-enabled:true}")
    private boolean holdOnRaiseEnabled;

    /**
     * 对外入口：新开事务写库；里边抛错也吃掉，别把采集/过站带崩。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void raise(String code, String message, Map<String, Object> payload) {
        try {
            doRaise(code, message, payload);
        } catch (Exception e) {
            log.error("[ALARM] raise 失败 code={} message={} payload={}", code, message, payload, e);
        }
    }

    /**
     * 真正落库：关阀就退；有未关的同键 OPEN 就累加次数，否则新建一条。
     * 写成功后登记 WS，等事务提交再推。
     */
    private void doRaise(String code, String message, Map<String, Object> payload) {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(code)) {
            log.warn("[ALARM] code 为空，丢弃 message={} payload={}", message, payload);
            return;
        }
        String alarmCode = code.trim();

        MesAlarmCode def = mesAlarmCodeMapper.selectById(alarmCode);
        String level = DEFAULT_LEVEL;
        boolean codeActive = def != null && def.getEnabled() != null && def.getEnabled() == 1;
        if (!codeActive) {
            log.warn("[ALARM] 码表无或已停用 code={}，仍按 {} 落库", alarmCode, DEFAULT_LEVEL);
        } else {
            level = normalizeLevel(def.getLevel());
        }

        EntityRef entity = resolveEntity(payload);
        String dedupeKey = alarmCode + "|" + entity.type + "|" + entity.id;
        LocalDateTime now = LocalDateTime.now();
        String payloadJson = payload == null || payload.isEmpty() ? null : JSONUtil.toJsonStr(payload);

        MesAlarm open = mesAlarmMapper.selectOne(new LambdaQueryWrapper<MesAlarm>()
                .eq(MesAlarm::getDedupeKey, dedupeKey)
                .eq(MesAlarm::getStatus, MesAlarm.STATUS_OPEN)
                .last("FOR UPDATE"));

        if (open != null) {
            open.setRaiseCount((open.getRaiseCount() == null ? 1 : open.getRaiseCount()) + 1);
            open.setLastRaiseAt(now);
            open.setMessage(message);
            open.setPayloadJson(payloadJson);
            open.setLevel(level);
            mesAlarmMapper.updateById(open);
            log.warn("[ALARM] bump code={} id={} count={} message={} payload={}",
                    alarmCode, open.getId(), open.getRaiseCount(), message, payload);
            alarmWsPublisher.publishAfterCommit(AlarmWsPublisher.ACTION_BUMP, open);
            return;
        }

        MesAlarm row = new MesAlarm();
        row.setCode(alarmCode);
        row.setLevel(level);
        row.setStatus(MesAlarm.STATUS_OPEN);
        row.setMessage(message);
        row.setEntityType(entity.type);
        row.setEntityId(entity.id);
        row.setDedupeKey(dedupeKey);
        row.setPayloadJson(payloadJson);
        row.setRaiseCount(1);
        row.setFirstRaiseAt(now);
        row.setLastRaiseAt(now);
        mesAlarmMapper.insert(row);
        log.warn("[ALARM] open code={} id={} entity={}/{} message={} payload={}",
                alarmCode, row.getId(), entity.type, entity.id, message, payload);
        alarmWsPublisher.publishAfterCommit(AlarmWsPublisher.ACTION_OPEN, row);
        maybeHoldOnRaise(codeActive ? def : null, row);
    }

    /**
     * 仅新 OPEN：码表 HOLD_LOT + Lot 实体 → 独立事务挂锁；失败不影响本告警行。
     */
    private void maybeHoldOnRaise(MesAlarmCode def, MesAlarm row) {
        if (!holdOnRaiseEnabled) {
            return;
        }
        if (def == null) {
            return;
        }
        String onRaise = def.getOnRaise();
        if (!StringUtils.hasText(onRaise) || !ON_RAISE_HOLD_LOT.equalsIgnoreCase(onRaise.trim())) {
            return;
        }
        if (AlarmSelfHoldCodes.isSelfHold(row.getCode())) {
            log.warn("[ALARM] HOLD_LOT 跳过：告警码已由业务自挂锁批 code={} alarmId={}",
                    row.getCode(), row.getId());
            return;
        }
        if (!MesAlarm.ENTITY_LOT.equals(row.getEntityType())
                || row.getEntityId() == null
                || row.getEntityId() <= 0) {
            log.warn("[ALARM] HOLD_LOT 跳过：实体非 Lot alarmId={} entity={}/{}",
                    row.getId(), row.getEntityType(), row.getEntityId());
            return;
        }
        if (!StringUtils.hasText(def.getHoldReasonCode())) {
            log.warn("[ALARM] HOLD_LOT 跳过：未配置锁批原因码 alarmId={} code={}",
                    row.getId(), row.getCode());
            return;
        }
        String reasonCode = def.getHoldReasonCode().trim();
        String remark = "告警策略锁批 alarmId=" + row.getId() + " code=" + row.getCode();
        Long lotId = row.getEntityId();
        Runnable job = () -> alarmHoldOnRaiseExecutor.holdLotAsync(lotId, reasonCode, remark);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    job.run();
                }
            });
        } else {
            job.run();
        }
    }

    /**
     * 从 payload 看出告警挂在谁身上：批次 > 机台 > SPC 图；都没有就 NONE/0。
     */
    private static EntityRef resolveEntity(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return new EntityRef(MesAlarm.ENTITY_NONE, 0L);
        }
        Long lotId = toLong(payload.get("lotId"));
        if (lotId != null && lotId > 0) {
            return new EntityRef(MesAlarm.ENTITY_LOT, lotId);
        }
        Long eqpId = toLong(payload.get("eqpId"));
        if (eqpId != null && eqpId > 0) {
            return new EntityRef(MesAlarm.ENTITY_EQP, eqpId);
        }
        Long chartId = toLong(payload.get("chartId"));
        if (chartId != null && chartId > 0) {
            return new EntityRef(MesAlarm.ENTITY_CHART, chartId);
        }
        return new EntityRef(MesAlarm.ENTITY_NONE, 0L);
    }

    /** payload 里的 id 可能是 Long / Integer / 字符串，统一收成 Long */
    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) {
                return null;
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 级别只认三种；乱写就按警告兜底 */
    private static String normalizeLevel(String level) {
        if (!StringUtils.hasText(level)) {
            return DEFAULT_LEVEL;
        }
        String u = level.trim().toUpperCase();
        if (LEVEL_CRITICAL.equals(u) || LEVEL_WARNING.equals(u) || LEVEL_INFO.equals(u)) {
            return u;
        }
        return DEFAULT_LEVEL;
    }

    /** 挂载对象：类型 + 主键 */
    private record EntityRef(String type, long id) {
    }
}
