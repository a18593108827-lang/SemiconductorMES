package com.mes.track.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Scrap 原因码选项 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackScrapReasonVO {

    private String code;
    private String label;
}
