package com.mes.lot.vo;

import lombok.Data;

/** 展平名单里的一条批 */
@Data
public class MesLotImpactMemberVO {

    private Long lotId;
    private String lotNo;
    /** ANCHOR=你点的 / ANCESTOR=上面来的 / DESCENDANT=下面去的 */
    private String relation;
    /** 离锚点几层；锚点自己是 0 */
    private Integer depthFromAnchor;
    private Integer qty;
    private String status;
}
