package com.mes.wip.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.PageResult;
import com.mes.route.entity.MesRoute;
import com.mes.route.entity.MesRouteVersion;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesRouteMapper;
import com.mes.route.mapper.MesRouteVersionMapper;
import com.mes.route.mapper.MesStepMapper;
import com.mes.wip.dto.MesWipQuery;
import com.mes.wip.entity.MesWipLot;
import com.mes.wip.mapper.MesWipLotMapper;
import com.mes.wip.service.WipService;
import com.mes.wip.vo.MesWipStepSummaryVO;
import com.mes.wip.vo.MesWipVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WipServiceImpl implements WipService {

    private static final List<String> DEFAULT_STATUSES = List.of(
            WipProjectionServiceImpl.STATUS_WAIT,
            WipProjectionServiceImpl.STATUS_PROCESSING,
            WipProjectionServiceImpl.STATUS_HELD);

    private final MesWipLotMapper mesWipLotMapper;
    private final MesStepMapper mesStepMapper;
    private final MesRouteMapper mesRouteMapper;
    private final MesRouteVersionMapper mesRouteVersionMapper;

    @Override
    public PageResult<MesWipVO> page(MesWipQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        Page<MesWipLot> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<MesWipLot> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            qw.and(w -> w.like(MesWipLot::getLotNo, keyword)
                    .or().like(MesWipLot::getProductCode, keyword)
                    .or().like(MesWipLot::getCustomerLot, keyword));
        }
        if (StringUtils.hasText(query.getStatus())) {
            qw.eq(MesWipLot::getStatus, query.getStatus().trim());
        } else {
            qw.in(MesWipLot::getStatus, DEFAULT_STATUSES);
        }
        if (StringUtils.hasText(query.getProductCode())) {
            qw.eq(MesWipLot::getProductCode, query.getProductCode().trim());
        }
        if (query.getCurrentSortNo() != null) {
            qw.eq(MesWipLot::getCurrentSortNo, query.getCurrentSortNo());
        }
        qw.orderByDesc(MesWipLot::getHotFlag).orderByDesc(MesWipLot::getPriority)
                .orderByAsc(MesWipLot::getCurrentSortNo).orderByAsc(MesWipLot::getLotNo);

        Page<MesWipLot> result = mesWipLotMapper.selectPage(page, qw);
        List<MesWipLot> rows = result.getRecords();
        if (rows.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), pageNo, pageSize);
        }

        Set<Long> stepIds = rows.stream().map(MesWipLot::getCurrentStepId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> routeIds = rows.stream().map(MesWipLot::getRouteId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> versionIds = rows.stream().map(MesWipLot::getRouteVersionId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, MesStep> stepMap = stepIds.isEmpty() ? Collections.emptyMap()
                : mesStepMapper.selectBatchIds(stepIds).stream().collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a));
        Map<Long, MesRoute> routeMap = routeIds.isEmpty() ? Collections.emptyMap()
                : mesRouteMapper.selectBatchIds(routeIds).stream().collect(Collectors.toMap(MesRoute::getId, r -> r, (a, b) -> a));
        Map<Long, MesRouteVersion> versionMap = versionIds.isEmpty() ? Collections.emptyMap()
                : mesRouteVersionMapper.selectBatchIds(versionIds).stream()
                .collect(Collectors.toMap(MesRouteVersion::getId, v -> v, (a, b) -> a));

        List<MesWipVO> records = new ArrayList<>(rows.size());
        for (MesWipLot row : rows) {
            records.add(toVo(row, stepMap.get(row.getCurrentStepId()), routeMap.get(row.getRouteId()),
                    versionMap.get(row.getRouteVersionId())));
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    public List<MesWipStepSummaryVO> summaryByStep() {
        List<MesWipStepSummaryVO> rows = mesWipLotMapper.summaryByStep();
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> stepIds = rows.stream().map(MesWipStepSummaryVO::getStepId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MesStep> stepMap = stepIds.isEmpty() ? Collections.emptyMap()
                : mesStepMapper.selectBatchIds(stepIds).stream().collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a));
        for (MesWipStepSummaryVO row : rows) {
            if (row.getWaitCount() == null) {
                row.setWaitCount(0L);
            }
            if (row.getProcessingCount() == null) {
                row.setProcessingCount(0L);
            }
            if (row.getHeldCount() == null) {
                row.setHeldCount(0L);
            }
            if (row.getTotal() == null) {
                row.setTotal(0L);
            }
            MesStep step = stepMap.get(row.getStepId());
            if (step != null) {
                row.setStepCode(step.getStepCode());
                row.setStepName(step.getStepName());
            }
        }
        return rows;
    }

    private static MesWipVO toVo(MesWipLot row, MesStep step, MesRoute route, MesRouteVersion version) {
        MesWipVO vo = new MesWipVO();
        vo.setLotId(row.getLotId());
        vo.setLotNo(row.getLotNo());
        vo.setProductCode(row.getProductCode());
        vo.setQty(row.getQty());
        vo.setPriority(row.getPriority());
        vo.setHotFlag(row.getHotFlag());
        vo.setCustomerLot(row.getCustomerLot());
        vo.setStatus(row.getStatus());
        vo.setCurrentSortNo(row.getCurrentSortNo());
        vo.setCurrentStepId(row.getCurrentStepId());
        if (step != null) {
            vo.setCurrentStepCode(step.getStepCode());
            vo.setCurrentStepName(step.getStepName());
        }
        vo.setCurrentEqpId(row.getCurrentEqpId());
        vo.setRouteId(row.getRouteId());
        if (route != null) {
            vo.setRouteCode(route.getRouteCode());
            vo.setRouteName(route.getRouteName());
        }
        vo.setRouteVersionId(row.getRouteVersionId());
        if (version != null) {
            vo.setRouteVersionNo(version.getVersionNo());
        }
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }
}
