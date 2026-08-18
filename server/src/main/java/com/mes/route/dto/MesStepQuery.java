package com.mes.route.dto;

import lombok.Data;

/** 工序分页查询 */
@Data
public class MesStepQuery {
    /** 编码/名称关键字 */
    private String keyword;
    /** 状态：1正常 0禁用 */
    private Integer status;
    private long page = 1;
    private long size = 20;
}
