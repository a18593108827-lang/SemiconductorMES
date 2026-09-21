package com.mes.complaint.vo;

import lombok.Data;

/** contain 单 Lot 明细；failed 才填 code/message */
@Data
public class ComplaintContainLotVO {

    private Long lotId;
    private String lotNo;
    private String code;
    private String message;
}
