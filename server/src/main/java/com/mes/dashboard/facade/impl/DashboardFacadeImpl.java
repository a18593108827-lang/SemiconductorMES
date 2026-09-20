package com.mes.dashboard.facade.impl;

import com.mes.alarm.facade.AlarmFacade;
import com.mes.alarm.vo.AlarmVO;
import com.mes.dashboard.facade.DashboardFacade;
import com.mes.dashboard.vo.DashboardAlarmVO;
import com.mes.dashboard.vo.DashboardEqpVO;
import com.mes.dashboard.vo.DashboardKpiVO;
import com.mes.dashboard.vo.DashboardOverviewVO;
import com.mes.dashboard.vo.DashboardTrendPointVO;
import com.mes.equipment.dto.MesEqpQuery;
import com.mes.equipment.service.MesEqpService;
import com.mes.equipment.service.impl.MesEqpServiceImpl;
import com.mes.equipment.vo.MesEqpVO;
import com.mes.history.facade.HistoryFacade;
import com.mes.history.vo.HistoryDailyCountVO;
import com.mes.hold.service.HoldService;
import com.mes.track.service.impl.TrackServiceImpl;
import com.mes.wip.dto.MesWipQuery;
import com.mes.wip.service.WipService;
import com.mes.wip.vo.MesWipVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 生产看板只读聚合。
 * 不造业务状态：WIP / Hold / Eqp / Alarm / TrackOut 都问源模块；
 * 某一块挂了只打 partial，其余块照常返回，投屏不整页 500。
 * 鉴权在 Controller（dashboard:view），这里不鉴权。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardFacadeImpl implements DashboardFacade {

    /** 趋势默认近 7 天（含今天） */
    private static final int TREND_DAYS_DEFAULT = 7;
    /** 趋势最多 31 天，防止一次扫履历过宽 */
    private static final int TREND_DAYS_MAX = 31;
    private static final int ALARM_LIMIT_DEFAULT = 20;
    private static final int ALARM_LIMIT_MAX = 100;
    /** 设备矩阵一期全拉启用设备；超过再考虑分页/缓存 */
    private static final int EQP_PAGE_SIZE = 500;
    /** 反查机台当前批时 WIP 最多扫这么多行 */
    private static final int WIP_LOT_PAGE_SIZE = 500;

    private final WipService wipService;
    private final HoldService holdService;
    private final MesEqpService mesEqpService;
    private final AlarmFacade alarmFacade;
    private final HistoryFacade historyFacade;

    /**
     * 组装整屏：钳制入参 → 逐块填充 KPI / 矩阵 / 报警流 / 趋势。
     * 各 fill* 内部吞异常，保证能返回 generatedAt。
     */
    @Override
    public DashboardOverviewVO overview(int trendDays, int alarmLimit) {
        int days = trendDays < 1 ? TREND_DAYS_DEFAULT : Math.min(trendDays, TREND_DAYS_MAX);
        int alarmLim = alarmLimit < 1 ? ALARM_LIMIT_DEFAULT : Math.min(alarmLimit, ALARM_LIMIT_MAX);

        DashboardOverviewVO vo = new DashboardOverviewVO();//看板数据
        vo.setGeneratedAt(LocalDateTime.now());
        DashboardKpiVO kpi = vo.getKpi();

        fillWip(vo, kpi);
        fillHold(vo, kpi);
        fillEquipment(vo, kpi);
        fillAlarms(vo, kpi, alarmLim);
        fillTrend(vo, days);
        return vo;
    }

    /**
     * 在制数
     */
    private void fillWip(DashboardOverviewVO vo, DashboardKpiVO kpi) {
        try {
            kpi.setWipCount(wipService.count());
        } catch (Exception e) {
            fail(vo, "wip", e);
        }
    }

    /**
     * 活跃锁批数：status=active，只 COUNT。
     */
    private void fillHold(DashboardOverviewVO vo, DashboardKpiVO kpi) {
        try {
            kpi.setHoldActiveCount(holdService.countActive());
        } catch (Exception e) {
            fail(vo, "hold", e);
        }
    }

    /**
     * 启用设备矩阵 + Down / Running 计数。
     * 当前批号不在 mes_eqp，靠 WIP.currentEqpId 反查；Eqp 无 lot 字段是故意的。
     */
    private void fillEquipment(DashboardOverviewVO vo, DashboardKpiVO kpi) {
        try {
            MesEqpQuery q = new MesEqpQuery();
            q.setPage(1);
            q.setSize(EQP_PAGE_SIZE);
            q.setEnabled(1);
            List<MesEqpVO> rows = mesEqpService.page(q).getRecords();

            Map<Long, String> lotByEqp = loadLotByEqp(vo);

            long down = 0;
            long running = 0;
            List<DashboardEqpVO> matrix = new ArrayList<>(rows.size());
            for (MesEqpVO e : rows) {
                if (MesEqpServiceImpl.STATUS_DOWN.equals(e.getStatus())) {
                    down++;
                }
                if (MesEqpServiceImpl.STATUS_RUNNING.equals(e.getStatus())) {
                    running++;
                }
                DashboardEqpVO row = new DashboardEqpVO();
                row.setId(e.getId());
                row.setEqpCode(e.getEqpCode());
                row.setName(e.getEqpName());
                row.setStatus(e.getStatus());
                row.setCurrentLotNo(lotByEqp.get(e.getId()));
                matrix.add(row);
            }
            vo.setEquipment(matrix);
            kpi.setEqpTotal(rows.size());
            kpi.setEqpDownCount(down);
            kpi.setEqpRunningCount(running);
        } catch (Exception e) {
            fail(vo, "equipment", e);
        }
    }

    /**
     * eqpId → lotNo；同一机多批时 putIfAbsent 留先扫到的一条即可（看板只展示占位）。
     * 反查失败不挡矩阵本身。
     */
    private Map<Long, String> loadLotByEqp(DashboardOverviewVO vo) {
        Map<Long, String> map = new HashMap<>();
        try {
            MesWipQuery q = new MesWipQuery();
            q.setPage(1);
            q.setSize(WIP_LOT_PAGE_SIZE);
            for (MesWipVO w : wipService.page(q).getRecords()) {
                if (w.getCurrentEqpId() == null || !StringUtils.hasText(w.getLotNo())) {
                    continue;
                }
                map.putIfAbsent(w.getCurrentEqpId(), w.getLotNo());
            }
        } catch (Exception e) {
            fail(vo, "wip-lot-on-eqp", e);
        }
        return map;
    }

    /**
     * 未关闭告警：KPI 用 OPEN+ACK 计数；流用最近 N 条。
     */
    private void fillAlarms(DashboardOverviewVO vo, DashboardKpiVO kpi, int alarmLim) {
        try {
            kpi.setAlarmOpenCount(alarmFacade.countUncleared());
            List<AlarmVO> rows = alarmFacade.listUncleared(alarmLim);
            List<DashboardAlarmVO> list = new ArrayList<>(rows.size());
            for (AlarmVO a : rows) {
                DashboardAlarmVO row = new DashboardAlarmVO();
                row.setId(a.getId());
                row.setLevel(a.getLevel());
                row.setSource(resolveAlarmSource(a));
                row.setMessage(a.getMessage());
                // 去重 bump 后看最近响的时间更贴近「现在还在闹」
                row.setRaisedAt(a.getLastRaiseAt() != null ? a.getLastRaiseAt() : a.getFirstRaiseAt());
                row.setStatus(a.getStatus());
                list.add(row);
            }
            vo.setAlarms(list);
        } catch (Exception e) {
            fail(vo, "alarm", e);
        }
    }

    /** 流上的 source：有实体就 code@TYPE:id，否则只显示告警码 */
    private static String resolveAlarmSource(AlarmVO a) {
        if (StringUtils.hasText(a.getCode())) {
            if (a.getEntityId() != null && a.getEntityId() > 0
                    && StringUtils.hasText(a.getEntityType())
                    && !"NONE".equalsIgnoreCase(a.getEntityType())) {
                return a.getCode() + "@" + a.getEntityType() + ":" + a.getEntityId();
            }
            return a.getCode();
        }
        return a.getEntityType() != null ? a.getEntityType() : "—";
    }

    /**
     * TrackOut 日吞吐：问 History 按日 COUNT，再按日历补缺日为 0，折线不断档。
     * 失败时仍返回全 0 序列，前端图能画。
     */
    private void fillTrend(DashboardOverviewVO vo, int days) {
        try {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(days - 1L);// 往前推几天
            List<HistoryDailyCountVO> raw = historyFacade.countDailyByTxType(
                    TrackServiceImpl.TX_TRACK_OUT, from, to);
            Map<String, Long> byDay = new HashMap<>();
            for (HistoryDailyCountVO r : raw) {
                if (r.getDay() != null) {
                    byDay.put(r.getDay(), r.getCount());
                }
            }
            List<DashboardTrendPointVO> trend = new ArrayList<>(days);
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                DashboardTrendPointVO p = new DashboardTrendPointVO();
                String key = d.toString();
                p.setDay(key);
                p.setTrackOutCount(byDay.getOrDefault(key, 0L));
                trend.add(p);
            }
            vo.setOutputTrend(trend);
        } catch (Exception e) {
            fail(vo, "trend", e);
            vo.setOutputTrend(emptyTrend(days));
        }
    }

    /** 趋势降级：连续 days 个点，计数全 0 */
    private static List<DashboardTrendPointVO> emptyTrend(int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);
        List<DashboardTrendPointVO> trend = new ArrayList<>(days);
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            DashboardTrendPointVO p = new DashboardTrendPointVO();
            p.setDay(d.toString());
            p.setTrackOutCount(0);
            trend.add(p);
        }
        return trend;
    }

    /** 标记 partial、记下域错误，打 warn 方便排查，不向上抛 */
    private void fail(DashboardOverviewVO vo, String domain, Exception e) {
        vo.setPartial(true);
        vo.getErrors().add(domain + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        log.warn("dashboard overview partial [{}]", domain, e);
    }
}
