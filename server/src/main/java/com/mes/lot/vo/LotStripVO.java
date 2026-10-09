package com.mes.lot.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** Strip 条级 */
@Data
public class LotStripVO {

    private Long id;
    private Long lotId;
    private String stripNo;
    private Integer seqNo;
    private Integer dieQty;
    private String binCode;
    private String status;
    private String remark;
    private Long createBy;
    private LocalDateTime createTime;
}
