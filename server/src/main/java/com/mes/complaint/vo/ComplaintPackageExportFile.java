package com.mes.complaint.vo;

/**
 * 追溯包 JSON 附件（内存一次写出）。
 */
public record ComplaintPackageExportFile(String fileName, byte[] content) {
}
