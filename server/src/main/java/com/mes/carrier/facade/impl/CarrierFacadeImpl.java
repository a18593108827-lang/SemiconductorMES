package com.mes.carrier.facade.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.carrier.dto.CarrierCreateDTO;
import com.mes.carrier.dto.CarrierQuery;
import com.mes.carrier.dto.CarrierUpdateDTO;
import com.mes.carrier.entity.MesCarrier;
import com.mes.carrier.entity.MesCarrierBinding;
import com.mes.carrier.event.CarrierChangedEvent;
import com.mes.carrier.facade.CarrierFacade;
import com.mes.carrier.mapper.MesCarrierBindingMapper;
import com.mes.carrier.mapper.MesCarrierMapper;
import com.mes.carrier.vo.CarrierBindingVO;
import com.mes.carrier.vo.CarrierVO;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.common.PageResult;
import com.mes.lot.entity.MesLot;
import com.mes.lot.mapper.MesLotMapper;
import com.mes.system.entity.SysUser;
import com.mes.system.mapper.SysUserMapper;
import com.mes.track.entity.MesTxLog;
import com.mes.track.mapper.MesTxLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarrierFacadeImpl implements CarrierFacade {

    /** 载具不存在 */
    public static final String ERR_NOT_FOUND = "CARRIER_NOT_FOUND";
    /** 状态不合法 / 迁态不允许 */
    public static final String ERR_STATUS = "CARRIER_STATUS_INVALID";
    /** 模块总开关关闭 */
    public static final String ERR_DISABLED = "CARRIER_DISABLED";
    /** 乐观锁冲突 */
    public static final String ERR_CONCURRENT = "CARRIER_CONCURRENT_MOD";
    /** 载具编码重复 */
    public static final String ERR_CODE_DUP = "CARRIER_CODE_DUP";
    /** 盒已绑其它 Lot */
    public static final String ERR_CARRIER_BOUND = "CARRIER_ALREADY_BOUND";
    /** Lot 已绑其它盒 */
    public static final String ERR_LOT_BOUND = "LOT_ALREADY_BOUND";
    /** TrackIn 闸：未绑 */
    public static final String ERR_REQUIRED = "CARRIER_REQUIRED";
    /** TrackIn 扫码闸：未提供有效扫码 */
    public static final String ERR_SCAN_REQUIRED = "CARRIER_SCAN_REQUIRED";
    /** TrackIn 扫码闸：与绑定不一致 */
    public static final String ERR_MISMATCH = "CARRIER_MISMATCH";

    public static final String TX_BIND = "CARRIER_BIND";
    public static final String TX_UNBIND = "CARRIER_UNBIND";

    private static final String LOT_MERGED = "merged";
    private static final String LOT_SCRAPPED = "scrapped";

    /** 台账合法状态集合 */
    private static final Set<String> ALL_STATUSES = Set.of(
            MesCarrier.STATUS_AVAILABLE,
            MesCarrier.STATUS_IN_USE,
            MesCarrier.STATUS_QUARANTINE,
            MesCarrier.STATUS_SCRAPPED);

    /** 洁净状态合法集合 */
    private static final Set<String> CLEAN_STATUSES = Set.of(
            MesCarrier.CLEAN_UNKNOWN,
            MesCarrier.CLEAN_CLEAN,
            MesCarrier.CLEAN_DIRTY);

    private final MesCarrierMapper carrierMapper;
    private final MesCarrierBindingMapper bindingMapper;
    private final MesLotMapper mesLotMapper;
    private final MesTxLogMapper mesTxLogMapper;
    private final SysUserMapper sysUserMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${mes.carrier.enabled:true}")
    private boolean carrierEnabled;

    @Value("${mes.carrier.track-in-scan-required:false}")
    private boolean trackInScanRequired;

    /** 新建载具台账，初始 AVAILABLE */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CarrierVO create(CarrierCreateDTO dto) {
        assertEnabled();
        String code = dto.getCarrierCode().trim();
        Long exists = carrierMapper.selectCount(new LambdaQueryWrapper<MesCarrier>()
                .eq(MesCarrier::getCarrierCode, code));
        AssertUtil.isTrue(exists == 0, ERR_CODE_DUP + ": 载具编码已存在");

        long userId = StpUtil.getLoginIdAsLong();
        MesCarrier row = new MesCarrier();
        row.setCarrierCode(code);
        row.setCarrierType(StringUtils.hasText(dto.getCarrierType())
                ? dto.getCarrierType().trim() : MesCarrier.TYPE_FOUP);
        row.setCapacity(normalizeCapacity(dto.getCapacity()));
        row.setStatus(MesCarrier.STATUS_AVAILABLE);
        row.setCleanStatus(normalizeClean(dto.getCleanStatus()));
        row.setLocationType(StringUtils.hasText(dto.getLocationType())
                ? dto.getLocationType().trim() : MesCarrier.LOC_NONE);
        row.setLocationRef(blankToNull(dto.getLocationRef()));
        row.setRemark(blankToNull(dto.getRemark()));
        row.setCreateBy(userId);
        row.setUpdateBy(userId);
        try {
            carrierMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ERR_CODE_DUP + ": 载具编码已存在");
        }
        return toVo(row);
    }

    /** 更新台账字段（不含编码、状态、绑定） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CarrierVO update(Long id, CarrierUpdateDTO dto) {
        assertEnabled();
        MesCarrier row = require(id);
        if (dto.getCapacity() != null) {
            row.setCapacity(normalizeCapacity(dto.getCapacity()));
        }
        if (dto.getCleanStatus() != null) {
            row.setCleanStatus(normalizeClean(dto.getCleanStatus()));
        }
        if (dto.getLocationType() != null) {
            row.setLocationType(StringUtils.hasText(dto.getLocationType())
                    ? dto.getLocationType().trim() : MesCarrier.LOC_NONE);
        }
        if (dto.getLocationRef() != null) {
            row.setLocationRef(blankToNull(dto.getLocationRef()));
        }
        if (dto.getRemark() != null) {
            row.setRemark(blankToNull(dto.getRemark()));
        }
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = carrierMapper.updateById(row);
        AssertUtil.isTrue(n > 0, ERR_CONCURRENT + ": 数据已被他人修改，请刷新后重试");
        return toVo(carrierMapper.selectById(id));
    }

    /** 台账改态；禁手改 IN_USE；隔离/报废前须无绑定 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CarrierVO changeStatus(Long id, String toStatus, String remark) {
        assertEnabled();
        AssertUtil.notBlank(toStatus, ERR_STATUS + ": 目标状态不能为空");
        String next = toStatus.trim();
        AssertUtil.isTrue(ALL_STATUSES.contains(next), ERR_STATUS + ": 状态不合法");
        AssertUtil.isTrue(!MesCarrier.STATUS_IN_USE.equals(next),
                ERR_STATUS + ": IN_USE 仅能由绑定产生，禁止手改");

        MesCarrier row = require(id);
        String from = row.getStatus();
        if (Objects.equals(from, next)) {
            return toVo(row);
        }
        AssertUtil.isTrue(!MesCarrier.STATUS_SCRAPPED.equals(from),
                ERR_STATUS + ": 已报废不可再改态");

        if (MesCarrier.STATUS_QUARANTINE.equals(next) || MesCarrier.STATUS_SCRAPPED.equals(next)) {
            assertNoBinding(id);
        }

        if (MesCarrier.STATUS_IN_USE.equals(from)) {
            throw new BusinessException(ERR_STATUS + ": 使用中请先解绑再改态");
        }

        boolean ok = switch (from) {
            case MesCarrier.STATUS_AVAILABLE ->
                    MesCarrier.STATUS_QUARANTINE.equals(next) || MesCarrier.STATUS_SCRAPPED.equals(next);
            case MesCarrier.STATUS_QUARANTINE ->
                    MesCarrier.STATUS_AVAILABLE.equals(next) || MesCarrier.STATUS_SCRAPPED.equals(next);
            default -> false;
        };
        AssertUtil.isTrue(ok, ERR_STATUS + ": 不允许 " + from + " → " + next);

        row.setStatus(next);
        if (remark != null) {
            row.setRemark(blankToNull(remark));
        }
        row.setUpdateBy(StpUtil.getLoginIdAsLong());
        int n = carrierMapper.updateById(row);
        AssertUtil.isTrue(n > 0, ERR_CONCURRENT + ": 数据已被他人修改，请刷新后重试");
        return toVo(carrierMapper.selectById(id));
    }

    /** 按 id 查载具 */
    @Override
    public CarrierVO get(Long id) {
        CarrierVO vo = toVo(require(id));
        fillBoundLots(List.of(vo));
        return vo;
    }

    /** 按编码查载具 */
    @Override
    public CarrierVO getByCode(String carrierCode) {
        AssertUtil.notBlank(carrierCode, ERR_NOT_FOUND + ": 载具编码不能为空");
        MesCarrier row = carrierMapper.selectOne(new LambdaQueryWrapper<MesCarrier>()
                .eq(MesCarrier::getCarrierCode, carrierCode.trim()));
        AssertUtil.notNull(row, ERR_NOT_FOUND + ": 载具不存在");
        CarrierVO vo = toVo(row);
        fillBoundLots(List.of(vo));
        return vo;
    }

    /** 分页列表 */
    @Override
    public PageResult<CarrierVO> list(CarrierQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : query.getSize();

        LambdaQueryWrapper<MesCarrier> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and(w -> w.like(MesCarrier::getCarrierCode, kw)
                    .or().like(MesCarrier::getRemark, kw));
        }
        if (StringUtils.hasText(query.getStatus())) {
            qw.eq(MesCarrier::getStatus, query.getStatus().trim());
        }
        if (StringUtils.hasText(query.getCarrierType())) {
            qw.eq(MesCarrier::getCarrierType, query.getCarrierType().trim());
        }
        qw.orderByAsc(MesCarrier::getCarrierCode);

        Page<MesCarrier> result = carrierMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        List<CarrierVO> records = new ArrayList<>(result.getRecords().size());
        for (MesCarrier row : result.getRecords()) {
            records.add(toVo(row));
        }
        fillBoundLots(records);
        return PageResult.of(records, result.getTotal(), pageNo, pageSize);
    }

    /** Lot 绑载具；锁序 Lot→Carrier；一 Lot 一盒 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CarrierBindingVO bind(Long lotId, String carrierRef) {
        assertEnabled();
        AssertUtil.notNull(lotId, "批次不能为空");
        AssertUtil.notBlank(carrierRef, ERR_NOT_FOUND + ": 载具不能为空");

        MesLot lot = lockLot(lotId);
        assertLotBindable(lot);

        MesCarrier carrier = resolveAndLockCarrier(carrierRef.trim());
        Long carrierId = carrier.getId();

        MesCarrierBinding existingLotBind = bindingMapper.selectOne(new LambdaQueryWrapper<MesCarrierBinding>()
                .eq(MesCarrierBinding::getLotId, lotId));
        if (existingLotBind != null) {
            // 如果已经绑定了当前载具就直接返回，否则报已绑定其他载具异常
            if (Objects.equals(existingLotBind.getCarrierId(), carrierId)) {
                return toBindingVo(lot, carrier, existingLotBind);
            }
            throw new BusinessException(ERR_LOT_BOUND + ": 批次已绑定其它载具，请先解绑");
        }

        MesCarrierBinding existingCarrierBind = bindingMapper.selectOne(new LambdaQueryWrapper<MesCarrierBinding>()
                .eq(MesCarrierBinding::getCarrierId, carrierId));
        if (existingCarrierBind != null) {
            throw new BusinessException(ERR_CARRIER_BOUND + ": 载具已绑定其它批次");
        }

        // 允许 AVAILABLE，或「标 IN_USE 但无 binding」的孤儿盒（真占用已在上方 existingCarrierBind 拦截）
        boolean statusOk = MesCarrier.STATUS_AVAILABLE.equals(carrier.getStatus())
                || MesCarrier.STATUS_IN_USE.equals(carrier.getStatus());
        AssertUtil.isTrue(statusOk, ERR_STATUS + ": 仅空闲载具可绑定，当前=" + carrier.getStatus());
        if (MesCarrier.STATUS_IN_USE.equals(carrier.getStatus())
                && lot.getCarrierId() != null
                && !Objects.equals(lot.getCarrierId(), carrierId)) {
            throw new BusinessException(ERR_LOT_BOUND + ": 批次已指向其它载具，请先解绑");
        }

        long userId = StpUtil.getLoginIdAsLong();
        LocalDateTime now = LocalDateTime.now();

        MesCarrierBinding binding = new MesCarrierBinding();
        binding.setCarrierId(carrierId);
        binding.setLotId(lotId);
        binding.setBindTime(now);
        binding.setBindBy(userId);
        binding.setCreateTime(now);
        binding.setUpdateTime(now);
        try {
            bindingMapper.insert(binding);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ERR_CONCURRENT + ": 绑定冲突，请重试");
        }

        lot.setCarrierId(carrierId);
        lot.setUpdateBy(userId);
        int lotRows = mesLotMapper.updateById(lot);
        AssertUtil.isTrue(lotRows > 0, ERR_CONCURRENT + ": 批次已被他人修改，请刷新后重试");

        carrier.setStatus(MesCarrier.STATUS_IN_USE);
        carrier.setUpdateBy(userId);
        int carrierRows = carrierMapper.updateById(carrier);
        AssertUtil.isTrue(carrierRows > 0, ERR_CONCURRENT + ": 载具已被他人修改，请刷新后重试");

        writeTxLog(lot, TX_BIND, carrierId, carrier.getCarrierCode(), "绑定载具 " + carrier.getCarrierCode());
        publishChangedAfterCommit(new CarrierChangedEvent(
                CarrierChangedEvent.ACTION_BIND, lotId, lot.getLotNo(), carrierId, carrier.getCarrierCode()));

        return toBindingVo(lot, carrier, binding);
    }

    /** 按 Lot 解绑；未绑幂等 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long lotId) {
        assertEnabled();
        AssertUtil.notNull(lotId, "批次不能为空");
        MesLot lot = lockLot(lotId);
        doUnbindLockedLot(lot);
    }

    /** 按载具解绑 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindByCarrier(Long carrierId) {
        assertEnabled();
        AssertUtil.notNull(carrierId, ERR_NOT_FOUND + ": 载具不存在");
        MesCarrierBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<MesCarrierBinding>()
                .eq(MesCarrierBinding::getCarrierId, carrierId));
        if (binding == null) {
            return;
        }
        MesLot lot = lockLot(binding.getLotId());
        doUnbindLockedLot(lot);
    }

    /** 根据批次id获取载具id（优先 binding，与 lot.carrier_id 对齐） */
    @Override
    public Long getCarrierId(Long lotId) {
        if (lotId == null) {
            return null;
        }
        MesCarrierBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<MesCarrierBinding>()
                .eq(MesCarrierBinding::getLotId, lotId));
        if (binding != null) {
            return binding.getCarrierId();
        }
        MesLot lot = mesLotMapper.selectById(lotId);
        return lot == null ? null : lot.getCarrierId();
    }

    /** 是否已绑：以 binding 表为准 */
    @Override
    public boolean isBound(Long lotId) {
        if (lotId == null) {
            return false;
        }
        Long cnt = bindingMapper.selectCount(new LambdaQueryWrapper<MesCarrierBinding>()
                .eq(MesCarrierBinding::getLotId, lotId));
        return cnt != null && cnt > 0;
    }

    /** 进站时校验；模块关闭时不挡；认 binding */
    @Override
    public void assertBound(Long lotId) {
        if (!carrierEnabled) {
            return;
        }
        AssertUtil.notNull(lotId, ERR_REQUIRED + ": 批次不能为空");
        AssertUtil.isTrue(isBound(lotId), ERR_REQUIRED + ": 批次未绑定载具");
    }

    /** TrackIn 扫码比对；认 binding → carrier_code */
    @Override
    public void assertMatch(Long lotId, String scannedCode) {
        if (!carrierEnabled || !trackInScanRequired) {
            return;
        }
        AssertUtil.notNull(lotId, ERR_REQUIRED + ": 批次不能为空");
        MesCarrierBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<MesCarrierBinding>()
                .eq(MesCarrierBinding::getLotId, lotId));
        AssertUtil.notNull(binding, ERR_REQUIRED + ": 批次未绑定载具");
        String scanned = scannedCode == null ? "" : scannedCode.trim();
        AssertUtil.isTrue(StringUtils.hasText(scanned), ERR_SCAN_REQUIRED + ": 请扫描载具编码");
        MesCarrier carrier = carrierMapper.selectById(binding.getCarrierId());
        AssertUtil.notNull(carrier,
                ERR_NOT_FOUND + ": 绑定载具台账缺失(carrierId=" + binding.getCarrierId() + ")，请工程清理脏绑定");
        AssertUtil.isTrue(Objects.equals(scanned, carrier.getCarrierCode()),
                ERR_MISMATCH + ": 扫码与绑定载具不一致");
    }

    /** 查当前绑定详情 */
    @Override
    public CarrierBindingVO getBinding(Long lotId) {
        if (lotId == null) {
            return null;
        }
        MesCarrierBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<MesCarrierBinding>()
                .eq(MesCarrierBinding::getLotId, lotId));
        if (binding == null) {
            return null;
        }
        MesLot lot = mesLotMapper.selectById(lotId);
        MesCarrier carrier = carrierMapper.selectById(binding.getCarrierId());
        if (lot == null || carrier == null) {
            return null;
        }
        return toBindingVo(lot, carrier, binding);
    }

    /** 批量解析 lotId → carrierCode */
    @Override
    public Map<Long, String> resolveCodes(Collection<Long> lotIds) {
        if (lotIds == null || lotIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> ids = lotIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<MesLot> lots = mesLotMapper.selectBatchIds(ids);
        Map<Long, Long> lotToCarrier = new HashMap<>();
        Set<Long> carrierIds = new LinkedHashSet<>();
        for (MesLot lot : lots) {
            if (lot.getCarrierId() != null) {
                lotToCarrier.put(lot.getId(), lot.getCarrierId());
                carrierIds.add(lot.getCarrierId());
            }
        }
        if (carrierIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> codeById = carrierMapper.selectBatchIds(carrierIds).stream()
                .collect(Collectors.toMap(MesCarrier::getId, MesCarrier::getCarrierCode, (a, b) -> a));
        Map<Long, String> result = new HashMap<>();
        for (Map.Entry<Long, Long> e : lotToCarrier.entrySet()) {
            String code = codeById.get(e.getValue());
            if (code != null) {
                result.put(e.getKey(), code);
            }
        }
        return result;
    }

    /** 已锁 Lot 上执行解绑；顺带修 binding 与 lot.carrier_id 漂移 */
    private void doUnbindLockedLot(MesLot lot) {
        MesCarrierBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<MesCarrierBinding>()
                .eq(MesCarrierBinding::getLotId, lot.getId()));
        if (binding == null && lot.getCarrierId() == null) {
            return;
        }

        Long bindingCarrierId = binding != null ? binding.getCarrierId() : null;
        Long lotCarrierId = lot.getCarrierId();
        // 主释放目标：binding 优先，否则 lot 冗余
        Long primaryCarrierId = bindingCarrierId != null ? bindingCarrierId : lotCarrierId;
        // 漂移的另一只盒：也要收回，避免 IN_USE 孤儿
        Long driftCarrierId = null;
        if (bindingCarrierId != null && lotCarrierId != null && !Objects.equals(bindingCarrierId, lotCarrierId)) {
            driftCarrierId = lotCarrierId;
        }

        MesCarrier carrier = primaryCarrierId != null ? lockCarrier(primaryCarrierId) : null;
        MesCarrier driftCarrier = null;
        if (driftCarrierId != null) {
            driftCarrier = lockCarrier(driftCarrierId);
        }

        if (binding != null) {
            bindingMapper.deleteById(binding.getId());
        }

        long userId = StpUtil.getLoginIdAsLong();
        int lotRows = mesLotMapper.update(null, new LambdaUpdateWrapper<MesLot>()
                .eq(MesLot::getId, lot.getId())
                .eq(MesLot::getVersion, lot.getVersion())
                .set(MesLot::getCarrierId, null)
                .set(MesLot::getUpdateBy, userId)
                .setSql("version = version + 1"));
        AssertUtil.isTrue(lotRows > 0, ERR_CONCURRENT + ": 批次已被他人修改，请刷新后重试");

        markCarrierAvailable(carrier, userId);
        markCarrierAvailable(driftCarrier, userId);

        String carrierCode = carrier != null ? carrier.getCarrierCode() : null;
        writeTxLog(lot, TX_UNBIND, primaryCarrierId, carrierCode,
                "解绑载具" + (carrierCode != null ? " " + carrierCode : ""));
        publishChangedAfterCommit(new CarrierChangedEvent(
                CarrierChangedEvent.ACTION_UNBIND, lot.getId(), lot.getLotNo(), primaryCarrierId, carrierCode));
    }

    /** IN_USE → AVAILABLE（隔离/报废不动） */
    private void markCarrierAvailable(MesCarrier carrier, long userId) {
        if (carrier == null) {
            return;
        }
        if (MesCarrier.STATUS_QUARANTINE.equals(carrier.getStatus())
                || MesCarrier.STATUS_SCRAPPED.equals(carrier.getStatus())) {
            return;
        }
        if (MesCarrier.STATUS_AVAILABLE.equals(carrier.getStatus())) {
            return;
        }
        carrier.setStatus(MesCarrier.STATUS_AVAILABLE);
        carrier.setUpdateBy(userId);
        int carrierRows = carrierMapper.updateById(carrier);
        AssertUtil.isTrue(carrierRows > 0, ERR_CONCURRENT + ": 载具已被他人修改，请刷新后重试");
    }

    /** 事务提交后再发领域事件，避免监听方读到未提交/回滚数据 */
    private void publishChangedAfterCommit(CarrierChangedEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(event);
                }
            });
        } else {
            eventPublisher.publishEvent(event);
        }
    }

    /** 获取批次 mysql行锁控制并发安全 */
    private MesLot lockLot(Long lotId) {
        MesLot lot = mesLotMapper.selectOne(new LambdaQueryWrapper<MesLot>()
                .eq(MesLot::getId, lotId)
                .last("FOR UPDATE"));
        AssertUtil.notNull(lot, "批次不存在");
        return lot;
    }

    /** 根据载体id获取载体，mysql行锁保证并发安全 */
    private MesCarrier lockCarrier(Long carrierId) {
        MesCarrier row = carrierMapper.selectOne(new LambdaQueryWrapper<MesCarrier>()
                .eq(MesCarrier::getId, carrierId)
                .last("FOR UPDATE"));
        AssertUtil.notNull(row, ERR_NOT_FOUND + ": 载具不存在");
        return row;
    }

    /** 查找载具：先按 id，再按 code */
    private MesCarrier resolveAndLockCarrier(String carrierRef) {
        if (carrierRef.matches("\\d+")) {// 判断是否为纯数字，至少要有一位
            try {
                Long id = Long.parseLong(carrierRef);
                MesCarrier byId = carrierMapper.selectOne(new LambdaQueryWrapper<MesCarrier>()
                        .eq(MesCarrier::getId, id)
                        .last("FOR UPDATE"));
                if (byId != null) {
                    return byId;
                }
            } catch (NumberFormatException ignore) {
                // fall through to code
            }
        }
        MesCarrier byCode = carrierMapper.selectOne(new LambdaQueryWrapper<MesCarrier>()
                .eq(MesCarrier::getCarrierCode, carrierRef)
                .last("FOR UPDATE"));
        AssertUtil.notNull(byCode, ERR_NOT_FOUND + ": 载具不存在");
        return byCode;
    }

    /** Lot 可绑校验 */
    private void assertLotBindable(MesLot lot) {
        AssertUtil.isTrue(!LOT_MERGED.equals(lot.getStatus()), "已合批批次不可绑定载具");
        AssertUtil.isTrue(!LOT_SCRAPPED.equals(lot.getStatus()), "已报废批次不可绑定载具");
    }

    /** 写绑/解履历 */
    private void writeTxLog(MesLot lot, String txType, Long carrierId, String carrierCode, String remark) {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        Map<String, Object> ext = new HashMap<>();
        ext.put("lotId", lot.getId());
        if (carrierId != null) {
            ext.put("carrierId", carrierId);
        }
        if (carrierCode != null) {
            ext.put("carrierCode", carrierCode);
        }

        MesTxLog log = new MesTxLog();
        log.setLotId(lot.getId());
        log.setLotNo(lot.getLotNo());
        log.setTxType(txType);
        log.setFromStatus(lot.getStatus());
        log.setToStatus(lot.getStatus());
        log.setFromSortNo(lot.getCurrentSortNo());
        log.setToSortNo(lot.getCurrentSortNo());
        log.setStepId(lot.getCurrentStepId());
        log.setEqpId(lot.getCurrentEqpId());
        log.setRouteVersionId(lot.getRouteVersionId());
        log.setRemark(remark);
        log.setExtJson(JSONUtil.toJsonStr(ext));
        log.setOperUserId(userId);
        log.setOperUserName(user != null ? user.getUserName() : null);
        log.setCreateTime(LocalDateTime.now());
        mesTxLogMapper.insert(log);
    }

    /** 模块总开关 */
    private void assertEnabled() {
        AssertUtil.isTrue(carrierEnabled, ERR_DISABLED + ": 载具模块已关闭");
    }

    /** 按 id 取实体，不存在则抛错 */
    private MesCarrier require(Long id) {
        AssertUtil.notNull(id, ERR_NOT_FOUND + ": 载具不存在");
        MesCarrier row = carrierMapper.selectById(id);
        AssertUtil.notNull(row, ERR_NOT_FOUND + ": 载具不存在");
        return row;
    }

    /** 隔离/报废前确认无 Lot 绑定 */
    private void assertNoBinding(Long carrierId) {
        Long cnt = bindingMapper.selectCount(new LambdaQueryWrapper<MesCarrierBinding>()
                .eq(MesCarrierBinding::getCarrierId, carrierId));
        AssertUtil.isTrue(cnt == 0, ERR_STATUS + ": 仍有绑定，请先解绑");
    }

    /** 容量默认 25，须 > 0 */
    private static int normalizeCapacity(Integer capacity) {
        int c = capacity == null ? 25 : capacity;
        AssertUtil.isTrue(c > 0, "容量必须大于0");
        return c;
    }

    /** 洁净状态默认 UNKNOWN */
    private static String normalizeClean(String cleanStatus) {
        if (!StringUtils.hasText(cleanStatus)) {
            return MesCarrier.CLEAN_UNKNOWN;
        }
        String v = cleanStatus.trim();
        AssertUtil.isTrue(CLEAN_STATUSES.contains(v), "洁净状态不合法");
        return v;
    }

    /** 空白串转 null */
    private static String blankToNull(String v) {
        if (!StringUtils.hasText(v)) {
            return null;
        }
        return v.trim();
    }

    /** 实体转 VO */
    private static CarrierVO toVo(MesCarrier row) {
        CarrierVO vo = new CarrierVO();
        vo.setId(row.getId());
        vo.setCarrierCode(row.getCarrierCode());
        vo.setCarrierType(row.getCarrierType());
        vo.setCapacity(row.getCapacity());
        vo.setStatus(row.getStatus());
        vo.setCleanStatus(row.getCleanStatus());
        vo.setLocationType(row.getLocationType());
        vo.setLocationRef(row.getLocationRef());
        vo.setRemark(row.getRemark());
        vo.setVersion(row.getVersion());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }

    /** 给 VO 补上当前绑的 Lot（id + lotNo），列表一页一次查完 */
    private void fillBoundLots(List<CarrierVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> carrierIds = records.stream().map(CarrierVO::getId).filter(Objects::nonNull).toList();
        if (carrierIds.isEmpty()) {
            return;
        }
        List<MesCarrierBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<MesCarrierBinding>()
                .in(MesCarrierBinding::getCarrierId, carrierIds));
        if (bindings.isEmpty()) {
            return;
        }
        Map<Long, MesCarrierBinding> byCarrier = bindings.stream()
                .collect(Collectors.toMap(MesCarrierBinding::getCarrierId, b -> b, (a, b) -> a));
        Set<Long> lotIds = bindings.stream().map(MesCarrierBinding::getLotId).collect(Collectors.toSet());
        Map<Long, MesLot> lotMap = mesLotMapper.selectBatchIds(lotIds).stream()
                .collect(Collectors.toMap(MesLot::getId, l -> l, (a, b) -> a));
        for (CarrierVO vo : records) {
            MesCarrierBinding binding = byCarrier.get(vo.getId());
            if (binding == null) {
                continue;
            }
            vo.setBoundLotId(binding.getLotId());
            MesLot lot = lotMap.get(binding.getLotId());
            if (lot != null) {
                vo.setBoundLotNo(lot.getLotNo());
            }
        }
    }

    /** 绑定转 VO */
    private static CarrierBindingVO toBindingVo(MesLot lot, MesCarrier carrier, MesCarrierBinding binding) {
        CarrierBindingVO vo = new CarrierBindingVO();
        vo.setLotId(lot.getId());
        vo.setLotNo(lot.getLotNo());
        vo.setCarrierId(carrier.getId());
        vo.setCarrierCode(carrier.getCarrierCode());
        vo.setBindTime(binding.getBindTime());
        vo.setBindBy(binding.getBindBy());
        return vo;
    }
}
