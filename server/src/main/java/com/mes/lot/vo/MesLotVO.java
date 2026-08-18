package com.mes.lot.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 批次列表 / 详情 */
@Data
public class MesLotVO {
    private Long id;
    private String lotNo;
    private String productCode;
    private Integer qty;
    /** 累计报废数量 */
    private Integer scrapQty;
    /** 优先级 1–100，越大越急 */
    private Integer priority;
    /** Hot Lot：0/1 */
    private Integer hotFlag;
    private String customerLot;
    /** 直系父 Lot（Split 产生） */
    private Long parentLotId;
    /** 合批后指向的主 Lot */
    private Long mergedToLotId;
    private Long routeId;
    private String routeCode;
    private String routeName;
    /** 放行快照版本 ID */
    private Long routeVersionId;
    /** 放行快照版本号 */
    private Integer routeVersionNo;
    /** 当前站顺序号 */
    private Integer currentSortNo;
    /** 当前工序 ID */
    private Long currentStepId;
    /** 当前设备 ID */
    private Long currentEqpId;
    /** created / wait / processing / held / completed / scrapped */
    private String status;
    private String remark;
    /** 乐观锁版本号 */
    private Integer version;
    /** 详情时返回快照步骤；列表为 null */
    private List<MesLotStepVO> steps;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
