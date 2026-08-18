package com.mes.route.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.common.AssertUtil;
import com.mes.route.entity.MesRouteEdge;
import com.mes.route.entity.MesRouteStep;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesRouteEdgeMapper;
import com.mes.route.mapper.MesRouteStepMapper;
import com.mes.route.mapper.MesStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运行时选边：TrackOut / context / rework 共用，避免 Impl 里抄三遍。
 */
@Component
@RequiredArgsConstructor
public class RouteEdgeResolver {

    private final MesRouteEdgeMapper mesRouteEdgeMapper;
    private final MesRouteStepMapper mesRouteStepMapper;
    private final MesStepMapper mesStepMapper;

    public List<MesRouteEdge> listNormalAndBranch(Long versionId, Integer fromSortNo) {
        return mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId)
                .eq(MesRouteEdge::getFromSortNo, fromSortNo)
                .in(MesRouteEdge::getEdgeType, RouteEdgeTypes.NORMAL, RouteEdgeTypes.BRANCH)
                .orderByAsc(MesRouteEdge::getSortNo));
    }

    public List<MesRouteEdge> listRework(Long versionId, Integer fromSortNo) {
        return mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId)
                .eq(MesRouteEdge::getFromSortNo, fromSortNo)
                .eq(MesRouteEdge::getEdgeType, RouteEdgeTypes.REWORK)
                .orderByAsc(MesRouteEdge::getSortNo));
    }

    public MesRouteEdge findRework(Long versionId, Integer fromSortNo, Integer toSortNo) {
        return mesRouteEdgeMapper.selectOne(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId)
                .eq(MesRouteEdge::getFromSortNo, fromSortNo)
                .eq(MesRouteEdge::getToSortNo, toSortNo)
                .eq(MesRouteEdge::getEdgeType, RouteEdgeTypes.REWORK)
                .last("LIMIT 1"));
    }

    /**
     * 获取skip边数组
     */
    public List<MesRouteEdge> listSkip(Long versionId, Integer fromSortNo) {
        return mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId)
                .eq(MesRouteEdge::getFromSortNo, fromSortNo)
                .eq(MesRouteEdge::getEdgeType, RouteEdgeTypes.SKIP_ALLOW)
                .orderByAsc(MesRouteEdge::getSortNo));
    }

    /**
     * 获取skip边
     */
    public MesRouteEdge findSkip(Long versionId, Integer fromSortNo, Integer toSortNo) {
        return mesRouteEdgeMapper.selectOne(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId)
                .eq(MesRouteEdge::getFromSortNo, fromSortNo)
                .eq(MesRouteEdge::getToSortNo, toSortNo)
                .eq(MesRouteEdge::getEdgeType, RouteEdgeTypes.SKIP_ALLOW)
                .last("LIMIT 1"));
    }

    /**
     * 获取off-flow边数组
     */
    public List<MesRouteEdge> listOffFlow(Long versionId, Integer fromSortNo) {
        return mesRouteEdgeMapper.selectList(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId)
                .eq(MesRouteEdge::getFromSortNo, fromSortNo)
                .eq(MesRouteEdge::getEdgeType, RouteEdgeTypes.OFF_FLOW)
                .orderByAsc(MesRouteEdge::getSortNo));
    }

    /**
     * 获取off-flow边
     */
    public MesRouteEdge findOffFlow(Long versionId, Integer fromSortNo, Integer toSortNo) {
        return mesRouteEdgeMapper.selectOne(new LambdaQueryWrapper<MesRouteEdge>()
                .eq(MesRouteEdge::getVersionId, versionId)
                .eq(MesRouteEdge::getFromSortNo, fromSortNo)
                .eq(MesRouteEdge::getToSortNo, toSortNo)
                .eq(MesRouteEdge::getEdgeType, RouteEdgeTypes.OFF_FLOW)
                .last("LIMIT 1"));
    }

    /** 从起点（最小 sort）沿 next 可达的主路径站集合 */
    public Set<Integer> computeMainPathSortNos(Long versionId) {
        List<MesRouteStep> steps = mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, versionId));
        if (steps.isEmpty()) {
            return Collections.emptySet();
        }
        Map<Integer, Integer> nextMap = new HashMap<>();
        Integer start = null;
        for (MesRouteStep step : steps) {
            nextMap.put(step.getSortNo(), step.getNextSortNo());
            if (start == null || step.getSortNo() < start) {
                start = step.getSortNo();
            }
        }
        return walkAlongNext(start, nextMap);
    }

    /** 从 entry 沿 next 走到终点（含 entry）；成环返回 null */
    public List<Integer> walkOffFlowChain(Long versionId, Integer entrySortNo) {
        if (versionId == null || entrySortNo == null) {
            return null;
        }
        List<MesRouteStep> steps = mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, versionId));
        Map<Integer, Integer> nextMap = new HashMap<>();
        for (MesRouteStep step : steps) {
            nextMap.put(step.getSortNo(), step.getNextSortNo());
        }
        if (!nextMap.containsKey(entrySortNo)) {
            return null;
        }
        List<Integer> chain = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Integer cur = entrySortNo;
        while (cur != null) {
            if (!visited.add(cur)) {
                return null;
            }
            chain.add(cur);
            cur = nextMap.get(cur);
        }
        return chain;
    }

    public boolean isOffFlowTerminal(Long versionId, Integer sortNo) {
        MesRouteStep step = findStep(versionId, sortNo);
        if (step == null) {
            return false;
        }
        return step.getNextSortNo() == null;
    }

    private static Set<Integer> walkAlongNext(Integer start, Map<Integer, Integer> nextMap) {
        Set<Integer> path = new HashSet<>();
        Integer cur = start;
        while (cur != null && path.add(cur)) {
            cur = nextMap.get(cur);
        }
        return path;
    }

    /**
     * 主路径 from→to 开区间内的站序；不可达返回 null。
     */
    public List<Integer> computeSkippedSortNos(Long versionId, Integer fromSortNo, Integer toSortNo) {
        if (versionId == null || fromSortNo == null || toSortNo == null
                || Objects.equals(fromSortNo, toSortNo)) {
            return null;
        }
        List<MesRouteStep> steps = mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, versionId));
        Map<Integer, Integer> nextMap = new HashMap<>();
        for (MesRouteStep step : steps) {
            nextMap.put(step.getSortNo(), step.getNextSortNo());
        }
        List<Integer> skipped = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Integer cur = nextMap.get(fromSortNo);
        while (cur != null) {
            if (!visited.add(cur)) {
                return null;
            }
            if (Objects.equals(cur, toSortNo)) {
                return skipped;
            }
            skipped.add(cur);
            cur = nextMap.get(cur);
        }
        return null;
    }

    /** 路径上 from、to、中间站 allow_skip 均为 1 */
    public Integer findDisallowedSkipSort(Long versionId, Integer fromSortNo, Integer toSortNo,
                                          List<Integer> skippedSortNos) {
        Set<Integer> need = new HashSet<>();
        need.add(fromSortNo);
        need.add(toSortNo);
        if (skippedSortNos != null) {
            need.addAll(skippedSortNos);
        }
        Map<Integer, MesRouteStep> map = mapRouteStepsBySort(versionId, need);
        for (Integer sortNo : need) {
            MesRouteStep step = map.get(sortNo);
            if (step == null || !Integer.valueOf(1).equals(step.getAllowSkip())) {
                return sortNo;
            }
        }
        return null;
    }

    /**
     * 获取指定站序的站点信息
     */
    public MesRouteStep findStep(Long versionId, Integer sortNo) {
        if (versionId == null || sortNo == null) {
            return null;
        }
        return mesRouteStepMapper.selectOne(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, versionId)
                .eq(MesRouteStep::getSortNo, sortNo)
                .last("LIMIT 1"));
    }

    public Map<Integer, MesRouteStep> mapRouteStepsBySort(Long versionId, Collection<Integer> sortNos) {
        if (sortNos == null || sortNos.isEmpty()) {
            return Collections.emptyMap();
        }
        return mesRouteStepMapper.selectList(new LambdaQueryWrapper<MesRouteStep>()
                        .eq(MesRouteStep::getVersionId, versionId)
                        .in(MesRouteStep::getSortNo, sortNos))
                .stream()
                .collect(Collectors.toMap(MesRouteStep::getSortNo, s -> s, (a, b) -> a));
    }

    public Map<Long, MesStep> mapStepsById(Collection<Long> stepIds) {
        if (stepIds == null || stepIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return mesStepMapper.selectBatchIds(stepIds).stream()
                .collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a));
    }

    /** context 默认下一站：normal 优先，否则步骤 next */
    public MesRouteStep resolveDefaultNext(Long versionId, MesRouteStep current) {
        List<MesRouteEdge> outEdges = listNormalAndBranch(versionId, current.getSortNo());
        MesRouteEdge normal = findNormal(outEdges);
        Integer nextSort = normal != null ? normal.getToSortNo() : current.getNextSortNo();
        return findStep(versionId, nextSort);
    }

    /**
     * TrackOut 选边：
     * 有 resultCode → 只匹配 branch；无 → normal / next；永不走 rework。
     */
    public RouteTrackOutDecision resolveTrackOut(Long versionId, MesRouteStep current, String resultCode) {
        String code = StringUtils.hasText(resultCode) ? resultCode.trim().toUpperCase() : null;
        List<MesRouteEdge> outEdges = listNormalAndBranch(versionId, current.getSortNo());

        if (StringUtils.hasText(code)) {
            MesRouteEdge matched = outEdges.stream()
                    .filter(e -> RouteEdgeTypes.BRANCH.equals(e.getEdgeType())
                            && code.equalsIgnoreCase(e.getConditionCode()))
                    .findFirst()
                    .orElse(null);
            AssertUtil.notNull(matched, "未配置分支条件: " + code);
            MesRouteStep next = findStep(versionId, matched.getToSortNo());
            AssertUtil.notNull(next, "分支目标站不存在");
            return new RouteTrackOutDecision(false, next.getSortNo(), next.getStepId(),
                    "完工并分支进入下一站", matched, code);
        }

        MesRouteEdge normal = findNormal(outEdges);
        Integer nextSort = normal != null ? normal.getToSortNo() : current.getNextSortNo();
        if (nextSort == null) {
            return new RouteTrackOutDecision(true, current.getSortNo(), current.getStepId(),
                    "末站完工", normal, null);
        }
        MesRouteStep next = findStep(versionId, nextSort);
        AssertUtil.notNull(next, "下一站不存在，路线数据异常");
        return new RouteTrackOutDecision(false, next.getSortNo(), next.getStepId(),
                "完工并进入下一站", normal, null);
    }

    private static MesRouteEdge findNormal(List<MesRouteEdge> outEdges) {
        return outEdges.stream()
                .filter(e -> RouteEdgeTypes.NORMAL.equals(e.getEdgeType()))
                .findFirst()
                .orElse(null);
    }
}
