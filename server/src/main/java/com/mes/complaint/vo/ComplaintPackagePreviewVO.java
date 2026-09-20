package com.mes.complaint.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 追溯包 preview（不落库） */
@Data
public class ComplaintPackagePreviewVO {

    /** 锚点 Lot */
    private Long anchorLotId;
    /** 锚点 Lot 号 */
    private String anchorLotNo;
    /** 影响面成员 */
    private List<ComplaintPackageMemberVO> members = new ArrayList<>();
    /** 成员数 */
    private int memberCount;
    /** 是否触达 depth/成员上限截断 */
    private boolean truncated;
    /** 只读聚合摘要 */
    private ComplaintPackageSummaryVO summary;
}
