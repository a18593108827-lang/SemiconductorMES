package com.mes.edc.service;

import com.mes.common.PageResult;
import com.mes.edc.dto.MesEdcCollectionCreateDTO;
import com.mes.edc.dto.MesEdcCollectionQuery;
import com.mes.edc.vo.EdcSeriesPoint;
import com.mes.edc.vo.MesEdcCollectionVO;

import java.time.LocalDateTime;
import java.util.List;

/** 手录采集：提交判定 + 按 visit 查询 */
public interface MesEdcCollectionService {

    /** 按批次/站/本趟开工/结果翻页，新的排前面。 */
    PageResult<MesEdcCollectionVO> page(MesEdcCollectionQuery query);

    /** 单条采集详情，带点值。没有就报「采集不存在」。 */
    MesEdcCollectionVO get(Long id);

    /** 按 id 找采集详情。没有或 id 空就返回空，不喊「不存在」。监听走这条。 */
    MesEdcCollectionVO find(Long id);

    /** 按特性+站点取时间线上的点，OOS 也要。条数空按 100，封顶 500。 */
    List<EdcSeriesPoint> listSeries(Long paramId, Long stepId, Long eqpId,
                                    LocalDateTime from, LocalDateTime to, Integer limit);

    /** 同 visit 最新一条；没有则 null */
    MesEdcCollectionVO getLatest(Long lotId, Long trackInTxId);

    /** 手录提交：对照规格判 OOS，写履历；采完发事件给 SPC。 */
    MesEdcCollectionVO submit(MesEdcCollectionCreateDTO dto);
}
