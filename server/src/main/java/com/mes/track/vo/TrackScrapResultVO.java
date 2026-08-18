package com.mes.track.vo;

import lombok.Data;

/** Scrap 报废事务结果 */
@Data
public class TrackScrapResultVO {

    private Long lotId;
    private String lotNo;
    /** partial | full */
    private String mode;
    private Integer qty;
    private Integer scrapQty;
    private String status;
    private Long txId;
}
