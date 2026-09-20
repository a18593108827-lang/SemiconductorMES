package com.mes.carrier.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.mes.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 载具台账（FOUP 等） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_carrier")
public class MesCarrier extends BaseEntity {

    /** 空闲可绑 */
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    /** 已绑 Lot 使用中（仅 bind 写入） */
    public static final String STATUS_IN_USE = "IN_USE";
    /** 隔离，禁止绑定 */
    public static final String STATUS_QUARANTINE = "QUARANTINE";
    /** 报废终态 */
    public static final String STATUS_SCRAPPED = "SCRAPPED";

    /** 默认类型：前开式晶圆盒 */
    public static final String TYPE_FOUP = "FOUP";

    /** 洁净状态未知 */
    public static final String CLEAN_UNKNOWN = "UNKNOWN";
    /** 已清洗 */
    public static final String CLEAN_CLEAN = "CLEAN";
    /** 脏盒 */
    public static final String CLEAN_DIRTY = "DIRTY";

    /** 无位置 */
    public static final String LOC_NONE = "NONE";

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 载具编码（唯一，扫码用） */
    private String carrierCode;
    /** 类型，默认 FOUP */
    private String carrierType;
    /** 槽位数，默认 25 */
    private Integer capacity;
    /** 台账状态：AVAILABLE / IN_USE / QUARANTINE / SCRAPPED */
    private String status;
    /** 洁净状态：UNKNOWN 未知 / CLEAN 已清洗 / DIRTY 脏盒（一期只记录，不卡绑） */
    private String cleanStatus;
    /** 当前位置类型：NONE 未记位 / STOCKER 库 / PORT 设备口 / OHB 架空缓冲 / MANUAL 人工站 */
    private String locationType;
    /** 位置具体引用（库位号、Port 编码等），与 locationType 配套 */
    private String locationRef;
    /** 备注 */
    private String remark;

    /** 乐观锁 */
    @Version
    private Integer version;

    /** 创建人 */
    private Long createBy;
    /** 更新人 */
    private Long updateBy;
}
