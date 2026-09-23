package com.mes.complaint.facade.impl;

import com.mes.common.BusinessException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 导出 format 白名单与归一的回归（K3 / C16 / F7）。
 *
 * <p>保护点：① `null` / 空白 等价 `json`；② `json` / `zip` 忽略大小写并 trim；
 * ③ 其它值抛带业务码前缀的业务异常（业务码恒在 `msg`，不是 `code`）。
 * 通过反射调私有静态方法，零 Spring 上下文（K2）。
 */
class ComplaintPackageFormatWhitelistTest {

    private String normalize(String format) throws Exception {
        Method m = ComplaintPackageFacadeImpl.class.getDeclaredMethod("assertFormat", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, format);
    }

    private Throwable failureOf(String format) throws Exception {
        try {
            normalize(format);
            throw new AssertionError("预期抛异常但未抛：" + format);
        } catch (InvocationTargetException e) {
            return e.getTargetException();
        }
    }

    @Test
    void nullAndBlankMeanJson() throws Exception {
        assertThat(normalize(null)).isEqualTo("json");
        assertThat(normalize("")).isEqualTo("json");
        assertThat(normalize("   ")).isEqualTo("json");
    }

    @Test
    void jsonAndZipAreCaseInsensitiveAndTrimmed() throws Exception {
        assertThat(normalize("json")).isEqualTo("json");
        assertThat(normalize("JSON")).isEqualTo("json");
        assertThat(normalize(" json ")).isEqualTo("json");
        assertThat(normalize("zip")).isEqualTo("zip");
        assertThat(normalize("ZIP")).isEqualTo("zip");
        assertThat(normalize(" zip ")).isEqualTo("zip");
    }

    @Test
    void unsupportedFormatCarriesBusinessCodeInMessage() throws Exception {
        Throwable t = failureOf("pdf");
        assertThat(t).isInstanceOf(BusinessException.class);
        assertThat(t.getMessage()).startsWith("COMPLAINT_PACKAGE_FORMAT_UNSUPPORTED");

        assertThat(failureOf("xml")).isInstanceOf(BusinessException.class);
        assertThat(failureOf("json2")).isInstanceOf(BusinessException.class);
    }
}
