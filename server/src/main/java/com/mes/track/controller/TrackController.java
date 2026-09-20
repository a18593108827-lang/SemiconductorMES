package com.mes.track.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.mes.common.R;
import com.mes.common.annotation.OperLog;
import com.mes.track.dto.TrackAbortDTO;
import com.mes.track.dto.TrackInDTO;
import com.mes.track.dto.TrackMoveDTO;
import com.mes.track.dto.TrackOffFlowDTO;
import com.mes.track.dto.TrackOffFlowResumeDTO;
import com.mes.track.dto.TrackOutDTO;
import com.mes.track.dto.TrackReleaseDTO;
import com.mes.track.dto.TrackReworkDTO;
import com.mes.track.dto.TrackSkipDTO;
import com.mes.track.dto.TrackMergeDTO;
import com.mes.track.dto.TrackBonusDTO;
import com.mes.track.dto.TrackScrapDTO;
import com.mes.track.dto.TrackSplitDTO;
import com.mes.track.service.TrackService;
import com.mes.track.vo.TrackAbortReasonVO;
import com.mes.track.vo.TrackBonusReasonVO;
import com.mes.track.vo.TrackBonusResultVO;
import com.mes.track.vo.TrackContextVO;
import com.mes.track.vo.TrackMergeCandidateVO;
import com.mes.track.vo.TrackMergeResultVO;
import com.mes.track.vo.TrackReleaseResultVO;
import com.mes.track.vo.TrackScrapReasonVO;
import com.mes.track.vo.TrackScrapResultVO;
import com.mes.track.vo.TrackSplitResultVO;
import com.mes.track.vo.TrackTxnResultVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Track 执行引擎：Release / TrackIn / TrackOut / Abort / Move
 */
@RestController
@RequestMapping("/track")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;

    /** 放行：绑 active 版本快照，进首站 wait */
    @SaCheckPermission(value = {"track:release", "lot:release"}, mode = SaMode.OR)
    @OperLog(module = "Track", action = "放行")
    @PostMapping("/release")
    public R<TrackReleaseResultVO> release(@Valid @RequestBody TrackReleaseDTO dto) {
        return R.ok(trackService.release(dto.getLotId()));
    }

    /** 开工：wait → processing */
    @SaCheckPermission("track:track-in")
    @OperLog(module = "Track", action = "TrackIn")
    @PostMapping("/track-in")
    public R<TrackTxnResultVO> trackIn(@Valid @RequestBody TrackInDTO dto) {
        return R.ok(trackService.trackIn(dto.getLotId(), dto.getEqpId(), dto.getCarrierCode()));
    }

    /** 完工：processing → 下一站 wait 或 completed */
    @SaCheckPermission("track:track-out")
    @OperLog(module = "Track", action = "TrackOut")
    @PostMapping("/track-out")
    public R<TrackTxnResultVO> trackOut(@Valid @RequestBody TrackOutDTO dto) {
        return R.ok(trackService.trackOut(dto.getLotId(), dto.getResultCode()));
    }

    /** 加工中止：加工中出事，回本站等待，机台腾出来 */
    @SaCheckPermission("track:abort")
    @OperLog(module = "Track", action = "Abort")
    @PostMapping("/abort")
    public R<TrackTxnResultVO> abort(@Valid @RequestBody TrackAbortDTO dto) {
        return R.ok(trackService.abort(dto.getLotId(), dto.getReasonCode(), dto.getRemark()));
    }

    /** Abort 原因码白名单 */
    @SaCheckPermission(value = {"track:abort", "track:view"}, mode = SaMode.OR)
    @GetMapping("/abort/reason-codes")
    public R<List<TrackAbortReasonVO>> abortReasonCodes() {
        return R.ok(trackService.abortReasonCodes());
    }

    /** 独立移站：没加工，只挪到工艺下一站 */
    @SaCheckPermission("track:move")
    @OperLog(module = "Track", action = "Move")
    @PostMapping("/move")
    public R<TrackTxnResultVO> move(@Valid @RequestBody TrackMoveDTO dto) {
        return R.ok(trackService.move(dto.getLotId(), dto.getToSortNo(), dto.getRemark()));
    }

    /** 返工回流 */
    @SaCheckPermission("track:rework")
    @OperLog(module = "Track", action = "Rework")
    @PostMapping("/rework")
    public R<TrackTxnResultVO> rework(@Valid @RequestBody TrackReworkDTO dto) {
        return R.ok(trackService.rework(dto.getLotId(), dto.getToSortNo(), dto.getReasonCode(), dto.getRemark()));
    }

    /** 前向跳站 */
    @SaCheckPermission("track:skip")
    @OperLog(module = "Track", action = "Skip")
    @PostMapping("/skip")
    public R<TrackTxnResultVO> skip(@Valid @RequestBody TrackSkipDTO dto) {
        return R.ok(trackService.skip(dto.getLotId(), dto.getToSortNo(), dto.getReasonCode(), dto.getRemark()));
    }

    /** 进入 Temporary Off-Flow */
    @SaCheckPermission("track:off-flow")
    @OperLog(module = "Track", action = "OffFlow")
    @PostMapping("/off-flow")
    public R<TrackTxnResultVO> enterOffFlow(@Valid @RequestBody TrackOffFlowDTO dto) {
        return R.ok(trackService.enterOffFlow(dto.getLotId(), dto.getToSortNo(), dto.getReasonCode(), dto.getRemark()));
    }

    /** Off-Flow 回主路径锚点 */
    @SaCheckPermission("track:off-flow")
    @OperLog(module = "Track", action = "OffFlowResume")
    @PostMapping("/off-flow/resume")
    public R<TrackTxnResultVO> resumeOffFlow(@Valid @RequestBody TrackOffFlowResumeDTO dto) {
        return R.ok(trackService.resumeOffFlow(dto.getLotId(), dto.getRemark()));
    }

    /** 分批：父保留余量，子继承快照与当前站 */
    @SaCheckPermission("track:split")
    @OperLog(module = "Track", action = "Split")
    @PostMapping("/split")
    public R<TrackSplitResultVO> split(@Valid @RequestBody TrackSplitDTO dto) {
        return R.ok(trackService.split(dto.getParentLotId(), dto.getChildren(),
                dto.getReasonCode(), dto.getRemark()));
    }

    /** 合批：主 qty 累加，源 → merged；须同产品/快照/站 */
    @SaCheckPermission("track:merge")
    @OperLog(module = "Track", action = "Merge")
    @PostMapping("/merge")
    public R<TrackMergeResultVO> merge(@Valid @RequestBody TrackMergeDTO dto) {
        return R.ok(trackService.merge(dto.getMainLotId(), dto.getSourceLotIds(),
                dto.getReasonCode(), dto.getRemark()));
    }

    /** 可合入主批的候选列表（现场台勾选源批用） */
    @SaCheckPermission(value = {"track:merge", "lot:list"}, mode = SaMode.OR)
    @GetMapping("/merge/candidates")
    public R<List<TrackMergeCandidateVO>> mergeCandidates(@RequestParam Long mainLotId) {
        return R.ok(trackService.mergeCandidates(mainLotId));
    }

    /** 报废：部分减 qty；全批 → scrapped */
    @SaCheckPermission("track:scrap")
    @OperLog(module = "Track", action = "Scrap")
    @PostMapping("/scrap")
    public R<TrackScrapResultVO> scrap(@Valid @RequestBody TrackScrapDTO dto) {
        return R.ok(trackService.scrap(dto.getLotId(), dto.getScrapQty(),
                dto.getReasonCode(), dto.getRemark()));
    }

    /** Scrap 原因码白名单 */
    @SaCheckPermission(value = {"track:scrap", "lot:list"}, mode = SaMode.OR)
    @GetMapping("/scrap/reason-codes")
    public R<List<TrackScrapReasonVO>> scrapReasonCodes() {
        return R.ok(trackService.scrapReasonCodes());
    }

    /** 数量调整：±delta；不改 scrap_qty/status */
    @SaCheckPermission("track:bonus")
    @OperLog(module = "Track", action = "Bonus")
    @PostMapping("/bonus")
    public R<TrackBonusResultVO> bonus(@Valid @RequestBody TrackBonusDTO dto) {
        return R.ok(trackService.bonus(dto.getLotId(), dto.getDelta(),
                dto.getReasonCode(), dto.getRemark()));
    }

    /** Bonus 原因码白名单 */
    @SaCheckPermission(value = {"track:bonus", "lot:list"}, mode = SaMode.OR)
    @GetMapping("/bonus/reason-codes")
    public R<List<TrackBonusReasonVO>> bonusReasonCodes() {
        return R.ok(trackService.bonusReasonCodes());
    }

    /** 执行上下文（只读） */
    @SaCheckPermission("track:view")
    @GetMapping("/lots/{lotId}/context")
    public R<TrackContextVO> context(@PathVariable Long lotId) {
        return R.ok(trackService.context(lotId));
    }
}
