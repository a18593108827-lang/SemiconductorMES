package com.mes.complaint.facade.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.alarm.facade.AlarmFacade;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.common.PageResult;
import com.mes.complaint.dto.ComplaintPackageBuildDTO;
import com.mes.complaint.dto.ComplaintPackagePreviewDTO;
import com.mes.complaint.dto.ComplaintPackageQuery;
import com.mes.complaint.entity.MesComplaintPackage;
import com.mes.complaint.entity.MesComplaintPackageMember;
import com.mes.complaint.facade.ComplaintPackageFacade;
import com.mes.complaint.mapper.MesComplaintPackageMapper;
import com.mes.complaint.mapper.MesComplaintPackageMemberMapper;
import com.mes.complaint.support.ComplaintPackageAssembler;
import com.mes.complaint.support.ComplaintPackageNoAllocator;
import com.mes.complaint.support.ComplaintPackageWriter;
import com.mes.complaint.vo.ComplaintPackageListVO;
import com.mes.complaint.vo.ComplaintPackageMemberVO;
import com.mes.complaint.vo.ComplaintPackagePreviewVO;
import com.mes.complaint.vo.ComplaintPackageSummaryVO;
import com.mes.complaint.vo.ComplaintPackageVO;
import com.mes.hold.service.HoldService;
import com.mes.lot.service.MesLotService;
import com.mes.lot.vo.MesLotImpactFlatVO;
import com.mes.lot.vo.MesLotImpactMemberVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 客诉追溯包门面：开关 / preview / build / get / page。
 * 编排方法不加事务；写入只走 Writer。
 */
@Service
@RequiredArgsConstructor
public class ComplaintPackageFacadeImpl implements ComplaintPackageFacade {

    // ===== 业务错误码 =====
    /** 功能开关关闭 */
    public static final String ERR_DISABLED = "COMPLAINT_PACKAGE_DISABLED";
    /** 影响面成员超上限 */
    public static final String ERR_TOO_LARGE = "COMPLAINT_PACKAGE_TOO_LARGE";
    /** 包 id 无效 */
    public static final String ERR_NOT_FOUND = "COMPLAINT_PACKAGE_NOT_FOUND";
    /** 包号并发冲突重试耗尽 */
    public static final String ERR_NO_CONFLICT = "COMPLAINT_PACKAGE_NO_CONFLICT";

    /** 报废状态（摘要 scrapLotCount 判定用） */
    private static final String STATUS_SCRAPPED = "scrapped";
    /** 包号 UK 冲突最大重试次数 */
    private static final int WRITE_RETRY = 3;
    /** list 默认页大小 */
    private static final int LIST_SIZE_DEFAULT = 20;
    /** list 页大小上限（超出截断） */
    private static final int LIST_SIZE_MAX = 100;

    @Value("${mes.complaint-package.enabled:false}")
    private boolean enabled;

    @Value("${mes.complaint-package.max-members:200}")
    private int maxMembers;

    private final MesLotService mesLotService;
    private final HoldService holdService;
    private final AlarmFacade alarmFacade;
    private final ComplaintPackageNoAllocator noAllocator;
    private final ComplaintPackageWriter writer;
    private final ComplaintPackageAssembler assembler;
    private final MesComplaintPackageMapper packageMapper;
    private final MesComplaintPackageMemberMapper memberMapper;

    /** 配置开关是否打开 */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /** 开关关则抛 COMPLAINT_PACKAGE_DISABLED */
    @Override
    public void assertEnabled() {
        AssertUtil.isTrue(enabled, ERR_DISABLED + ": 客诉追溯包已关闭");
    }

    /** 影响面 preview（不落库）：展平谱系 + 摘要计数 */
    @Override
    public ComplaintPackagePreviewVO preview(ComplaintPackagePreviewDTO dto) {
        assertEnabled();
        AssertUtil.notNull(dto, "参数不能为空");
        AssertUtil.notNull(dto.getAnchorLotId(), "锚点批次不能为空");

        MesLotImpactFlatVO flat = flattenOrReject(dto.getAnchorLotId(), dto.getDirection(), dto.getDepth());
        ComplaintPackagePreviewVO vo = new ComplaintPackagePreviewVO();
        vo.setAnchorLotId(flat.getAnchorLotId());
        vo.setAnchorLotNo(flat.getAnchorLotNo());
        vo.setTruncated(flat.isTruncated());
        List<ComplaintPackageMemberVO> members = toPreviewMembers(flat);
        vo.setMembers(members);
        vo.setMemberCount(members.size());
        vo.setSummary(buildPreviewSummary(members));
        return vo;
    }

    /**
     * build：影响面落包头+成员（Writer 事务），提交后装配 VO。
     * 本方法禁带 @Transactional（R9）；UK 冲突在事务外重试。
     */
    @Override
    public ComplaintPackageVO build(ComplaintPackageBuildDTO dto) {
        // 校验
        assertEnabled();
        AssertUtil.notNull(dto, "参数不能为空");
        AssertUtil.notNull(dto.getAnchorLotId(), "锚点批次不能为空");

        // 主装实体
        MesLotImpactFlatVO flat = flattenOrReject(dto.getAnchorLotId(), dto.getDirection(), dto.getDepth());
        MesComplaintPackage header = newHeader(dto, flat);
        List<MesComplaintPackageMember> members = toPersistMembers(flat);
        header.setPackageNo(noAllocator.nextCandidate());

        // 写入数据
        int retries = 0;
        while (true) {
            try {
                writer.insert(header, members);
                break;
            } catch (RuntimeException ex) {
                // 按约束名分流：包号冲突可重试；成员重复是算法 bug；其余原样抛
                String msg = constraintMessage(ex);
                if (msg.contains("uk_complaint_pkg_lot")) {
                    throw new BusinessException("包内成员重复，属算法错误");
                }
                if (msg.contains("uk_complaint_package_no")) {
                    if (retries >= WRITE_RETRY) {
                        // 重读 max + 抖动后仍冲突：放弃，让客户端重试
                        throw new BusinessException(ERR_NO_CONFLICT + ": 包号生成冲突，请重试");
                    }
                    retries++;
                    // 重读当日 max + 整型抖动，打散同频 herd；雪花 id 保留（上一笔已回滚）
                    header.setPackageNo(noAllocator.nextAfterConflict());
                    continue;
                }
                throw ex;
            }
        }
        // 提交后只读装配（R1：禁入事务）；build 复用本次展开树（R13）
        return assembler.assemble(header, members, flat.getTree());
    }

    /** 详情：包头 + 成员（以表为准）+ 装配块现查 */
    @Override
    public ComplaintPackageVO get(Long id) {
        assertEnabled();
        AssertUtil.notNull(id, "包 id 不能为空");
        MesComplaintPackage pkg = packageMapper.selectById(id);
        AssertUtil.notNull(pkg, ERR_NOT_FOUND + ": 追溯包不存在");
        List<MesComplaintPackageMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<MesComplaintPackageMember>()
                        .eq(MesComplaintPackageMember::getPackageId, id));
        // get 现查树（成员已冻结，树允许漂移）
        return assembler.assemble(pkg, members, null);
    }

    /** 分页摘要：anchorLotId / status / 时间范围筛选，create_time 倒序；size 截 100 */
    @Override
    public PageResult<ComplaintPackageListVO> page(ComplaintPackageQuery query) {
        assertEnabled();
        ComplaintPackageQuery q = query != null ? query : new ComplaintPackageQuery();
        long pageNo = q.getPage() <= 0 ? 1 : q.getPage();
        long size = q.getSize() <= 0 ? LIST_SIZE_DEFAULT : Math.min(q.getSize(), LIST_SIZE_MAX);

        LambdaQueryWrapper<MesComplaintPackage> w = new LambdaQueryWrapper<>();
        if (q.getAnchorLotId() != null) {
            w.eq(MesComplaintPackage::getAnchorLotId, q.getAnchorLotId());
        }
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(MesComplaintPackage::getStatus, q.getStatus().trim());
        }
        if (q.getFrom() != null) {
            w.ge(MesComplaintPackage::getCreateTime, q.getFrom());
        }
        if (q.getTo() != null) {
            w.le(MesComplaintPackage::getCreateTime, q.getTo());
        }
        w.orderByDesc(MesComplaintPackage::getCreateTime).orderByDesc(MesComplaintPackage::getId);

        Page<MesComplaintPackage> result = packageMapper.selectPage(new Page<>(pageNo, size), w);
        List<ComplaintPackageListVO> records = new ArrayList<>();
        for (MesComplaintPackage row : result.getRecords()) {
            records.add(toListVo(row));
        }
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    /** 展平影响面并做成员上限校验（preview / build 同一算法） */
    private MesLotImpactFlatVO flattenOrReject(Long anchorLotId, String direction, Integer depth) {
        MesLotImpactFlatVO flat = mesLotService.flattenImpact(anchorLotId, direction, depth);
        List<MesLotImpactMemberVO> raw = flat.getMembers() == null ? List.of() : flat.getMembers();
        int limit = maxMembers < 1 ? 200 : maxMembers;
        AssertUtil.isTrue(raw.size() <= limit,
                ERR_TOO_LARGE + ": 影响面成员超过上限 " + limit + "，请缩小 depth 或换锚点");
        return flat;
    }

    /** 组装包头实体：锚点/方向/深度/快照/审计（不设 id 与 packageNo） */
    private MesComplaintPackage newHeader(ComplaintPackageBuildDTO dto, MesLotImpactFlatVO flat) {
        MesComplaintPackage header = new MesComplaintPackage();
        header.setAnchorLotId(flat.getAnchorLotId());
        header.setAnchorLotNo(flat.getAnchorLotNo());
        String dir = dto.getDirection() == null ? MesComplaintPackage.DIR_BOTH
                : dto.getDirection().trim().toLowerCase(Locale.ROOT);
        header.setDirection(dir.isEmpty() ? MesComplaintPackage.DIR_BOTH : dir);
        int depth = dto.getDepth() == null || dto.getDepth() <= 0 ? 5 : Math.min(dto.getDepth(), 20);
        header.setDepth(depth);
        header.setMemberCount(flat.getMembers() == null ? 0 : flat.getMembers().size());
        header.setTruncated(flat.isTruncated() ? 1 : 0);
        header.setReasonCode(blankToNull(dto.getReasonCode(), 64));
        header.setRemark(blankToNull(dto.getRemark(), 512));
        header.setStatus(MesComplaintPackage.STATUS_READY);
        header.setCreateBy(resolveCreateBy());
        return header;
    }

    /** 展平结果 → 成员实体（qty/status 取生成时快照；packageId 由 Writer 回填） */
    private List<MesComplaintPackageMember> toPersistMembers(MesLotImpactFlatVO flat) {
        List<MesComplaintPackageMember> out = new ArrayList<>();
        if (flat.getMembers() == null) {
            return out;
        }
        for (MesLotImpactMemberVO m : flat.getMembers()) {
            MesComplaintPackageMember row = new MesComplaintPackageMember();
            row.setLotId(m.getLotId());
            row.setLotNo(m.getLotNo());
            row.setRelation(m.getRelation());
            row.setDepthFromAnchor(m.getDepthFromAnchor());
            row.setQtySnapshot(m.getQty());
            row.setStatusSnapshot(m.getStatus());
            out.add(row);
        }
        return out;
    }

    /** 展平结果 → preview 成员 VO */
    private List<ComplaintPackageMemberVO> toPreviewMembers(MesLotImpactFlatVO flat) {
        List<MesLotImpactMemberVO> raw = flat.getMembers() == null ? List.of() : flat.getMembers();
        List<ComplaintPackageMemberVO> members = new ArrayList<>(raw.size());
        for (MesLotImpactMemberVO m : raw) {
            ComplaintPackageMemberVO row = new ComplaintPackageMemberVO();
            row.setLotId(m.getLotId());
            row.setLotNo(m.getLotNo());
            row.setRelation(m.getRelation());
            row.setDepth(m.getDepthFromAnchor());
            row.setQty(m.getQty());
            row.setStatus(m.getStatus());
            members.add(row);
        }
        return members;
    }

    /** preview 摘要：active Hold 批次数 / 报废批次数 / 未关闭告警条数 */
    private ComplaintPackageSummaryVO buildPreviewSummary(List<ComplaintPackageMemberVO> members) {
        ComplaintPackageSummaryVO s = new ComplaintPackageSummaryVO();
        if (members == null || members.isEmpty()) {
            return s;
        }
        long holdLots = 0;
        long scrapLots = 0;
        for (ComplaintPackageMemberVO m : members) {
            if (m.getLotId() != null && holdService.hasActive(m.getLotId())) {
                holdLots++;
            }
            if (STATUS_SCRAPPED.equalsIgnoreCase(m.getStatus())) {
                scrapLots++;
            }
        }
        s.setActiveHoldCount(holdLots);
        s.setScrapLotCount(scrapLots);
        s.setOpenAlarmCount(alarmFacade.countUnclearedForLots(
                members.stream().map(ComplaintPackageMemberVO::getLotId).collect(Collectors.toList())));
        return s;
    }

    /** 包头实体 → 分页摘要 VO */
    private static ComplaintPackageListVO toListVo(MesComplaintPackage row) {
        ComplaintPackageListVO vo = new ComplaintPackageListVO();
        vo.setPackageId(row.getId());
        vo.setPackageNo(row.getPackageNo());
        vo.setAnchorLotNo(row.getAnchorLotNo());
        vo.setDirection(row.getDirection());
        vo.setDepth(row.getDepth());
        vo.setMemberCount(row.getMemberCount());
        vo.setTruncated(row.getTruncated() != null && row.getTruncated() == 1);
        vo.setStatus(row.getStatus());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }

    /** 当前登录人（无登录上下文返回 null；与 HoldServiceImpl 同款约定） */
    private static Long resolveCreateBy() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception ignore) {
            return null;
        }
    }

    /** 空白转 null；超长截断（DTO @Size 已挡，双保险） */
    private static String blankToNull(String raw, int max) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** 沿异常链拼所有消息，供约束名判断（约束名可能在 DuplicateKey / Persistence / SQL 层） */
    private static String constraintMessage(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        Throwable t = ex;
        while (t != null) {
            if (t instanceof DuplicateKeyException || t instanceof DataIntegrityViolationException
                    || t.getMessage() != null) {
                if (t.getMessage() != null) {
                    sb.append(t.getMessage()).append(' ');
                }
            }
            t = t.getCause();
        }
        return sb.toString();
    }
}
