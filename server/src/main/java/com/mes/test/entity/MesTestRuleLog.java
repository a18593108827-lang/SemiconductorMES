package com.mes.test.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 规则变更审计。只追加，无软删 */
@Data
@TableName("mes_test_rule_log")
public class MesTestRuleLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 规则 id */
    private Long ruleId;

    /** CREATE / UPDATE / DELETE */
    private String action;

    /** 变更前快照 JSON，新建为空 */
    private String beforeJson;

    /** 变更后快照 JSON，删除为空 */
    private String afterJson;

    /** 操作人 */
    private Long opBy;

    /** 操作时间 */
    private LocalDateTime opAt;
}
