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
import com.mes.edc.event.EdcCollectedEvent;
import com.mes.edc.mapper.MesEdcCollectionItemMapper;
import com.mes.edc.mapper.MesEdcCollectionMapper;
import com.mes.edc.mapper.MesEdcParamMapper;
import com.mes.edc.mapper.MesEdcPlanItemMapper;
import com.mes.edc.mapper.MesEdcPlanMapper;
import com.mes.edc.mapper.MesEdcSpecMapper;
import com.mes.edc.service.MesEdcCollectionService;
import com.mes.edc.vo.EdcSeriesPoint;
import com.mes.edc.vo.MesEdcCollectionItemVO;
import com.mes.edc.vo.MesEdcCollectionVO;
import com.mes.equipment.entity.MesEqp;
import com.mes.equipment.mapper.MesEqpMapper;
import com.mes.hold.dto.MesHoldCreateDTO;
import com.mes.hold.service.HoldService;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.route.entity.MesStep;
import com.mes.route.mapper.MesStepMapper;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysUserMapper;
import com.mes.track.entity.MesTxLog;
import com.mes.track.mapper.MesTxLogMapper;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
    public static final String REASON_EDC_OOS = "EDC_OOS";
    public static final String TX_EDC_COLLECT = "EDC_COLLECT";
    public static final int ENABLED = 1;
    public static final int MANDATORY = 1;
    public static final int SERIES_LIMIT_DEFAULT = 100;
    public static final int SERIES_LIMIT_MAX = 500;

    private final MesEdcCollectionMapper mesEdcCollectionMapper;
    private final MesEdcCollectionItemMapper mesEdcCollectionItemMapper;
    private final MesEdcPlanMapper mesEdcPlanMapper;
    private final MesEdcPlanItemMapper mesEdcPlanItemMapper;
    private final MesEdcParamMapper mesEdcParamMapper;
    private final MesEdcSpecMapper mesEdcSpecMapper;
    private final MesLotMapper mesLotMapper;
    private final MesTxLogMapper mesTxLogMapper;
    private final MesStepMapper mesStepMapper;
    private final MesEqpMapper mesEqpMapper;
    private final SysUserMapper sysUserMapper;
    private final HoldService holdService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${mes.edc.auto-hold-on-oos:false}")
    private boolean autoHoldOnOos;

    /** 按批次/站/本趟开工/结果翻页，新的排前面。 */
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

    /** 单条采集详情，带点值。 */
    @Override
    public MesEdcCollectionVO get(Long id) {
        MesEdcCollectionVO vo = find(id);
        AssertUtil.notNull(vo, "采集不存在");
        return vo;
    }

    /** 按 id 找采集详情。没有或 id 空就返回空，不喊「不存在」。监听走这条，页面详情走 get。 */
    @Override
    public MesEdcCollectionVO find(Long id) {
        if (id == null) {
            return null;
        }
        MesEdcCollection row = mesEdcCollectionMapper.selectById(id);
        if (row == null) {
            return null;
        }
        return toDetailVo(row);
    }

    /** 按特性+站点取时间线上的点，OOS 也要。没传特性或站点就空列表。条数空按 100，封顶 500。 */
    @Override
    public List<EdcSeriesPoint> listSeries(Long paramId, Long stepId, Long eqpId,
                                           LocalDateTime from, LocalDateTime to, Integer limit) {
        if (paramId == null || stepId == null) {
            return List.of();
        }
        int n = (limit == null || limit < 1) ? SERIES_LIMIT_DEFAULT : Math.min(limit, SERIES_LIMIT_MAX);
        return mesEdcCollectionMapper.listSeries(paramId, stepId, eqpId, from, to, n);
    }

    /** 这趟开工最新一条采集；门禁认它，不管以前合格不合格。 */
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

    /** 手录提交：对照规格判 OOS，写履历；超规且开关开着再锁批。同趟可重采。 */
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

        writeCollectTx(lot, head, userId);

        if (anyOos) {
            holdOnOos(lot, head);
        }

        publishCollected(head);
        return toDetailVo(head);
    }

    /** 采完喊一声给 SPC。这里不判异；没人听也没事，别把采集搞失败。站码机台码一起带上。 */
    private void publishCollected(MesEdcCollection head) {
        String stepCode = null;
        if (head.getStepId() != null) {
            MesStep step = mesStepMapper.selectById(head.getStepId());
            stepCode = step != null ? step.getStepCode() : null;
        }
        String eqpCode = null;
        if (head.getEqpId() != null) {
            MesEqp eqp = mesEqpMapper.selectById(head.getEqpId());
            eqpCode = eqp != null ? eqp.getEqpCode() : null;
        }
        eventPublisher.publishEvent(new EdcCollectedEvent(
                head.getId(),
                head.getLotId(),
                head.getLotNo(),
                head.getStepId(),
                stepCode,
                head.getEqpId(),
                eqpCode,
                head.getCollectedAt()));
    }

    /** 记一笔量测履历。不改批次状态，只让调查台能看到这次采了啥、合不合格。 */
    private void writeCollectTx(MesLot lot, MesEdcCollection head, long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        Map<String, Object> ext = new HashMap<>();
        ext.put("collectionId", head.getId());
        ext.put("result", head.getResult());
        ext.put("trackInTxId", head.getTrackInTxId());

        MesTxLog log = new MesTxLog();
        log.setLotId(lot.getId());
        log.setLotNo(lot.getLotNo());
        log.setTxType(TX_EDC_COLLECT);
        log.setFromStatus(lot.getStatus());
        log.setToStatus(lot.getStatus());
        log.setFromSortNo(head.getSortNo());
        log.setToSortNo(head.getSortNo());
        log.setStepId(head.getStepId());
        log.setEqpId(head.getEqpId());
        log.setRouteVersionId(head.getRouteVersionId());
        log.setRemark(RESULT_PASS.equals(head.getResult()) ? "量测合格" : "量测不合格");
        log.setExtJson(JSONUtil.toJsonStr(ext));
        log.setOperUserId(userId);
        log.setOperUserName(user != null ? user.getUserName() : null);
        log.setCreateTime(head.getCollectedAt() != null ? head.getCollectedAt() : LocalDateTime.now());
        mesTxLogMapper.insert(log);
    }

    /** 超规才锁；没开开关或已经锁着就放过。缺必采不算超规，不在这儿挂。 */
    private void holdOnOos(MesLot lot, MesEdcCollection head) {
        if (!autoHoldOnOos || holdService.hasActive(lot.getId())) {
            return;
        }
        MesHoldCreateDTO dto = new MesHoldCreateDTO();
        dto.setLotId(lot.getId());
        dto.setReasonCode(REASON_EDC_OOS);
        dto.setRemark("量测超规 collectionId=" + head.getId());
        holdService.create(dto);
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

    /** 空串当没填。 */
    private static String blankToNull(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        return v.trim();
    }

    /** 把头和点值拼成详情。 */
    private MesEdcCollectionVO toDetailVo(MesEdcCollection row) {
        List<MesEdcCollectionItem> items = mesEdcCollectionItemMapper.selectList(
                new LambdaQueryWrapper<MesEdcCollectionItem>()
                        .eq(MesEdcCollectionItem::getCollectionId, row.getId())
                        .orderByAsc(MesEdcCollectionItem::getId));
        MesStep step = mesStepMapper.selectById(row.getStepId());
        return toVo(row, step, items.size(), toItemVos(items));
    }

    /** 点值带上特性名、单位、规格版本；一次查出，不在循环里打库。 */
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

    /** 列表页只要点数，不拉明细。 */
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

    /** 列表上的站名，批量查工序。 */
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

    /** 采集头转对外对象；items 可空，列表页不带点。 */
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
