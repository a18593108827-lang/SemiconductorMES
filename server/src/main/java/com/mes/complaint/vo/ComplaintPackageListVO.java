package com.mes.complaint.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 追溯包分页摘要（不含成员） */
@Data
public class ComplaintPackageListVO {

    /** 包 ID */
    private Long packageId;
    /** 业务号 CP-yyyyMMdd-序号 */
    private String packageNo;
    /** 锚点 Lot 号 */
    private String anchorLotNo;
    /** 展开方向 up/down/both */
    private String direction;
    /** 展开深度 */
    private Integer depth;
    /** 成员数快照 */
    private Integer memberCount;
    /** 是否触达上限截断 */
    private boolean truncated;
    /** 包状态 */
    private String status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
