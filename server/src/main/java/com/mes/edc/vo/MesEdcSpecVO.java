package com.mes.edc.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 规格对外展示（带上特性编码名称，列表好看一点） */
@Data
public class MesEdcSpecVO {
    private Long id;
    private Long paramId;
    private String paramCode;
    private String paramName;
    private String productCode;
    private Integer versionNo;
    private String status;
    private BigDecimal usl;
    private BigDecimal lsl;
    private BigDecimal target;
    private String remark;
    private LocalDateTime publishedAt;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
