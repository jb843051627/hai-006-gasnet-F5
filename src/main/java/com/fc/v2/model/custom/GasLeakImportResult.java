package com.fc.v2.model.custom;

import java.io.Serializable;

/**
 * 检漏数据批量导入结果汇总
 *
 * @author fuce
 * @date 2026-09-27
 */
public class GasLeakImportResult implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 落定的上传批次号（未指定时系统生成） */
    private String batchNo;

    /** 数据行总数（不含表头） */
    private int totalRows;

    /** 校验通过并入库行数 */
    private int okRows;

    /** 校验失败退回行数 */
    private int failRows;

    /** 同批次重复跳过行数 */
    private int dupRows;

    public GasLeakImportResult() {
    }

    public GasLeakImportResult(String batchNo, int totalRows, int okRows, int failRows, int dupRows) {
        this.batchNo = batchNo;
        this.totalRows = totalRows;
        this.okRows = okRows;
        this.failRows = failRows;
        this.dupRows = dupRows;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getOkRows() {
        return okRows;
    }

    public void setOkRows(int okRows) {
        this.okRows = okRows;
    }

    public int getFailRows() {
        return failRows;
    }

    public void setFailRows(int failRows) {
        this.failRows = failRows;
    }

    public int getDupRows() {
        return dupRows;
    }

    public void setDupRows(int dupRows) {
        this.dupRows = dupRows;
    }
}
