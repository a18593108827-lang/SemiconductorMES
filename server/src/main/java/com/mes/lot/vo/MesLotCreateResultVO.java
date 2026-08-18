package com.mes.lot.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 新建批次结果 */
@Data
@AllArgsConstructor
public class MesLotCreateResultVO {
    private Long id;
    /** 实际落库批次号（自动或手动） */
    private String lotNo;
}
