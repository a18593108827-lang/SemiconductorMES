package com.mes.edc.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.edc.dto.MesEdcSpecCreateDTO;
import com.mes.edc.dto.MesEdcSpecQuery;
import com.mes.edc.dto.MesEdcSpecUpdateDTO;
import com.mes.edc.entity.MesEdcParam;
import com.mes.edc.entity.MesEdcSpec;
import com.mes.edc.mapper.MesEdcParamMapper;
import com.mes.edc.mapper.MesEdcSpecMapper;
import com.mes.edc.service.MesEdcSpecService;
import com.mes.edc.vo.MesEdcSpecVO;
import lombok.RequiredArgsConstructor;
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
import java.util.stream.Collectors;

/**
 * 规格业务。
 * 记一句：采集判定认 active；改上下限只能动草稿，改完要发布才生效。
 */
@Service
@RequiredArgsConstructor
public class MesEdcSpecServiceImpl implements MesEdcSpecService {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_OBSOLETE = "obsolete";

    private final MesEdcSpecMapper mesEdcSpecMapper;
    private final MesEdcParamMapper mesEdcParamMapper;

    @Override
    public PageResult<MesEdcSpecVO> page(MesEdcSpecQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        LambdaQueryWrapper<MesEdcSpec> qw = new LambdaQueryWrapper<>();
        if (query.getParamId() != null) {
            qw.eq(MesEdcSpec::getParamId, query.getParamId());
        }
        if (query.getProductCode() != null) {
            qw.eq(MesEdcSpec::getProductCode, normalizeProduct(query.getProductCode()));
        }
        if (StringUtils.hasText(query.getStatus())) {
            qw.eq(MesEdcSpec::getStatus, query.getStatus().trim());
        }
        qw.orderByDesc(MesEdcSpec::getParamId)
                .orderByAsc(MesEdcSpec::getProductCode)
                .orderByDesc(MesEdcSpec::getVersionNo);

        Page<MesEdcSpec> result = mesEdcSpecMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        Map<Long, MesEdcParam> paramMap = loadParamMap(result.getRecords());
        List<MesEdcSpecVO> records = new ArrayList<>(result.getRecords().size());
        for (MesEdcSpec row : result.getRecords()) {
            records.add(toVo(row, paramMap.get(row.getParamId())));
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    public MesEdcSpecVO get(Long id) {
        MesEdcSpec row = mesEdcSpecMapper.selectById(id);
        AssertUtil.notNull(row, "规格不存在");
        MesEdcParam param = mesEdcParamMapper.selectById(row.getParamId());
        return toVo(row, param);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesEdcSpecVO create(MesEdcSpecCreateDTO dto) {
        MesEdcParam param = mesEdcParamMapper.selectById(dto.getParamId());
        AssertUtil.notNull(param, "特性不存在");

        String product = normalizeProduct(dto.getProductCode());
        assertLimits(dto.getUsl(), dto.getLsl());

        // 同特性+产品只允许一份草稿，避免工程师开一堆半成品
        Long draftCount = mesEdcSpecMapper.selectCount(new LambdaQueryWrapper<MesEdcSpec>()
                .eq(MesEdcSpec::getParamId, dto.getParamId())
                .eq(MesEdcSpec::getProductCode, product)
                .eq(MesEdcSpec::getStatus, STATUS_DRAFT));
        AssertUtil.isTrue(draftCount == 0, "已有草稿规格，请先发布或继续编辑现有草稿");

        Integer maxNo = mesEdcSpecMapper.selectList(new LambdaQueryWrapper<MesEdcSpec>()
                        .eq(MesEdcSpec::getParamId, dto.getParamId())
                        .eq(MesEdcSpec::getProductCode, product)
                        .orderByDesc(MesEdcSpec::getVersionNo)
                        .last("LIMIT 1"))
                .stream()
                .map(MesEdcSpec::getVersionNo)
                .findFirst()
                .orElse(0);

        long userId = StpUtil.getLoginIdAsLong();
        MesEdcSpec row = new MesEdcSpec();
        row.setParamId(dto.getParamId());
        row.setProductCode(product);
        row.setVersionNo(maxNo + 1);
        row.setStatus(STATUS_DRAFT);
        row.setUsl(dto.getUsl());
        row.setLsl(dto.getLsl());
        row.setTarget(dto.getTarget());
        row.setRemark(blankToNull(dto.getRemark()));
        row.setCreateBy(userId);
        row.setUpdateBy(userId);
        mesEdcSpecMapper.insert(row);
        return toVo(row, param);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MesEdcSpecUpdateDTO dto) {
        MesEdcSpec row = mesEdcSpecMapper.selectById(id);
        AssertUtil.notNull(row, "规格不存在");
        AssertUtil.isTrue(STATUS_DRAFT.equals(row.getStatus()), "仅草稿规格可编辑");

        assertLimits(dto.getUsl(), dto.getLsl());

        row.setUsl(dto.getUsl());
        row.setLsl(dto.getLsl());
        row.setTarget(dto.getTarget());
        row.setRemark(blankToNull(dto.getRemark()));
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesEdcSpecMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        MesEdcSpec row = mesEdcSpecMapper.selectById(id);
        AssertUtil.notNull(row, "规格不存在");
        AssertUtil.isTrue(STATUS_DRAFT.equals(row.getStatus()), "仅草稿可发布");
        assertLimits(row.getUsl(), row.getLsl());

        // 同 param+product 旧生效版先退役，保证 active 唯一
        mesEdcSpecMapper.update(null, new LambdaUpdateWrapper<MesEdcSpec>()
                .eq(MesEdcSpec::getParamId, row.getParamId())
                .eq(MesEdcSpec::getProductCode, row.getProductCode())
                .eq(MesEdcSpec::getStatus, STATUS_ACTIVE)
                .set(MesEdcSpec::getStatus, STATUS_OBSOLETE));

        row.setStatus(STATUS_ACTIVE);
        row.setPublishedAt(LocalDateTime.now());
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesEdcSpecMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    /** 空产品统一成空串，跟库唯一键一致 */
    private static String normalizeProduct(String productCode) {
        if (!StringUtils.hasText(productCode)) {
            return "";
        }
        return productCode.trim();
    }

    /** 上下限至少配一侧；两边都有时上限不能小于下限 */
    private static void assertLimits(BigDecimal usl, BigDecimal lsl) {
        AssertUtil.isTrue(usl != null || lsl != null, "上限与下限不能同时为空");
        if (usl != null && lsl != null) {
            AssertUtil.isTrue(usl.compareTo(lsl) >= 0, "上限不能小于下限");
        }
    }

    private static String blankToNull(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        return v.trim();
    }

    private Map<Long, MesEdcParam> loadParamMap(List<MesEdcSpec> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(MesEdcSpec::getParamId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mesEdcParamMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MesEdcParam::getId, p -> p, (a, b) -> a, HashMap::new));
    }

    private static MesEdcSpecVO toVo(MesEdcSpec row, MesEdcParam param) {
        MesEdcSpecVO vo = new MesEdcSpecVO();
        vo.setId(row.getId());
        vo.setParamId(row.getParamId());
        if (param != null) {
            vo.setParamCode(param.getParamCode());
            vo.setParamName(param.getParamName());
        }
        vo.setProductCode(row.getProductCode());
        vo.setVersionNo(row.getVersionNo());
        vo.setStatus(row.getStatus());
        vo.setUsl(row.getUsl());
        vo.setLsl(row.getLsl());
        vo.setTarget(row.getTarget());
        vo.setRemark(row.getRemark());
        vo.setPublishedAt(row.getPublishedAt());
        vo.setVersion(row.getVersion());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }
}
