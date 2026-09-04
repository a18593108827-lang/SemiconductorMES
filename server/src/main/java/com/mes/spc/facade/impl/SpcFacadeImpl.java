package com.mes.spc.facade.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.alarm.service.AlarmService;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.edc.facade.EdcFacade;
import com.mes.edc.vo.EdcSeriesPoint;
import com.mes.edc.vo.MesEdcCollectionItemVO;
import com.mes.edc.vo.MesEdcCollectionVO;
import com.mes.spc.dto.SpcChartSaveDTO;
import com.mes.spc.entity.MesSpcChart;
import com.mes.spc.entity.MesSpcEval;
import com.mes.spc.facade.SpcFacade;
import com.mes.spc.mapper.MesSpcChartMapper;
import com.mes.spc.mapper.MesSpcEvalMapper;
import com.mes.spc.support.SpcImr;
import com.mes.spc.vo.SpcChartVO;
import com.mes.spc.vo.SpcEvalVO;
import com.mes.spc.vo.SpcSeriesPointVO;
import com.mes.spc.vo.SpcSeriesVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SPC 观察者。
 * 记一句：采集已经成功了才过来看；超控制限只喊一声，不锁批、不挡过站。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpcFacadeImpl implements SpcFacade {

    public static final String ALARM_SPC_OOC = "SPC_OOC";
    public static final String RULE_WE1 = "WE1";
    public static final String RULE_RUN = "RUN";
    public static final String MODE_MANUAL = "MANUAL";
    public static final String MODE_LEARNING = "LEARNING";
    public static final String TYPE_IMR = "IMR";
    public static final int ENABLED = 1;
    public static final int DISABLED = 0;
    public static final int OOC_YES = 1;
    public static final int SERIES_LIMIT_DEFAULT = 100;
    public static final int SERIES_LIMIT_MAX = 500;
    public static final int LEARNING_N_DEFAULT = 25;
    public static final int RUN_N_DEFAULT = 7;

    private final EdcFacade edcFacade;
    private final MesSpcChartMapper mesSpcChartMapper;
    private final MesSpcEvalMapper mesSpcEvalMapper;
    private final AlarmService alarmService;

    @Value("${mes.spc.enabled:true}")
    private boolean spcEnabled;

    /** 采集提交后来判。开关关了、没图、出错，都不许把采集接口搞失败。 */
    @Override
    public void onCollected(Long collectionId) {
        try {
            evaluateCollected(collectionId);
        } catch (Exception e) {
            log.error("SPC判异失败 collectionId={}", collectionId, e);
        }
    }

    /** 按 id 取图。没有就空，别抛。 */
    @Override
    public SpcChartVO getChart(Long chartId) {
        MesSpcChart row = loadChart(chartId);
        if (row == null) {
            return null;
        }
        return toChartVo(row, countPoints(row));
    }

    /** 按站或特性筛图，都能空，停用的也列出方便维护。 */
    @Override
    public List<SpcChartVO> listCharts(Long stepId, Long paramId) {
        LambdaQueryWrapper<MesSpcChart> qw = new LambdaQueryWrapper<>();
        if (stepId != null) {
            qw.eq(MesSpcChart::getStepId, stepId);
        }
        if (paramId != null) {
            qw.eq(MesSpcChart::getParamId, paramId);
        }
        qw.orderByDesc(MesSpcChart::getUpdateTime);
        List<MesSpcChart> rows = mesSpcChartMapper.selectList(qw);
        List<SpcChartVO> list = new ArrayList<>(rows.size());
        for (MesSpcChart row : rows) {
            list.add(toChartVo(row, null));
        }
        return list;
    }

    /** 拉这张图的趋势。规格限跟着点上的快照走，不拿来判失控。点不够 25 个不给 Cpk。 */
    @Override
    public SpcSeriesVO getSeries(Long chartId, LocalDateTime from, LocalDateTime to, Integer limit) {
        MesSpcChart chart = loadChart(chartId);
        if (chart == null) {
            return null;
        }
        List<EdcSeriesPoint> edcPoints = edcFacade.listSeries(
                chart.getParamId(), chart.getStepId(), seriesEqpId(chart), from, to, limit);
        SpcSeriesVO vo = new SpcSeriesVO();
        vo.setChart(toChartVo(chart, edcPoints.size()));
        fillSpec(vo, edcPoints);
        fillCapability(vo, edcPoints);
        vo.setPoints(toSeriesPoints(chart.getId(), edcPoints));
        vo.setLastEval(loadLastOoc(chart.getId()));
        return vo;
    }

    /** 新建或改图。同一站同一特性同一机台只能一张。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SpcChartVO saveChart(SpcChartSaveDTO cmd) {
        AssertUtil.notNull(cmd, "图不能为空");
        AssertUtil.notNull(cmd.getParamId(), "特性不能为空");
        AssertUtil.notNull(cmd.getStepId(), "工序不能为空");
        // 限模式：有填认填的，空着默认 LEARNING（先攒点再算限）
        String mode = StringUtils.hasText(cmd.getLimitMode()) ? cmd.getLimitMode().trim() : MODE_LEARNING;
        AssertUtil.isTrue(MODE_MANUAL.equals(mode) || MODE_LEARNING.equals(mode), "限模式只能是 MANUAL 或 LEARNING");

        // 机台空着当站级图，库里用 0 占位，唯一索引才立得住
        long eqpId = cmd.getEqpId() == null ? MesSpcChart.EQP_NONE : cmd.getEqpId();
        long userId = StpUtil.getLoginIdAsLong();
        MesSpcChart row;
        if (cmd.getId() != null) {
            // 有 id = 改旧图；找不到就报错，不悄悄新建
            row = loadChart(cmd.getId());
            AssertUtil.notNull(row, "图不存在");
            row.setUpdateBy(userId);
        } else {
            // 没 id = 建新图；类型一期固定 I-MR
            row = new MesSpcChart();
            row.setChartType(TYPE_IMR);
            row.setCreateBy(userId);
            row.setUpdateBy(userId);
        }
        row.setParamId(cmd.getParamId());
        row.setStepId(cmd.getStepId());
        row.setEqpId(eqpId);
        row.setChartType(TYPE_IMR);
        row.setLimitMode(mode);
        // 学习点数太小没意义，空或小于 2 按默认 25
        row.setLearningN(cmd.getLearningN() == null || cmd.getLearningN() < 2 ? LEARNING_N_DEFAULT : cmd.getLearningN());
        row.setUcl(cmd.getUcl());
        row.setCl(cmd.getCl());
        row.setLcl(cmd.getLcl());
        // 连跑规则：空或负数按默认 7；0 表示关掉
        row.setRunN(cmd.getRunN() == null || cmd.getRunN() < 0 ? RUN_N_DEFAULT : cmd.getRunN());
        // 启停没传就默认开着
        row.setEnabled(cmd.getEnabled() == null ? ENABLED : (Objects.equals(cmd.getEnabled(), ENABLED) ? ENABLED : DISABLED));
        try {
            if (row.getId() == null) {
                mesSpcChartMapper.insert(row);
            } else {
                mesSpcChartMapper.updateById(row);
            }
        } catch (DataIntegrityViolationException e) {
            // 撞了 uk_spc_chart_ctx：同一站同一特性同一机台已经有图
            throw new BusinessException("该站该特性已有图");
        }
        return toChartVo(row, null);
    }

    /** 手填控制限。LEARNING 算出来的限也走这条改；改完就冻住，不会再自动算。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setLimits(Long chartId, BigDecimal ucl, BigDecimal cl, BigDecimal lcl) {
        MesSpcChart row = loadChart(chartId);
        AssertUtil.notNull(row, "图不存在");
        row.setUcl(ucl);
        row.setCl(cl);
        row.setLcl(lcl);
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        mesSpcChartMapper.updateById(row);
    }

    /** 停图只是不判了，图和限都还在。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(Long chartId, boolean enabled) {
        MesSpcChart row = loadChart(chartId);
        AssertUtil.notNull(row, "图不存在");
        row.setEnabled(enabled ? ENABLED : DISABLED);
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        mesSpcChartMapper.updateById(row);
    }

    /** 真正干活：拉开这一单的点，按特性找图，站级图和机台图都能命中。 */
    private void evaluateCollected(Long collectionId) {
        if (!spcEnabled || collectionId == null) {
            return;
        }
        MesEdcCollectionVO col = edcFacade.getCollection(collectionId);
        if (col == null || col.getStepId() == null || col.getItems() == null) {
            return;
        }
        for (MesEdcCollectionItemVO item : col.getItems()) {
            if (item == null || item.getParamId() == null || item.getValueNum() == null) {
                continue;
            }
            List<MesSpcChart> charts = findCharts(item.getParamId(), col.getStepId(), col.getEqpId());
            for (MesSpcChart chart : charts) {
                try {
                    evaluateItem(chart, col, item);
                } catch (Exception e) {
                    log.error("SPC判异失败 chartId={} itemId={}", chart.getId(), item.getId(), e);
                }
            }
        }
    }

    /** 一张图判一个点：没限就先学习；有限再看出界和连跑。 */
    private void evaluateItem(MesSpcChart chart, MesEdcCollectionVO col, MesEdcCollectionItemVO item) {
        if (!Objects.equals(chart.getEnabled(), ENABLED)) {
            return;
        }
        List<EdcSeriesPoint> points = edcFacade.listSeries(
                chart.getParamId(), chart.getStepId(), seriesEqpId(chart), null, null, SERIES_LIMIT_MAX);
        points = ensureCurrent(points, col, item);
        if (!hasLimits(chart)) {
            tryLearn(chart, points);
            return;
        }
        String rule = judge(chart, points, item);
        if (rule == null) {
            return;
        }
        raiseOoc(chart, col, item, rule);
    }

    /** 限还空着：点攒够学习数，用这 n 个点算出 UCL/CL/LCL 写回去，以后不再自动改。 */
    private void tryLearn(MesSpcChart chart, List<EdcSeriesPoint> points) {
        int need = chart.getLearningN() == null || chart.getLearningN() < 2 ? LEARNING_N_DEFAULT : chart.getLearningN();
        List<BigDecimal> values = values(points);
        if (values.size() < 2 || values.size() < need) {
            return;
        }
        List<BigDecimal> sample = values.subList(0, need);
        SpcImr.Limits limits = SpcImr.limits(sample);
        if (limits == null) {
            return;
        }
        MesSpcChart fresh = loadChart(chart.getId());
        if (fresh == null || hasLimits(fresh)) {
            return;
        }
        fresh.setUcl(limits.ucl());
        fresh.setCl(limits.cl());
        fresh.setLcl(limits.lcl());
        mesSpcChartMapper.updateById(fresh);
        chart.setUcl(limits.ucl());
        chart.setCl(limits.cl());
        chart.setLcl(limits.lcl());
    }

    /** WE1 出界优先；过了再看连跑同侧。点贴在限上不算失控。 */
    private String judge(MesSpcChart chart, List<EdcSeriesPoint> points, MesEdcCollectionItemVO item) {
        BigDecimal value = item.getValueNum();
        if (value == null) {
            return null;
        }
        if (chart.getUcl() != null && value.compareTo(chart.getUcl()) > 0) {
            return RULE_WE1;
        }
        if (chart.getLcl() != null && value.compareTo(chart.getLcl()) < 0) {
            return RULE_WE1;
        }
        int runN = chart.getRunN() == null ? 0 : chart.getRunN();
        if (runN <= 0 || chart.getCl() == null) {
            return null;
        }
        int idx = indexOf(points, item.getId());
        if (idx < 0 || idx + 1 < runN) {
            return null;
        }
        List<EdcSeriesPoint> window = points.subList(idx + 1 - runN, idx + 1);
        int side = 0;
        for (EdcSeriesPoint p : window) {
            if (p.getValueNum() == null) {
                return null;
            }
            int cmp = p.getValueNum().compareTo(chart.getCl());
            if (cmp == 0) {
                return null;
            }
            int thisSide = cmp > 0 ? 1 : -1;
            if (side == 0) {
                side = thisSide;
            } else if (side != thisSide) {
                return null;
            }
        }
        return RULE_RUN;
    }

    /** 写下当时用的哪版限，再喊 Alarm。同一点同一图已经判过就不再喊。 */
    private void raiseOoc(MesSpcChart chart, MesEdcCollectionVO col, MesEdcCollectionItemVO item, String rule) {
        Long exists = mesSpcEvalMapper.selectCount(new LambdaQueryWrapper<MesSpcEval>()
                .eq(MesSpcEval::getChartId, chart.getId())
                .eq(MesSpcEval::getCollectionItemId, item.getId()));
        if (exists != null && exists > 0) {
            return;
        }
        MesSpcEval eval = new MesSpcEval();
        eval.setChartId(chart.getId());
        eval.setCollectionItemId(item.getId());
        eval.setOoc(OOC_YES);
        eval.setRuleCode(rule);
        eval.setUclSnap(chart.getUcl());
        eval.setClSnap(chart.getCl());
        eval.setLclSnap(chart.getLcl());
        try {
            mesSpcEvalMapper.insert(eval);
        } catch (DataIntegrityViolationException e) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("chartId", chart.getId());
        payload.put("collectionItemId", item.getId());
        payload.put("lotId", col.getLotId());
        payload.put("paramId", item.getParamId());
        payload.put("stepId", col.getStepId());
        payload.put("ruleCode", rule);
        payload.put("value", item.getValueNum());
        payload.put("ucl", chart.getUcl());
        payload.put("lcl", chart.getLcl());
        alarmService.raise(ALARM_SPC_OOC, "SPC失控 " + rule, payload);
    }

    /** 站级图（机台=0）和这台机自己的图，启用的都拿来判。 */
    private List<MesSpcChart> findCharts(Long paramId, Long stepId, Long eqpId) {
        List<Long> eqpIds = new ArrayList<>();
        eqpIds.add(MesSpcChart.EQP_NONE);
        if (eqpId != null && eqpId != MesSpcChart.EQP_NONE) {
            eqpIds.add(eqpId);
        }
        return mesSpcChartMapper.selectList(new LambdaQueryWrapper<MesSpcChart>()
                .eq(MesSpcChart::getParamId, paramId)
                .eq(MesSpcChart::getStepId, stepId)
                .eq(MesSpcChart::getEnabled, ENABLED)
                .in(MesSpcChart::getEqpId, eqpIds));
    }

    /** 刚采的这一点如果还没进序列，补到末尾，避免提交后立刻判时漏点。 */
    private static List<EdcSeriesPoint> ensureCurrent(List<EdcSeriesPoint> points,
                                                      MesEdcCollectionVO col,
                                                      MesEdcCollectionItemVO item) {
        if (indexOf(points, item.getId()) >= 0) {
            return points;
        }
        List<EdcSeriesPoint> copy = new ArrayList<>(points);
        EdcSeriesPoint p = new EdcSeriesPoint();
        p.setItemId(item.getId());
        p.setCollectionId(col.getId());
        p.setLotId(col.getLotId());
        p.setLotNo(col.getLotNo());
        p.setEqpId(col.getEqpId());
        p.setCollectedAt(col.getCollectedAt());
        p.setValueNum(item.getValueNum());
        p.setItemResult(item.getItemResult());
        p.setUslSnap(item.getUslSnap());
        p.setLslSnap(item.getLslSnap());
        copy.add(p);
        return copy;
    }

    /** 两条限都空才叫还没限；只填一边也算有限，按有的那边判。 */
    private static boolean hasLimits(MesSpcChart chart) {
        return chart.getUcl() != null || chart.getLcl() != null;
    }

    /** 站级图读点时不过滤机台。 */
    private static Long seriesEqpId(MesSpcChart chart) {
        if (chart.getEqpId() == null || chart.getEqpId() == MesSpcChart.EQP_NONE) {
            return null;
        }
        return chart.getEqpId();
    }

    private MesSpcChart loadChart(Long chartId) {
        if (chartId == null) {
            return null;
        }
        return mesSpcChartMapper.selectById(chartId);
    }

    /** 粗算当前点数，给图卡片用。 */
    private Integer countPoints(MesSpcChart chart) {
        return edcFacade.listSeries(
                chart.getParamId(), chart.getStepId(), seriesEqpId(chart), null, null, SERIES_LIMIT_MAX).size();
    }

    private SpcChartVO toChartVo(MesSpcChart row, Integer n) {
        SpcChartVO vo = new SpcChartVO();
        vo.setId(row.getId());
        vo.setParamId(row.getParamId());
        vo.setStepId(row.getStepId());
        vo.setEqpId(row.getEqpId() == null || row.getEqpId() == MesSpcChart.EQP_NONE ? null : row.getEqpId());
        vo.setChartType(row.getChartType());
        vo.setLimitMode(row.getLimitMode());
        vo.setLearningN(row.getLearningN());
        vo.setUcl(row.getUcl());
        vo.setCl(row.getCl());
        vo.setLcl(row.getLcl());
        vo.setRunN(row.getRunN());
        vo.setEnabled(row.getEnabled());
        vo.setN(n);
        vo.setVersion(row.getVersion());
        return vo;
    }

    /** 规格取序列里最后一条有快照的，只给图画虚线。 */
    private static void fillSpec(SpcSeriesVO vo, List<EdcSeriesPoint> points) {
        for (int i = points.size() - 1; i >= 0; i--) {
            EdcSeriesPoint p = points.get(i);
            if (p.getUslSnap() != null || p.getLslSnap() != null) {
                vo.setSpecUsl(p.getUslSnap());
                vo.setSpecLsl(p.getLslSnap());
                return;
            }
        }
    }

    /** 点少于 25 个 Cpk/Cp 一律空，避免手录样本太少还报指数。 */
    private static void fillCapability(SpcSeriesVO vo, List<EdcSeriesPoint> points) {
        if (points.size() < SpcImr.CPK_MIN_N) {
            return;
        }
        List<BigDecimal> values = values(points);
        if (values.size() < SpcImr.CPK_MIN_N) {
            return;
        }
        BigDecimal sig = SpcImr.sigma(values);
        BigDecimal mean = SpcImr.mean(values);
        vo.setCp(SpcImr.cp(vo.getSpecUsl(), vo.getSpecLsl(), sig));
        vo.setCpk(SpcImr.cpk(vo.getSpecUsl(), vo.getSpecLsl(), mean, sig));
    }

    private List<SpcSeriesPointVO> toSeriesPoints(Long chartId, List<EdcSeriesPoint> edcPoints) {
        if (edcPoints.isEmpty()) {
            return List.of();
        }
        List<Long> itemIds = edcPoints.stream().map(EdcSeriesPoint::getItemId).filter(Objects::nonNull).toList();
        Set<Long> oocItems = itemIds.isEmpty() ? Set.of()
                : mesSpcEvalMapper.selectList(new LambdaQueryWrapper<MesSpcEval>()
                        .eq(MesSpcEval::getChartId, chartId)
                        .eq(MesSpcEval::getOoc, OOC_YES)
                        .in(MesSpcEval::getCollectionItemId, itemIds)
                        .select(MesSpcEval::getCollectionItemId))
                .stream()
                .map(MesSpcEval::getCollectionItemId)
                .collect(Collectors.toSet());
        List<SpcSeriesPointVO> list = new ArrayList<>(edcPoints.size());
        for (EdcSeriesPoint p : edcPoints) {
            SpcSeriesPointVO vo = new SpcSeriesPointVO();
            vo.setItemId(p.getItemId());
            vo.setCollectionId(p.getCollectionId());
            vo.setLotId(p.getLotId());
            vo.setLotNo(p.getLotNo());
            vo.setTime(p.getCollectedAt());
            vo.setValue(p.getValueNum());
            vo.setItemResult(p.getItemResult());
            vo.setEvalOoc(p.getItemId() != null && oocItems.contains(p.getItemId()));
            list.add(vo);
        }
        return list;
    }

    private SpcEvalVO loadLastOoc(Long chartId) {
        MesSpcEval row = mesSpcEvalMapper.selectOne(new LambdaQueryWrapper<MesSpcEval>()
                .eq(MesSpcEval::getChartId, chartId)
                .eq(MesSpcEval::getOoc, OOC_YES)
                .orderByDesc(MesSpcEval::getCreateTime)
                .last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        SpcEvalVO vo = new SpcEvalVO();
        vo.setId(row.getId());
        vo.setChartId(row.getChartId());
        vo.setCollectionItemId(row.getCollectionItemId());
        vo.setOoc(row.getOoc());
        vo.setRuleCode(row.getRuleCode());
        vo.setUclSnap(row.getUclSnap());
        vo.setClSnap(row.getClSnap());
        vo.setLclSnap(row.getLclSnap());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    private static List<BigDecimal> values(List<EdcSeriesPoint> points) {
        List<BigDecimal> xs = new ArrayList<>();
        for (EdcSeriesPoint p : points) {
            if (p.getValueNum() != null) {
                xs.add(p.getValueNum());
            }
        }
        return xs;
    }

    private static int indexOf(List<EdcSeriesPoint> points, Long itemId) {
        if (itemId == null) {
            return -1;
        }
        for (int i = 0; i < points.size(); i++) {
            if (itemId.equals(points.get(i).getItemId())) {
                return i;
            }
        }
        return -1;
    }
}
