package com.mes.edc.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.PageResult;
import com.mes.edc.dto.MesEdcCollectionCreateDTO;
import com.mes.edc.dto.MesEdcCollectionQuery;
import com.mes.edc.entity.MesEdcCollection;
import com.mes.edc.entity.MesEdcCollectionItem;
import com.mes.edc.entity.MesEdcParam;
import com.mes.edc.entity.MesEdcPlan;
import com.mes.edc.entity.MesEdcPlanItem;
import com.mes.edc.entity.MesEdcSpec;
import com.mes.edc.mapper.MesEdcCollectionItemMapper;
import com.mes.edc.mapper.MesEdcCollectionMapper;
import com.mes.edc.mapper.MesEdcParamMapper;
import com.mes.edc.mapper.MesEdcPlanItemMapper;
import com.mes.edc.mapper.MesEdcPlanMapper;
import com.mes.edc.mapper.MesEdcSpecMapper;
import com.mes.edc.service.MesEdcCollectionService;
import com.mes.edc.vo.MesEdcCollectionItemVO;
import com.mes.edc.vo.MesEdcCollectionVO;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesStepMapper;
import com.mes.track.entity.MesTxLog;
import com.mes.track.mapper.MesTxLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 采集业务。
 * 记一句：对照规格判 OOS；缺必采或有 OOS → 头 FAIL；同 visit 可重采，认最新条。
 */
@Service
@RequiredArgsConstructor
public class MesEdcCollectionServiceImpl implements MesEdcCollectionService {

    public static final String RESULT_PASS = "PASS";
    public static final String RESULT_FAIL = "FAIL";
    public static final String ITEM_OOS = "OOS";
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String TX_TRACK_IN = "TRACK_IN";
    public static final String STATUS_PROCESSING = "processing";
    public static final String SPEC_ACTIVE = "active";
    public static final int ENABLED = 1;
    public static final int MANDATORY = 1;

    private final MesEdcCollectionMapper mesEdcCollectionMapper;
    private final MesEdcCollectionItemMapper mesEdcCollectionItemMapper;
    private final MesEdcPlanMapper mesEdcPlanMapper;
    private final MesEdcPlanItemMapper mesEdcPlanItemMapper;
    private final MesEdcParamMapper mesEdcParamMapper;
    private final MesEdcSpecMapper mesEdcSpecMapper;
    private final MesLotMapper mesLotMapper;
    private final MesTxLogMapper mesTxLogMapper;
    private final MesStepMapper mesStepMapper;

    @Override
    public PageResult<MesEdcCollectionVO> page(MesEdcCollectionQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        LambdaQueryWrapper<MesEdcCollection> qw = new LambdaQueryWrapper<>();
        if (query.getLotId() != null) {
            qw.eq(MesEdcCollection::getLotId, query.getLotId());
        }
        if (query.getStepId() != null) {
            qw.eq(MesEdcCollection::getStepId, query.getStepId());
        }
        if (query.getTrackInTxId() != null) {
            qw.eq(MesEdcCollection::getTrackInTxId, query.getTrackInTxId());
        }
        if (StringUtils.hasText(query.getResult())) {
            qw.eq(MesEdcCollection::getResult, query.getResult().trim());
        }
        qw.orderByDesc(MesEdcCollection::getCollectedAt);

        Page<MesEdcCollection> result = mesEdcCollectionMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        Map<Long, Integer> itemCountMap = loadItemCountMap(result.getRecords());
        Map<Long, MesStep> stepMap = loadStepMap(result.getRecords());
        List<MesEdcCollectionVO> records = new ArrayList<>(result.getRecords().size());
        for (MesEdcCollection row : result.getRecords()) {
            records.add(toVo(row, stepMap.get(row.getStepId()), itemCountMap.getOrDefault(row.getId(), 0), null));
        }
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    @Override
    public MesEdcCollectionVO get(Long id) {
        MesEdcCollection row = mesEdcCollectionMapper.selectById(id);
        AssertUtil.notNull(row, "采集不存在");
        return toDetailVo(row);
    }

    @Override
    public MesEdcCollectionVO getLatest(Long lotId, Long trackInTxId) {
        AssertUtil.notNull(lotId, "批次不能为空");
        AssertUtil.notNull(trackInTxId, "本趟TrackIn不能为空");
        MesEdcCollection row = mesEdcCollectionMapper.selectOne(new LambdaQueryWrapper<MesEdcCollection>()
                .eq(MesEdcCollection::getLotId, lotId)
                .eq(MesEdcCollection::getTrackInTxId, trackInTxId)
                .orderByDesc(MesEdcCollection::getCollectedAt)
                .last("LIMIT 1"));
        if (row == null) {
            return null;
        }
        return toDetailVo(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesEdcCollectionVO submit(MesEdcCollectionCreateDTO dto) {
        MesLot lot = mesLotMapper.selectById(dto.getLotId());
        AssertUtil.notNull(lot, "批次不存在");
        AssertUtil.isTrue(STATUS_PROCESSING.equals(lot.getStatus()), "仅加工中可采集");

        MesTxLog tx = mesTxLogMapper.selectById(dto.getTrackInTxId());
        AssertUtil.notNull(tx, "本趟TrackIn不存在");
        AssertUtil.isTrue(Objects.equals(tx.getLotId(), lot.getId()), "TrackIn与批次不匹配");
        AssertUtil.isTrue(TX_TRACK_IN.equals(tx.getTxType()), "必须绑定本趟TrackIn");
        AssertUtil.isTrue(Objects.equals(lot.getCurrentStepId(), tx.getStepId()), "批次已不在该站，不能采集");

        Long stepId = tx.getStepId();
        AssertUtil.notNull(stepId, "开工履历缺少工序");

        MesEdcPlan plan = mesEdcPlanMapper.selectOne(new LambdaQueryWrapper<MesEdcPlan>()
                .eq(MesEdcPlan::getStepId, stepId)
                .eq(MesEdcPlan::getEnabled, ENABLED));
        AssertUtil.notNull(plan, "本站未配置启用的采集计划");

        List<MesEdcPlanItem> planItems = mesEdcPlanItemMapper.selectList(new LambdaQueryWrapper<MesEdcPlanItem>()
                .eq(MesEdcPlanItem::getPlanId, plan.getId())
                .orderByAsc(MesEdcPlanItem::getSortNo));
        AssertUtil.isTrue(!planItems.isEmpty(), "计划未配置采集项");

        Map<Long, MesEdcPlanItem> planItemMap = planItems.stream()
                .collect(Collectors.toMap(MesEdcPlanItem::getParamId, Function.identity(), (a, b) -> a));

        List<MesEdcCollectionCreateDTO.Item> submitted =
                dto.getItems() == null ? List.of() : dto.getItems();
        Map<Long, BigDecimal> valueMap = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (MesEdcCollectionCreateDTO.Item item : submitted) {
            AssertUtil.notNull(item.getParamId(), "特性不能为空");
            AssertUtil.notNull(item.getValue(), "量测值不能为空");
            AssertUtil.isTrue(seen.add(item.getParamId()), "同一采集内特性不能重复");
            AssertUtil.isTrue(planItemMap.containsKey(item.getParamId()), "特性不在本站计划内");
            valueMap.put(item.getParamId(), item.getValue());
        }

        boolean missingMandatory = false;
        for (MesEdcPlanItem planItem : planItems) {
            if (Objects.equals(planItem.getMandatory(), MANDATORY) && !valueMap.containsKey(planItem.getParamId())) {
                missingMandatory = true;
                break;
            }
        }

        List<Long> paramIds = planItems.stream().map(MesEdcPlanItem::getParamId).toList();
        Map<Long, MesEdcParam> paramMap = paramIds.isEmpty() ? Map.of()
                : mesEdcParamMapper.selectBatchIds(paramIds).stream()
                .collect(Collectors.toMap(MesEdcParam::getId, p -> p, (a, b) -> a, HashMap::new));

        String product = lot.getProductCode() == null ? "" : lot.getProductCode().trim();
        LocalDateTime now = LocalDateTime.now();
        long userId = StpUtil.getLoginIdAsLong();

        List<MesEdcCollectionItem> itemRows = new ArrayList<>();
        boolean anyOos = false;
        for (MesEdcPlanItem planItem : planItems) {
            BigDecimal value = valueMap.get(planItem.getParamId());
            if (value == null) {
                continue;
            }
            MesEdcParam param = paramMap.get(planItem.getParamId());
            String paramCode = param != null ? param.getParamCode() : String.valueOf(planItem.getParamId());
            MesEdcSpec spec = resolveSpec(planItem, product);
            AssertUtil.notNull(spec, "特性无生效规格，无法判定: " + paramCode);

            boolean oos = isOos(value, spec.getUsl(), spec.getLsl());
            if (oos) {
                anyOos = true;
            }

            MesEdcCollectionItem row = new MesEdcCollectionItem();
            row.setParamId(planItem.getParamId());
            row.setSpecId(spec.getId());
            row.setUslSnap(spec.getUsl());
            row.setLslSnap(spec.getLsl());
            row.setValueNum(value);
            row.setItemResult(oos ? ITEM_OOS : RESULT_PASS);
            itemRows.add(row);
        }

        String headResult = (anyOos || missingMandatory) ? RESULT_FAIL : RESULT_PASS;

        MesEdcCollection head = new MesEdcCollection();
        head.setLotId(lot.getId());
        head.setLotNo(lot.getLotNo());
        head.setRouteVersionId(tx.getRouteVersionId() != null ? tx.getRouteVersionId() : lot.getRouteVersionId());
        AssertUtil.notNull(head.getRouteVersionId(), "路线版本未知");
        Integer sortNo = tx.getToSortNo() != null ? tx.getToSortNo() : tx.getFromSortNo();
        AssertUtil.notNull(sortNo, "站序未知");
        head.setSortNo(sortNo);
        head.setStepId(stepId);
        head.setTrackInTxId(tx.getId());
        head.setPlanId(plan.getId());
        head.setResult(headResult);
        head.setSource(SOURCE_MANUAL);
        head.setEqpId(dto.getEqpId() != null ? dto.getEqpId() : tx.getEqpId());
        head.setRemark(blankToNull(dto.getRemark()));
        head.setCollectedBy(userId);
        head.setCollectedAt(now);
        mesEdcCollectionMapper.insert(head);

        for (MesEdcCollectionItem row : itemRows) {
            row.setCollectionId(head.getId());
            mesEdcCollectionItemMapper.insert(row);
        }

        return toDetailVo(head);
    }

    /** 指定 spec 用指定的；空则产品维 active，没有再兜全产品默认 */
    private MesEdcSpec resolveSpec(MesEdcPlanItem planItem, String product) {
        if (planItem.getSpecId() != null) {
            MesEdcSpec spec = mesEdcSpecMapper.selectById(planItem.getSpecId());
            AssertUtil.notNull(spec, "计划指定的规格不存在");
            AssertUtil.isTrue(Objects.equals(spec.getParamId(), planItem.getParamId()), "规格与特性不匹配");
            return spec;
        }
        if (StringUtils.hasText(product)) {
            MesEdcSpec byProduct = mesEdcSpecMapper.selectOne(new LambdaQueryWrapper<MesEdcSpec>()
                    .eq(MesEdcSpec::getParamId, planItem.getParamId())
                    .eq(MesEdcSpec::getProductCode, product)
                    .eq(MesEdcSpec::getStatus, SPEC_ACTIVE)
                    .last("LIMIT 1"));
            if (byProduct != null) {
                return byProduct;
            }
        }
        return mesEdcSpecMapper.selectOne(new LambdaQueryWrapper<MesEdcSpec>()
                .eq(MesEdcSpec::getParamId, planItem.getParamId())
                .eq(MesEdcSpec::getProductCode, "")
                .eq(MesEdcSpec::getStatus, SPEC_ACTIVE)
                .last("LIMIT 1"));
    }

    /** 缺侧不检；压线算 PASS */
    static boolean isOos(BigDecimal value, BigDecimal usl, BigDecimal lsl) {
        if (lsl != null && value.compareTo(lsl) < 0) {
            return true;
        }
        return usl != null && value.compareTo(usl) > 0;
    }

    private static String blankToNull(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        return v.trim();
    }

    private MesEdcCollectionVO toDetailVo(MesEdcCollection row) {
        List<MesEdcCollectionItem> items = mesEdcCollectionItemMapper.selectList(
                new LambdaQueryWrapper<MesEdcCollectionItem>()
                        .eq(MesEdcCollectionItem::getCollectionId, row.getId())
                        .orderByAsc(MesEdcCollectionItem::getId));
        MesStep step = mesStepMapper.selectById(row.getStepId());
        return toVo(row, step, items.size(), toItemVos(items));
    }

    private List<MesEdcCollectionItemVO> toItemVos(List<MesEdcCollectionItem> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> paramIds = items.stream().map(MesEdcCollectionItem::getParamId).distinct().toList();
        List<Long> specIds = items.stream().map(MesEdcCollectionItem::getSpecId).filter(Objects::nonNull).distinct().toList();
        Map<Long, MesEdcParam> paramMap = paramIds.isEmpty() ? Map.of()
                : mesEdcParamMapper.selectBatchIds(paramIds).stream()
                .collect(Collectors.toMap(MesEdcParam::getId, p -> p, (a, b) -> a, HashMap::new));
        Map<Long, MesEdcSpec> specMap = specIds.isEmpty() ? Map.of()
                : mesEdcSpecMapper.selectBatchIds(specIds).stream()
                .collect(Collectors.toMap(MesEdcSpec::getId, s -> s, (a, b) -> a, HashMap::new));

        List<MesEdcCollectionItemVO> list = new ArrayList<>(items.size());
        for (MesEdcCollectionItem item : items) {
            MesEdcCollectionItemVO vo = new MesEdcCollectionItemVO();
            vo.setId(item.getId());
            vo.setCollectionId(item.getCollectionId());
            vo.setParamId(item.getParamId());
            MesEdcParam param = paramMap.get(item.getParamId());
            if (param != null) {
                vo.setParamCode(param.getParamCode());
                vo.setParamName(param.getParamName());
                vo.setUnit(param.getUnit());
            }
            vo.setSpecId(item.getSpecId());
            MesEdcSpec spec = item.getSpecId() != null ? specMap.get(item.getSpecId()) : null;
            if (spec != null) {
                vo.setSpecVersionNo(spec.getVersionNo());
            }
            vo.setUslSnap(item.getUslSnap());
            vo.setLslSnap(item.getLslSnap());
            vo.setValueNum(item.getValueNum());
            vo.setItemResult(item.getItemResult());
            list.add(vo);
        }
        return list;
    }

    private Map<Long, Integer> loadItemCountMap(List<MesEdcCollection> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(MesEdcCollection::getId).toList();
        List<MesEdcCollectionItem> items = mesEdcCollectionItemMapper.selectList(
                new LambdaQueryWrapper<MesEdcCollectionItem>()
                        .in(MesEdcCollectionItem::getCollectionId, ids)
                        .select(MesEdcCollectionItem::getCollectionId));
        Map<Long, Integer> map = new HashMap<>();
        for (MesEdcCollectionItem item : items) {
            map.merge(item.getCollectionId(), 1, Integer::sum);
        }
        return map;
    }

    private Map<Long, MesStep> loadStepMap(List<MesEdcCollection> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = rows.stream().map(MesEdcCollection::getStepId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mesStepMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(MesStep::getId, s -> s, (a, b) -> a, HashMap::new));
    }

    private static MesEdcCollectionVO toVo(MesEdcCollection row, MesStep step, int itemCount,
                                           List<MesEdcCollectionItemVO> items) {
        MesEdcCollectionVO vo = new MesEdcCollectionVO();
        vo.setId(row.getId());
        vo.setLotId(row.getLotId());
        vo.setLotNo(row.getLotNo());
        vo.setRouteVersionId(row.getRouteVersionId());
        vo.setSortNo(row.getSortNo());
        vo.setStepId(row.getStepId());
        if (step != null) {
            vo.setStepCode(step.getStepCode());
            vo.setStepName(step.getStepName());
        }
        vo.setTrackInTxId(row.getTrackInTxId());
        vo.setPlanId(row.getPlanId());
        vo.setResult(row.getResult());
        vo.setSource(row.getSource());
        vo.setEqpId(row.getEqpId());
        vo.setRemark(row.getRemark());
        vo.setCollectedBy(row.getCollectedBy());
        vo.setCollectedAt(row.getCollectedAt());
        vo.setItemCount(itemCount);
        vo.setItems(items != null ? items : List.of());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }
}
