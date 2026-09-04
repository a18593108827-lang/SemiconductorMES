package com.mes.spc.facade;

import com.mes.spc.dto.SpcChartSaveDTO;
import com.mes.spc.vo.SpcChartVO;
import com.mes.spc.vo.SpcSeriesVO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SPC 对外唯一门面：听采集、判失控、维护图。
 * 别人禁止直查 mes_spc*，也禁止注入 EDC mapper。
 */
public interface SpcFacade {

    /** 采集提交后调用。内部把异常吃掉，只打日志，不能连累采集。 */
    void onCollected(Long collectionId);

    /** 按 id 取图；没有就空。 */
    SpcChartVO getChart(Long chartId);

    /** 按站 / 特性筛图，都能空。 */
    List<SpcChartVO> listCharts(Long stepId, Long paramId);

    /** 拉趋势点。n&lt;25 不给 Cpk。 */
    SpcSeriesVO getSeries(Long chartId, LocalDateTime from, LocalDateTime to, Integer limit);

    /** 新建或改图。机台空存 0。 */
    SpcChartVO saveChart(SpcChartSaveDTO cmd);

    /** 手填控制限。模式可以还是 LEARNING，但限冻住。 */
    void setLimits(Long chartId, BigDecimal ucl, BigDecimal cl, BigDecimal lcl);

    /** 启停一张图。停了采集后不再判。 */
    void enable(Long chartId, boolean enabled);
}
