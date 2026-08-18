package com.mes.lot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 分合批谱系（只追加） */
@Data
@TableName("mes_lot_genealogy")
public class MesLotGenealogy {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** split / merge */
    private String txnType;

    private Long parentLotId;
    private Long childLotId;
    private Integer qty;
    private Long txId;
    private String reasonCode;
    private Long createBy;
    private LocalDateTime createTime;
}
