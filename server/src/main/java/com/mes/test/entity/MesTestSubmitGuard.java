package com.mes.test.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 提交守卫。同一 10 分钟桶内相同载荷只能插入一行 */
@Data
@TableName("mes_test_submit_guard")
public class MesTestSubmitGuard {

    private Long id;

    /** 所属批 */
    private Long lotId;

    /** 设备 id，无设备为 0 */
    private Long eqpKey;

    /** 测试程序名 */
    private String programName;

    /** 程序版本 */
    private String programVersion;

    /** 测试完成时间 */
    private LocalDateTime testTime;

    /** 本次测试颗数 */
    private Integer totalQty;
}
