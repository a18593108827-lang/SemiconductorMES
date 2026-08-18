package com.mes.hold.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mes.common.AssertUtil;
import com.mes.hold.entity.MesHoldReason;
import com.mes.hold.mapper.MesHoldReasonMapper;
import com.mes.hold.service.HoldReasonService;
import com.mes.hold.vo.MesHoldReasonVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HoldReasonServiceImpl implements HoldReasonService {

    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 0;

    private final MesHoldReasonMapper mesHoldReasonMapper;
    @Override
    public List<MesHoldReasonVO> listEnabled() {
        List<MesHoldReason> rows = mesHoldReasonMapper.selectList(new LambdaQueryWrapper<MesHoldReason>()
                .eq(MesHoldReason::getStatus, STATUS_ENABLED)
                .orderByAsc(MesHoldReason::getReasonCode));
        return rows.stream().map(this::toVo).toList();
    }

    @Override
    public List<MesHoldReasonVO> listAll() {
        List<MesHoldReason> rows = mesHoldReasonMapper.selectList(new LambdaQueryWrapper<MesHoldReason>()
                .orderByAsc(MesHoldReason::getReasonCode));
        return rows.stream().map(this::toVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        MesHoldReason row = mesHoldReasonMapper.selectById(id);
        AssertUtil.notNull(row, "原因码不存在");
        AssertUtil.isTrue(status != null && (status == STATUS_ENABLED || status == STATUS_DISABLED),
                "状态只能为0或1");
        row.setStatus(status);
        int rows = mesHoldReasonMapper.updateById(row);
        AssertUtil.isTrue(rows > 0, "更新失败");
    }

    private MesHoldReasonVO toVo(MesHoldReason row) {
        MesHoldReasonVO vo = new MesHoldReasonVO();
        vo.setId(row.getId());
        vo.setReasonCode(row.getReasonCode());
        vo.setReasonName(row.getReasonName());
        vo.setCategory(row.getCategory());
        vo.setStatus(row.getStatus());
        vo.setRemark(row.getRemark());
        return vo;
    }
}
