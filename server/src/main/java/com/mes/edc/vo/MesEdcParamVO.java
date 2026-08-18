package com.mes.edc.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 量测特性对外展示 */
@Data
public class MesEdcParamVO {
    private Long id;
    private String paramCode;
    private String paramName;
    private String unit;
    private String valueType;
    private Integer enabled;
    private String remark;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
