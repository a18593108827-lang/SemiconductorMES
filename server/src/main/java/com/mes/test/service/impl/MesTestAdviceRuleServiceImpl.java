package com.mes.test.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mes.common.AssertUtil;
import com.mes.common.BusinessException;
import com.mes.common.PageResult;
import com.mes.test.dto.TestAdviceRuleQuery;
import com.mes.test.dto.TestAdviceRuleSaveDTO;
import com.mes.test.entity.MesTestAdviceRule;
import com.mes.test.entity.MesTestRuleLog;
import com.mes.test.mapper.MesTestAdviceRuleMapper;
import com.mes.test.mapper.MesTestRuleLogMapper;
import com.mes.test.service.MesTestAdviceRuleService;
import com.mes.test.vo.TestAdviceRuleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MesTestAdviceRuleServiceImpl implements MesTestAdviceRuleService {

    private static final String UK = "TEST_RULE_UK_CONFLICT: 相同范围已有该Bin规则";
    private static final String NOT_FOUND = "TEST_RULE_NOT_FOUND: 规则不存在";
    private static final String STALE = "TEST_RULE_NOT_FOUND: 数据已被他人修改，请刷新后重试";
    private static final Set<String> ACTIONS = Set.of("SUGGEST_HOLD", "SUGGEST_REWORK", "SUGGEST_RETEST");

    private final MesTestAdviceRuleMapper mesTestAdviceRuleMapper;
    private final MesTestRuleLogMapper mesTestRuleLogMapper;

    /** 分页查规则，可按产品 / bin / 启停过滤 */
    @Override
    public PageResult<TestAdviceRuleVO> page(TestAdviceRuleQuery query) {
        long pageNo = query.getPage() < 1 ? 1 : query.getPage();
        long pageSize = query.getSize() < 1 ? 20 : query.getSize();
        LambdaQueryWrapper<MesTestAdviceRule> qw = new LambdaQueryWrapper<>();
        if (query.getProductCode() != null) {
            qw.eq(MesTestAdviceRule::getProductCode, query.getProductCode());
        }
        if (query.getBinCode() != null && !query.getBinCode().isBlank()) {
            qw.eq(MesTestAdviceRule::getBinCode, query.getBinCode().trim());
        }
        if (query.getEnabled() != null) {
            qw.eq(MesTestAdviceRule::getEnabled, query.getEnabled());
        }
        qw.orderByAsc(MesTestAdviceRule::getProductCode)
                .orderByAsc(MesTestAdviceRule::getBinCode)
                .orderByDesc(MesTestAdviceRule::getId);
        Page<MesTestAdviceRule> result = mesTestAdviceRuleMapper.selectPage(new Page<>(pageNo, pageSize), qw);
        return PageResult.of(result.getRecords().stream().map(this::toVo).toList(), result.getTotal(), pageNo, pageSize);
    }

    /** 新建规则；UK 冲突拒；同事务写 CREATE 审计 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TestAdviceRuleVO create(TestAdviceRuleSaveDTO dto) {
        MesTestAdviceRule row = new MesTestAdviceRule();
        fill(row, dto);
        row.setVersion(0);
        try {
            mesTestAdviceRuleMapper.insert(row);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(UK);
        }
        writeLog(row.getId(), "CREATE", null, row);
        return toVo(row);
    }

    /** 修改规则（乐观锁）；同事务写 UPDATE 审计 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TestAdviceRuleSaveDTO dto) {
        MesTestAdviceRule row = mesTestAdviceRuleMapper.selectById(id);
        AssertUtil.notNull(row, NOT_FOUND);
        AssertUtil.notNull(dto.getVersion(), STALE);
        String before = JSONUtil.toJsonStr(row);
        fill(row, dto);
        row.setVersion(dto.getVersion());
        int n;
        try {
            n = mesTestAdviceRuleMapper.updateById(row);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(UK);
        }
        AssertUtil.isTrue(n > 0, STALE);
        writeLog(id, "UPDATE", before, row);
    }

    /** 软删规则；同事务写 DELETE 审计 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MesTestAdviceRule row = mesTestAdviceRuleMapper.selectById(id);
        AssertUtil.notNull(row, NOT_FOUND);
        String before = JSONUtil.toJsonStr(row);
        mesTestAdviceRuleMapper.deleteById(id);
        writeLog(id, "DELETE", before, null);
    }

    /**
     * 业务：提交测试后，给某个不良 bin 对上「该用哪本规矩卡」。
     * 质量可能只给某款产品某版程序配了严限，也可能只配了全厂默认——这里从细到粗找，命中最具体的那条；
     * 四档都没有则返回 null，后面不算超限、不生成建议单。
     */
    @Override
    public MesTestAdviceRule findRule(String productCode, String programName, String programVersion, String binCode) {
        String bin = trimToEmpty(binCode);
        if (bin.isEmpty()) {
            return null;
        }
        for (String[] key : fallbackKeys(productCode, programName, programVersion)) {
            MesTestAdviceRule hit = mesTestAdviceRuleMapper.selectOne(new LambdaQueryWrapper<MesTestAdviceRule>()
                    .eq(MesTestAdviceRule::getProductCode, key[0])
                    .eq(MesTestAdviceRule::getProgramName, key[1])
                    .eq(MesTestAdviceRule::getProgramVersion, key[2])
                    .eq(MesTestAdviceRule::getBinType, "HARD")
                    .eq(MesTestAdviceRule::getBinCode, bin)
                    .eq(MesTestAdviceRule::getEnabled, 1)
                    .last("LIMIT 1"));
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    /**
     * 找规矩时的放宽顺序：产品+程序+版本 → 产品+程序 → 仅产品 → 全厂默认。
     * 入参本身已空的维度会跳过，避免重复查同一档。
     */
    static List<String[]> fallbackKeys(String productCode, String programName, String programVersion) {
        String p = trimToEmpty(productCode);
        String pn = trimToEmpty(programName);
        String pv = trimToEmpty(programVersion);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String[]> keys = new ArrayList<>();
        addKey(keys, seen, p, pn, pv);
        addKey(keys, seen, p, pn, "");
        addKey(keys, seen, p, "", "");
        addKey(keys, seen, "", "", "");
        return keys;
    }

    /** 同一档只加一次 */
    private static void addKey(List<String[]> keys, Set<String> seen, String p, String pn, String pv) {
        String token = p + "\0" + pn + "\0" + pv;
        if (seen.add(token)) {
            keys.add(new String[]{p, pn, pv});
        }
    }

    /** 校验并写入规则字段；空维度转空串 */
    private void fill(MesTestAdviceRule row, TestAdviceRuleSaveDTO dto) {
        String product = trimToEmpty(dto.getProductCode());
        String program = trimToEmpty(dto.getProgramName());
        String version = trimToEmpty(dto.getProgramVersion());
        String binType = trimToEmpty(dto.getBinType());
        String binCode = trimToEmpty(dto.getBinCode());
        String action = trimToEmpty(dto.getSuggestedAction());
        AssertUtil.notBlank(binType, "TEST_RECORD_FIELD_REQUIRED: binType不能为空");
        AssertUtil.isTrue("HARD".equals(binType), "TEST_RECORD_FIELD_REQUIRED: P0仅支持HARD");
        AssertUtil.notBlank(binCode, "TEST_RECORD_FIELD_REQUIRED: binCode不能为空");
        AssertUtil.notNull(dto.getMaxRatio(), "TEST_RECORD_FIELD_REQUIRED: maxRatio不能为空");
        AssertUtil.isTrue(dto.getMaxRatio().compareTo(BigDecimal.ZERO) >= 0
                        && dto.getMaxRatio().compareTo(BigDecimal.ONE) <= 0,
                "TEST_RECORD_FIELD_REQUIRED: maxRatio须在0~1");
        AssertUtil.notBlank(action, "TEST_RECORD_FIELD_REQUIRED: suggestedAction不能为空");
        AssertUtil.isTrue(ACTIONS.contains(action), "TEST_RECORD_FIELD_REQUIRED: suggestedAction非法");
        if (!program.isEmpty()) {
            AssertUtil.notBlank(product, "TEST_RECORD_FIELD_REQUIRED: 配程序须同时配产品");
        }
        if (!version.isEmpty()) {
            AssertUtil.notBlank(program, "TEST_RECORD_FIELD_REQUIRED: 配版本须同时配程序");
        }
        int enabled = dto.getEnabled() == null ? 1 : dto.getEnabled();
        AssertUtil.isTrue(enabled == 0 || enabled == 1, "TEST_RECORD_FIELD_REQUIRED: enabled只能为0或1");
        row.setProductCode(product);
        row.setProgramName(program);
        row.setProgramVersion(version);
        row.setBinType(binType);
        row.setBinCode(binCode);
        row.setMaxRatio(dto.getMaxRatio());
        row.setSuggestedAction(action);
        row.setDefaultReasonCode(emptyToNull(dto.getDefaultReasonCode()));
        row.setEnabled(enabled);
        row.setRemark(emptyToNull(dto.getRemark()));
    }

    /** 追加一条规则变更审计 */
    private void writeLog(Long ruleId, String action, String before, MesTestAdviceRule after) {
        MesTestRuleLog log = new MesTestRuleLog();
        log.setRuleId(ruleId);
        log.setAction(action);
        log.setBeforeJson(before);
        log.setAfterJson(after == null ? null : JSONUtil.toJsonStr(after));
        log.setOpBy(StpUtil.getLoginIdAsLong());
        log.setOpAt(LocalDateTime.now());
        mesTestRuleLogMapper.insert(log);
    }

    /** 规则行转出参 */
    private TestAdviceRuleVO toVo(MesTestAdviceRule row) {
        TestAdviceRuleVO vo = new TestAdviceRuleVO();
        vo.setId(row.getId());
        vo.setProductCode(row.getProductCode());
        vo.setProgramName(row.getProgramName());
        vo.setProgramVersion(row.getProgramVersion());
        vo.setBinType(row.getBinType());
        vo.setBinCode(row.getBinCode());
        vo.setMaxRatio(row.getMaxRatio());
        vo.setSuggestedAction(row.getSuggestedAction());
        vo.setDefaultReasonCode(row.getDefaultReasonCode());
        vo.setEnabled(row.getEnabled());
        vo.setVersion(row.getVersion());
        vo.setRemark(row.getRemark());
        return vo;
    }

    /** 去掉首尾空白，null 当空串 */
    static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** 空白转成 null */
    private static String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
