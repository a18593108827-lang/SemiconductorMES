package com.mes.report.facade.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.history.facade.HistoryFacade;
import com.mes.history.vo.HistoryDailyCountVO;
import com.mes.history.vo.HistoryStepCountVO;
import com.mes.hold.entity.MesHoldReason;
import com.mes.hold.mapper.MesHoldReasonMapper;
import com.mes.hold.service.HoldService;
import com.mes.hold.vo.HoldReasonAggVO;
import com.mes.report.facade.ReportFacade;
import com.mes.report.support.ReportDateWindow;
import com.mes.report.vo.ReportDayPointVO;
import com.mes.report.vo.ReportHoldReasonVO;
import com.mes.report.vo.ReportHoldVO;
import com.mes.report.vo.ReportMoveVO;
import com.mes.report.vo.ReportStepPointVO;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesStepMapper;
import com.mes.track.service.impl.TrackServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Move / Hold 报表编排：Move 只问 {@link HistoryFacade}；Hold 只问 {@link HoldService}。
 * 工序 / 原因显示名只读主数据 Mapper。无实例可变字段，请求内局部集合，并发安全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportFacadeImpl implements ReportFacade {

    private static final String UNATTRIBUTED_NAME = "未归属";

    private final HistoryFacade historyFacade;
    private final MesStepMapper mesStepMapper;
    private final HoldService holdService;
    private final MesHoldReasonMapper mesHoldReasonMapper;

    @Override
    public ReportMoveVO moveSummary(LocalDate from, LocalDate to) {
        ReportDateWindow window = ReportDateWindow.resolve(from, to);

        ReportMoveVO vo = new ReportMoveVO();
        vo.setGeneratedAt(LocalDateTime.now());
        vo.setFrom(window.from());
        vo.setTo(window.to());

        fillByDay(vo, window);
        fillByStep(vo, window);
        return vo;
    }

    @Override
    public ReportHoldVO holdSummary(LocalDate from, LocalDate to) {
        ReportDateWindow window = ReportDateWindow.resolve(from, to);

        ReportHoldVO vo = new ReportHoldVO();
        vo.setGeneratedAt(LocalDateTime.now());
        vo.setFrom(window.from());
        vo.setTo(window.to());

        fillByReason(vo, window);
        return vo;
    }

    /**
     * 填按日出站：问 History 拿 TRACK_OUT 日计数，缺的天补 0，并累加 total。
     * 查失败则标 partial，byDay 改用全 0 序列。
     */
    private void fillByDay(ReportMoveVO vo, ReportDateWindow window) {
        try {
            List<HistoryDailyCountVO> raw = historyFacade.countDailyByTxType(
                    TrackServiceImpl.TX_TRACK_OUT, window.from(), window.to());
            Map<String, Long> countByDay = indexByDay(raw);
            List<ReportDayPointVO> byDay = new ArrayList<>(window.dayCount());
            long total = 0L;
            for (LocalDate d = window.from(); !d.isAfter(window.to()); d = d.plusDays(1)) {
                String key = d.toString();
                long c = countByDay.getOrDefault(key, 0L);
                ReportDayPointVO p = new ReportDayPointVO();
                p.setDay(key);
                p.setTrackOutCount(c);
                byDay.add(p);
                total += c;
            }
            vo.setByDay(byDay);
            vo.setTotalTrackOut(total);
        } catch (Exception e) {
            failMove(vo, "move-by-day", e);
            vo.setByDay(zeroDays(window));
            vo.setTotalTrackOut(0L);
        }
    }

    /**
     * 填按站出站：同一 TRACK_OUT 窗按 step_id 分组；无站归「未归属」。
     * 失败则 byStep 空列表 + partial，不影响已填好的 byDay。
     */
    private void fillByStep(ReportMoveVO vo, ReportDateWindow window) {
        try {
            List<HistoryStepCountVO> raw = historyFacade.countByStepAndTxType(
                    TrackServiceImpl.TX_TRACK_OUT, window.from(), window.to());
            if (raw == null || raw.isEmpty()) {
                vo.setByStep(Collections.emptyList());
                return;
            }
            Map<Long, MesStep> stepMap = loadSteps(raw);
            List<ReportStepPointVO> byStep = new ArrayList<>(raw.size());
            for (HistoryStepCountVO r : raw) {
                if (r == null) {
                    continue;
                }
                ReportStepPointVO p = new ReportStepPointVO();
                Long stepId = r.getStepId();
                p.setStepId(stepId);
                p.setTrackOutCount(r.getCount());
                if (stepId == null) {
                    p.setStepName(UNATTRIBUTED_NAME);
                } else {
                    MesStep step = stepMap.get(stepId);
                    if (step != null) {
                        p.setStepCode(step.getStepCode());
                        p.setStepName(step.getStepName());
                    }
                }
                byStep.add(p);
            }
            vo.setByStep(byStep);
        } catch (Exception e) {
            failMove(vo, "move-by-step", e);
            vo.setByStep(Collections.emptyList());
        }
    }

    /**
     * 填 Hold 按原因：hold_time 落窗；名从原因码主数据；失败则空列表 + partial。
     */
    private void fillByReason(ReportHoldVO vo, ReportDateWindow window) {
        try {
            List<HoldReasonAggVO> raw = holdService.summarizeByReason(window.from(), window.to());
            if (raw == null || raw.isEmpty()) {
                vo.setByReason(Collections.emptyList());
                vo.setTotalHold(0L);
                return;
            }
            Map<String, String> nameByCode = loadReasonNames(raw);
            List<ReportHoldReasonVO> byReason = new ArrayList<>(raw.size());
            long total = 0L;
            for (HoldReasonAggVO r : raw) {
                if (r == null) {
                    continue;
                }
                ReportHoldReasonVO p = new ReportHoldReasonVO();
                String code = r.getReasonCode();
                p.setReasonCode(code);
                if (!StringUtils.hasText(code)) {
                    p.setReasonName(UNATTRIBUTED_NAME);
                } else {
                    p.setReasonName(nameByCode.getOrDefault(code, null));
                }
                p.setHoldCount(r.getHoldCount());
                p.setActiveCount(r.getActiveCount());
                p.setAvgDurationMinutes(r.getAvgDurationMinutes());
                byReason.add(p);
                total += r.getHoldCount();
            }
            vo.setByReason(byReason);
            vo.setTotalHold(total);
        } catch (Exception e) {
            failHold(vo, "hold-by-reason", e);
            vo.setByReason(Collections.emptyList());
            vo.setTotalHold(0L);
        }
    }

    /** 根据 stepId 批量读工序主数据 */
    private Map<Long, MesStep> loadSteps(List<HistoryStepCountVO> raw) {
        Set<Long> ids = new HashSet<>();
        for (HistoryStepCountVO r : raw) {
            if (r != null && r.getStepId() != null) {
                ids.add(r.getStepId());
            }
        }
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MesStep> steps = mesStepMapper.selectBatchIds(ids);
        if (steps == null || steps.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, MesStep> map = new HashMap<>(steps.size() * 2);
        for (MesStep s : steps) {
            if (s != null && s.getId() != null) {
                map.put(s.getId(), s);
            }
        }
        return map;
    }

    /** 按 reasonCode 批量读原因名 */
    private Map<String, String> loadReasonNames(List<HoldReasonAggVO> raw) {
        Set<String> codes = new HashSet<>();
        for (HoldReasonAggVO r : raw) {
            if (r != null && StringUtils.hasText(r.getReasonCode())) {
                codes.add(r.getReasonCode());
            }
        }
        if (codes.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MesHoldReason> rows = mesHoldReasonMapper.selectList(new LambdaQueryWrapper<MesHoldReason>()
                .in(MesHoldReason::getReasonCode, codes));
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>(rows.size() * 2);
        for (MesHoldReason row : rows) {
            if (row != null && StringUtils.hasText(row.getReasonCode())) {
                map.put(row.getReasonCode(), row.getReasonName());
            }
        }
        return map;
    }

    /** 获取日期对应 TRACK_OUT 次数 */
    private static Map<String, Long> indexByDay(List<HistoryDailyCountVO> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Long> map = new HashMap<>(Math.max(16, raw.size() * 2));
        for (HistoryDailyCountVO r : raw) {
            if (r != null && r.getDay() != null) {
                map.put(r.getDay(), r.getCount());
            }
        }
        return map;
    }

    /** 降级用：窗内每天一点、计数全 0，保证折线不断档 */
    private static List<ReportDayPointVO> zeroDays(ReportDateWindow window) {
        List<ReportDayPointVO> byDay = new ArrayList<>(window.dayCount());
        for (LocalDate d = window.from(); !d.isAfter(window.to()); d = d.plusDays(1)) {
            ReportDayPointVO p = new ReportDayPointVO();
            p.setDay(d.toString());
            p.setTrackOutCount(0L);
            byDay.add(p);
        }
        return byDay;
    }

    private void failMove(ReportMoveVO vo, String domain, Exception e) {
        vo.setPartial(true);
        vo.getErrors().add(domain + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        log.warn("report moveSummary partial [{}]", domain, e);
    }

    private void failHold(ReportHoldVO vo, String domain, Exception e) {
        vo.setPartial(true);
        vo.getErrors().add(domain + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        log.warn("report holdSummary partial [{}]", domain, e);
    }
}
