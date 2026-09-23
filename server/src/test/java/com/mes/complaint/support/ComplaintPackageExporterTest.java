package com.mes.complaint.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mes.complaint.vo.ComplaintPackageMemberVO;
import com.mes.complaint.vo.ComplaintPackageVO;
import com.mes.config.JacksonConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 客诉追溯包导出装配的回归用例（CP-6 验收断言沉淀）。
 *
 * <p>用 {@code @JsonTest} 注入**容器同源**的 ObjectMapper（K3）：手搓 builder 时
 * {@code WRITE_DATES_AS_TIMESTAMPS} 未关会出数组，导致误判代码有 bug（实测踩点）。
 * 不依赖 MySQL / Redis / 网络（K2）。
 */
@JsonTest
class ComplaintPackageExporterTest {

    /**
     * 最小测试配置：只起 Jackson 自动配置 + 项目 {@link JacksonConfig}。
     *
     * <p>为什么不直接用 {@code MesApplication}：它是 {@code @SpringBootApplication}，
     * 切片测试加载它会触发全量组件扫描，把 MyBatis mapper 也注册进来（缺 SqlSessionFactory → 上下文启动失败）。
     * 这里保持"容器同源"的语义（Spring Boot JacksonAutoConfiguration + 项目定制项），但不带数据库相关 bean。
     */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(JacksonConfig.class)
    static class JacksonOnlyConfig {
    }

    /** 固定导出时间：既当 exportedAt 又当 ZIP entry 时间，避免时间抖动 */
    private static final LocalDateTime EXPORTED_AT = LocalDateTime.of(2026, 9, 22, 11, 20, 30);
    private static final Long EXPORTED_BY = 7L;
    private static final String PACKAGE_NO = "CP-20260922-1";

    @Autowired
    private ObjectMapper objectMapper;

    private ComplaintPackageExporter exporter() {
        return new ComplaintPackageExporter(objectMapper);
    }

    private ComplaintPackageVO sampleVo() {
        ComplaintPackageVO vo = new ComplaintPackageVO();
        vo.setPackageId(100L);
        vo.setPackageNo(PACKAGE_NO);
        vo.setAnchorLotId(100L);
        vo.setAnchorLotNo("LOT-A001");
        vo.setDirection("both");
        vo.setDepth(5);
        vo.setMemberCount(2);
        vo.setTruncated(true);
        vo.setStatus("CONTAINING");
        vo.setCreateBy(1L);
        vo.setCreateTime(LocalDateTime.of(2026, 9, 22, 9, 30, 0));
        vo.setReasonCode(null);
        vo.setRemark("客诉编号 Q-1\n第二行");
        ComplaintPackageMemberVO member = new ComplaintPackageMemberVO();
        member.setLotId(100L);
        member.setLotNo("LOT-A001");
        member.setRelation("ANCHOR");
        member.setDepth(0);
        member.setQty(25);
        member.setStatus("wait");
        vo.setMembers(new ArrayList<>(List.of(member)));
        return vo;
    }

    @Test
    void zipHasExactlyTwoFlatEntries() throws Exception {
        byte[] zip = exporter().toZipBytes(sampleVo(), EXPORTED_BY, EXPORTED_AT, 100, 20, 20);

        assertThat(zip).startsWith("PK".getBytes(StandardCharsets.US_ASCII));
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            List<String> names = new ArrayList<>();
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                names.add(e.getName());
            }
            assertThat(names).containsExactly(PACKAGE_NO + ".json", "README.txt");
            assertThat(names).allSatisfy(n -> assertThat(n).doesNotContain("/").doesNotContain("\\"));
        }
    }

    /** K8 / F14：本地头与中央目录时间必须一致（setTimeLocal 必须在 putNextEntry 之前） */
    @Test
    void localHeaderTimeMatchesCentralDirectory() throws Exception {
        byte[] zip = exporter().toZipBytes(sampleVo(), EXPORTED_BY, EXPORTED_AT, 100, 20, 20);

        List<List<Integer>> local = new ArrayList<>();
        int i = 0;
        while (true) {
            i = indexOf(zip, new byte[]{'P', 'K', 3, 4}, i);
            if (i < 0) {
                break;
            }
            int dos = (zip[i + 10] & 0xFF) | ((zip[i + 11] & 0xFF) << 8);
            local.add(List.of(dos >> 11 & 0x1F, dos >> 5 & 0x3F, (dos & 0x1F) * 2));
            i += 4;
        }

        File tmp = File.createTempFile("cpprobe", ".zip");
        List<List<Integer>> central = new ArrayList<>();
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(zip);
            }
            try (ZipFile zf = new ZipFile(tmp)) {
                zf.stream().forEach(e -> central.add(List.of(e.getTimeLocal().getHour(),
                        e.getTimeLocal().getMinute(), e.getTimeLocal().getSecond())));
                assertThat(zf.size()).isEqualTo(2);
            }
        } finally {
            assertThat(tmp.delete()).isTrue();
        }

        assertThat(local).isEqualTo(central);
        assertThat(central).allSatisfy(t -> assertThat(t.get(0)).isEqualTo(EXPORTED_AT.getHour()));
    }

    /** K4：ZIP 内 JSON 与 format=json 同源——同参数下必须逐字节相同 */
    @Test
    void innerJsonIsByteIdenticalToJsonExport() throws Exception {
        ComplaintPackageVO vo = sampleVo();
        byte[] json = exporter().toJsonBytes(vo, EXPORTED_BY, EXPORTED_AT);
        byte[] zip = exporter().toZipBytes(vo, EXPORTED_BY, EXPORTED_AT, 100, 20, 20);

        byte[] inner = readEntry(zip, PACKAGE_NO + ".json");
        assertThat(inner).isEqualTo(json);

        JsonNode node = objectMapper.readTree(json);
        assertThat(node.get("exportedAt").asText()).isEqualTo(EXPORTED_AT.toString());
        assertThat(node.get("exportedBy").isTextual()).isTrue();
        assertThat(node.get("exportedBy").asText()).isEqualTo(String.valueOf(EXPORTED_BY));
        assertThat(node.get("packageId").isTextual()).isTrue();
        assertThat(node.get("members")).hasSize(1);
    }

    @Test
    void readmeCarriesRequiredLinesAndFoldsRemark() throws Exception {
        byte[] json = exporter().toJsonBytes(sampleVo(), EXPORTED_BY, EXPORTED_AT);
        byte[] zip = exporter().toZipBytes(sampleVo(), EXPORTED_BY, EXPORTED_AT, 100, 20, 20);
        String readme = new String(readEntry(zip, "README.txt"), StandardCharsets.UTF_8);

        assertThat(readme.lines()).contains(
                "客诉追溯包",
                "包号: " + PACKAGE_NO,
                "锚点批次: LOT-A001 (100)",
                "方向: both",
                "深度: 5",
                "成员数: 2",
                "影响面已截断: 是",
                "每 Lot 履历上限: 100",
                "每 Lot Hold 各状态上限: 20",
                "每 Lot 未关闭告警上限: 20",
                "包状态: CONTAINING",
                "CONTAINING 为遏制进行中，不是结案快照",
                "原因码: -",
                "备注: 客诉编号 Q-1 第二行",
                "生成人: 1",
                "文件清单:",
                "  " + PACKAGE_NO + ".json (" + json.length + " 字节)",
                "  README.txt",
                "空履历 / 空 Hold / genealogy 空表示无数据或装配失败（见服务端 WARN）",
                "本包为客诉调查证据，非 eDHR / Device History，不含良率、OEE 数据");
    }

    /** K5：README 时间与人员文本必须与 JSON 同值同形 */
    @Test
    void readmeTimeEqualsJsonExportedAt() throws Exception {
        byte[] json = exporter().toJsonBytes(sampleVo(), EXPORTED_BY, EXPORTED_AT);
        byte[] zip = exporter().toZipBytes(sampleVo(), EXPORTED_BY, EXPORTED_AT, 100, 20, 20);
        String readme = new String(readEntry(zip, "README.txt"), StandardCharsets.UTF_8);
        JsonNode node = objectMapper.readTree(json);

        assertThat(readme.lines()).contains(
                "导出时间: " + node.get("exportedAt").asText(),
                "生成时间: " + node.get("createTime").asText(),
                "导出人: " + node.get("exportedBy").asText());
    }

    @Test
    void fileNameConventions() {
        ComplaintPackageExporter exporter = exporter();
        assertThat(exporter.jsonFileName(PACKAGE_NO)).isEqualTo(PACKAGE_NO + ".json");
        assertThat(exporter.zipFileName(PACKAGE_NO)).isEqualTo(PACKAGE_NO + ".zip");
    }

    private static byte[] readEntry(byte[] zip, String name) throws Exception {
        File tmp = File.createTempFile("cpprobe", ".zip");
        try {
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(zip);
            }
            try (ZipFile zf = new ZipFile(tmp)) {
                try (InputStream in = zf.getInputStream(zf.getEntry(name))) {
                    return in.readAllBytes();
                }
            }
        } finally {
            tmp.delete();
        }
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        for (int i = from; i <= data.length - pattern.length; i++) {
            if (Arrays.equals(Arrays.copyOfRange(data, i, i + pattern.length), pattern)) {
                return i;
            }
        }
        return -1;
    }
}
