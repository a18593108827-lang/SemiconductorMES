package com.mes.equipment.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.equipment.dto.MesEqpCreateDTO;
import com.mes.equipment.dto.MesEqpQuery;
import com.mes.equipment.dto.MesEqpUpdateDTO;
import com.mes.equipment.entity.MesEqp;
import com.mes.equipment.mapper.MesEqpMapper;
import com.mes.equipment.service.MesEqpService;
import com.mes.equipment.vo.MesEqpOptionVO;
import com.mes.equipment.vo.MesEqpVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MesEqpServiceImpl implements MesEqpService {

    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_DOWN = "down";
    public static final String STATUS_PM = "pm";
    public static final String STATUS_ENG = "eng";
    public static final String STATUS_OFFLINE = "offline";

    private static final Set<String> STATUSES = Set.of(
            STATUS_IDLE, STATUS_RUNNING, STATUS_DOWN, STATUS_PM, STATUS_ENG, STATUS_OFFLINE);

    /** TrackIn 允许的业务态 */
    private static final Set<String> USABLE_STATUSES = Set.of(STATUS_IDLE, STATUS_RUNNING);

    public static final int ENABLED = 1;
    public static final int DISABLED = 0;

    private final MesEqpMapper mesEqpMapper;

    @Override
    public PageResult<MesEqpVO> page(MesEqpQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        LambdaQueryWrapper<MesEqp> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and(w -> w.like(MesEqp::getEqpCode, kw).or().like(MesEqp::getEqpName, kw));
        }
        if (StringUtils.hasText(query.getStatus())) {
            qw.eq(MesEqp::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getEqpType())) {
            qw.eq(MesEqp::getEqpType, query.getEqpType().trim());
        }
        if (query.getEnabled() != null) {
            qw.eq(MesEqp::getEnabled, query.getEnabled());
        }
        qw.orderByAsc(MesEqp::getEqpCode);

        Page<MesEqp> result = mesEqpMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<MesEqpVO> records = new ArrayList<>(result.getRecords().size());
        for (MesEqp row : result.getRecords()) {
            records.add(toVo(row));
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    public MesEqpVO get(Long id) {
        MesEqp row = mesEqpMapper.selectById(id);
        AssertUtil.notNull(row, "设备不存在");
        return toVo(row);
    }

    @Override
    public List<MesEqpOptionVO> listOptions(String eqpType) {
        LambdaQueryWrapper<MesEqp> qw = new LambdaQueryWrapper<>();
        qw.eq(MesEqp::getEnabled, ENABLED)
                .in(MesEqp::getStatus, USABLE_STATUSES);
        if (StringUtils.hasText(eqpType)) {
            qw.eq(MesEqp::getEqpType, eqpType.trim());
        }
        qw.orderByAsc(MesEqp::getEqpCode);
        List<MesEqp> rows = mesEqpMapper.selectList(qw);
        List<MesEqpOptionVO> list = new ArrayList<>(rows.size());
        for (MesEqp row : rows) {
            MesEqpOptionVO vo = new MesEqpOptionVO();
            vo.setId(row.getId());
            vo.setEqpCode(row.getEqpCode());
            vo.setEqpName(row.getEqpName());
            vo.setEqpType(row.getEqpType());
            vo.setArea(row.getArea());
            vo.setStatus(row.getStatus());
            list.add(vo);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesEqpVO create(MesEqpCreateDTO dto) {
        String code = dto.getEqpCode().trim();
        Long count = mesEqpMapper.selectCount(new LambdaQueryWrapper<MesEqp>().eq(MesEqp::getEqpCode, code));
        AssertUtil.isTrue(count == 0, "设备编码已存在");

        long userId = StpUtil.getLoginIdAsLong();
        MesEqp row = new MesEqp();
        row.setEqpCode(code);
        row.setEqpName(dto.getEqpName().trim());
        row.setEqpType(blankToNull(dto.getEqpType()));
        row.setArea(blankToNull(dto.getArea()));
        row.setRemark(blankToNull(dto.getRemark()));
        row.setStatus(STATUS_IDLE);
        row.setEnabled(ENABLED);
        row.setCreateBy(userId);
        row.setUpdateBy(userId);
        mesEqpMapper.insert(row);
        return toVo(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MesEqpUpdateDTO dto) {
        MesEqp row = mesEqpMapper.selectById(id);
        AssertUtil.notNull(row, "设备不存在");

        row.setEqpName(dto.getEqpName().trim());
        row.setEqpType(blankToNull(dto.getEqpType()));
        row.setArea(blankToNull(dto.getArea()));
        row.setRemark(blankToNull(dto.getRemark()));
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesEqpMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateEnabled(Long id, Integer enabled) {
        MesEqp row = mesEqpMapper.selectById(id);
        AssertUtil.notNull(row, "设备不存在");
        AssertUtil.isTrue(enabled != null && (enabled == ENABLED || enabled == DISABLED), "启停只能为0或1");

        row.setEnabled(enabled);
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesEqpMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        MesEqp row = mesEqpMapper.selectById(id);
        AssertUtil.notNull(row, "设备不存在");
        AssertUtil.isTrue(StringUtils.hasText(status), "状态不能为空");
        String next = status.trim();
        AssertUtil.isTrue(STATUSES.contains(next), "状态不合法");

        row.setStatus(next);
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = mesEqpMapper.updateById(row);
        AssertUtil.isTrue(n > 0, "数据已被他人修改，请刷新后重试");
    }

    @Override
    public void assertUsable(Long eqpId) {
        if (eqpId == null) {
            return;
        }
        MesEqp row = mesEqpMapper.selectById(eqpId);
        AssertUtil.notNull(row, "设备不存在");
        AssertUtil.isTrue(Objects.equals(row.getEnabled(), ENABLED), "设备已停用：" + row.getEqpCode());
        String status = row.getStatus();
        AssertUtil.isTrue(USABLE_STATUSES.contains(status),
                "设备不可用：" + row.getEqpCode() + "（" + statusLabel(status) + "）");
    }

    private static String statusLabel(String status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case STATUS_IDLE -> "空闲";
            case STATUS_RUNNING -> "加工中";
            case STATUS_DOWN -> "故障";
            case STATUS_PM -> "保养";
            case STATUS_ENG -> "工程";
            case STATUS_OFFLINE -> "离线";
            default -> status;
        };
    }

    private static String blankToNull(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        return v.trim();
    }

    private MesEqpVO toVo(MesEqp row) {
        MesEqpVO vo = new MesEqpVO();
        vo.setId(row.getId());
        vo.setEqpCode(row.getEqpCode());
        vo.setEqpName(row.getEqpName());
        vo.setEqpType(row.getEqpType());
        vo.setArea(row.getArea());
        vo.setStatus(row.getStatus());
        vo.setEnabled(row.getEnabled());
        vo.setRemark(row.getRemark());
        vo.setVersion(row.getVersion());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }
}
