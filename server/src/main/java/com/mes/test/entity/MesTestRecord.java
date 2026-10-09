package com.mes.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 测试记录头。作废只软删本表 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_test_record")
public class MesTestRecord extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务号 TR-yyyyMMdd-序号 */
    private String recordNo;

    /** 所属批 */
    private Long lotId;

    /** 批次号快照 */
    private String lotNo;

    /** CP / FT / OTHER */
    private String testStage;

    /** 测试程序名 */
    private String programName;

    /** 程序版本 */
    private String programVersion;

    /** 测试设备，外协或手工可空 */
    private Long eqpId;

    /** 设备编码快照 */
    private String eqpCode;

    /** 测试完成时间 */
    private LocalDateTime testTime;

    /** 本次测试颗数 */
    private Integer totalQty;

    /** FILE / API / MANUAL */
    private String sourceType;

    /** 文件引用或外部标识，不存文件本体 */
    private String sourceRef;

    private String remark;

    /** 登记人 */
    private Long createBy;
}
