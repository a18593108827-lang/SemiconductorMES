package com.mes.alarm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 一条真实告警（谁响了、挂在哪个对象上、现在什么状态）。
 * 关闭走 CLEARED，不要靠软删当关闭。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_alarm")
public class MesAlarm extends BaseEntity {

    /** 刚响，还没人认 */
    public static final String STATUS_OPEN = "OPEN";
    /** 有人点过确认，还没关 */
    public static final String STATUS_ACK = "ACK";
    /** 已关闭，结束了 */
    public static final String STATUS_CLEARED = "CLEARED";

    /** 挂不上具体对象（只有码和消息） */
    public static final String ENTITY_NONE = "NONE";
    /** 挂在批次上 */
    public static final String ENTITY_LOT = "LOT";
    /** 挂在机台上 */
    public static final String ENTITY_EQP = "EQP";
    /** 挂在 SPC 图上 */
    public static final String ENTITY_CHART = "CHART";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 告警码，对应码表，比如 SPC_OOC */
    private String code;
    /** 响的时候级别快照：严重 / 警告 / 提示；以后改码表不影响历史 */
    private String level;
    /** 生命周期：OPEN → ACK → CLEARED；也可以 OPEN 直接关 */
    private String status;
    /** 给人看的一句话说明 */
    private String message;
    /** 挂在哪类对象上：批次 / 机台 / 图 / 无 */
    private String entityType;
    /** 对象主键；没有对象就写 0，别存 null */
    private Long entityId;
    /** 去重键：同码+同对象 OPEN 时合并成一条，格式 code|type|id */
    private String dedupeKey;
    /** 触发方塞的上下文 JSON（lotId、chartId 等），查详情用 */
    private String payloadJson;
    /** 同一条 OPEN 被反复 raise 的次数；刷屏时只加这个数 */
    private Integer raiseCount;
    /** 第一次响的时间 */
    private LocalDateTime firstRaiseAt;
    /** 最近一次响的时间（去重 bump 会刷新） */
    private LocalDateTime lastRaiseAt;
    /** 谁点的确认 */
    private Long ackBy;
    /** 什么时候确认的 */
    private LocalDateTime ackAt;
    /** 确认时写的备注 */
    private String ackRemark;
    /** 谁关的 */
    private Long clearBy;
    /** 什么时候关的 */
    private LocalDateTime clearAt;
    /** 关闭时写的备注 */
    private String clearRemark;
}
