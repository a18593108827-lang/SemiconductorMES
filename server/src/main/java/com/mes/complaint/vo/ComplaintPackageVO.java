package com.mes.complaint.vo;

import com.mes.alarm.vo.AlarmVO;
import com.mes.history.vo.HistoryTxVO;
import com.mes.lot.vo.MesLotGenealogyNodeVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 追溯包详情（build / get 同形） */
@Data
public class ComplaintPackageVO {

    /** 包 ID */
    private Long packageId;
    /** 业务号 CP-yyyyMMdd-序号 */
    private String packageNo;
    /** 锚点 Lot */
    private Long anchorLotId;
    /** 锚点 Lot 号 */
    private String anchorLotNo;
    /** 展开方向 up/down/both */
    private String direction;
    /** 展开深度 */
    private Integer depth;
    /** 成员数 */
    private Integer memberCount;
    /** 是否触达上限截断 */
    private boolean truncated;
    /** 调查原因码 */
    private String reasonCode;
    /** 备注 */
    private String remark;
    /** 包状态 READY/CONTAINING/CONTAINED/VOID */
    private String status;
    /** 创建人 */
    private Long createBy;
    /** 创建时间 */
    private LocalDateTime createTime;

    /** 成员清单（排序 relation → depth → lotNo） */
    private List<ComplaintPackageMemberVO> members = new ArrayList<>();
    /** 锚点谱系树（build 复用展开结果，get 现查；失败为 null） */
    private MesLotGenealogyNodeVO genealogy;
    /** 每成员履历：lotId → 最近 N 条 ASC（N=history-per-lot；单 Lot 失败为空列表） */
    private Map<Long, List<HistoryTxVO>> historiesByLot = new LinkedHashMap<>();
    /** 每成员 Hold：lotId → active/released 各截 20 */
    private Map<Long, ComplaintPackageLotHoldsVO> holdsByLot = new LinkedHashMap<>();
    /** 每成员未关闭告警：lotId → 最多 20 条（OPEN+ACK） */
    private Map<Long, List<AlarmVO>> alarmsByLot = new LinkedHashMap<>();
    /** 只读聚合摘要 */
    private ComplaintPackageSummaryVO summary;
}
