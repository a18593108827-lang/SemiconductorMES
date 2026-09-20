package com.mes.track.vo;

import com.mes.hold.vo.MesFutureHoldVO;
import com.mes.lot.vo.MesLotStepVO;
import lombok.Data;

import java.util.List;

/** 现场台执行上下文（只读） */
@Data
public class TrackContextVO {
    private Long lotId;
    private String lotNo;
    private String status;
    private Long routeId;
    private Long routeVersionId;
    private Integer routeVersionNo;
    private Integer currentSortNo;
    private Long currentStepId;
    private Long currentEqpId;
    private MesLotStepVO currentStep;
    private MesLotStepVO nextStep;
    private Boolean canTrackIn;
    private Boolean canTrackOut;
    private Boolean canRework;
    private Boolean canSkip;
    private Boolean canEnterOffFlow;
    private Boolean canResumeOffFlow;
    /** 是否可分批（wait + 有 qty + track:split） */
    private Boolean canSplit;
    /** 是否可作合批主批（wait + 快照 + track:merge） */
    private Boolean canMerge;
    /** 是否可报废（wait + qty≥1 + track:scrap） */
    private Boolean canScrap;
    /** 是否可数量调整（wait + track:bonus） */
    private Boolean canBonus;
    /** 是否可加工中止（processing + track:abort；Hold 中不行） */
    private Boolean canAbort;
    /** 是否可独立移站（wait + 有下一站 + track:move；Hold/旁路不行） */
    private Boolean canMove;
    /** 默认下一站序号（没有下一站则 null） */
    private Integer nextSortNo;
    /** 默认下一站名称（方便按钮旁直接展示） */
    private String nextStepName;
    private Boolean offFlow;
    private Integer offFlowAnchorSortNo;
    private Integer reworkCount;
    private List<TrackReworkOptionVO> reworkOptions;
    private List<TrackBranchOptionVO> branchOptions;
    private List<TrackSkipOptionVO> skipOptions;
    private List<TrackOffFlowOptionVO> offFlowOptions;
    private Boolean completed;
    /** 未生效预约锁批（pending） */
    private List<MesFutureHoldVO> pendingFutureHolds;
    /** Queue Time 开窗；无则 null */
    private TrackQueueTimeVO queueTime;
    /** Process Time 站内加工；无则 null */
    private TrackProcessTimeVO processTime;
    /** 量测门禁；非 processing 为 null */
    private TrackEdcVO edc;
    /** 当前载具 ID；未绑 null */
    private Long carrierId;
    /** 当前载具编码；未绑 null */
    private String carrierCode;
    /** TrackIn 是否强制已绑（配置开且模块启用） */
    private Boolean carrierRequired;
    /** TrackIn 是否强制扫码比对（配置开且模块启用） */
    private Boolean carrierScanRequired;
}
