package com.mes.track.vo;

import lombok.Data;

/** Bonus 数量调整事务结果 */
@Data
public class TrackBonusResultVO {

    private Long lotId;
    private String lotNo;
    private Integer delta;
    private Integer qty;
    private Integer scrapQty;
    private String status;
    private Long txId;
}
