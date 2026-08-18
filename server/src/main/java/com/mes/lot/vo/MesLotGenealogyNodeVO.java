package com.mes.lot.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 谱系树节点（Split/Merge 追溯） */
@Data
public class MesLotGenealogyNodeVO {

    private Long lotId;
    private String lotNo;
    private Integer qty;
    private String status;
    /** 进入本节点的事务类型：split / merge；根可空 */
    private String txnType;
    /** 该谱系边创建时间 */
    private LocalDateTime txnTime;
    /** 边转移数量；根可空 */
    private Integer qtyTransferred;
    /** 关联 mes_tx_log.id；根可空 */
    private Long txId;
    /** 原因码；根可空 */
    private String reasonCode;
    /** 子节点（向下展开） */
    private List<MesLotGenealogyNodeVO> children;
}
