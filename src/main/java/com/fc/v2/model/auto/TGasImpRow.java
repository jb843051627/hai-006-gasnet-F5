package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 检漏数据导入核对行对象 t_gas_imp_row
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_gas_imp_row")
@ApiModel(value = "TGasImpRow", description = "检漏数据导入核对行")
public class TGasImpRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 检漏数据上传批次号 */
    @TableField("batch_no")
    @ApiModelProperty(value = "检漏数据上传批次号")
    private String batchNo;

    /** 原表行次(Excel行号，表头为第1行) */
    @TableField("row_no")
    @ApiModelProperty(value = "原表行次(Excel行号，表头为第1行)")
    private Integer rowNo;

    /** 检测设备/项代号(选填) */
    @TableField("item_code")
    @ApiModelProperty(value = "检测设备/项代号(选填)")
    private String itemCode;

    /** 管段编号(原始值) */
    @TableField("site_no")
    @ApiModelProperty(value = "管段编号(原始值)")
    private String siteNo;

    /** 检测时间(解析成功值) */
    @TableField("detect_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "检测时间(解析成功值)")
    private Date detectTime;

    /** 检测时间原始文本 */
    @TableField("detect_time_raw")
    @ApiModelProperty(value = "检测时间原始文本")
    private String detectTimeRaw;

    /** 本行浓度读数ppm(解析成功值) */
    @TableField("qty")
    @ApiModelProperty(value = "本行浓度读数ppm(解析成功值)")
    private BigDecimal qty;

    /** 浓度原始文本 */
    @TableField("conc_raw")
    @ApiModelProperty(value = "浓度原始文本")
    private String concRaw;

    /** 经度(解析成功值) */
    @TableField("lng")
    @ApiModelProperty(value = "经度(解析成功值)")
    private BigDecimal lng;

    /** 经度原始文本 */
    @TableField("lng_raw")
    @ApiModelProperty(value = "经度原始文本")
    private String lngRaw;

    /** 纬度(解析成功值) */
    @TableField("lat")
    @ApiModelProperty(value = "纬度(解析成功值)")
    private BigDecimal lat;

    /** 纬度原始文本 */
    @TableField("lat_raw")
    @ApiModelProperty(value = "纬度原始文本")
    private String latRaw;

    /** 校验失败原因(多项以；分隔) */
    @TableField("err_msg")
    @ApiModelProperty(value = "校验失败原因(多项以；分隔)")
    private String errMsg;

    /** 入库后关联正式记录 t_gas_leak_check.id */
    @TableField("check_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "入库后关联正式记录 t_gas_leak_check.id")
    private Long checkId;

    /** 行落地情形 0待核 1已入库 2退回(校验失败) 3重复跳过 */
    @TableField("status")
    @ApiModelProperty(value = "行落地情形 0待核 1已入库 2退回(校验失败) 3重复跳过")
    private Integer status;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public Date getDetectTime() {
        return detectTime;
    }

    public void setDetectTime(Date detectTime) {
        this.detectTime = detectTime;
    }

    public String getDetectTimeRaw() {
        return detectTimeRaw;
    }

    public void setDetectTimeRaw(String detectTimeRaw) {
        this.detectTimeRaw = detectTimeRaw;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public String getConcRaw() {
        return concRaw;
    }

    public void setConcRaw(String concRaw) {
        this.concRaw = concRaw;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public void setLng(BigDecimal lng) {
        this.lng = lng;
    }

    public String getLngRaw() {
        return lngRaw;
    }

    public void setLngRaw(String lngRaw) {
        this.lngRaw = lngRaw;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(BigDecimal lat) {
        this.lat = lat;
    }

    public String getLatRaw() {
        return latRaw;
    }

    public void setLatRaw(String latRaw) {
        this.latRaw = latRaw;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public Long getCheckId() {
        return checkId;
    }

    public void setCheckId(Long checkId) {
        this.checkId = checkId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
