package com.mes.test.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.common.PageResult;
import com.mes.lot.service.MesLotService;
import com.mes.lot.vo.MesLotVO;
import com.mes.test.dto.TestBinLineDTO;
import com.mes.test.dto.TestRecordCreateDTO;
import com.mes.test.dto.TestRecordQuery;
import com.mes.test.entity.MesBinDef;
import com.mes.test.entity.MesTestBinSummary;
import com.mes.test.entity.MesTestRecord;
import com.mes.test.entity.MesTestSubmitGuard;
import com.mes.test.mapper.MesBinDefMapper;
import com.mes.test.mapper.MesTestBinSummaryMapper;
import com.mes.test.mapper.MesTestRecordMapper;
import com.mes.test.mapper.MesTestRecordNoSeqMapper;
import com.mes.test.mapper.MesTestSubmitGuardMapper;
import com.mes.test.service.MesTestRecordService;
import com.mes.test.vo.TestBinLineVO;
import com.mes.test.vo.TestRecordVO;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MesTestRecordServiceImpl implements MesTestRecordService {

    /** 记录号里的日期，yyyyMMdd */
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    /** 必填项缺失 */
    private static final String FIELD = "TEST_RECORD_FIELD_REQUIRED: 必填项缺失，";
    /** 字典里解析不到这一档 */
    private static final String NOT_FOUND = "TEST_BIN_DEF_NOT_FOUND: 档位定义不存在";
    /** 同一次提交里档号重复 */
    private static final String DUP_LINE = "TEST_BIN_CODE_DUPLICATED: 同一请求内档号重复";
    /** 硬档颗数加起来不等于总量 */
    private static final String SUM = "TEST_BIN_SUM_MISMATCH: 硬档颗数之和与总量不符";
    /** 同一时间桶内重复提交 */
    private static final String DUP_SUBMIT = "TEST_RECORD_DUPLICATE: 重复提交";
    /** 对外批量查询时，每条记录最多带出的档数 */
    private static final int BIN_CAP = 50;

    private final MesTestRecordMapper mesTestRecordMapper;
    private final MesTestBinSummaryMapper mesTestBinSummaryMapper;
    private final MesTestSubmitGuardMapper mesTestSubmitGuardMapper;
    private final MesTestRecordNoSeqMapper mesTestRecordNoSeqMapper;
    private final MesBinDefMapper mesBinDefMapper;
    private final MesLotService mesLotService;

    /** 校验后同一事务写入守卫、记录头和汇总 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestRecordVO create(TestRecordCreateDTO dto) {
        AssertUtil.notNull(dto, FIELD + "参数不能为空");
        AssertUtil.notNull(dto.getLotId(), FIELD + "批次不能为空");
        String stage = required(dto.getTestStage(), "测试阶段");
        String program = required(dto.getProgramName(), "程序名");
        String version = required(dto.getProgramVersion(), "程序版本");
        AssertUtil.notNull(dto.getTestTime(), FIELD + "测试完成时间不能为空");
        AssertUtil.notNull(dto.getTotalQty(), FIELD + "测试颗数不能为空");
        AssertUtil.isTrue(dto.getTotalQty() > 0, FIELD + "测试颗数必须大于0");
        String sourceType = required(dto.getSourceType(), "数据来源");
        AssertUtil.isTrue("CP".equals(stage) || "FT".equals(stage) || "OTHER".equals(stage), FIELD + "测试阶段只能是晶圆测试、成品测试或其他");
        AssertUtil.isTrue("FILE".equals(sourceType) || "API".equals(sourceType) || "MANUAL".equals(sourceType), FIELD + "数据来源只能是文件、接口或手工");
        List<TestBinLineDTO> lines = dto.getBins();// bin的各档颗数
        AssertUtil.notEmpty(lines, FIELD + "分档明细不能为空");
        assertLines(lines);
        checkSum(lines, dto.getTotalQty());
        MesLotVO lot = mesLotService.get(dto.getLotId());
        AssertUtil.isTrue(!"merged".equals(lot.getStatus()) && !"scrapped".equals(lot.getStatus()), "已合批或已报废批次不能登记测试");
        String product = lot.getProductCode() == null ? "" : lot.getProductCode().trim();// 产品代码
        List<MesBinDef> resolved = resolve(lines, product, program, version);

        MesTestSubmitGuard guard = new MesTestSubmitGuard();
        guard.setId(IdWorker.getId());
        guard.setLotId(lot.getId());// 批次ID
        guard.setEqpKey(dto.getEqpId() == null ? 0L : dto.getEqpId());
        guard.setProgramName(program);// 程序名
        guard.setProgramVersion(version);// 程序版本
        guard.setTestTime(dto.getTestTime());// 测试时间
        guard.setTotalQty(dto.getTotalQty());
        insertGuard(guard);

        String recordNo = nextRecordNo();
        MesTestRecord record = new MesTestRecord();
        record.setRecordNo(recordNo);
        record.setLotId(lot.getId());
        record.setLotNo(lot.getLotNo());
        record.setTestStage(stage);
        record.setProgramName(program);
        record.setProgramVersion(version);
        record.setEqpId(dto.getEqpId());
        record.setEqpCode(emptyToNull(dto.getEqpCode()));
        record.setTestTime(dto.getTestTime());
        record.setTotalQty(dto.getTotalQty());
        record.setSourceType(sourceType);
        record.setSourceRef(emptyToNull(dto.getSourceRef()));
        record.setRemark(emptyToNull(dto.getRemark()));
        record.setCreateBy(StpUtil.getLoginIdAsLong());
        try {
            mesTestRecordMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(DUP_SUBMIT);
        }
        List<MesTestBinSummary> saved = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            TestBinLineDTO line = lines.get(i);
            MesBinDef def = resolved.get(i);
            MesTestBinSummary row = new MesTestBinSummary();
            row.setRecordId(record.getId());
            row.setBinType(line.getBinType().trim());
            row.setBinCode(line.getBinCode().trim());
            row.setBinName(def.getBinName());
            row.setBinQty(line.getBinQty());
            row.setIsShippable(def.getIsShippable());
            mesTestBinSummaryMapper.insert(row);
            saved.add(row);
        }
        return toVo(record, saved);
    }

    /** 分页查记录头，不带各档明细 */
    @Override
    public PageResult<TestRecordVO> page(TestRecordQuery query) {
        long pageNo = query.getPage() <= 0 ? 1 : query.getPage();
        long pageSize = query.getSize() <= 0 ? 20 : Math.min(query.getSize(), 100);
        LambdaQueryWrapper<MesTestRecord> qw = new LambdaQueryWrapper<>();
        if (query.getLotId() != null) {
            qw.eq(MesTestRecord::getLotId, query.getLotId());
        }
        if (StringUtils.hasText(query.getLotNo())) {
            qw.like(MesTestRecord::getLotNo, query.getLotNo().trim());
        }
        if (StringUtils.hasText(query.getStage())) {
            qw.eq(MesTestRecord::getTestStage, query.getStage().trim());
        }
        if (StringUtils.hasText(query.getProgramName())) {
            qw.like(MesTestRecord::getProgramName, query.getProgramName().trim());
        }
        if (query.getFrom() != null) {
            qw.ge(MesTestRecord::getTestTime, query.getFrom());
        }
        if (query.getTo() != null) {
            qw.le(MesTestRecord::getTestTime, query.getTo());
        }
        qw.orderByDesc(MesTestRecord::getTestTime).orderByDesc(MesTestRecord::getId);
        Page<MesTestRecord> result = mesTestRecordMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return PageResult.of(toVos(result.getRecords(), false, Integer.MAX_VALUE), result.getTotal(), pageNo, pageSize);
    }

    /** 单条详情，含全部档位和占比 */
    @Override
    public TestRecordVO get(Long id) {
        MesTestRecord record = mesTestRecordMapper.selectById(id);
        AssertUtil.notNull(record, "测试记录不存在");
        return toVo(record, loadBins(List.of(record.getId()), Integer.MAX_VALUE).getOrDefault(record.getId(), List.of()));
    }

    /** 作废只软删头表，原因写入备注，守卫和汇总不动 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidRecord(Long id, String reason) {
        AssertUtil.notBlank(reason, FIELD + "作废原因不能为空");
        MesTestRecord record = mesTestRecordMapper.selectById(id);
        AssertUtil.notNull(record, "测试记录不存在");
        String text = reason.trim();
        String old = record.getRemark();
        String merged = (old == null || old.isBlank()) ? text : old + " | 作废:" + text;
        AssertUtil.isTrue(merged.length() <= 512, FIELD + "作废原因过长，请缩短后重试");
        record.setRemark(merged);
        mesTestRecordMapper.updateById(record);
        mesTestRecordMapper.deleteById(id);
    }

    /** 某批全部测试记录，新的在前，带各档明细 */
    @Override
    public List<TestRecordVO> listByLot(Long lotId) {
        AssertUtil.notNull(lotId, FIELD + "批次不能为空");
        List<MesTestRecord> records = mesTestRecordMapper.selectList(new LambdaQueryWrapper<MesTestRecord>()
                .eq(MesTestRecord::getLotId, lotId)
                .orderByDesc(MesTestRecord::getTestTime)
                .orderByDesc(MesTestRecord::getId));
        return toVos(records, true, Integer.MAX_VALUE);
    }

    /** 多批各取最近若干条，每条最多 50 档 */
    @Override
    public List<TestRecordVO> listRecordsByLots(Collection<Long> lotIds, int capPerLot) {
        if (lotIds == null || lotIds.isEmpty()) {
            return List.of();
        }
        int cap = capPerLot < 1 ? 20 : capPerLot;
        List<MesTestRecord> records = mesTestRecordMapper.selectRecentByLots(lotIds, cap);
        return toVos(records, true, BIN_CAP);
    }

    /**
     * 校验档位合法性
     * 档位必填、颗数非负，同一请求内档号不重复
     */
    private void assertLines(List<TestBinLineDTO> lines) {
        Set<String> seen = new HashSet<>();
        for (TestBinLineDTO line : lines) {
            AssertUtil.notNull(line, FIELD + "分档明细不能有空行");
            AssertUtil.notBlank(line.getBinType(), FIELD + "档类型不能为空");
            AssertUtil.notBlank(line.getBinCode(), FIELD + "档号不能为空");
            AssertUtil.notNull(line.getBinQty(), FIELD + "颗数不能为空");
            AssertUtil.isTrue(line.getBinQty() >= 0, FIELD + "颗数不能为负");
            String type = line.getBinType().trim();
            String code = line.getBinCode().trim();
            AssertUtil.isTrue("HARD".equals(type) || "SOFT".equals(type), FIELD + "档类型只能是硬档或软档");
            AssertUtil.isTrue(seen.add(type + "\0" + code), DUP_LINE);
        }
    }

    /** HARD 档颗数之和必须等于总量 */
    private void checkSum(List<TestBinLineDTO> lines, int totalQty) {
        long hard = 0;
        for (TestBinLineDTO line : lines) {
            if ("HARD".equals(line.getBinType().trim())) {
                hard += line.getBinQty();
            }
        }
        AssertUtil.isTrue(hard == totalQty, SUM);
    }

    /** 按程序版本、程序、产品、全局四级回退解析每档 */
    private List<MesBinDef> resolve(List<TestBinLineDTO> lines, String product, String program, String version) {
        Set<String> types = new HashSet<>();
        Set<String> codes = new HashSet<>();
        for (TestBinLineDTO line : lines) {
            types.add(line.getBinType().trim());
            codes.add(line.getBinCode().trim());
        }
        List<MesBinDef> defs = mesBinDefMapper.selectList(new LambdaQueryWrapper<MesBinDef>()
                .eq(MesBinDef::getStatus, 1)
                .in(MesBinDef::getBinType, types)
                .in(MesBinDef::getBinCode, codes));
        List<MesBinDef> picked = new ArrayList<>(lines.size());
        for (TestBinLineDTO line : lines) {
            picked.add(pick(defs, line.getBinType().trim(), line.getBinCode().trim(), product, program, version));
        }
        return picked;
    }

    /** 同一档取最精确的一条启用定义 */
    private MesBinDef pick(List<MesBinDef> defs, String binType, String binCode, String product, String program, String version) {
        MesBinDef best = null;
        int bestRank = 0;
        for (MesBinDef def : defs) {
            if (!binType.equals(def.getBinType()) || !binCode.equals(def.getBinCode())) {
                continue;
            }
            int rank = rank(def, product, program, version);
            if (rank > bestRank) {
                bestRank = rank;
                best = def;
            }
        }
        AssertUtil.notNull(best, NOT_FOUND);
        return best;
    }

    /** 4 版本精确，3 程序，2 产品，1 全局，0 不匹配 */
    private int rank(MesBinDef def, String product, String program, String version) {
        String pc = def.getProductCode() == null ? "" : def.getProductCode();
        String pn = def.getProgramName() == null ? "" : def.getProgramName();
        String pv = def.getProgramVersion() == null ? "" : def.getProgramVersion();
        if (product.equals(pc) && program.equals(pn) && version.equals(pv)) {
            return 4;
        }
        if (product.equals(pc) && program.equals(pn) && pv.isEmpty()) {
            return 3;
        }
        if (product.equals(pc) && pn.isEmpty() && pv.isEmpty()) {
            return 2;
        }
        if (pc.isEmpty() && pn.isEmpty() && pv.isEmpty()) {
            return 1;
        }
        return 0;
    }

    /** 插入守卫。唯一键冲突视为重复提交 */
    private void insertGuard(MesTestSubmitGuard guard) {
        try {
            mesTestSubmitGuardMapper.insertGuard(guard);
        } catch (RuntimeException ex) {
            if (isDuplicate(ex)) {
                throw new BusinessException(DUP_SUBMIT);
            }
            throw ex;
        }
    }

    /** 异常链上是否为唯一键冲突 */
    private boolean isDuplicate(Throwable ex) {
        while (ex != null) {
            if (ex instanceof DuplicateKeyException) {
                return true;
            }
            String msg = ex.getMessage();
            if (msg != null && (msg.contains("uk_test_submit_guard") || msg.contains("uk_record_no"))) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }

    /** 同一事务内取当日流水，拼成记录号 */
    private String nextRecordNo() {
        String day = LocalDate.now().format(DAY);
        mesTestRecordNoSeqMapper.bump(day);
        long seq = mesTestRecordNoSeqMapper.lastInsertId();
        AssertUtil.isTrue(seq > 0, "测试记录号生成失败");
        return "TR-" + day + "-" + String.format("%03d", seq);
    }

    /** 批量转出参。withBins 为假时不查明细 */
    private List<TestRecordVO> toVos(List<MesTestRecord> records, boolean withBins, int binCap) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, List<MesTestBinSummary>> bins = withBins
                ? loadBins(records.stream().map(MesTestRecord::getId).toList(), binCap)
                : Map.of();
        List<TestRecordVO> vos = new ArrayList<>(records.size());
        for (MesTestRecord record : records) {
            vos.add(toVo(record, bins.getOrDefault(record.getId(), List.of())));
        }
        return vos;
    }

    /** 一次查出这些记录的汇总，每条截到 binCap 档 */
    private Map<Long, List<MesTestBinSummary>> loadBins(List<Long> recordIds, int binCap) {
        if (recordIds.isEmpty()) {
            return Map.of();
        }
        List<MesTestBinSummary> rows = mesTestBinSummaryMapper.selectList(new LambdaQueryWrapper<MesTestBinSummary>()
                .in(MesTestBinSummary::getRecordId, recordIds)
                .orderByAsc(MesTestBinSummary::getBinType)
                .orderByAsc(MesTestBinSummary::getBinCode));
        Map<Long, List<MesTestBinSummary>> grouped = new LinkedHashMap<>();
        for (MesTestBinSummary row : rows) {
            List<MesTestBinSummary> list = grouped.computeIfAbsent(row.getRecordId(), key -> new ArrayList<>());
            if (list.size() < binCap) {
                list.add(row);
            }
        }
        return grouped;
    }

    /** 单条转出参，并算出每档占比 */
    private TestRecordVO toVo(MesTestRecord record, List<MesTestBinSummary> bins) {
        TestRecordVO vo = new TestRecordVO();
        vo.setId(record.getId());
        vo.setRecordNo(record.getRecordNo());
        vo.setLotId(record.getLotId());
        vo.setLotNo(record.getLotNo());
        vo.setTestStage(record.getTestStage());
        vo.setProgramName(record.getProgramName());
        vo.setProgramVersion(record.getProgramVersion());
        vo.setEqpId(record.getEqpId());
        vo.setEqpCode(record.getEqpCode());
        vo.setTestTime(record.getTestTime());
        vo.setTotalQty(record.getTotalQty());
        vo.setSourceType(record.getSourceType());
        vo.setSourceRef(record.getSourceRef());
        vo.setRemark(record.getRemark());
        vo.setCreateTime(record.getCreateTime());
        List<TestBinLineVO> lines = new ArrayList<>(bins.size());
        for (MesTestBinSummary bin : bins) {
            TestBinLineVO line = new TestBinLineVO();
            line.setBinType(bin.getBinType());
            line.setBinCode(bin.getBinCode());
            line.setBinName(bin.getBinName());
            line.setBinQty(bin.getBinQty());
            line.setIsShippable(bin.getIsShippable());
            if (record.getTotalQty() != null && record.getTotalQty() > 0 && bin.getBinQty() != null) {
                line.setRatio(BigDecimal.valueOf(bin.getBinQty())
                        .divide(BigDecimal.valueOf(record.getTotalQty()), 4, RoundingMode.HALF_UP));
            }
            lines.add(line);
        }
        vo.setBins(lines);
        return vo;
    }

    /** 必填字符串，返回去掉空白后的值 */
    private String required(String value, String name) {
        AssertUtil.notBlank(value, FIELD + name + "不能为空");
        return value.trim();
    }

    /** 空白转成 null */
    private String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
