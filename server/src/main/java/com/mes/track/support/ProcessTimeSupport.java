package com.mes.track.support;

import cn.hutool.json.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mes.alarm.service.AlarmService;
import com.mes.common.BusinessException;
import com.mes.hold.dto.MesHoldCreateDTO;
import com.mes.hold.service.HoldService;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.route.entity.MesRouteStep;
import com.mes.track.vo.TrackProcessTimeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 站内加工时长（Process Time）。
 * <p>
 * 人话：开工开始计时，完工看够不够久。<br>
 * 太短 → 不让出站；太长 → 先让出站腾机台，再自动锁批让人处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessTimeSupport {

    public static final String ERR_TOO_SHORT = "PROCESS_TIME_TOO_SHORT";
    public static final String ERR_CLOCK_MISSING = "PROCESS_TIME_CLOCK_MISSING";
    public static final String ALARM_CODE = "PROCESS_TIME_VIOLATION";
    /** Hold 原因码：加工超时 */
    public static final String REASON_PROCESS_TIME_EXCEED = "PROCESS_TIME_EXCEED";

    private final MesLotMapper mesLotMapper;
    private final AlarmService alarmService;
    private final HoldService holdService;

    @Value("${mes.process-time.enabled:true}")
    private boolean enabled;

    /** 加工超时要不要顺带推一条告警（末站已完工没法锁批时，一定会告警） */
    @Value("${mes.process-time.alarm-on-max-exceed:true}")
    private boolean alarmOnMaxExceed;

    public boolean isEnabled() {
        return enabled;
    }

    /** 这站有没有配最短/最长加工时间（有一边就不空） */
    public boolean isConstrained(MesRouteStep step) {
        return step != null && (step.getMinProcessMin() != null || step.getMaxProcessMin() != null);
    }

    /** 只清内存里的开表时间，不写库 */
    public void clear(MesLot lot) {
        lot.setProcessStartedAt(null);
    }

    /**
     * 把开表时间从库里清掉。
     * updateById 写不进 null，所以必须单独 SET NULL；顺带刷新乐观锁 version。
     */
    public void clearPersisted(MesLot lot) {
        clear(lot);
        if (lot.getId() == null) {
            return;
        }
        mesLotMapper.update(null, new LambdaUpdateWrapper<MesLot>()
                .eq(MesLot::getId, lot.getId())
                .set(MesLot::getProcessStartedAt, null));
        MesLot fresh = mesLotMapper.selectById(lot.getId());
        if (fresh != null) {
            lot.setVersion(fresh.getVersion());
        }
    }

    /**
     * 开工时：本站配了时长就按下秒表；没配就不计时。
     * 只改内存，后面跟 TrackIn 一起 updateById。
     */
    public void startOnTrackIn(MesLot lot, MesRouteStep step) {
        if (!enabled || !isConstrained(step)) {
            clear(lot);
            return;
        }
        lot.setProcessStartedAt(LocalDateTime.now());
    }

    /**
     * 完工前检查加工多久了。
     * <ul>
     *   <li>没开表 / 太短 → 直接抛错，不让出站</li>
     *   <li>太长 → 不拦，打个 exceededMax 标记，等出站成功后再锁批</li>
     * </ul>
     */
    public SettleResult assertOnTrackOut(MesLot lot, MesRouteStep step) {
        if (!enabled || !isConstrained(step)) {
            return SettleResult.skipped();
        }
        if (lot.getProcessStartedAt() == null) {
            throw new BusinessException(ERR_CLOCK_MISSING + ": 加工计时缺失，禁止完工");
        }
        long elapsed = Math.max(0, Duration.between(lot.getProcessStartedAt(), LocalDateTime.now()).toMinutes());
        Integer min = step.getMinProcessMin();
        Integer max = step.getMaxProcessMin();
        // 还没到最短加工时间：多半是误点，拦住
        if (min != null && elapsed < min) {
            throw new BusinessException(ERR_TOO_SHORT + ": 加工时长不足 " + elapsed + "/" + min + " 分钟，禁止完工");
        }
        // 超过最长：先让人出站，后面再 Hold
        boolean exceededMax = max != null && elapsed > max;
        return new SettleResult(true, elapsed, min, max, lot.getProcessStartedAt(), exceededMax);
    }

    /** 推一条「加工超时」告警，失败只打日志，不挡过站 */
    private void raiseMaxAlarm(MesLot lot, SettleResult settle) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("lotId", lot.getId());
            payload.put("lotNo", lot.getLotNo());
            payload.put("sortNo", lot.getCurrentSortNo());
            payload.put("elapsedMin", settle.elapsedMin());
            payload.put("maxProcessMin", settle.maxProcessMin());
            payload.put("reason", REASON_PROCESS_TIME_EXCEED);
            alarmService.raise(ALARM_CODE,
                    "Process Time 超时: " + settle.elapsedMin() + "/" + settle.maxProcessMin() + " 分钟",
                    payload);
        } catch (Exception ex) {
            log.warn("Process Time Alarm 失败 lotId={}", lot.getId(), ex);
        }
    }

    /**
     * 出站已经成功之后再调用。
     * 如果刚才加工超时了：能锁批就锁批；末站已完工锁不了就至少告警一声。
     */
    public void disposeAfterTrackOut(MesLot lot, SettleResult settle) {
        if (!enabled || settle == null || !settle.constrained() || !settle.exceededMax()) {
            return;
        }
        String status = lot.getStatus();
        // Hold 只吃 wait / processing；completed 锁不了
        boolean canHold = "wait".equals(status) || "processing".equals(status);
        if (alarmOnMaxExceed || !canHold) {
            raiseMaxAlarm(lot, settle);
        }
        if (!canHold) {
            return;
        }
        if (holdService.hasActive(lot.getId())) {
            MesLot fresh = mesLotMapper.selectById(lot.getId());
            if (fresh != null) {
                lot.setStatus(fresh.getStatus());
                lot.setVersion(fresh.getVersion());
            }
            return;
        }
        try {
            MesHoldCreateDTO dto = new MesHoldCreateDTO();
            dto.setLotId(lot.getId());
            dto.setReasonCode(REASON_PROCESS_TIME_EXCEED);
            dto.setRemark(String.format("Process Time 超时 elapsed=%d max=%d",
                    settle.elapsedMin(), settle.maxProcessMin()));
            holdService.create(dto);
            MesLot fresh = mesLotMapper.selectById(lot.getId());
            if (fresh != null) {
                lot.setStatus(fresh.getStatus());
                lot.setVersion(fresh.getVersion());
            }
        } catch (Exception ex) {
            if (holdService.hasActive(lot.getId())) {
                log.warn("Process Time 超限已有锁批 lotId={}", lot.getId());
                return;
            }
            log.error("Process Time 超限 Hold 失败 lotId={}", lot.getId(), ex);
            throw ex instanceof RuntimeException re ? re : new BusinessException("Process Time 超限锁批失败");
        }
    }

    /**
     * 给现场台用的倒计时信息。
     * canTrackOutByTime：够不够最短时间（超时仍为 true，因为超时允许出站）。
     * willHoldOnOut：出站后会不会自动锁批。
     */
    public TrackProcessTimeVO toContextVo(MesLot lot, MesRouteStep step) {
        if (!enabled || lot == null || !"processing".equals(lot.getStatus()) || !isConstrained(step)) {
            return null;
        }
        if (lot.getProcessStartedAt() == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        long elapsed = Math.max(0, Duration.between(lot.getProcessStartedAt(), now).toMinutes());
        Integer min = step.getMinProcessMin();
        Integer max = step.getMaxProcessMin();
        boolean tooShort = min != null && elapsed < min;
        boolean exceededMax = max != null && elapsed > max;
        TrackProcessTimeVO vo = new TrackProcessTimeVO();
        vo.setStartedAt(lot.getProcessStartedAt());
        vo.setMinProcessMin(min);
        vo.setMaxProcessMin(max);
        vo.setElapsedMin(elapsed);
        if (min != null) {
            vo.setRemainToMinMin(Math.max(0, min - elapsed));
        }
        if (max != null) {
            vo.setRemainToMaxMin(max - elapsed);
        }
        vo.setCanTrackOutByTime(!tooShort);
        vo.setExceededMax(exceededMax);
        vo.setWillHoldOnOut(exceededMax);
        return vo;
    }

    /** 完工流水里记一下加工了多久、上下限、有没有超时 */
    public void putExt(JSONObject ext, SettleResult settle) {
        if (ext == null || settle == null || !settle.constrained()) {
            return;
        }
        ext.set("processElapsedMin", settle.elapsedMin());
        if (settle.minProcessMin() != null) {
            ext.set("minProcessMin", settle.minProcessMin());
        }
        if (settle.maxProcessMin() != null) {
            ext.set("maxProcessMin", settle.maxProcessMin());
        }
        if (settle.startedAt() != null) {
            ext.set("processStartedAt", settle.startedAt().toString());
        }
        if (settle.exceededMax()) {
            ext.set("processTimeExceededMax", true);
        }
    }

    /** 开工流水里记一下几点开的表 */
    public void putTrackInExt(JSONObject ext, MesLot lot) {
        if (ext == null || lot == null || lot.getProcessStartedAt() == null) {
            return;
        }
        ext.set("processStartedAt", lot.getProcessStartedAt().toString());
    }

    /**
     * 一次完工检查的结果：加工了多久、有没有超时。
     * exceededMax=true 表示出站后要去锁批。
     */
    public record SettleResult(boolean constrained, long elapsedMin, Integer minProcessMin,
                               Integer maxProcessMin, LocalDateTime startedAt, boolean exceededMax) {
        /** 本站不管时长 */
        public static SettleResult skipped() {
            return new SettleResult(false, 0, null, null, null, false);
        }
    }
}
