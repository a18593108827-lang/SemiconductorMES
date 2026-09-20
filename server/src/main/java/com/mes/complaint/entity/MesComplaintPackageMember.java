package com.mes.complaint.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客诉追溯包成员快照 */
@Data
@TableName("mes_complaint_package_member")
public class MesComplaintPackageMember {

    // ===== 与锚点关系枚举 =====
    /** 锚点自身 */
    public static final String REL_ANCHOR = "ANCHOR";
    /** 祖先（上游） */
    public static final String REL_ANCESTOR = "ANCESTOR";
    /** 子孙（下游） */
    public static final String REL_DESCENDANT = "DESCENDANT";

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属包 ID */
    private Long packageId;
    /** 成员 Lot */
    private Long lotId;
    /** 成员 Lot 号快照 */
    private String lotNo;
    /** 与锚点关系（见 REL_* 枚举） */
    private String relation;
    /** 相对锚点深度（0=锚点） */
    private Integer depthFromAnchor;
    /** 生成时 qty 快照（展示用，非运行态真相） */
    private Integer qtySnapshot;
    /** 生成时 status 快照（展示用，非运行态真相） */
    private String statusSnapshot;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
