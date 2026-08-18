package com.mes.edc.dto;

import lombok.Data;

/** 计划分页查询 */
@Data
public class MesEdcPlanQuery {
    private Long stepId;
    /** 1要求门禁 0不要求；空=全要 */
    private Integer required;
    /** 1启用 0停用；空=全要 */
    private Integer enabled;
    private long page = 1;
    private long size = 20;
}
