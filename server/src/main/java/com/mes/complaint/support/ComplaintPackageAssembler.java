package com.mes.complaint.support;

import com.mes.alarm.facade.AlarmFacade;
import com.mes.alarm.vo.AlarmVO;
import com.mes.complaint.entity.MesComplaintPackage;
import com.mes.complaint.entity.MesComplaintPackageMember;
import com.mes.complaint.vo.ComplaintPackageLotHoldsVO;
import com.mes.complaint.vo.ComplaintPackageMemberVO;
import com.mes.complaint.vo.ComplaintPackageSummaryVO;
import com.mes.complaint.vo.ComplaintPackageVO;
import com.mes.history.facade.HistoryFacade;
import com.mes.history.vo.HistoryTxVO;
import com.mes.hold.service.HoldService;
import com.mes.hold.vo.MesHoldVO;
import com.mes.lot.service.MesLotService;
import com.mes.lot.vo.MesLotGenealogyNodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 提交后只读装配。按块 / 按 Lot 失败隔离；失败记 WARN。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintPackageAssembler {

    /** Hold 状态：生效中 */
    private static final String HOLD_ACTIVE = "active";
    /** Hold 状态：已释放 */
    private static final String HOLD_RELEASED = "released";
    /** Lot 报废状态（摘要判定用） */
    private static final String STATUS_SCRAPPED = "scrapped";
    /** 每 Lot Hold active/released 各自上限 */
    private static final int HOLD_CAP = 20;
    /** 每 Lot 未关闭告警上限 */
    private static final int ALARM_CAP = 20;

    @Value("${mes.complaint-package.history-per-lot:100}")
    private int historyPerLot;

    private final HistoryFacade historyFacade;
    private final HoldService holdService;
    private final AlarmFacade alarmFacade;
    private final MesLotService mesLotService;

    /**
     * 只读装配：包头 + 成员（固定排序）+ 四块（genealogy / 履历 / Hold / Alarm）+ 摘要。
     * 必须在写入事务提交后调用（R1）；按块 / 按 Lot 失败隔离，失败记 WARN（R11）。
     *
     * @param treeForBuild build 复用的展开树；get 传 null（内部现查 genealogy）
     */
    public ComplaintPackageVO assemble(MesComplaintPackage pkg,
                                       List<MesComplaintPackageMember> members,
                                       MesLotGenealogyNodeVO treeForBuild) {
        ComplaintPackageVO vo = toHeader(pkg);
        List<ComplaintPackageMemberVO> memberVos = toMemberVos(members);
        vo.setMembers(memberVos);
        vo.setMemberCount(memberVos.size());

        vo.setGenealogy(resolveGenealogy(pkg, treeForBuild));
        vo.setHistoriesByLot(loadHistories(pkg.getPackageNo(), memberVos));
        vo.setHoldsByLot(loadHolds(pkg.getPackageNo(), memberVos));
        vo.setAlarmsByLot(loadAlarms(pkg.getPackageNo(), memberVos));
        vo.setSummary(buildSummary(memberVos, vo.getHoldsByLot()));
        return vo;
    }

    /**
     * 生效的「每 Lot 履历条数」：配置 &lt; 1 时按 100 兜底。
     * 查询（{@link #loadHistories}）与溯源包封面共用此单一来源（K15 / D7），
     * 禁止在别处再写 `history-per-lot` 的 `@Value` 或第二份钳制。
     */
    public int historyPerLot() {
        return historyPerLot < 1 ? 100 : historyPerLot;
    }

    /** 每 Lot Hold `active` / `released` 各自上限（封面用；返回常量防分叉，K15 / D9） */
    public int holdCap() {
        return HOLD_CAP;
    }

    /** 每 Lot 未关闭告警上限（封面用；返回常量防分叉，K15 / D9） */
    public int alarmCap() {
        return ALARM_CAP;
    }

    /** 包头实体 → VO 头部字段 */
    private ComplaintPackageVO toHeader(MesComplaintPackage pkg) {
        ComplaintPackageVO vo = new ComplaintPackageVO();
        vo.setPackageId(pkg.getId());
        vo.setPackageNo(pkg.getPackageNo());
        vo.setAnchorLotId(pkg.getAnchorLotId());
        vo.setAnchorLotNo(pkg.getAnchorLotNo());
        vo.setDirection(pkg.getDirection());
        vo.setDepth(pkg.getDepth());
        vo.setTruncated(pkg.getTruncated() != null && pkg.getTruncated() == 1);
        vo.setReasonCode(pkg.getReasonCode());
        vo.setRemark(pkg.getRemark());
        vo.setStatus(pkg.getStatus());
        vo.setCreateBy(pkg.getCreateBy());
        vo.setCreateTime(pkg.getCreateTime());
        return vo;
    }

    /** 成员实体 → VO 并排序：relation → depth → lotNo（R7，保证 get 顺序稳定） */
    private List<ComplaintPackageMemberVO> toMemberVos(List<MesComplaintPackageMember> members) {
        List<ComplaintPackageMemberVO> out = new ArrayList<>();
        if (members == null) {
            return out;
        }
        for (MesComplaintPackageMember m : members) {
            ComplaintPackageMemberVO row = new ComplaintPackageMemberVO();
            row.setLotId(m.getLotId());
            row.setLotNo(m.getLotNo());
            row.setRelation(m.getRelation());
            row.setDepth(m.getDepthFromAnchor());
            row.setQty(m.getQtySnapshot());
            row.setStatus(m.getStatusSnapshot());
            out.add(row);
        }
        out.sort(ComplaintPackageAssembler::compareMembers);
        return out;
    }

    /** 成员排序比较器：ANCHOR(0) → ANCESTOR(1) → DESCENDANT(2) → 未知(9) */
    static int compareMembers(ComplaintPackageMemberVO a, ComplaintPackageMemberVO b) {
        int rel = Integer.compare(relRank(a.getRelation()), relRank(b.getRelation()));
        if (rel != 0) {
            return rel;
        }
        int da = a.getDepth() == null ? 0 : a.getDepth();
        int db = b.getDepth() == null ? 0 : b.getDepth();
        int depth = Integer.compare(da, db);
        if (depth != 0) {
            return depth;
        }
        String na = a.getLotNo() == null ? "" : a.getLotNo();
        String nb = b.getLotNo() == null ? "" : b.getLotNo();
        return na.compareTo(nb);
    }

    /** 关系 → 排序权重（ANCHOR 最前，未知关系最后） */
    static int relRank(String relation) {
        if (MesComplaintPackageMember.REL_ANCHOR.equals(relation)) {
            return 0;
        }
        if (MesComplaintPackageMember.REL_ANCESTOR.equals(relation)) {
            return 1;
        }
        if (MesComplaintPackageMember.REL_DESCENDANT.equals(relation)) {
            return 2;
        }
        return 9;
    }

    /** 根据LotId展开 genealogy；失败为 null */
    private MesLotGenealogyNodeVO resolveGenealogy(MesComplaintPackage pkg, MesLotGenealogyNodeVO treeForBuild) {
        if (treeForBuild != null) {
            return treeForBuild;
        }
        try {
            return mesLotService.genealogy(pkg.getAnchorLotId(), pkg.getDirection(), pkg.getDepth());
        } catch (Exception ex) {
            log.warn("assemble genealogy failed packageNo={} lotId={} block=genealogy err={}",
                    pkg.getPackageNo(), pkg.getAnchorLotId(), ex.toString());
            return null;
        }
    }

    /** 每成员履历：一次 IN + 窗口函数取每批最近 cap 条（ASC）；单批失败 → 空列表 */
    private Map<Long, List<HistoryTxVO>> loadHistories(String packageNo, List<ComplaintPackageMemberVO> members) {
        Map<Long, List<HistoryTxVO>> map = new LinkedHashMap<>();
        for (ComplaintPackageMemberVO m : members) {
            if (m.getLotId() != null) {
                map.put(m.getLotId(), List.of());
            }
        }
        if (map.isEmpty()) {
            return map;
        }
        int cap = historyPerLot();
        try {
            List<HistoryTxVO> rows = historyFacade.listByLots(map.keySet(), cap);
            if (rows == null || rows.isEmpty()) {
                return map;
            }
            // listByLots 已按 lot 分桶并翻成 ASC，每桶 ≤ cap；这里按 lotId 归位
            Map<Long, List<HistoryTxVO>> grouped = new LinkedHashMap<>();
            for (HistoryTxVO h : rows) {
                grouped.computeIfAbsent(h.getLotId(), k -> new ArrayList<>()).add(h);
            }
            for (Map.Entry<Long, List<HistoryTxVO>> e : grouped.entrySet()) {
                if (map.containsKey(e.getKey())) {
                    map.put(e.getKey(), e.getValue());
                }
            }
        } catch (Exception ex) {
            log.warn("assemble history failed packageNo={} block=historiesByLot err={}",
                    packageNo, ex.toString());
        }
        return map;
    }

    /** 每成员 Hold：按 status 拆 active / released，各截 20；失败 → 空盒 */
    private Map<Long, ComplaintPackageLotHoldsVO> loadHolds(String packageNo, List<ComplaintPackageMemberVO> members) {
        Map<Long, ComplaintPackageLotHoldsVO> map = new LinkedHashMap<>();
        for (ComplaintPackageMemberVO m : members) {
            if (m.getLotId() != null) {
                map.put(m.getLotId(), new ComplaintPackageLotHoldsVO());
            }
        }
        if (map.isEmpty()) {
            return map;
        }
        try {
            List<MesHoldVO> rows = holdService.listByLots(map.keySet());
            if (rows == null || rows.isEmpty()) {
                return map;
            }
            // 已按 hold_time 倒序返回；逐条入桶，每桶 active/released 各截 HOLD_CAP
            for (MesHoldVO h : rows) {
                ComplaintPackageLotHoldsVO box = map.get(h.getLotId());
                if (box == null) {
                    continue;
                }
                if (HOLD_ACTIVE.equals(h.getStatus())) {
                    if (box.getActive().size() < HOLD_CAP) {
                        box.getActive().add(h);
                    }
                } else if (HOLD_RELEASED.equals(h.getStatus())) {
                    if (box.getReleased().size() < HOLD_CAP) {
                        box.getReleased().add(h);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("assemble hold failed packageNo={} block=holdsByLot err={}",
                    packageNo, ex.toString());
        }
        return map;
    }

    /** 未关闭告警：一次 IN 批量查（R12），按 entityId 分组、每 Lot 截 20；整批失败 → 全空 */
    private Map<Long, List<AlarmVO>> loadAlarms(String packageNo, List<ComplaintPackageMemberVO> members) {
        Map<Long, List<AlarmVO>> map = new LinkedHashMap<>();
        List<Long> lotIds = new ArrayList<>();
        for (ComplaintPackageMemberVO m : members) {
            if (m.getLotId() != null) {
                map.put(m.getLotId(), new ArrayList<>());
                lotIds.add(m.getLotId());
            }
        }
        if (lotIds.isEmpty()) {
            return map;
        }
        try {
            List<AlarmVO> rows = alarmFacade.listUnclearedForLots(lotIds);
            if (rows != null) {
                for (AlarmVO a : rows) {
                    if (a.getEntityId() == null) {
                        continue;
                    }
                    List<AlarmVO> bucket = map.get(a.getEntityId());
                    if (bucket != null && bucket.size() < ALARM_CAP) {
                        bucket.add(a);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("assemble alarm failed packageNo={} lotId=null block=alarmsByLot err={}",
                    packageNo, ex.toString());
        }
        return map;
    }

    /** 详情摘要：复用已装配的 Hold 盒子数 active 批次（免二次 N+1 hasActive）+ 报废 + 告警计数 */
    private ComplaintPackageSummaryVO buildSummary(List<ComplaintPackageMemberVO> members,
                                                   Map<Long, ComplaintPackageLotHoldsVO> holdsByLot) {
        ComplaintPackageSummaryVO s = new ComplaintPackageSummaryVO();
        if (members == null || members.isEmpty()) {
            return s;
        }
        long holdLots = 0;
        long scrapLots = 0;
        for (ComplaintPackageMemberVO m : members) {
            if (STATUS_SCRAPPED.equalsIgnoreCase(m.getStatus())) {
                scrapLots++;
            }
            ComplaintPackageLotHoldsVO box = m.getLotId() == null ? null : holdsByLot.get(m.getLotId());
            if (box != null && box.getActive() != null && !box.getActive().isEmpty()) {
                holdLots++;
            }
        }
        s.setActiveHoldCount(holdLots);
        s.setScrapLotCount(scrapLots);
        try {
            s.setOpenAlarmCount(alarmFacade.countUnclearedForLots(
                    members.stream().map(ComplaintPackageMemberVO::getLotId).filter(Objects::nonNull).toList()));
        } catch (Exception ex) {
            log.warn("assemble summary alarm-count failed block=summary err={}", ex.toString());
        }
        return s;
    }

}
