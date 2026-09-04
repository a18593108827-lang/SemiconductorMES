package com.mes.history.support;

import cn.hutool.json.JSONUtil;
import com.mes.equipment.entity.MesEqp;
import com.mes.equipment.mapper.MesEqpMapper;
import com.mes.history.vo.HistoryTxVO;
import com.mes.recipe.entity.MesRecipeVersion;
import com.mes.recipe.mapper.MesRecipeVersionMapper;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesStepMapper;
import com.mes.track.entity.MesTxLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把 tx_log 行拼成调查能看的 VO。
 * 站名/机台/配方版本号当时没冗余进表，查询时批量填；严重级别按事务码映射，不入库。
 */
@Component
@RequiredArgsConstructor
public class HistoryTxAssembler {

    private static final Set<String> SEVERITY_DANGER = Set.of("HOLD", "SCRAP");
    private static final Set<String> SEVERITY_WARNING = Set.of("ABORT", "REWORK", "SKIP", "OFF_FLOW", "BONUS");

    private final MesStepMapper mesStepMapper;
    private final MesEqpMapper mesEqpMapper;
    private final MesRecipeVersionMapper mesRecipeVersionMapper;

    /** 批量查主数据再填，禁止逐行查库。 */
    public List<HistoryTxVO> toVoList(List<MesTxLog> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> stepIds = rows.stream().map(MesTxLog::getStepId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> eqpIds = rows.stream().map(MesTxLog::getEqpId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> recipeVersionIds = rows.stream().map(MesTxLog::getRecipeVersionId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, MesStep> stepMap = stepIds.isEmpty()
                ? Collections.emptyMap()
                : mesStepMapper.selectBatchIds(stepIds).stream()
                .collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a));
        Map<Long, MesEqp> eqpMap = eqpIds.isEmpty()
                ? Collections.emptyMap()
                : mesEqpMapper.selectBatchIds(eqpIds).stream()
                .collect(Collectors.toMap(MesEqp::getId, e -> e, (a, b) -> a));
        Map<Long, MesRecipeVersion> recipeVersionMap = recipeVersionIds.isEmpty()
                ? Collections.emptyMap()
                : mesRecipeVersionMapper.selectBatchIds(recipeVersionIds).stream()
                .collect(Collectors.toMap(MesRecipeVersion::getId, v -> v, (a, b) -> a));

        List<HistoryTxVO> list = new ArrayList<>(rows.size());
        for (MesTxLog row : rows) {
            list.add(toVo(row, stepMap, eqpMap, recipeVersionMap));
        }
        return list;
    }

    private HistoryTxVO toVo(MesTxLog row, Map<Long, MesStep> stepMap, Map<Long, MesEqp> eqpMap,
                             Map<Long, MesRecipeVersion> recipeVersionMap) {
        HistoryTxVO item = new HistoryTxVO();
        item.setId(row.getId());
        item.setLotId(row.getLotId());
        item.setLotNo(row.getLotNo());
        item.setTxType(row.getTxType());
        item.setFromStatus(row.getFromStatus());
        item.setToStatus(row.getToStatus());
        item.setFromSortNo(row.getFromSortNo());
        item.setToSortNo(row.getToSortNo());
        item.setStepId(row.getStepId());
        MesStep step = row.getStepId() != null ? stepMap.get(row.getStepId()) : null;
        item.setStepName(step != null ? step.getStepName() : null);
        item.setEqpId(row.getEqpId());
        MesEqp eqp = row.getEqpId() != null ? eqpMap.get(row.getEqpId()) : null;
        if (eqp != null) {
            item.setEqpCode(eqp.getEqpCode());
            item.setEqpName(eqp.getEqpName());
        }
        item.setRecipeId(row.getRecipeId());
        item.setRecipeVersionId(row.getRecipeVersionId());
        MesRecipeVersion recipeVersion = row.getRecipeVersionId() != null
                ? recipeVersionMap.get(row.getRecipeVersionId()) : null;
        item.setRecipeVersionNo(recipeVersion != null ? recipeVersion.getVersionNo() : null);
        item.setRouteVersionId(row.getRouteVersionId());
        item.setRemark(row.getRemark());
        item.setExtJson(row.getExtJson());
        item.setExt(parseExt(row.getExtJson()));
        item.setSeverity(severity(row));
        item.setOperUserId(row.getOperUserId());
        item.setOperUserName(row.getOperUserName());
        item.setCreateTime(row.getCreateTime());
        return item;
    }

    /** 调查台颜色：锁批/报废红，中止这类黄。量测单独看结果，不合格才黄，合格当普通流水。不认识的码别丢掉，当普通。 */
    private static String severity(MesTxLog row) {
        String txType = row.getTxType();
        if (txType == null) {
            return "info";
        }
        if ("EDC_COLLECT".equals(txType)) {
            String json = row.getExtJson();
            if (json == null || json.isBlank()) {
                return "info";
            }
            try {
                String result = JSONUtil.parseObj(json).getStr("result");
                if ("FAIL".equals(result)) {
                    return "warning";
                }
            } catch (Exception ignored) {
                return "info";
            }
            return "info";
        }
        if (SEVERITY_DANGER.contains(txType)) {
            return "danger";
        }
        if (SEVERITY_WARNING.contains(txType)) {
            return "warning";
        }
        return "info";
    }

    /** 坏 JSON 不当整页失败，ext 空着，extJson 原文仍在。 */
    private static Object parseExt(String extJson) {
        if (extJson == null || extJson.isBlank()) {
            return null;
        }
        try {
            return JSONUtil.parse(extJson);
        } catch (Exception ignored) {
            return null;
        }
    }
}
