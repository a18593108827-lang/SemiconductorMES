package com.mes.complaint.vo;

import lombok.Data;

/** 影响面成员（preview） */
@Data
public class ComplaintPackageMemberVO {

    /** 成员 Lot */
    private Long lotId;
    /** 成员 Lot 号 */
    private String lotNo;
    /** ANCHOR / ANCESTOR / DESCENDANT */
    private String relation;
    /** 相对锚点深度 */
    private Integer depth;
    /** Lot 数量 */
    private Integer qty;
    /** Lot 状态快照 */
    private String status;
}
