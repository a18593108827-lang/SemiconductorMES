package com.mes.edc.dto;

import lombok.Data;

/** 采集分页：按批次 / 工序 / 本趟 visit 筛 */
@Data
public class MesEdcCollectionQuery {
    private Long lotId;
    private Long stepId;
    private Long trackInTxId;
    /** PASS / FAIL；空=全要 */
    private String result;
    private long page = 1;
    private long size = 20;
}
