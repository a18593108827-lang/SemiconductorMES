package com.mes.edc.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.edc.dto.MesEdcPlanCreateDTO;
import com.mes.edc.dto.MesEdcPlanItemsReplaceDTO;
import com.mes.edc.dto.MesEdcPlanQuery;
import com.mes.edc.dto.MesEdcPlanUpdateDTO;
import com.mes.edc.entity.MesEdcParam;
import com.mes.edc.entity.MesEdcPlan;
import com.mes.edc.entity.MesEdcPlanItem;
import com.mes.edc.entity.MesEdcSpec;
import com.mes.edc.mapper.MesEdcParamMapper;
import com.mes.edc.mapper.MesEdcPlanItemMapper;
import com.mes.edc.mapper.MesEdcPlanMapper;
import com.mes.edc.mapper.MesEdcSpecMapper;
import com.mes.edc.service.MesEdcPlanService;
import com.mes.edc.vo.MesEdcPlanItemVO;
import com.mes.edc.vo.MesEdcPlanVO;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 站计划业务。
 * 记一句：没配 / 停用 / required=0 → TrackOut 不拦；required=1 才卡采集。
 */
@Service
@RequiredArgsConstructor
public class MesEdcPlanServiceImpl implements MesEdcPlanService {

    public static final int ENABLED = 1;
    public static final int DISABLED = 0;
    public static final int REQUIRED = 1;
    public static final int NOT_REQUIRED = 0;
    public static final int MANDATORY = 1;

    private final MesEdcPlanMapper mesEdcPlanMapper;
    private final MesEdcPlanItemMapper mesEdcPlanItemMapper;
    private final MesEdcParamMapper mesEdcParamMapper;
    private final MesEdcSpecMapper mesEdcSpecMapper;
    private final MesStepMapper mesStepMapper;

    @Override
    public PageResult<MesEdcPlanVO> page(MesEdcPlanQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        LambdaQueryWrapper<MesEdcPlan> qw = new LambdaQueryWrapper<>();
        if (query.getStepId() != null) {
            qw.eq(MesEdcPlan::getStepId, query.getStepId());
        }
        if (query.getRequired() != null) {
            qw.eq(MesEdcPlan::getRequired, query.getRequired());
        }
        if (query.getEnabled() != null) {
            qw.eq(MesEdcPlan::getEnabled, query.getEnabled());
        }
        qw.orderByDesc(MesEdcPlan::getUpdateTime);

        Page<MesEdcPlan> result = mesEdcPlanMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        Map<Long, MesStep> stepMap = loadStepMap(result.getRecords());
        Map<Long, Integer> itemCountMap = loadItemCountMap(result.getRecords());

        List<MesEdcPlanVO> records = new ArrayList<>(result.getRecords().size());
        for (MesEdcPlan row : result.getRecords()) {
            records.add(toVo(row, stepMap.get(row.getStepId()), itemCountMap.getOrDefault(row.getId(), 0), null));
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    public MesEdcPlanVO get(Long id) {
        MesEdcPlan row = mesEdcPlanMapper.selectById(id);
        AssertUtil.notNull(row, "计划不存在");
        MesStep step = mesStepMapper.selectById(row.getStepId());
        List<MesEdcPlanItem> items = mesEdcPlanItemMapper.selectList(new LambdaQueryWrapper<MesEdcPlanItem>()
                .eq(MesEdcPlanItem::getPlanId, id)
                .orderByAsc(MesEdcPlanItem::getSortNo));
        return toVo(row, step, items.size(), toItemVos(items));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesEdcPlanVO create(MesEdcPlanCreateDTO dto) {
        MesStep step = mesStepMapper.selectById(dto.getStepId());
        AssertUtil.notNull(step, "工序不存在");

        Long exists = mesEdcPlanMapper.selectCount(new LambdaQueryWrapper<MesEdcPlan>()
                .eq(MesEdcPlan::getStepId, dto.getStepId()));
        AssertUtil.isTrue(exists == 0, "该工序已有采集计划");

        int required = dto.getRequired() == null ? NOT_REQUIRED : dto.getRequired();
        AssertUtil.isTrue(required == REQUIRED || required == NOT_REQUIRED, "门禁开关只能为0或1");
        // 新建时还没 items；若直接开 required，后面 replaceItems 前门禁会空采——先禁止
        AssertUtil.isTrue(required == NOT_REQUIRED, "新建时请先保持门禁关闭，配好计划项后再打开");

        long userId = StpUtil.getLoginIdAsLong();
        MesEdcPlan row = new MesEdcPlan();
        row.setStepId(dto.getStepId());
        row.setRequired(required);
        row.setEnabled(ENABLED);
        row.setRemark(blankToNull(dto.getRemark()));
        row.setCreateBy(userId);
        row.setUpdateBy(userId);
        mesEdcPlanMapper.insert(row);
        return toVo(row, step, 0, List.of());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MesEdcPlanUpdateDTO dto) {
        MesEdcPlan row = mesEdcPlanMapper.selectById(id);
        AssertUtil.notNull(row, "计划不存在");
        AssertUtil.isTrue(dto.getRequired() == REQUIRED || dto.getRequired() == NOT_REQUIRED, "门禁开关只能为0或1");

        if (dto.getRequired() == REQUIRED) {
            Long itemCount = mesEdcPlanItemMapper.selectCount(new LambdaQueryWrapper<MesEdcPlanItem>()
                    .eq(MesEdcPlanItem::getPlanId, id));
            AssertUtil.isTrue(itemCount > 0, "已开门禁时必须先配置计划项");
        }

        row.setRequired(dto.getRequired());
        row.setRemark(blankToNull(dto.getRemark()));
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesEdcPlanMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, Integer enabled) {
        MesEdcPlan row = mesEdcPlanMapper.selectById(id);
        AssertUtil.notNull(row, "计划不存在");
        AssertUtil.isTrue(enabled != null && (enabled == ENABLED || enabled == DISABLED), "启停只能为0或1");

        row.setEnabled(enabled);
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesEdcPlanMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceItems(Long id, MesEdcPlanItemsReplaceDTO dto) {
        MesEdcPlan plan = mesEdcPlanMapper.selectById(id);
        AssertUtil.notNull(plan, "计划不存在");

        List<MesEdcPlanItemsReplaceDTO.MesEdcPlanItemDTO> items =
                dto.getItems() == null ? List.of() : dto.getItems();
        if (Objects.equals(plan.getRequired(), REQUIRED)) {
            AssertUtil.isTrue(!items.isEmpty(), "已开门禁时计划项不能为空");
        }

        Set<Long> paramSeen = new HashSet<>();
        int idx = 0;
        List<MesEdcPlanItem> rows = new ArrayList<>(items.size());
        for (MesEdcPlanItemsReplaceDTO.MesEdcPlanItemDTO item : items) {
            idx++;
            AssertUtil.notNull(item.getParamId(), "特性不能为空");
            AssertUtil.isTrue(paramSeen.add(item.getParamId()), "同一计划内特性不能重复");

            MesEdcParam param = mesEdcParamMapper.selectById(item.getParamId());
            AssertUtil.notNull(param, "特性不存在: " + item.getParamId());
            AssertUtil.isTrue(Objects.equals(param.getEnabled(), ENABLED), "特性已停用: " + param.getParamCode());

            if (item.getSpecId() != null) {
                MesEdcSpec spec = mesEdcSpecMapper.selectById(item.getSpecId());
                AssertUtil.notNull(spec, "规格不存在: " + item.getSpecId());
                AssertUtil.isTrue(Objects.equals(spec.getParamId(), item.getParamId()),
                        "规格与特性不匹配: " + param.getParamCode());
            }

            int mandatory = item.getMandatory() == null ? MANDATORY : item.getMandatory();
            AssertUtil.isTrue(mandatory == 0 || mandatory == 1, "必采标志只能为0或1");

            MesEdcPlanItem row = new MesEdcPlanItem();
            row.setPlanId(id);
            row.setParamId(item.getParamId());
            row.setSpecId(item.getSpecId());
            row.setSortNo(item.getSortNo() != null ? item.getSortNo() : idx);
            row.setMandatory(mandatory);
            rows.add(row);
        }

        // 物理删再插，躲开软删唯一键冲突
        mesEdcPlanItemMapper.physicalDeleteByPlanId(id);
        for (MesEdcPlanItem row : rows) {
            mesEdcPlanItemMapper.insert(row);
        }

        plan.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesEdcPlanMapper.updateById(plan);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    private static String blankToNull(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        return v.trim();
    }

    private Map<Long, MesStep> loadStepMap(List<MesEdcPlan> plans) {
        if (plans.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = plans.stream().map(MesEdcPlan::getStepId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mesStepMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a, HashMap::new));
    }

    private Map<Long, Integer> loadItemCountMap(List<MesEdcPlan> plans) {
        if (plans.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = plans.stream().map(MesEdcPlan::getId).toList();
        List<MesEdcPlanItem> items = mesEdcPlanItemMapper.selectList(new LambdaQueryWrapper<MesEdcPlanItem>()
                .in(MesEdcPlanItem::getPlanId, ids)
                .select(MesEdcPlanItem::getPlanId));
        Map<Long, Integer> map = new HashMap<>();
        for (MesEdcPlanItem item : items) {
            map.merge(item.getPlanId(), 1, Integer::sum);
        }
        return map;
    }

    private List<MesEdcPlanItemVO> toItemVos(List<MesEdcPlanItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> paramIds = items.stream().map(MesEdcPlanItem::getParamId).distinct().toList();
        List<Long> specIds = items.stream().map(MesEdcPlanItem::getSpecId).filter(Objects::nonNull).distinct().toList();
        Map<Long, MesEdcParam> paramMap = paramIds.isEmpty() ? Map.of()
                : mesEdcParamMapper.selectBatchIds(paramIds).stream()
                .collect(Collectors.toMap(MesEdcParam::getId, p -> p, (a, b) -> a, HashMap::new));
        Map<Long, MesEdcSpec> specMap = specIds.isEmpty() ? Map.of()
                : mesEdcSpecMapper.selectBatchIds(specIds).stream()
                .collect(Collectors.toMap(MesEdcSpec::getId, s -> s, (a, b) -> a, HashMap::new));

        List<MesEdcPlanItemVO> list = new ArrayList<>(items.size());
        for (MesEdcPlanItem item : items) {
            MesEdcPlanItemVO vo = new MesEdcPlanItemVO();
            vo.setId(item.getId());
            vo.setPlanId(item.getPlanId());
            vo.setParamId(item.getParamId());
            MesEdcParam param = paramMap.get(item.getParamId());
            if (param != null) {
                vo.setParamCode(param.getParamCode());
                vo.setParamName(param.getParamName());
                vo.setUnit(param.getUnit());
            }
            vo.setSpecId(item.getSpecId());
            if (item.getSpecId() != null) {
                MesEdcSpec spec = specMap.get(item.getSpecId());
                if (spec != null) {
                    vo.setSpecVersionNo(spec.getVersionNo());
                    vo.setSpecStatus(spec.getStatus());
                }
            }
            vo.setSortNo(item.getSortNo());
            vo.setMandatory(item.getMandatory());
            list.add(vo);
        }
        return list;
    }

    private static MesEdcPlanVO toVo(MesEdcPlan row, MesStep step, int itemCount, List<MesEdcPlanItemVO> items) {
        MesEdcPlanVO vo = new MesEdcPlanVO();
        vo.setId(row.getId());
        vo.setStepId(row.getStepId());
        if (step != null) {
            vo.setStepCode(step.getStepCode());
            vo.setStepName(step.getStepName());
        }
        vo.setRequired(row.getRequired());
        vo.setEnabled(row.getEnabled());
        vo.setRemark(row.getRemark());
        vo.setVersion(row.getVersion());
        vo.setItemCount(itemCount);
        vo.setItems(items != null ? items : List.of());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }
}
