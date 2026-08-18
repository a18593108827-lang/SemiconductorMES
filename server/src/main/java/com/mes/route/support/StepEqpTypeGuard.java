package com.mes.route.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.common.AssertUtil;
import com.mes.equipment.entity.MesEqp;
import com.mes.equipment.mapper.MesEqpMapper;
import com.mes.lot.entity.MesLot;
import com.mes.route.entity.MesRouteStep;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesRouteStepMapper;
import com.mes.route.mapper.MesStepMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 站设备类型校验：Dispatch / TrackIn 共用；优先读 RouteStep 快照。
 */
@Component
@RequiredArgsConstructor
public class StepEqpTypeGuard {

    private static final Logger log = LoggerFactory.getLogger(StepEqpTypeGuard.class);

    public static final int STEP_TYPE_PROCESS = 1;

    private final MesRouteStepMapper mesRouteStepMapper;
    private final MesStepMapper mesStepMapper;
    private final MesEqpMapper mesEqpMapper;

    @Value("${mes.step.eqp-type-mode:soft}")
    private String eqpTypeMode;

    public boolean isOff() {
        return "off".equalsIgnoreCase(trimMode());
    }

    public boolean isForce() {
        return "force".equalsIgnoreCase(trimMode());
    }

    /** 解析当前站要求的设备类型；off 恒为 null */
    public String resolveRequired(MesLot lot) {
        if (isOff() || lot == null) {
            return null;
        }
        MesRouteStep routeStep = findRouteStep(lot);
        if (routeStep != null && StringUtils.hasText(routeStep.getEqpType())) {
            return routeStep.getEqpType().trim();
        }
        Long stepId = routeStep != null ? routeStep.getStepId() : lot.getCurrentStepId();
        if (stepId == null) {
            return null;
        }
        MesStep step = mesStepMapper.selectById(stepId);
        if (step != null && StringUtils.hasText(step.getEqpType())) {
            if (routeStep != null) {
                log.warn("route_step 缺 eqp_type 快照，回退 mes_step: versionId={} sortNo={}",
                        lot.getRouteVersionId(), lot.getCurrentSortNo());
            }
            return step.getEqpType().trim();
        }
        return null;
    }

    /** TrackIn / 预约：类型必须匹配 */
    public void requireMatch(MesLot lot, Long eqpId) {
        if (isOff()) {
            return;
        }
        AssertUtil.notNull(eqpId, "设备不能为空");
        MesEqp eqp = mesEqpMapper.selectById(eqpId);
        AssertUtil.notNull(eqp, "设备不存在");

        MesRouteStep routeStep = findRouteStep(lot);
        String required = resolveRequired(lot);

        if (!StringUtils.hasText(required)) {
            if (isForce() && isProcessStep(routeStep, lot)) {
                AssertUtil.isTrue(false, "加工站未配置设备类型，无法开工");
            }
            return;
        }

        String actual = eqp.getEqpType() == null ? null : eqp.getEqpType().trim();
        AssertUtil.isTrue(required.equals(actual),
                "设备类型与当前站不匹配，要求=" + required + "，实际=" + (actual == null ? "空" : actual));
    }

    /** 发布：force 时加工站必须有快照类型 */
    public void assertPublishable(List<MesRouteStep> steps) {
        if (!isForce() || steps == null) {
            return;
        }
        for (MesRouteStep step : steps) {
            if (STEP_TYPE_PROCESS == (step.getStepType() == null ? -1 : step.getStepType())
                    && !StringUtils.hasText(step.getEqpType())) {
                AssertUtil.isTrue(false, "加工站未配置设备类型: sortNo=" + step.getSortNo());
            }
        }
    }

    private MesRouteStep findRouteStep(MesLot lot) {
        if (lot.getRouteVersionId() == null || lot.getCurrentSortNo() == null) {
            return null;
        }
        return mesRouteStepMapper.selectOne(new LambdaQueryWrapper<MesRouteStep>()
                .eq(MesRouteStep::getVersionId, lot.getRouteVersionId())
                .eq(MesRouteStep::getSortNo, lot.getCurrentSortNo())
                .last("LIMIT 1"));
    }

    private boolean isProcessStep(MesRouteStep routeStep, MesLot lot) {
        Integer type = routeStep != null ? routeStep.getStepType() : null;
        if (type == null) {
            Long stepId = routeStep != null ? routeStep.getStepId() : lot.getCurrentStepId();
            if (stepId != null) {
                MesStep step = mesStepMapper.selectById(stepId);
                if (step != null) {
                    type = step.getStepType();
                }
            }
        }
        return type != null && type == STEP_TYPE_PROCESS;
    }

    private String trimMode() {
        return eqpTypeMode == null ? "soft" : eqpTypeMode.trim();
    }
}
