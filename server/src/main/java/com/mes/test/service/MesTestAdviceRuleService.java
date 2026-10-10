package com.mes.test.service;

import com.mes.common.PageResult;
import com.mes.test.dto.TestAdviceRuleQuery;
import com.mes.test.dto.TestAdviceRuleSaveDTO;
import com.mes.test.entity.MesTestAdviceRule;
import com.mes.test.vo.TestAdviceRuleVO;

public interface MesTestAdviceRuleService {

    PageResult<TestAdviceRuleVO> page(TestAdviceRuleQuery query);

    TestAdviceRuleVO create(TestAdviceRuleSaveDTO dto);

    void update(Long id, TestAdviceRuleSaveDTO dto);

    void delete(Long id);

    /**
     * 为某个 HARD bin 找出该用哪条阈值规矩：优先「产品+程序+版本」，没有再放宽到产品/程序、仅产品、全厂默认；
     * 只认已启用规则。找不到返回 null（表示这档没人管，不举牌）。
     */
    MesTestAdviceRule findRule(String productCode, String programName, String programVersion, String binCode);
}
