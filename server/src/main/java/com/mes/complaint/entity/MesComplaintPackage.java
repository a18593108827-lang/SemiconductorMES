package com.mes.complaint.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客诉追溯包头（审计；履历真相仍在 tx_log） */
@Data
@TableName("mes_complaint_package")
public class MesComplaintPackage {

    // ===== 包状态枚举 =====
    /** 已生成（P0 仅此态） */
    public static final String STATUS_READY = "READY";
    /** 遏制进行中（CP-5 占位用） */
    public static final String STATUS_CONTAINING = "CONTAINING";
    /** 已遏制（至少一次 contain 成功） */
    public static final String STATUS_CONTAINED = "CONTAINED";
    /** 已作废 */
    public static final String STATUS_VOID = "VOID";

    // ===== 展开方向枚举 =====
    /** 只向上（祖先） */
    public static final String DIR_UP = "up";
    /** 只向下（子孙） */
    public static final String DIR_DOWN = "down";
    /** 双向（默认） */
    public static final String DIR_BOTH = "both";

    /** 主键 = packageId（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 业务号 CP-yyyyMMdd-序号（UK） */
    private String packageNo;
    /** 锚点 Lot */
    private Long anchorLotId;
    /** 锚点 Lot 号快照（生成时冗余，便查） */
    private String anchorLotNo;
    /** 展开方向 up/down/both */
    private String direction;
    /** 展开深度（1~20） */
    private Integer depth;
    /** 成员数快照 */
    private Integer memberCount;
    /** 是否触达 depth/成员上限截断 0/1 */
    private Integer truncated;
    /** 调查原因码（P0 无字典，长度 64） */
    private String reasonCode;
    /** 备注（长度 512） */
    private String remark;
    /** 包状态（见 STATUS_* 枚举） */
    private String status;

    /** 创建人 */
    private Long createBy;
    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 首次遏制人（CP-5 写入） */
    private Long containBy;
    /** 首次遏制时间（CP-5 写入） */
    private LocalDateTime containTime;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
