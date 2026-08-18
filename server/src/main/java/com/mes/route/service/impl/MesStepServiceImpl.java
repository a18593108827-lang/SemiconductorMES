package com.mes.route.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.route.dto.MesStepCreateDTO;
import com.mes.route.dto.MesStepQuery;
import com.mes.route.dto.MesStepUpdateDTO;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesStepMapper;
import com.mes.route.service.MesStepService;
import com.mes.route.vo.MesStepVO;
import com.mes.route.support.ProcessTimeBounds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 工序服务实现
 */
@Service
@RequiredArgsConstructor
public class MesStepServiceImpl implements MesStepService {

    private final MesStepMapper mesStepMapper;

    @Override
    public PageResult<MesStepVO> page(MesStepQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        Page<MesStep> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<MesStep> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            qw.and(w -> w.like(MesStep::getStepCode, keyword).or().like(MesStep::getStepName, keyword));//模糊查询工序编码和名称
        }
        if (query.getStatus() != null) {
            qw.eq(MesStep::getStatus, query.getStatus());
        }
        qw.orderByAsc(MesStep::getStepCode);

        Page<MesStep> result = mesStepMapper.selectPage(page, qw);
        List<MesStepVO> records = new ArrayList<>(result.getRecords().size());
        for (MesStep step : result.getRecords()) {
            records.add(toVo(step));
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    public void create(MesStepCreateDTO dto) {
        String stepCode = dto.getStepCode().trim();
        Long count = mesStepMapper.selectCount(new LambdaQueryWrapper<MesStep>()
                .eq(MesStep::getStepCode, stepCode));
        AssertUtil.isTrue(count == 0, "工序编码已存在");
        AssertUtil.isTrue(isValidStepType(dto.getStepType()), "工序类型不合法");

        MesStep step = new MesStep();
        step.setStepCode(stepCode);
        step.setStepName(dto.getStepName().trim());
        step.setStepType(dto.getStepType());
        step.setEqpType(blankToNull(dto.getEqpType()));
        step.setAllowSkip(dto.getAllowSkip());
        step.setMaxQueueMin(dto.getMaxQueueMin());
        ProcessTimeBounds.validate(dto.getMinProcessMin(), dto.getMaxProcessMin());
        step.setMinProcessMin(dto.getMinProcessMin());
        step.setMaxProcessMin(dto.getMaxProcessMin());
        step.setRemark(blankToNull(dto.getRemark()));
        step.setStatus(1);
        mesStepMapper.insert(step);
    }

    @Override
    public void update(Long id, MesStepUpdateDTO dto) {
        MesStep step = mesStepMapper.selectById(id);
        AssertUtil.notNull(step, "工序不存在");
        AssertUtil.isTrue(isValidStepType(dto.getStepType()), "工序类型不合法");
        AssertUtil.isTrue(dto.getStatus() == 0 || dto.getStatus() == 1, "状态不合法");

        step.setStepName(dto.getStepName().trim());
        step.setStepType(dto.getStepType());
        step.setStatus(dto.getStatus());
        step.setEqpType(blankToNull(dto.getEqpType()));
        step.setAllowSkip(dto.getAllowSkip());
        step.setMaxQueueMin(dto.getMaxQueueMin());
        ProcessTimeBounds.validate(dto.getMinProcessMin(), dto.getMaxProcessMin());
        step.setMinProcessMin(dto.getMinProcessMin());
        step.setMaxProcessMin(dto.getMaxProcessMin());
        step.setRemark(blankToNull(dto.getRemark()));
        mesStepMapper.updateById(step);
    }

    private static boolean isValidStepType(Integer type) {
        return type != null && (type == 1 || type == 2 || type == 3);
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static MesStepVO toVo(MesStep step) {
        MesStepVO vo = new MesStepVO();
        vo.setId(step.getId());
        vo.setStepCode(step.getStepCode());
        vo.setStepName(step.getStepName());
        vo.setStepType(step.getStepType());
        vo.setEqpType(step.getEqpType());
        vo.setAllowSkip(step.getAllowSkip());
        vo.setMaxQueueMin(step.getMaxQueueMin());
        vo.setMinProcessMin(step.getMinProcessMin());
        vo.setMaxProcessMin(step.getMaxProcessMin());
        vo.setStatus(step.getStatus());
        vo.setRemark(step.getRemark());
        vo.setCreateTime(step.getCreateTime());
        vo.setUpdateTime(step.getUpdateTime());
        return vo;
    }
}
