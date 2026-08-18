package com.mes.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志
 */
@Data
@TableName("sys_oper_log")
public class SysOperLog {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 操作人ID */
    private Long userId;

    /** 操作人工号 */
    private String username;

    /** 模块 */
    private String module;

    /** 动作 */
    private String action;

    /** 批次号 */
    private String lotNo;

    /** 请求URI */
    private String requestUri;

    /** 请求方法 */
    private String requestMethod;

    /** 请求参数摘要 */
    private String requestParam;

    /** 结果：1成功 0失败 */
    private Integer status;

    /** 错误信息 */
    private String errorMsg;

    /** IP */
    private String ip;

    /** 耗时ms */
    private Long costTime;

    /** 操作时间 */
    private LocalDateTime createTime;
}
