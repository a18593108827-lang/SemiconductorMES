package com.mes.complaint.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mes.complaint.vo.ComplaintPackageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 追溯包导出装配：JSON 组装 / README 封面 / ZIP 打包 / 文件名（CP-4 + CP-6）。
 *
 * <p>只读组件：只吃 {@link ComplaintPackageVO} + 容器 {@code ObjectMapper} + 方法入参，
 * <b>零</b> Mapper / Facade / Assembler 依赖（K9）。上限值（履历 / Hold / 告警）一律由
 * 调用方传入，本类不写死数字、不读配置（K14 / K15 / D9）。
 *
 * <p>包装顺序约束（K8）：每 entry 必须<b>先</b> {@code setTimeLocal} <b>再</b> {@code putNextEntry}；
 * 反序会让本地头写当前时间、中央目录写设定值，同一 entry 出现两个时间。
 * 另：ZIP 的 DOS 时间只有 2 秒粒度，回读可能比设定值早 1 秒，属正常。
 *
 * <p>时序：调用方在只读装配（{@code get()}）返回后才调用本类，全程无事务、不加锁（K2 / K11）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintPackageExporter {

    /** 单包 JSON 超此字节数只留痕不拒（K10）；验收不改此常量 */
    private static final long LARGE_BYTES = 10L * 1024 * 1024;
    /** JSON 附件后缀（外层下载名与 ZIP 内 entry 名同一份拼接，K6 / D6） */
    private static final String JSON_SUFFIX = ".json";
    /** ZIP 外层下载名后缀 */
    private static final String ZIP_SUFFIX = ".zip";
    /** ZIP 内封面文件名 */
    private static final String README_ENTRY = "README.txt";
    /** 空值占位 */
    private static final String DASH = "-";

    private final ObjectMapper objectMapper;

    /** ZIP 内 JSON entry 名（= JSON 下载名） */
    public String jsonFileName(String packageNo) {
        return packageNo + JSON_SUFFIX;
    }

    /** ZIP 外层下载名 */
    public String zipFileName(String packageNo) {
        return packageNo + ZIP_SUFFIX;
    }

    /**
     * JSON 附件字节：Get VO + {@code exportedAt} / {@code exportedBy}。
     * 异常向上抛，由 Facade 单点翻译（K12）；超 {@link #LARGE_BYTES} 记 WARN（K10）。
     */
    public byte[] toJsonBytes(ComplaintPackageVO vo, Long exportedBy, LocalDateTime exportedAt) throws IOException {
        ObjectNode node = buildJsonNode(vo, exportedBy, exportedAt);
        byte[] content = objectMapper.writeValueAsBytes(node);
        if (content.length > LARGE_BYTES) {
            log.warn("complaint export large package packageNo={} bytes={} memberCount={}",
                    vo.getPackageNo(), content.length, vo.getMemberCount());
        }
        return content;
    }

    /**
     * JSON 根节点（CP-4 口径逐字保持）。同一次导出内 JSON 与 README 复用同一
     * {@code exportedAt} / {@code exportedBy}，禁止另写一套序列化（K4 / K5）。
     */
    public ObjectNode buildJsonNode(ComplaintPackageVO vo, Long exportedBy, LocalDateTime exportedAt) {
        ObjectNode node = objectMapper.convertValue(vo, ObjectNode.class);
        node.set("exportedAt", objectMapper.valueToTree(exportedAt));
        node.set("exportedBy", objectMapper.valueToTree(exportedBy));
        return node;
    }

    /**
     * ZIP 附件字节：{@code {packageNo}.json} + {@code README.txt}，恰好 2 个 entry、扁平无目录（D2）。
     * 内存一次写出（P0 口径）；内部走同一 {@link #buildJsonNode}（K4）。
     */
    public byte[] toZipBytes(ComplaintPackageVO vo, Long exportedBy, LocalDateTime exportedAt,
                             int historyPerLot, int holdCap, int alarmCap) throws IOException {
        String jsonName = jsonFileName(vo.getPackageNo());
        byte[] json = toJsonBytes(vo, exportedBy, exportedAt);
        String readme = toReadme(vo, exportedBy, exportedAt, jsonName, json.length, historyPerLot, holdCap, alarmCap);

        ByteArrayOutputStream baos = new ByteArrayOutputStream(json.length + 2048);
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            putText(zos, jsonName, json, exportedAt);
            putText(zos, README_ENTRY, readme.getBytes(StandardCharsets.UTF_8), exportedAt);
        }
        return baos.toByteArray();
    }

    /**
     * 封面文本（README.txt）。行序固定（K14 / 实施步骤 b.4）：
     * 包号 / 锚点 / 方向 / 深度 / 成员数 / 截断 / 三个上限 / 状态 / 遏制进行中说明 /
     * 原因码 / 备注 / 生成人时间 / 导出人时间 / 文件清单 / 空块说明 / 口径句。
     */
    public String toReadme(ComplaintPackageVO vo, Long exportedBy, LocalDateTime exportedAt, String jsonName,
                           long jsonBytes, int historyPerLot, int holdCap, int alarmCap) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("客诉追溯包\n");
        sb.append("包号: ").append(dashIfBlank(vo.getPackageNo())).append('\n');
        sb.append("锚点批次: ").append(dashIfBlank(vo.getAnchorLotNo()))
                .append(" (").append(vo.getAnchorLotId() == null ? DASH : vo.getAnchorLotId()).append(")\n");
        sb.append("方向: ").append(dashIfBlank(vo.getDirection())).append('\n');
        sb.append("深度: ").append(vo.getDepth() == null ? DASH : vo.getDepth()).append('\n');
        sb.append("成员数: ").append(vo.getMemberCount() == null ? 0 : vo.getMemberCount()).append('\n');
        sb.append("影响面已截断: ").append(vo.isTruncated() ? "是" : "否").append('\n');
        sb.append("每 Lot 履历上限: ").append(historyPerLot).append('\n');
        sb.append("每 Lot Hold 各状态上限: ").append(holdCap).append('\n');
        sb.append("每 Lot 未关闭告警上限: ").append(alarmCap).append('\n');
        sb.append("包状态: ").append(dashIfBlank(vo.getStatus())).append('\n');
        sb.append("CONTAINING 为遏制进行中，不是结案快照\n");
        sb.append("原因码: ").append(dashIfBlank(vo.getReasonCode())).append('\n');
        sb.append("备注: ").append(dashIfBlank(vo.getRemark())).append('\n');
        sb.append("生成人: ").append(scalar(vo.getCreateBy())).append('\n');
        sb.append("生成时间: ").append(scalar(vo.getCreateTime())).append('\n');
        sb.append("导出人: ").append(scalar(exportedBy)).append('\n');
        sb.append("导出时间: ").append(scalar(exportedAt)).append('\n');
        sb.append("文件清单:\n");
        sb.append("  ").append(jsonName).append(" (").append(jsonBytes).append(" 字节)\n");
        sb.append("  ").append(README_ENTRY).append('\n');
        sb.append("空履历 / 空 Hold / genealogy 空表示无数据或装配失败（见服务端 WARN）\n");
        sb.append("本包为客诉调查证据，非 eDHR / Device History，不含良率、OEE 数据\n");
        return sb.toString();
    }

    /** 写单个文本 entry：先 setTimeLocal 再 putNextEntry（K8 / F14） */
    private static void putText(ZipOutputStream zos, String name, byte[] content, LocalDateTime time) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTimeLocal(time);
        zos.putNextEntry(entry);
        zos.write(content);
        zos.closeEntry();
    }

    /** 标量文本：与 JSON 同一 ObjectMapper，无需另写格式化器（K5） */
    private String scalar(Object value) {
        if (value == null) {
            return DASH;
        }
        JsonNode node = objectMapper.valueToTree(value);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return DASH;
        }
        if (node.isValueNode()) {
            String text = node.asText();
            return text == null || text.isBlank() ? DASH : text;
        }
        // 容器把 JavaTime 配成数组/对象时（WRITE_DATES_AS_TIMESTAMPS=true）仍印同一 ObjectMapper
        // 的序列化文本，避免封面时间/人员静默变 "-"（实测：容器默认关闭该 feature，走上面的标量分支）
        return node.toString();
    }

    /** 空 / 空白 → "-"；换行折成空格保持一行（K14） */
    private static String dashIfBlank(String raw) {
        String text = oneLine(raw);
        return text.isEmpty() ? DASH : text;
    }

    /** 折行 + 去首尾空白 */
    private static String oneLine(String raw) {
        return raw == null ? "" : raw.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
