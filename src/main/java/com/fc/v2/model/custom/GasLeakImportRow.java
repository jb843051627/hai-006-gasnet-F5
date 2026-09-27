package com.fc.v2.model.custom;

import java.io.Serializable;

/**
 * 检漏数据导入 Excel 单行（解析后的原始文本，尚未校验）
 *
 * @author fuce
 * @date 2026-09-27
 */
public class GasLeakImportRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Excel 行号（1起，表头为第1行） */
    private int rowNo;

    /** 管段编号（原始文本） */
    private String siteNo;

    /** 检测时间（原始文本） */
    private String detectTimeRaw;

    /** 浓度值（原始文本） */
    private String concRaw;

    /** 经度（原始文本） */
    private String lngRaw;

    /** 纬度（原始文本） */
    private String latRaw;

    /** 设备/项代号（选填，原始文本） */
    private String itemCode;

    /** 备注（选填，原始文本） */
    private String remark;

    public int getRowNo() {
        return rowNo;
    }

    public void setRowNo(int rowNo) {
        this.rowNo = rowNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getDetectTimeRaw() {
        return detectTimeRaw;
    }

    public void setDetectTimeRaw(String detectTimeRaw) {
        this.detectTimeRaw = detectTimeRaw;
    }

    public String getConcRaw() {
        return concRaw;
    }

    public void setConcRaw(String concRaw) {
        this.concRaw = concRaw;
    }

    public String getLngRaw() {
        return lngRaw;
    }

    public void setLngRaw(String lngRaw) {
        this.lngRaw = lngRaw;
    }

    public String getLatRaw() {
        return latRaw;
    }

    public void setLatRaw(String latRaw) {
        this.latRaw = latRaw;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
