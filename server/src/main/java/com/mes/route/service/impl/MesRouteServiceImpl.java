package com.mes.route.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.route.dto.MesRouteCreateDTO;
import com.mes.route.dto.MesRouteQuery;
import com.mes.route.dto.MesRouteStepsSaveDTO;
import com.mes.route.dto.MesRouteUpgradeDTO;
import com.mes.route.entity.MesRoute;
import com.mes.route.entity.MesRouteEdge;
import com.mes.route.entity.MesRouteStep;
import com.mes.route.entity.MesRouteVersion;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesRouteEdgeMapper;
import com.mes.route.mapper.MesRouteMapper;
import com.mes.route.mapper.MesRouteStepMapper;
import com.mes.route.mapper.MesRouteVersionMapper;
import com.mes.route.mapper.MesStepMapper;
import com.mes.route.service.MesRouteService;
import com.mes.route.support.StepEqpTypeGuard;
import com.mes.route.vo.MesRouteEdgeVO;
import com.mes.route.vo.MesRouteStepVO;
import com.mes.route.vo.MesRouteVO;
import com.mes.route.vo.MesRouteVersionDetailVO;
import com.mes.route.vo.MesRouteVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mes.route.support.ProcessTimeBounds.validate;

/**
 * 工艺路线服务实现：版本生命周期与草稿步骤维护
 */
@Service
@RequiredArgsConstructor
public class MesRouteServiceImpl implements MesRouteService {

    /** 草稿 */
    public static final String STATUS_DRAFT = "draft";
    /** 生效（同路线至多一个） */
    public static final String STATUS_ACTIVE = "active";
    /** 归档 */
    public static final String STATUS_ARCHIVED = "archived";

    /** 回流边：Track 走 /track/rework，带次数上限 */
    public static final String EDGE_REWORK = com.mes.route.support.RouteEdgeTypes.REWORK;
    /** 默认出边：与步骤 next_sort_no 对齐，TrackOut 无 resultCode 时走这条 */
    public static final String EDGE_NORMAL = com.mes.route.support.RouteEdgeTypes.NORMAL;
    /** 条件分支边：TrackOut 带 resultCode 时按 condition_code 匹配 */
    public static final String EDGE_BRANCH = com.mes.route.support.RouteEdgeTypes.BRANCH;
    /** 跳站白名单边：Track 走 /track/skip */
    public static final String EDGE_SKIP_ALLOW = com.mes.route.support.RouteEdgeTypes.SKIP_ALLOW;
    /** 临时离线边：Track 走 /track/off-flow */
    public static final String EDGE_OFF_FLOW = com.mes.route.support.RouteEdgeTypes.OFF_FLOW;
    /** Queue Time 跨站约束边：不参与导航 */
    public static final String EDGE_TIME_LINK = com.mes.route.support.RouteEdgeTypes.TIME_LINK;

    private final MesRouteMapper mesRouteMapper;
    private final MesRouteVersionMapper mesRouteVersionMapper;
    private final MesRouteStepMapper mesRouteStepMapper;
    private final MesRouteEdgeMapper mesRouteEdgeMapper;
    private final MesStepMapper mesStepMapper;
    private final StepEqpTypeGuard stepEqpTypeGuard;

    @Override
    public PageResult<MesRouteVO> page(MesRouteQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        Page<MesRoute> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<MesRoute> qw = new LambdaQueryWrapper<>();
        // 模糊查询
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            qw.and(w -> w.like(MesRoute::getRouteCode, keyword)
                    .or().like(MesRoute::getRouteName, keyword)
                    .or().like(MesRoute::getProductCode, keyword));
        }
        if (query.getStatus() != null) {
            qw.eq(MesRoute::getStatus, query.getStatus());
        }
        qw.orderByAsc(MesRoute::getRouteCode);

        Page<MesRoute> result = mesRouteMapper.selectPage(page, qw);
        List<MesRoute> routes = result.getRecords();
        if (routes.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), pageNo, pageSize);
        }

        List<Long> routeIds = routes.stream().map(MesRoute::getId).toList();
        Map<Long, MesRouteVersion> activeMap = mesRouteVersionMapper.selectList(new LambdaQueryWrapper<MesRouteVersion>()
                        .in(MesRouteVersion::getRouteId, routeIds)
                        .eq(MesRouteVersion::getStatus, STATUS_ACTIVE))
                .stream()
                .collect(Collectors.toMap(MesRouteVersion::getRouteId, v -> v, (a, b) -> a));

        List<MesRouteVO> records = new ArrayList<>(routes.size());
        for (MesRoute route : routes) {
            MesRouteVO vo = toRouteVo(route);
            MesRouteVersion active = activeMap.get(route.getId());
            if (active != null) {
                vo.setActiveVersionId(active.getId());
                vo.setActiveVersionNo(active.getVersionNo());
            }
            records.add(vo);
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(MesRouteCreateDTO dto) {
        String routeCode = dto.getRouteCode().trim();
        Long count = mesRouteMapper.selectCount(new LambdaQueryWrapper<MesRoute>()
                .eq(MesRoute::getRouteCode, routeCode));
        AssertUtil.isTrue(count == 0, "路线编码已存在");

        MesRoute route = new MesRoute();
        route.setRouteCode(routeCode);
        route.setRouteName(dto.getRouteName().trim());
        route.setProductCode(blankToNull(dto.getProductCode()));
        route.setRemark(blankToNull(dto.getRemark()));
        route.setStatus(1);
        mesRouteMapper.insert(route);

        MesRouteVersion version = new MesRouteVersion();
        version.setRouteId(route.getId());
        version.setVersionNo(1);
        version.setStatus(STATUS_DRAFT);
        mesRouteVersionMapper.insert(version);
        return route.getId();
    }

    @Override
    public MesRouteVO get(Long id) {
        MesRoute route = mesRouteMapper.selectById(id);
        AssertUtil.notNull(route, "路线不存在");
        MesRouteVO vo = toRouteVo(route);
        MesRouteVersion active = mesRouteVersionMapper.selectOne(new LambdaQueryWrapper<MesRouteVersion>()
                .eq(MesRouteVersion::getRouteId, id)
                .eq(MesRouteVersion::getStatus, STATUS_ACTIVE)
                .last("LIMIT 1"));
        if (active != null) {
            vo.setActiveVersionId(active.getId());
            vo.setActiveVersionNo(active.getVersionNo());
        }
        return vo;
    }

    @Override
    public List<MesRouteVersionVO> listVersions(Long routeId) {
        MesRoute route = mesRouteMapper.selectById(routeId);
        AssertUtil.notNull(route, "路线不存在");
        List<MesRouteVersion> versions = mesRouteVersionMapper.selectList(new LambdaQueryWrapper<MesRouteVersion>()
                .eq(MesRouteVersion::getRouteId, routeId)
                .orderByDesc(MesRouteVersion::getVersionNo));
        List<MesRouteVersionVO> list = new ArrayList<>(versions.size());
        for (MesRouteVersion v : versions) {
            list.add(toVersionVo(v));
        }
        return list;
    }

    @Override
    public MesRouteVersionDetailVO getVersion(Long versionId) {
        MesRouteVersion version = mesRouteVersionMapper.selectById(versionId);
        AssertUtil.notNull(version, "版本不存在");
        MesRoute route = mesRouteMapper.selectById(version.getRouteId());
        AssertUtil.notNull(route, "路线不存在");

        List<MesRouteStep> steps = mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, versionId)
                .orderByAsc(MesRouteStep::getSortNo));

        Map<Long, MesStep> stepMap = Collections.emptyMap();//定义空Map
        if (!steps.isEmpty()) {
            List<Long> stepIds = steps.stream().map(MesRouteStep::getStepId).distinct().toList();
            stepMap = mesStepMapper.selectBatchIds(stepIds).stream()
                    .collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a));
        }

        MesRouteVersionDetailVO vo = new MesRouteVersionDetailVO();
        vo.setId(version.getId());
        vo.setRouteId(route.getId());
        vo.setRouteCode(route.getRouteCode());
        vo.setRouteName(route.getRouteName());
        vo.setVersionNo(version.getVersionNo());
        vo.setStatus(version.getStatus());
        vo.setPublishedAt(version.getPublishedAt());
        vo.setPublishedBy(version.getPublishedBy());
        vo.setRemark(version.getRemark());

        List<MesRouteStepVO> stepVos = new ArrayList<>(steps.size());
        for (MesRouteStep rs : steps) {
            MesRouteStepVO svo = new MesRouteStepVO();
            svo.setId(rs.getId());
            svo.setStepId(rs.getStepId());
            svo.setSortNo(rs.getSortNo());
            svo.setNextSortNo(rs.getNextSortNo());
            // 站属性：优先快照，旧数据回退主数据
            svo.setEqpType(rs.getEqpType());
            svo.setStepType(rs.getStepType());
            svo.setAllowSkip(rs.getAllowSkip());
            svo.setMaxQueueMin(rs.getMaxQueueMin());
            svo.setMinProcessMin(rs.getMinProcessMin());
            svo.setMaxProcessMin(rs.getMaxProcessMin());
            MesStep step = stepMap.get(rs.getStepId());
            if (step != null) {
                svo.setStepCode(step.getStepCode());
                svo.setStepName(step.getStepName());
                if (svo.getStepType() == null) {
                    svo.setStepType(step.getStepType());
                }
                if (!StringUtils.hasText(svo.getEqpType())) {
                    svo.setEqpType(step.getEqpType());
                }
                if (svo.getAllowSkip() == null) {
                    svo.setAllowSkip(step.getAllowSkip());
                }
                if (svo.getMaxQueueMin() == null) {
                    svo.setMaxQueueMin(step.getMaxQueueMin());
                }
                if (svo.getMinProcessMin() == null) {
                    svo.setMinProcessMin(step.getMinProcessMin());
                }
                if (svo.getMaxProcessMin() == null) {
                    svo.setMaxProcessMin(step.getMaxProcessMin());
                }
            }
            stepVos.add(svo);
        }
        vo.setSteps(stepVos);
        vo.setEdges(listEdgeVos(versionId));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDraftSteps(Long versionId, MesRouteStepsSaveDTO dto) {
        MesRouteVersion version = mesRouteVersionMapper.selectById(versionId);
        AssertUtil.notNull(version, "版本不存在");
        AssertUtil.isTrue(STATUS_DRAFT.equals(version.getStatus()), "仅草稿版本可编辑步骤");

        List<MesRouteStepsSaveDTO.Item> items = dto.getSteps() == null ? List.of() : dto.getSteps();
        validateStepItems(items, true);

        // 物理删除：逻辑删除会占住 uk_ver_sort(version_id, sort_no)
        mesRouteStepMapper.physicalDeleteByVersionId(versionId);

        Map<Long, MesStep> stepMap = loadStepMap(items.stream().map(MesRouteStepsSaveDTO.Item::getStepId).toList());
        for (MesRouteStepsSaveDTO.Item item : items) {
            MesRouteStep row = new MesRouteStep();
            row.setVersionId(versionId);
            row.setStepId(item.getStepId());
            row.setSortNo(item.getSortNo());
            row.setNextSortNo(item.getNextSortNo());
            copyAttrsFromMaster(row, stepMap.get(item.getStepId()));// 站属性：优先快照，旧数据回退主数据
            mesRouteStepMapper.insert(row);
        }

        // ---------- 边表维护 ----------
        // normal 一律由步骤 next 生成，不信前端传的 normal，避免与线性顺序打架
        // edges != null：整表覆盖（先清空再写 branch/rework + 重建 normal）
        // edges == null：只重建 normal，保留已有 branch/rework（兼容旧客户端）
        if (dto.getEdges() != null) {
            Set<Integer> sortNos = items.stream().map(MesRouteStepsSaveDTO.Item::getSortNo)
                    .collect(Collectors.toSet());
            Map<String, MesRouteStepsSaveDTO.EdgeItem> normalQtime = dto.getEdges().stream()
                    .filter(e -> e.getEdgeType() != null
                            && EDGE_NORMAL.equalsIgnoreCase(e.getEdgeType().trim()))
                    .collect(Collectors.toMap(
                            e -> e.getFromSortNo() + "-" + e.getToSortNo(),
                            e -> e,
                            (a, b) -> a));
            List<MesRouteStepsSaveDTO.EdgeItem> nonNormal = dto.getEdges().stream()
                    .filter(e -> e.getEdgeType() != null
                            && !EDGE_NORMAL.equalsIgnoreCase(e.getEdgeType().trim()))
                    .toList();
            validateEdgeItems(nonNormal, sortNos);
            validateQtimeFields(dto.getEdges());
            mesRouteEdgeMapper.physicalDeleteByVersionId(versionId);
            Map<Integer, Integer> stepQtime = loadStepMaxQueueBySort(versionId);
            insertNormalEdgesFromSteps(versionId, items, normalQtime, stepQtime);
            int i = 0;
            for (MesRouteStepsSaveDTO.EdgeItem edge : nonNormal) {
                MesRouteEdge row = toEdgeEntity(versionId, edge, i++);
                mesRouteEdgeMapper.insert(row);
            }
        } else {
            Map<String, MesRouteEdge> oldNormals = loadNormalEdgeMap(versionId);
            mesRouteEdgeMapper.physicalDeleteNormalByVersionId(versionId);
            Map<Integer, Integer> stepQtime = loadStepMaxQueueBySort(versionId);
            insertNormalEdgesFromSteps(versionId, items, toOverlayFromEdges(oldNormals), stepQtime);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long versionId) {
        MesRouteVersion version = mesRouteVersionMapper.selectById(versionId);
        AssertUtil.notNull(version, "版本不存在");
        AssertUtil.isTrue(STATUS_DRAFT.equals(version.getStatus()), "仅草稿可发布");

        List<MesRouteStep> steps = mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, versionId)
                .orderByAsc(MesRouteStep::getSortNo));
        AssertUtil.notEmpty(steps, "至少配置一个步骤才能发布");
        validatePersistedSteps(steps);

        // 发布前按主数据刷新站属性快照
        refreshStepAttrsFromMaster(steps);
        steps = mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, versionId)
                .orderByAsc(MesRouteStep::getSortNo));
        stepEqpTypeGuard.assertPublishable(steps);

        // 发布瞬间再刷一遍 normal：保留旧 normal 上的 QueueTime，缺则回填步骤 max_queue_min
        Map<String, MesRouteEdge> oldNormals = loadNormalEdgeMap(versionId);
        mesRouteEdgeMapper.physicalDeleteNormalByVersionId(versionId);
        int ni = 0;
        for (MesRouteStep step : steps) {
            if (step.getNextSortNo() == null) {
                continue; // 终点站没有默认出边
            }
            MesRouteEdge row = new MesRouteEdge();
            row.setVersionId(versionId);
            row.setFromSortNo(step.getSortNo());
            row.setToSortNo(step.getNextSortNo());
            row.setEdgeType(EDGE_NORMAL);
            row.setSortNo(ni++);
            applyQtimeToNormal(row, oldNormals.get(step.getSortNo() + "-" + step.getNextSortNo()),
                    step.getMaxQueueMin());
            mesRouteEdgeMapper.insert(row);
        }

        Set<Integer> sortNos = steps.stream().map(MesRouteStep::getSortNo).collect(Collectors.toSet());
        List<MesRouteEdge> edges = mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId));
        validatePersistedEdges(edges, sortNos);
        validateNormalAndBranchRules(steps, edges); // 每站 1 条 normal、branch 条件不重复
        validateReworkCanReachMainEnd(steps, edges); // 回流目标沿 next 必须能走到终点
        validateSkipAllowRules(steps, edges); // 跳站前向可达 + allow_skip
        validateOffFlowRules(steps, edges); // Off-Flow 主/旁路不相交
        validateTimeLinkRules(edges, sortNos);
        // 旧版进行归档处理（修改状态）
        mesRouteVersionMapper.update(null, new LambdaUpdateWrapper<MesRouteVersion>()
                .eq(MesRouteVersion::getRouteId, version.getRouteId())
                .eq(MesRouteVersion::getStatus, STATUS_ACTIVE)
                .set(MesRouteVersion::getStatus, STATUS_ARCHIVED));

        version.setStatus(STATUS_ACTIVE);
        version.setPublishedAt(LocalDateTime.now());
        version.setPublishedBy(StpUtil.getLoginIdAsLong());
        mesRouteVersionMapper.updateById(version);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long upgrade(Long routeId, MesRouteUpgradeDTO dto) {
        MesRoute route = mesRouteMapper.selectById(routeId);
        AssertUtil.notNull(route, "路线不存在");

        MesRouteVersion from = mesRouteVersionMapper.selectById(dto.getFromVersionId());
        AssertUtil.notNull(from, "源版本不存在");
        AssertUtil.isTrue(Objects.equals(from.getRouteId(), routeId), "源版本不属于该路线");

        Long draftCount = mesRouteVersionMapper.selectCount(new LambdaQueryWrapper<MesRouteVersion>()
                .eq(MesRouteVersion::getRouteId, routeId)
                .eq(MesRouteVersion::getStatus, STATUS_DRAFT));
        AssertUtil.isTrue(draftCount == 0, "已有草稿版本，请先发布或继续编辑现有草稿");

        // 获取最大版本号
        Integer maxNo = mesRouteVersionMapper.selectList(new LambdaQueryWrapper<MesRouteVersion>()
                        .eq(MesRouteVersion::getRouteId, routeId)
                        .orderByDesc(MesRouteVersion::getVersionNo)
                        .last("LIMIT 1"))
                .stream()
                .map(MesRouteVersion::getVersionNo)
                .findFirst()
                .orElse(0);

        MesRouteVersion draft = new MesRouteVersion();
        draft.setRouteId(routeId);
        draft.setVersionNo(maxNo + 1);
        draft.setStatus(STATUS_DRAFT);
        draft.setRemark("基于 v" + from.getVersionNo() + " 升版");
        mesRouteVersionMapper.insert(draft);

        List<MesRouteStep> fromSteps = mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, from.getId())
                .orderByAsc(MesRouteStep::getSortNo));
        for (MesRouteStep src : fromSteps) {
            MesRouteStep row = new MesRouteStep();
            row.setVersionId(draft.getId());
            row.setStepId(src.getStepId());
            row.setSortNo(src.getSortNo());
            row.setNextSortNo(src.getNextSortNo());
            row.setEqpType(src.getEqpType());
            row.setStepType(src.getStepType());
            row.setAllowSkip(src.getAllowSkip());
            row.setMaxQueueMin(src.getMaxQueueMin());
            row.setMinProcessMin(src.getMinProcessMin());
            row.setMaxProcessMin(src.getMaxProcessMin());
            mesRouteStepMapper.insert(row);
        }

        // 边：只拷 branch/rework；normal 丢弃后按步骤 next 重建（与保存/发布同一策略）
        List<MesRouteEdge> fromEdges = mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, from.getId())
                .orderByAsc(MesRouteEdge::getSortNo));
        for (MesRouteEdge src : fromEdges) {
            if (EDGE_NORMAL.equals(src.getEdgeType())) {
                continue;
            }
            MesRouteEdge row = new MesRouteEdge();
            row.setVersionId(draft.getId());
            row.setFromSortNo(src.getFromSortNo());
            row.setToSortNo(src.getToSortNo());
            row.setEdgeType(src.getEdgeType());
            row.setMaxReworkCount(src.getMaxReworkCount());
            row.setMaxQueueMin(src.getMaxQueueMin());
            row.setMinQueueMin(src.getMinQueueMin());
            row.setOnViolate(src.getOnViolate());
            row.setReasonCodes(src.getReasonCodes());
            row.setConditionCode(src.getConditionCode());
            row.setSortNo(src.getSortNo());
            mesRouteEdgeMapper.insert(row);
        }
        // 按源步骤的 next 生成新草稿的默认出边（带 QueueTime）
        Map<String, MesRouteEdge> oldNormals = fromEdges.stream()
                .filter(e -> EDGE_NORMAL.equals(e.getEdgeType()))
                .collect(Collectors.toMap(
                        e -> e.getFromSortNo() + "-" + e.getToSortNo(),
                        e -> e,
                        (a, b) -> a));
        int ni = 0;
        for (MesRouteStep src : fromSteps) {
            if (src.getNextSortNo() == null) {
                continue;
            }
            MesRouteEdge row = new MesRouteEdge();
            row.setVersionId(draft.getId());
            row.setFromSortNo(src.getSortNo());
            row.setToSortNo(src.getNextSortNo());
            row.setEdgeType(EDGE_NORMAL);
            row.setSortNo(ni++);
            applyQtimeToNormal(row, oldNormals.get(src.getSortNo() + "-" + src.getNextSortNo()),
                    src.getMaxQueueMin());
            mesRouteEdgeMapper.insert(row);
        }
        return draft.getId();
    }

    /**
     * 获取当前版本的所有边
     */
    private List<MesRouteEdgeVO> listEdgeVos(Long versionId) {
        List<MesRouteEdge> edges = mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId)
                .orderByAsc(MesRouteEdge::getFromSortNo)
                .orderByAsc(MesRouteEdge::getSortNo));
        List<MesRouteEdgeVO> list = new ArrayList<>(edges.size());
        for (MesRouteEdge e : edges) {
            MesRouteEdgeVO vo = new MesRouteEdgeVO();
            vo.setId(e.getId());
            vo.setFromSortNo(e.getFromSortNo());
            vo.setToSortNo(e.getToSortNo());
            vo.setEdgeType(e.getEdgeType());
            vo.setMaxReworkCount(e.getMaxReworkCount());
            vo.setMaxQueueMin(e.getMaxQueueMin());
            vo.setMinQueueMin(e.getMinQueueMin());
            vo.setOnViolate(e.getOnViolate());
            vo.setReasonCodes(e.getReasonCodes());
            vo.setConditionCode(e.getConditionCode());
            vo.setSortNo(e.getSortNo());
            list.add(vo);
        }
        return list;
    }

    /** 把步骤链表 next_sort_no 落成 normal 边，供 TrackOut 默认选边 */
    private void insertNormalEdgesFromSteps(Long versionId, List<MesRouteStepsSaveDTO.Item> items,
                                            Map<String, MesRouteStepsSaveDTO.EdgeItem> qtimeOverlay,
                                            Map<Integer, Integer> stepQtime) {
        int i = 0;
        for (MesRouteStepsSaveDTO.Item item : items) {
            if (item.getNextSortNo() == null) {
                continue;
            }
            MesRouteEdge row = new MesRouteEdge();
            row.setVersionId(versionId);
            row.setFromSortNo(item.getSortNo());
            row.setToSortNo(item.getNextSortNo());
            row.setEdgeType(EDGE_NORMAL);
            row.setSortNo(i++);
            MesRouteStepsSaveDTO.EdgeItem overlay = qtimeOverlay == null ? null
                    : qtimeOverlay.get(item.getSortNo() + "-" + item.getNextSortNo());
            Integer stepMax = stepQtime == null ? null : stepQtime.get(item.getSortNo());
            if (overlay != null) {
                applyQtimeFromItem(row, overlay, stepMax);
            } else if (stepMax != null && stepMax >= 1) {
                row.setMaxQueueMin(stepMax);
            }
            mesRouteEdgeMapper.insert(row);
        }
    }

    private Map<String, MesRouteEdge> loadNormalEdgeMap(Long versionId) {
        return mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                        .eq(MesRouteEdge::getVersionId, versionId)
                        .eq(MesRouteEdge::getEdgeType, EDGE_NORMAL))
                .stream()
                .collect(Collectors.toMap(
                        e -> e.getFromSortNo() + "-" + e.getToSortNo(),
                        e -> e,
                        (a, b) -> a));
    }

    private Map<String, MesRouteStepsSaveDTO.EdgeItem> toOverlayFromEdges(Map<String, MesRouteEdge> edges) {
        Map<String, MesRouteStepsSaveDTO.EdgeItem> map = new HashMap<>();
        if (edges == null) {
            return map;
        }
        for (Map.Entry<String, MesRouteEdge> e : edges.entrySet()) {
            MesRouteStepsSaveDTO.EdgeItem item = new MesRouteStepsSaveDTO.EdgeItem();
            item.setMaxQueueMin(e.getValue().getMaxQueueMin());
            item.setMinQueueMin(e.getValue().getMinQueueMin());
            item.setOnViolate(e.getValue().getOnViolate());
            map.put(e.getKey(), item);
        }
        return map;
    }

    private Map<Integer, Integer> loadStepMaxQueueBySort(Long versionId) {
        return mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                        .eq(MesRouteStep::getVersionId, versionId))
                .stream()
                .filter(s -> s.getMaxQueueMin() != null && s.getMaxQueueMin() >= 1)
                .collect(Collectors.toMap(MesRouteStep::getSortNo, MesRouteStep::getMaxQueueMin, (a, b) -> a));
    }

    private void applyQtimeToNormal(MesRouteEdge row, MesRouteEdge old, Integer stepMax) {
        if (old != null && old.getMaxQueueMin() != null && old.getMaxQueueMin() >= 1) {
            row.setMaxQueueMin(old.getMaxQueueMin());
            row.setMinQueueMin(old.getMinQueueMin());
            row.setOnViolate(old.getOnViolate());
        } else if (stepMax != null && stepMax >= 1) {
            row.setMaxQueueMin(stepMax);
        }
    }

    private void applyQtimeFromItem(MesRouteEdge row, MesRouteStepsSaveDTO.EdgeItem item, Integer stepMax) {
        if (item.getMaxQueueMin() != null && item.getMaxQueueMin() >= 1) {
            row.setMaxQueueMin(item.getMaxQueueMin());
            row.setMinQueueMin(item.getMinQueueMin());
            row.setOnViolate(normalizeOnViolate(item.getOnViolate()));
        } else if (stepMax != null && stepMax >= 1) {
            row.setMaxQueueMin(stepMax);
        }
    }

    private String normalizeOnViolate(String onViolate) {
        if (!StringUtils.hasText(onViolate)) {
            return null;
        }
        String p = onViolate.trim().toUpperCase();
        AssertUtil.isTrue("HOLD".equals(p) || "ALARM".equals(p) || "HOLD_ALARM".equals(p),
                "非法 onViolate: " + onViolate);
        return p;
    }

    private void validateQtimeFields(List<MesRouteStepsSaveDTO.EdgeItem> edges) {
        for (MesRouteStepsSaveDTO.EdgeItem edge : edges) {
            if (edge.getMaxQueueMin() != null) {
                AssertUtil.isTrue(edge.getMaxQueueMin() >= 1, "maxQueueMin 须≥1");
            }
            if (StringUtils.hasText(edge.getOnViolate())) {
                normalizeOnViolate(edge.getOnViolate());
            }
        }
    }

    private void validateTimeLinkRules(List<MesRouteEdge> edges, Set<Integer> sortNos) {
        for (MesRouteEdge edge : edges) {
            if (!EDGE_TIME_LINK.equals(edge.getEdgeType())) {
                continue;
            }
            AssertUtil.notNull(edge.getMaxQueueMin(), "time_link 须配置 maxQueueMin");
            AssertUtil.isTrue(edge.getMaxQueueMin() >= 1, "maxQueueMin 须≥1");
            AssertUtil.isTrue(sortNos.contains(edge.getFromSortNo()) && sortNos.contains(edge.getToSortNo()),
                    "time_link 站序非法");
            AssertUtil.isFalse(Objects.equals(edge.getFromSortNo(), edge.getToSortNo()),
                    "time_link 不能指向自身");
        }
    }

    /** DTO → 实体；branch 的 conditionCode 统一转大写，方便 TrackOut 匹配 */
    private MesRouteEdge toEdgeEntity(Long versionId, MesRouteStepsSaveDTO.EdgeItem edge, int defaultSort) {
        String type = edge.getEdgeType().trim().toLowerCase();
        MesRouteEdge row = new MesRouteEdge();
        row.setVersionId(versionId);
        row.setFromSortNo(edge.getFromSortNo());
        row.setToSortNo(edge.getToSortNo());
        row.setEdgeType(type);
        row.setMaxReworkCount(edge.getMaxReworkCount());
        row.setMaxQueueMin(edge.getMaxQueueMin());
        row.setMinQueueMin(edge.getMinQueueMin());
        row.setOnViolate(normalizeOnViolate(edge.getOnViolate()));
        row.setReasonCodes(blankToNull(edge.getReasonCodes()));
        if (EDGE_BRANCH.equals(type) && StringUtils.hasText(edge.getConditionCode())) {
            row.setConditionCode(edge.getConditionCode().trim().toUpperCase());
        } else {
            row.setConditionCode(null);
        }
        row.setSortNo(edge.getSortNo() != null ? edge.getSortNo() : defaultSort);
        return row;
    }

    /**
     * 保存入参校验（不含 normal，normal 服务端自建）。
     * rework 要次数上限；branch 要条件码且同站不重复。
     */
    private void validateEdgeItems(List<MesRouteStepsSaveDTO.EdgeItem> edges, Set<Integer> sortNos) {
        Set<String> uniq = new HashSet<>();
        Set<String> branchConds = new HashSet<>();
        for (MesRouteStepsSaveDTO.EdgeItem edge : edges) {
            AssertUtil.notNull(edge.getFromSortNo(), "边起点不能为空");
            AssertUtil.notNull(edge.getToSortNo(), "边终点不能为空");
            AssertUtil.notBlank(edge.getEdgeType(), "边类型不能为空");
            String type = edge.getEdgeType().trim().toLowerCase();
            AssertUtil.isTrue(EDGE_BRANCH.equals(type) || EDGE_REWORK.equals(type)
                            || EDGE_NORMAL.equals(type) || EDGE_SKIP_ALLOW.equals(type)
                            || EDGE_OFF_FLOW.equals(type) || EDGE_TIME_LINK.equals(type),
                    "不支持的边类型: " + type);
            AssertUtil.isTrue(sortNos.contains(edge.getFromSortNo()),
                    "边起点顺序号不存在: " + edge.getFromSortNo());
            AssertUtil.isTrue(sortNos.contains(edge.getToSortNo()),
                    "边终点顺序号不存在: " + edge.getToSortNo());
            AssertUtil.isFalse(Objects.equals(edge.getFromSortNo(), edge.getToSortNo()),
                    "边不能指向自身");
            String key = edge.getFromSortNo() + "-" + edge.getToSortNo() + "-" + type;
            AssertUtil.isTrue(uniq.add(key), "边重复: " + key);
            if (EDGE_REWORK.equals(type)) {
                AssertUtil.notNull(edge.getMaxReworkCount(), "回流边须配置次数上限");
                AssertUtil.isTrue(edge.getMaxReworkCount() >= 1, "回流次数上限须≥1");
                AssertUtil.isTrue(!StringUtils.hasText(edge.getConditionCode()), "回流边不能配置条件码");
            }
            if (EDGE_BRANCH.equals(type)) {
                AssertUtil.notBlank(edge.getConditionCode(), "分支边须配置条件码");
                String cond = edge.getConditionCode().trim().toUpperCase();
                String ck = edge.getFromSortNo() + "-" + cond;
                AssertUtil.isTrue(branchConds.add(ck), "同站分支条件码重复: " + cond);
            }
            if (EDGE_SKIP_ALLOW.equals(type)) {
                AssertUtil.isTrue(!StringUtils.hasText(edge.getConditionCode()), "跳站边不能配置条件码");
                AssertUtil.isTrue(edge.getMaxReworkCount() == null, "跳站边不能配置回流次数");
            }
            if (EDGE_OFF_FLOW.equals(type)) {
                AssertUtil.notNull(edge.getMaxReworkCount(), "Off-Flow 边须配置次数上限");
                AssertUtil.isTrue(edge.getMaxReworkCount() >= 1, "Off-Flow 次数上限须≥1");
                AssertUtil.isTrue(!StringUtils.hasText(edge.getConditionCode()), "Off-Flow 边不能配置条件码");
            }
            if (EDGE_TIME_LINK.equals(type)) {
                AssertUtil.notNull(edge.getMaxQueueMin(), "time_link 须配置 maxQueueMin");
                AssertUtil.isTrue(edge.getMaxQueueMin() >= 1, "maxQueueMin 须≥1");
                AssertUtil.isTrue(!StringUtils.hasText(edge.getConditionCode()), "time_link 不能配置条件码");
                AssertUtil.isTrue(edge.getMaxReworkCount() == null, "time_link 不能配置回流次数");
            }
            if (EDGE_NORMAL.equals(type)) {
                AssertUtil.isTrue(!StringUtils.hasText(edge.getConditionCode()), "默认边不能配置条件码");
            }
        }
    }

    /** 校验已保存的边合法性 */
    private void validatePersistedEdges(List<MesRouteEdge> edges, Set<Integer> sortNos) {
        for (MesRouteEdge edge : edges) {
            AssertUtil.isTrue(sortNos.contains(edge.getFromSortNo()),
                    "边起点顺序号不存在: " + edge.getFromSortNo());
            AssertUtil.isTrue(sortNos.contains(edge.getToSortNo()),
                    "边终点顺序号不存在: " + edge.getToSortNo());
            if (EDGE_REWORK.equals(edge.getEdgeType())) {
                AssertUtil.notNull(edge.getMaxReworkCount(), "回流边须配置次数上限");
                AssertUtil.isTrue(edge.getMaxReworkCount() >= 1, "回流次数上限须≥1");
                AssertUtil.isFalse(Objects.equals(edge.getFromSortNo(), edge.getToSortNo()),
                        "回流边不能指向自身");
            }
            if (EDGE_BRANCH.equals(edge.getEdgeType())) {
                AssertUtil.notBlank(edge.getConditionCode(), "分支边须配置条件码");
                AssertUtil.isFalse(Objects.equals(edge.getFromSortNo(), edge.getToSortNo()),
                        "分支边不能指向自身");
            }
            if (EDGE_SKIP_ALLOW.equals(edge.getEdgeType())) {
                AssertUtil.isFalse(Objects.equals(edge.getFromSortNo(), edge.getToSortNo()),
                        "跳站边不能指向自身");
            }
            if (EDGE_OFF_FLOW.equals(edge.getEdgeType())) {
                AssertUtil.notNull(edge.getMaxReworkCount(), "Off-Flow 边须配置次数上限");
                AssertUtil.isTrue(edge.getMaxReworkCount() >= 1, "Off-Flow 次数上限须≥1");
                AssertUtil.isFalse(Objects.equals(edge.getFromSortNo(), edge.getToSortNo()),
                        "Off-Flow 边不能指向自身");
            }
            if (edge.getMaxQueueMin() != null) {
                AssertUtil.isTrue(edge.getMaxQueueMin() >= 1, "maxQueueMin 须≥1");
            }
            if (EDGE_TIME_LINK.equals(edge.getEdgeType())) {
                AssertUtil.notNull(edge.getMaxQueueMin(), "time_link 须配置 maxQueueMin");
                AssertUtil.isFalse(Objects.equals(edge.getFromSortNo(), edge.getToSortNo()),
                        "time_link 不能指向自身");
            }
        }
    }

    /**
     * 发布结构校验：
     * - 有 next 的站：必须恰好 1 条 normal，且 to == next_sort_no
     * - 终点（next=null）：不能有 normal
     * - 同站 branch 的 condition_code 不重复
     */
    private void validateNormalAndBranchRules(List<MesRouteStep> steps, List<MesRouteEdge> edges) {
        Map<Integer, List<MesRouteEdge>> normalsByFrom = edges.stream()
                .filter(e -> EDGE_NORMAL.equals(e.getEdgeType()))
                .collect(Collectors.groupingBy(MesRouteEdge::getFromSortNo));
        Map<Integer, List<MesRouteEdge>> branchesByFrom = edges.stream()
                .filter(e -> EDGE_BRANCH.equals(e.getEdgeType()))
                .collect(Collectors.groupingBy(MesRouteEdge::getFromSortNo));

        for (MesRouteStep step : steps) {
            List<MesRouteEdge> normals = normalsByFrom.getOrDefault(step.getSortNo(), List.of());
            if (step.getNextSortNo() == null) {
                AssertUtil.isTrue(normals.isEmpty(),
                        "终点站不能配置默认出边: " + step.getSortNo());
            } else {
                AssertUtil.isTrue(normals.size() == 1,
                        "非终点站须恰好一条默认出边: " + step.getSortNo());
                AssertUtil.isTrue(Objects.equals(normals.get(0).getToSortNo(), step.getNextSortNo()),
                        "默认出边与步骤下一站不一致: " + step.getSortNo());
            }
            List<MesRouteEdge> branches = branchesByFrom.getOrDefault(step.getSortNo(), List.of());
            Set<String> conds = new HashSet<>();
            for (MesRouteEdge b : branches) {
                String cond = b.getConditionCode().trim().toUpperCase();
                AssertUtil.isTrue(conds.add(cond),
                        "同站分支条件码重复: " + step.getSortNo() + "/" + cond);
            }
        }
    }

    /**
     * 回流可达性：从 rework 的目标站出发，只沿步骤 next 走，必须碰到终点（next=null）。
     * 中途断链或 next 成环 → 发布失败，避免 Lot 回流后卡死。
     */
    private void validateReworkCanReachMainEnd(List<MesRouteStep> steps, List<MesRouteEdge> edges) {
        Map<Integer, Integer> nextMap = new HashMap<>();
        for (MesRouteStep step : steps) {
            nextMap.put(step.getSortNo(), step.getNextSortNo());
        }
        for (MesRouteEdge edge : edges) {
            if (!EDGE_REWORK.equals(edge.getEdgeType())) {
                continue;
            }
            Integer cur = edge.getToSortNo();
            Set<Integer> visited = new HashSet<>();
            boolean reachedEnd = false;
            while (cur != null) {
                AssertUtil.isTrue(nextMap.containsKey(cur),
                        "回流后主路径断链，无法到达终点: " + edge.getFromSortNo() + "→" + edge.getToSortNo()
                                + "（断于 " + cur + "）");
                AssertUtil.isTrue(visited.add(cur),
                        "回流后无法到达终点（主路径成环）: " + edge.getFromSortNo() + "→" + edge.getToSortNo());
                Integer next = nextMap.get(cur);
                if (next == null) {
                    reachedEnd = true;
                    break;
                }
                cur = next;
            }
            AssertUtil.isTrue(reachedEnd,
                    "回流后无法到达主路径终点: " + edge.getFromSortNo() + "→" + edge.getToSortNo());
        }
    }

    /**
     * 跳站边：from 沿 next 前向可达 to；from/to/中间站 allow_skip=1；同站目标不重复。
     */
    private void validateSkipAllowRules(List<MesRouteStep> steps, List<MesRouteEdge> edges) {
        Map<Integer, MesRouteStep> stepBySort = steps.stream()
                .collect(Collectors.toMap(MesRouteStep::getSortNo, s -> s, (a, b) -> a));
        // next 可为 null（终点），Collectors.toMap 不允许 null value
        Map<Integer, Integer> nextMap = new HashMap<>();
        for (MesRouteStep step : steps) {
            nextMap.put(step.getSortNo(), step.getNextSortNo());
        }
        Set<String> skipTargets = new HashSet<>();
        for (MesRouteEdge edge : edges) {
            if (!EDGE_SKIP_ALLOW.equals(edge.getEdgeType())) {
                continue;
            }
            String tk = edge.getFromSortNo() + "-" + edge.getToSortNo();
            AssertUtil.isTrue(skipTargets.add(tk),
                    "同站跳站目标重复: " + edge.getFromSortNo() + "→" + edge.getToSortNo());

            List<Integer> skipped = new ArrayList<>();
            Set<Integer> visited = new HashSet<>();
            Integer cur = nextMap.get(edge.getFromSortNo());
            boolean reached = false;
            while (cur != null) {
                AssertUtil.isTrue(visited.add(cur),
                        "跳站路径主路径成环: " + edge.getFromSortNo() + "→" + edge.getToSortNo());
                if (Objects.equals(cur, edge.getToSortNo())) {
                    reached = true;
                    break;
                }
                skipped.add(cur);
                cur = nextMap.get(cur);
            }
            AssertUtil.isTrue(reached,
                    "跳站目标不可沿主路径前向到达: " + edge.getFromSortNo() + "→" + edge.getToSortNo());

            Set<Integer> need = new HashSet<>();
            need.add(edge.getFromSortNo());
            need.add(edge.getToSortNo());
            need.addAll(skipped);
            for (Integer sortNo : need) {
                MesRouteStep step = stepBySort.get(sortNo);
                AssertUtil.notNull(step, "跳站路径站不存在: " + sortNo);
                AssertUtil.isTrue(Integer.valueOf(1).equals(step.getAllowSkip()),
                        "跳站路径含不可跳站: sortNo=" + sortNo);
            }
        }
    }

    /**
     * Off-Flow：from 在主路径；to 及旁路链不与主路径相交；旁路无环且有末站。
     */
    private void validateOffFlowRules(List<MesRouteStep> steps, List<MesRouteEdge> edges) {
        Map<Integer, Integer> nextMap = new HashMap<>();
        Integer start = null;
        for (MesRouteStep step : steps) {
            nextMap.put(step.getSortNo(), step.getNextSortNo());
            if (start == null || step.getSortNo() < start) {
                start = step.getSortNo();
            }
        }
        Set<Integer> mainPath = new HashSet<>();
        Integer cur = start;
        while (cur != null && mainPath.add(cur)) {
            cur = nextMap.get(cur);
        }

        Set<String> offTargets = new HashSet<>();
        for (MesRouteEdge edge : edges) {
            if (!EDGE_OFF_FLOW.equals(edge.getEdgeType())) {
                continue;
            }
            String tk = edge.getFromSortNo() + "-" + edge.getToSortNo();
            AssertUtil.isTrue(offTargets.add(tk),
                    "同站 Off-Flow 目标重复: " + edge.getFromSortNo() + "→" + edge.getToSortNo());
            AssertUtil.isTrue(mainPath.contains(edge.getFromSortNo()),
                    "Off-Flow 触发站须在主路径: " + edge.getFromSortNo());
            AssertUtil.isFalse(mainPath.contains(edge.getToSortNo()),
                    "Off-Flow 入口不能在主路径: " + edge.getToSortNo());
            AssertUtil.isFalse(Objects.equals(edge.getToSortNo(), start),
                    "Off-Flow 入口不能是路线起点");

            List<Integer> chain = new ArrayList<>();
            Set<Integer> visited = new HashSet<>();
            Integer walk = edge.getToSortNo();
            while (walk != null) {
                AssertUtil.isTrue(nextMap.containsKey(walk),
                        "Off-Flow 旁路站不存在: " + walk);
                AssertUtil.isTrue(visited.add(walk),
                        "Off-Flow 旁路成环: " + edge.getToSortNo());
                AssertUtil.isFalse(mainPath.contains(walk),
                        "Off-Flow 旁路与主路径相交: sortNo=" + walk);
                chain.add(walk);
                walk = nextMap.get(walk);
            }
            AssertUtil.isFalse(chain.isEmpty(), "Off-Flow 旁路为空");
        }
    }

    /** 从主数据拷贝站属性到路线步骤快照 */
    private static void copyAttrsFromMaster(MesRouteStep row, MesStep master) {
        if (master == null) {
            return;
        }
        row.setEqpType(blankToNull(master.getEqpType()));
        row.setStepType(master.getStepType());
        row.setAllowSkip(master.getAllowSkip());
        row.setMaxQueueMin(master.getMaxQueueMin());
        row.setMinProcessMin(master.getMinProcessMin());
        row.setMaxProcessMin(master.getMaxProcessMin());
    }

    private void refreshStepAttrsFromMaster(List<MesRouteStep> steps) {
        Map<Long, MesStep> stepMap = loadStepMap(steps.stream().map(MesRouteStep::getStepId).toList());
        for (MesRouteStep row : steps) {
            MesStep master = stepMap.get(row.getStepId());
            AssertUtil.notNull(master, "工序不存在: " + row.getStepId());
            copyAttrsFromMaster(row, master);
            mesRouteStepMapper.updateById(row);
        }
    }

    /** 批量加载工序主数据 */
    private Map<Long, MesStep> loadStepMap(List<Long> stepIds) {
        if (stepIds == null || stepIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = stepIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return mesStepMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a));
    }

    /** 校验保存入参：顺序号唯一、下一站合法、工序存在且启用 */
    private void validateStepItems(List<MesRouteStepsSaveDTO.Item> items, boolean requireEnabledStep) {
        if (items.isEmpty()) {
            return;
        }
        Set<Integer> sortNos = new HashSet<>();
        for (MesRouteStepsSaveDTO.Item item : items) {
            AssertUtil.notNull(item.getStepId(), "工序不能为空");
            AssertUtil.notNull(item.getSortNo(), "顺序号不能为空");
            AssertUtil.isTrue(sortNos.add(item.getSortNo()), "顺序号重复: " + item.getSortNo());
        }
        for (MesRouteStepsSaveDTO.Item item : items) {
            if (item.getNextSortNo() != null) {
                AssertUtil.isTrue(sortNos.contains(item.getNextSortNo()),
                        "下一站顺序号不存在: " + item.getNextSortNo());
                AssertUtil.isFalse(Objects.equals(item.getSortNo(), item.getNextSortNo()),
                        "下一站不能指向自身");
            }
        }

        List<Long> stepIds = items.stream().map(MesRouteStepsSaveDTO.Item::getStepId).distinct().toList();
        Map<Long, MesStep> stepMap = mesStepMapper.selectBatchIds(stepIds).stream()
                .collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a));
        AssertUtil.isTrue(stepMap.size() == stepIds.size(), "存在无效工序");
        if (requireEnabledStep) {
            for (Long stepId : stepIds) {
                MesStep step = stepMap.get(stepId);
                AssertUtil.isTrue(step.getStatus() != null && step.getStatus() == 1,
                        "工序已禁用，不能挂到草稿: " + step.getStepCode());
            }
        }
    }

    /** 发布前校验已落库步骤的顺序与下一站 */
    private void validatePersistedSteps(List<MesRouteStep> steps) {
        Set<Integer> sortNos = new HashSet<>();
        for (MesRouteStep step : steps) {
            AssertUtil.isTrue(sortNos.add(step.getSortNo()), "顺序号重复: " + step.getSortNo());
        }
        for (MesRouteStep step : steps) {
            if (step.getNextSortNo() != null) {
                AssertUtil.isTrue(sortNos.contains(step.getNextSortNo()),
                        "下一站顺序号不存在: " + step.getNextSortNo());
            }
            validate(
                    step.getMinProcessMin(), step.getMaxProcessMin());
        }
    }

    private static MesRouteVO toRouteVo(MesRoute route) {
        MesRouteVO vo = new MesRouteVO();
        vo.setId(route.getId());
        vo.setRouteCode(route.getRouteCode());
        vo.setRouteName(route.getRouteName());
        vo.setProductCode(route.getProductCode());
        vo.setStatus(route.getStatus());
        vo.setRemark(route.getRemark());
        vo.setCreateTime(route.getCreateTime());
        vo.setUpdateTime(route.getUpdateTime());
        return vo;
    }

    private static MesRouteVersionVO toVersionVo(MesRouteVersion v) {
        MesRouteVersionVO vo = new MesRouteVersionVO();
        vo.setId(v.getId());
        vo.setRouteId(v.getRouteId());
        vo.setVersionNo(v.getVersionNo());
        vo.setStatus(v.getStatus());
        vo.setPublishedAt(v.getPublishedAt());
        vo.setPublishedBy(v.getPublishedBy());
        vo.setRemark(v.getRemark());
        vo.setCreateTime(v.getCreateTime());
        vo.setUpdateTime(v.getUpdateTime());
        return vo;
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
