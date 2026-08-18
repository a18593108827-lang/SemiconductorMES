package com.mes.lot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 批次实体：主数据 + 快照指针 + Track 写入的运行态
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_lot")
public class MesLot extends BaseEntity {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 厂内批次号（唯一） */
    private String lotNo;

    /** 产品编码（一期字符串，无 Product 表） */
    private String productCode;

    /** 数量 */
    private Integer qty;

    /** 累计报废数量 */
    private Integer scrapQty;

    /** 优先级：1–100，越大越急，默认 50 */
    private Integer priority;

    /** Hot Lot：0/1 */
    private Integer hotFlag;

    /** 客户侧批次号（对外对账/追溯，可空） */
    private String customerLot;

    /** 直系父 Lot（Split 子批） */
    private Long parentLotId;

    /** 合批后指向的主 Lot */
    private Long mergedToLotId;

    /** 目标/已绑工艺路线 ID */
    private Long routeId;

    /** 放行快照版本 ID；Release 后锁定，Track 只认此字段 */
    private Long routeVersionId;

    /** 当前站顺序号（快照内）；未进站可空 */
    private Integer currentSortNo;

    /** 当前工序 ID */
    private Long currentStepId;

    /** 当前设备 ID（TrackIn 后；一期可空） */
    private Long currentEqpId;

    /** 按触发站累计返工次数 JSON，如 {"60":1} */
    private String reworkCounts;

    /** 是否在 Temporary Off-Flow 中：0/1 */
    private Integer offFlow;

    /** Off-Flow 锚点站序 */
    private Integer offFlowAnchorSort;

    /** Off-Flow 锚点工序 */
    private Long offFlowAnchorStepId;

    /** Off-Flow 锚点机台 */
    private Long offFlowAnchorEqpId;

    /** Off-Flow 锚点状态 wait/processing */
    private String offFlowAnchorStatus;

    /** 按触发站累计 Off-Flow 次数 JSON，如 {"30":1} */
    private String offFlowCounts;

    /** Queue Time 开窗触发站 */
    private Integer qtimeFromSort;

    /** Queue Time 目标站 */
    private Integer qtimeToSort;

    /** Queue Time 开窗时刻 */
    private LocalDateTime qtimeStartedAt;

    /** Queue Time 开窗固化上限分钟 */
    private Integer qtimeMaxMin;

    /** Queue Time 开窗固化策略 HOLD/ALARM/HOLD_ALARM */
    private String qtimeOnViolate;

    /** 本站开工按下秒表的时间；出站/返工后清空。没配加工时长的站不写 */
    private LocalDateTime processStartedAt;

    /**
     * 状态：created 已创建 / released 已放行(过渡) / wait 等待加工 /
     * processing 加工中 / held 锁批 / completed 已完工 / scrapped 已报废 / merged 已合批
     */
    private String status;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号（并发更新防覆盖） */
    @Version
    private Integer version;

    /** 创建人 user_id */
    private Long createBy;

    /** 更新人 user_id */
    private Long updateBy;
}
