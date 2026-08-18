package com.mes.track.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Abort 原因码下拉项 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackAbortReasonVO {

    private String code;
    private String label;
}
