package com.mes.edc.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.edc.dto.MesEdcParamCreateDTO;
import com.mes.edc.dto.MesEdcParamQuery;
import com.mes.edc.dto.MesEdcParamUpdateDTO;
import com.mes.edc.entity.MesEdcParam;
import com.mes.edc.mapper.MesEdcParamMapper;
import com.mes.edc.service.MesEdcParamService;
import com.mes.edc.vo.MesEdcParamVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 量测特性业务：只管主数据本身。
 * Spec / Plan / 采集以后再做，这里不碰。
 */
@Service
@RequiredArgsConstructor
public class MesEdcParamServiceImpl implements MesEdcParamService {

    public static final int ENABLED = 1;
    public static final int DISABLED = 0;
    /** 一期固定数字；以后要字符串再放开 */
    public static final String VALUE_TYPE_NUMBER = "NUMBER";

    private final MesEdcParamMapper mesEdcParamMapper;

    @Override
    public PageResult<MesEdcParamVO> page(MesEdcParamQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        LambdaQueryWrapper<MesEdcParam> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and(w -> w.like(MesEdcParam::getParamCode, kw).or().like(MesEdcParam::getParamName, kw));
        }
        if (query.getEnabled() != null) {
            qw.eq(MesEdcParam::getEnabled, query.getEnabled());
        }
        qw.orderByAsc(MesEdcParam::getParamCode);

        Page<MesEdcParam> result = mesEdcParamMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<MesEdcParamVO> records = new ArrayList<>(result.getRecords().size());
        for (MesEdcParam row : result.getRecords()) {
            records.add(toVo(row));
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    public MesEdcParamVO get(Long id) {
        MesEdcParam row = mesEdcParamMapper.selectById(id);
        AssertUtil.notNull(row, "特性不存在");
        return toVo(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesEdcParamVO create(MesEdcParamCreateDTO dto) {
        String code = dto.getParamCode().trim();
        Long count = mesEdcParamMapper.selectCount(new LambdaQueryWrapper<MesEdcParam>()
                .eq(MesEdcParam::getParamCode, code));
        AssertUtil.isTrue(count == 0, "特性编码已存在");

        long userId = StpUtil.getLoginIdAsLong();
        MesEdcParam row = new MesEdcParam();
        row.setParamCode(code);
        row.setParamName(dto.getParamName().trim());
        row.setUnit(blankToNull(dto.getUnit()));
        row.setValueType(VALUE_TYPE_NUMBER);
        row.setRemark(blankToNull(dto.getRemark()));
        row.setEnabled(ENABLED);
        row.setCreateBy(userId);
        row.setUpdateBy(userId);
        mesEdcParamMapper.insert(row);
        return toVo(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MesEdcParamUpdateDTO dto) {
        MesEdcParam row = mesEdcParamMapper.selectById(id);
        AssertUtil.notNull(row, "特性不存在");

        // 编码不动：历史采集、Plan 项都靠它认人
        row.setParamName(dto.getParamName().trim());
        row.setUnit(blankToNull(dto.getUnit()));
        row.setRemark(blankToNull(dto.getRemark()));
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesEdcParamMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, Integer enabled) {
        MesEdcParam row = mesEdcParamMapper.selectById(id);
        AssertUtil.notNull(row, "特性不存在");
        AssertUtil.isTrue(enabled != null && (enabled == ENABLED || enabled == DISABLED), "启停只能为0或1");

        // 停用不级联：已挂的 Spec/Plan 还在，只是新建计划时别再选禁用项
        row.setEnabled(enabled);
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesEdcParamMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    private static String blankToNull(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        return v.trim();
    }

    private static MesEdcParamVO toVo(MesEdcParam row) {
        MesEdcParamVO vo = new MesEdcParamVO();
        vo.setId(row.getId());
        vo.setParamCode(row.getParamCode());
        vo.setParamName(row.getParamName());
        vo.setUnit(row.getUnit());
        vo.setValueType(row.getValueType());
        vo.setEnabled(row.getEnabled());
        vo.setRemark(row.getRemark());
        vo.setVersion(row.getVersion());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }
}
