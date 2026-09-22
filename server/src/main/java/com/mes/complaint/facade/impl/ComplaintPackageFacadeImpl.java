package com.mes.complaint.facade.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.alarm.facade.AlarmFacade;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.common.PageResult;
import com.mes.complaint.dto.ComplaintPackageBuildDTO;
import com.mes.complaint.dto.ComplaintPackageContainDTO;
import com.mes.complaint.dto.ComplaintPackagePreviewDTO;
import com.mes.complaint.dto.ComplaintPackageQuery;
import com.mes.complaint.entity.MesComplaintPackage;
import com.mes.complaint.entity.MesComplaintPackageMember;
import com.mes.complaint.facade.ComplaintPackageFacade;
import com.mes.complaint.mapper.MesComplaintPackageMapper;
import com.mes.complaint.mapper.MesComplaintPackageMemberMapper;
import com.mes.complaint.support.ComplaintPackageAssembler;
import com.mes.complaint.support.ComplaintPackageContainWriter;
import com.mes.complaint.support.ComplaintPackageExporter;
import com.mes.complaint.support.ComplaintPackageNoAllocator;
import com.mes.complaint.support.ComplaintPackageWriter;
import com.mes.complaint.vo.ComplaintContainLotVO;
import com.mes.complaint.vo.ComplaintContainResultVO;
import com.mes.complaint.vo.ComplaintPackageExportFile;
import com.mes.complaint.vo.ComplaintPackageListVO;
import com.mes.complaint.vo.ComplaintPackageMemberVO;
import com.mes.complaint.vo.ComplaintPackagePreviewVO;
import com.mes.complaint.vo.ComplaintPackageSummaryVO;
import com.mes.complaint.vo.ComplaintPackageVO;
import com.mes.hold.dto.MesHoldCreateDTO;
import com.mes.hold.service.HoldService;
import com.mes.lot.service.MesLotService;
import com.mes.lot.vo.MesLotImpactFlatVO;
import com.mes.lot.vo.MesLotImpactMemberVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 客诉追溯包门面：开关 / preview / build / get / page / export / contain。
 * 编排方法不加事务；写入只走 Writer / ContainWriter。
 */
@Slf4j
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
    /** 导出 format 非 json / zip */
    public static final String ERR_FORMAT = "COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED";
    /** contain 的 lotIds 越界 */
    public static final String ERR_LOT_NOT_IN = "COMPLAINT_PACKAGE_LOT_NOT_IN_PACKAGE";
    /** 同包正在 contain */
    public static final String ERR_IN_PROGRESS = "COMPLAINT_PACKAGE_CONTAIN_IN_PROGRESS";
    /** 包行已 VOID */
    public static final String ERR_VOID = "COMPLAINT_PACKAGE_VOID";

    /** 报废状态（摘要 scrapLotCount 判定用） */
    private static final String STATUS_SCRAPPED = "scrapped";
    /** 包号 UK 冲突最大重试次数 */
    private static final int WRITE_RETRY = 3;
    /** list 默认页大小 */
    private static final int LIST_SIZE_DEFAULT = 20;
    /** list 页大小上限（超出截断） */
    private static final int LIST_SIZE_MAX = 100;
    /** 导出格式：JSON 附件（空 / 空白等价） */
    private static final String FORMAT_JSON = "json";
    /** 导出格式：ZIP 证据包（JSON + README.txt） */
    private static final String FORMAT_ZIP = "zip";

    @Value("${mes.complaint-package.enabled:false}")
    private boolean enabled;

    @Value("${mes.complaint-package.max-members:200}")
    private int maxMembers;

    /** CONTAINING 无心跳超过此时长允许换 token 抢占 */
    @Value("${mes.complaint-package.contain-rescue-seconds:60}")
    private int containRescueSeconds;

    private final MesLotService mesLotService;
    private final HoldService holdService;
    private final AlarmFacade alarmFacade;
    private final ComplaintPackageNoAllocator noAllocator;
    private final ComplaintPackageWriter writer;
    private final ComplaintPackageContainWriter containWriter;
    private final ComplaintPackageAssembler assembler;
    private final ComplaintPackageExporter exporter;
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

    /** 附件导出：assertEnabled → format → get → Exporter（JSON / ZIP）；禁加事务 */
    @Override
    public ComplaintPackageExportFile exportFile(Long id, String format) {
        assertEnabled();
        String normalized = assertFormat(format);
        // 一次取值：JSON 的 exportedAt / exportedBy 与 README 的导出行必须同值（K5）
        LocalDateTime exportedAt = LocalDateTime.now();
        Long exportedBy = resolveCreateBy();
        ComplaintPackageVO vo = get(id);
        try {
            if (FORMAT_ZIP.equals(normalized)) {
                byte[] zip = exporter.toZipBytes(vo, exportedBy, exportedAt,
                        assembler.historyPerLot(), assembler.holdCap(), assembler.alarmCap());
                return new ComplaintPackageExportFile(exporter.zipFileName(vo.getPackageNo()), zip);
            }
            byte[] json = exporter.toJsonBytes(vo, exportedBy, exportedAt);
            return new ComplaintPackageExportFile(exporter.jsonFileName(vo.getPackageNo()), json);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 序列化 / 打包失败必须留痕（K12 单点翻译，文案与 CP-4 一致）
            log.warn("complaint export failed packageNo={} format={}", vo.getPackageNo(), normalized, e);
            throw new BusinessException("追溯包导出失败");
        }
    }

    /** token 占位后逐 Lot create；无外层事务；finally 结束 CAS 后重读包头 */
    @Override
    public ComplaintContainResultVO contain(Long id, ComplaintPackageContainDTO dto) {
        assertEnabled();
        AssertUtil.notNull(id, "包 id 不能为空");
        AssertUtil.notNull(dto, "参数不能为空");
        holdService.assertReasonUsable(dto.getReasonCode());

        MesComplaintPackage pkg = packageMapper.selectById(id);
        AssertUtil.notNull(pkg, ERR_NOT_FOUND + ": 追溯包不存在");
        if (MesComplaintPackage.STATUS_VOID.equals(pkg.getStatus())) {
            throw new BusinessException(ERR_VOID + ": 追溯包已作废");
        }

        List<MesComplaintPackageMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<MesComplaintPackageMember>()
                        .eq(MesComplaintPackageMember::getPackageId, id));
        List<MesComplaintPackageMember> targets = resolveTargets(members, dto.getLotIds());

        String token = UUID.randomUUID().toString();
        if (!containWriter.occupy(id, token, containRescueSeconds)) {
            rejectOccupyMiss(id);
        }

        // 三个篮子须在 try 外声明：finally 的 finish 依赖 succeeded，VO 装配又在 finish 之后（K10）
        List<ComplaintContainLotVO> succeeded = new ArrayList<>();
        List<ComplaintContainLotVO> skipped = new ArrayList<>();
        List<ComplaintContainLotVO> failed = new ArrayList<>();
        try {
            // 占位成功后立即进入 try：此后任何异常都由 finally 的 finish 兜住，不留 60s 占位残留待救援
            String holdRemark = holdRemark(pkg.getPackageNo(), dto.getRemark());
            for (MesComplaintPackageMember m : targets) {
                applyOne(m, dto.getReasonCode().trim(), holdRemark, succeeded, skipped, failed);
                if (!containWriter.heartbeat(id, token)) {
                    break;
                }
            }
        } finally {
            containWriter.finish(id, token, !succeeded.isEmpty(), resolveCreateBy());
        }

        MesComplaintPackage latest = packageMapper.selectById(id);
        AssertUtil.notNull(latest, ERR_NOT_FOUND + ": 追溯包不存在");
        ComplaintContainResultVO vo = new ComplaintContainResultVO();
        vo.setSucceeded(succeeded);
        vo.setSkipped(skipped);
        vo.setFailed(failed);
        vo.setSucceededCount(succeeded.size());
        vo.setSkippedCount(skipped.size());
        vo.setFailedCount(failed.size());
        vo.setStatus(latest.getStatus());
        vo.setContainBy(latest.getContainBy());
        vo.setContainTime(latest.getContainTime());
        return vo;
    }

    /** 单 Lot create；已锁跳过（skipped），业务/运行时错进 failed，不中断循环 */
    private void applyOne(MesComplaintPackageMember m, String reasonCode, String holdRemark,
                          List<ComplaintContainLotVO> succeeded,
                          List<ComplaintContainLotVO> skipped,
                          List<ComplaintContainLotVO> failed) {
        MesHoldCreateDTO create = new MesHoldCreateDTO();
        create.setLotId(m.getLotId());
        create.setReasonCode(reasonCode);
        create.setRemark(holdRemark);
        try {
            holdService.create(create);
            succeeded.add(lotVo(m, null, null));
        } catch (BusinessException e) {
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains(HoldService.MSG_ALREADY_HELD)) {
                skipped.add(lotVo(m, null, null));
            } else {
                failed.add(lotVo(m, failCode(e), msg));
            }
        } catch (RuntimeException e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            failed.add(lotVo(m, "ERROR", msg));
        }
    }

    /** 占位失败：按当前行状态报进行中 / 作废 / 不存在 */
    private void rejectOccupyMiss(Long id) {
        MesComplaintPackage row = packageMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ERR_NOT_FOUND + ": 追溯包不存在");
        }
        if (MesComplaintPackage.STATUS_CONTAINING.equals(row.getStatus())) {
            throw new BusinessException(ERR_IN_PROGRESS + ": 该追溯包正在遏制");
        }
        if (MesComplaintPackage.STATUS_VOID.equals(row.getStatus())) {
            throw new BusinessException(ERR_VOID + ": 追溯包已作废");
        }
        throw new BusinessException(ERR_NOT_FOUND + ": 追溯包不存在");
    }

    /** 把“用户给的 lotIds + 包成员表”解析成一份确定的目标清单：空 lotIds=全员；非空须 必须是 成员；结果按 lotId 升序 */
    private List<MesComplaintPackageMember> resolveTargets(List<MesComplaintPackageMember> members,
                                                           List<String> lotIds) {
        List<MesComplaintPackageMember> sorted = new ArrayList<>(members == null ? List.of() : members);
        sorted.sort(Comparator.comparing(MesComplaintPackageMember::getLotId, Comparator.nullsLast(Long::compareTo)));
        if (lotIds == null || lotIds.isEmpty()) {
            return sorted;
        }
        Set<Long> memberIds = sorted.stream()
                .map(MesComplaintPackageMember::getLotId)
                .collect(Collectors.toSet());
        Set<Long> requested = new LinkedHashSet<>();
        for (String raw : lotIds) {
            if (!StringUtils.hasText(raw)) {
                throw new BusinessException(ERR_LOT_NOT_IN + ": 批次不在本包影响面内");
            }
            try {
                requested.add(Long.parseLong(raw.trim()));
            } catch (NumberFormatException e) {
                throw new BusinessException(ERR_LOT_NOT_IN + ": 批次不在本包影响面内");
            }
        }
        for (Long lotId : requested) {
            if (!memberIds.contains(lotId)) {
                throw new BusinessException(ERR_LOT_NOT_IN + ": 批次不在本包影响面内");
            }
        }
        List<MesComplaintPackageMember> out = new ArrayList<>();
        for (MesComplaintPackageMember m : sorted) {
            if (requested.contains(m.getLotId())) {
                out.add(m);
            }
        }
        return out;
    }

    /** Hold 备注拼 [包号]；超 512 截尾保前缀 */
    private static String holdRemark(String packageNo, String remark) {
        String prefix = "[" + (packageNo == null ? "" : packageNo) + "]";
        String body = StringUtils.hasText(remark) ? remark.trim() : "";
        String out = body.isEmpty() ? prefix : prefix + " " + body;
        return out.length() > 512 ? out.substring(0, 512) : out;
    }

    /** 三段明细行 */
    private static ComplaintContainLotVO lotVo(MesComplaintPackageMember m, String code, String message) {
        ComplaintContainLotVO vo = new ComplaintContainLotVO();
        vo.setLotId(m.getLotId());
        vo.setLotNo(m.getLotNo());
        vo.setCode(code);
        vo.setMessage(message);
        return vo;
    }

    /** failed.code：有 COMPLAINT_PACKAGE_ 前缀则抽出，否则 HOLD */
    private static String failCode(BusinessException e) {
        String m = e.getMessage();
        if (m != null && m.startsWith("COMPLAINT_PACKAGE_")) {
            int colon = m.indexOf(':');
            return colon > 0 ? m.substring(0, colon).trim() : m;
        }
        return "HOLD";
    }

    /** format 归一：null / 空白 = json；json / zip 忽略大小写；返回归一值，其它抛 ERR_FORMAT */
    private static String assertFormat(String format) {
        if (!StringUtils.hasText(format)) {
            return FORMAT_JSON;
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        if (FORMAT_JSON.equals(normalized) || FORMAT_ZIP.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ERR_FORMAT + ": 不支持的导出格式");
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
