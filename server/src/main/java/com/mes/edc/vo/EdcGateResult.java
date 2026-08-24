package com.mes.edc.vo;

import lombok.Data;

/** EDC 门禁判定结果。TrackOut / 现场预检共用。 */
@Data
public class EdcGateResult {
    /** 本站启用 Plan 且 required=1（与应急开关无关） */
    private boolean required;
    /** true=允许 TrackOut */
    private boolean clear;
    /** NONE / NO_DATA / OOS / GATE_DISABLED */
    private String reasonCode;
    private String message;
    private Long collectionId;
    private Long trackInTxId;
}
