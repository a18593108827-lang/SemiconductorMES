package com.mes.edc.dto;

import lombok.Data;

/** 特性分页查询 */
@Data
public class MesEdcParamQuery {
    /** 搜编码或名称 */
    private String keyword;
    /** 1启用 0停用；空=全要 */
    private Integer enabled;
    private long page = 1;
    private long size = 20;
}
