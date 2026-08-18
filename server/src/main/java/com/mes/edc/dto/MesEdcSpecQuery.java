package com.mes.edc.dto;

import lombok.Data;

/** 规格分页查询 */
@Data
public class MesEdcSpecQuery {
    /** 按特性筛 */
    private Long paramId;
    /** 产品；传空串可查全产品默认 */
    private String productCode;
    /** draft / active / obsolete；空=全要 */
    private String status;
    private long page = 1;
    private long size = 20;
}
