package com.mes.lot.dto;

import lombok.Data;

/** Strip 批量登记中的一条 */
@Data
public class LotStripSaveDTO {

    /** 条号 */
    private String stripNo;

    /** 批内序号 */
    private Integer seqNo;

    /** 本条颗数 */
    private Integer dieQty;

    /** 该条最终判定档，可空 */
    private String binCode;

    /** 条级状态，可空 */
    private String status;

    private String remark;
}
