package com.mes.edc.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SPC 画图 / 判异用的一个点。值仍是 EDC 采的，这里只是读出来。 */
@Data
public class EdcSeriesPoint {
    /** 采集项 id，后面判异按它去重 */
    private Long itemId;
    private Long collectionId;
    private Long lotId;
    private String lotNo;
    private Long eqpId;
    private LocalDateTime collectedAt;
    private BigDecimal valueNum;
    /** PASS / OOS，图上要标出来 */
    private String itemResult;
    /** 当时规格上限，只展示，不拿来判 OOC */
    private BigDecimal uslSnap;
    /** 当时规格下限，只展示，不拿来判 OOC */
    private BigDecimal lslSnap;
}
