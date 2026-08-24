package com.mes.track.service;

import com.mes.track.dto.TrackSplitChildDTO;
import com.mes.track.vo.TrackContextVO;
import com.mes.track.vo.TrackMergeCandidateVO;
import com.mes.track.vo.TrackMergeResultVO;
import com.mes.track.vo.TrackReleaseResultVO;
import com.mes.track.vo.TrackAbortReasonVO;
import com.mes.track.vo.TrackBonusReasonVO;
import com.mes.track.vo.TrackBonusResultVO;
import com.mes.track.vo.TrackScrapReasonVO;
import com.mes.track.vo.TrackScrapResultVO;
import com.mes.track.vo.TrackSplitResultVO;
import com.mes.track.vo.TrackTxnResultVO;
import com.mes.history.vo.HistoryTxVO;

import java.util.List;

/** Track 执行引擎 */
public interface TrackService {

    /** 放行：绑 active 版本，进首站 wait */
    TrackReleaseResultVO release(Long lotId);

    /** 开工：wait → processing */
    TrackTxnResultVO trackIn(Long lotId, Long eqpId);

    /** 分批：父保留余量，子继承快照与当前站 */
    TrackSplitResultVO split(Long parentLotId, List<TrackSplitChildDTO> children, String reasonCode, String remark);

    /** 合批：主 qty 累加，源 → merged；须同产品/快照/站 */
    TrackMergeResultVO merge(Long mainLotId, List<Long> sourceLotIds, String reasonCode, String remark);

    /** 可合入主批的候选（同站同快照同产品且 wait） */
    List<TrackMergeCandidateVO> mergeCandidates(Long mainLotId);

    /** 报废：部分减 qty；全批 status→scrapped */
    TrackScrapResultVO scrap(Long lotId, Integer scrapQty, String reasonCode, String remark);

    /** Scrap 原因码白名单（供 UI 下拉） */
    List<TrackScrapReasonVO> scrapReasonCodes();

    /** 数量调整：±delta；不改 scrap_qty/status */
    TrackBonusResultVO bonus(Long lotId, Integer delta, String reasonCode, String remark);

    /** Bonus 原因码白名单（供 UI 下拉） */
    List<TrackBonusReasonVO> bonusReasonCodes();

    /**
     * 完工：processing → 下一站 wait，或末站 completed。
     * 加工站主路径仍由此推进；未加工只搬家请用 {@link #move}。
     */
    TrackTxnResultVO trackOut(Long lotId, String resultCode);

    /**
     * 加工中止：processing → 本站 wait。
     * 站别/数量不动，机台让出来，秒表清掉；不是报废也不是跳站。
     */
    TrackTxnResultVO abort(Long lotId, String reasonCode, String remark);

    /** Abort 原因码白名单（供 UI 下拉） */
    List<TrackAbortReasonVO> abortReasonCodes();

    /**
     * 独立移站：wait → 合法下一站 wait。
     * 人话：本站没干活，只把批挪到工艺规定的下一站；不是完工，也不是跳站。
     */
    TrackTxnResultVO move(Long lotId, Integer toSortNo, String remark);

    /** 返工回流：wait|processing → wait(目标站) */
    TrackTxnResultVO rework(Long lotId, Integer toSortNo, String reasonCode, String remark);

    /** 前向跳站：仅 wait → wait(目标站)；processing 拒绝 */
    TrackTxnResultVO skip(Long lotId, Integer toSortNo, String reasonCode, String remark);

    /** 进入 Temporary Off-Flow：wait|processing → 旁路入口 wait */
    TrackTxnResultVO enterOffFlow(Long lotId, Integer toSortNo, String reasonCode, String remark);

    /** 旁路末站回锚点 */
    TrackTxnResultVO resumeOffFlow(Long lotId, String remark);

    /** 执行上下文：当前站 / 下一站 / 可操作标记 */
    TrackContextVO context(Long lotId);

    /** 按 Lot 查事务履历；只委托 HistoryFacade，禁止本类再查 mes_tx_log */
    List<HistoryTxVO> history(Long lotId);
}
