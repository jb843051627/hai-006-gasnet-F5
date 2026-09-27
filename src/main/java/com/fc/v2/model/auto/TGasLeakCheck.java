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
 * 泄漏浓度检测评定记录对象 t_gas_leak_check
 *
 * @author fuce
 * @date 2026-09-26
 */
@TableName("t_gas_leak_check")
@ApiModel(value = "TGasLeakCheck", description = "泄漏浓度检测评定记录")
public class TGasLeakCheck implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 检测评定记录号 */
    @TableField("check_no")
    @ApiModelProperty(value = "检测评定记录号")
    private String checkNo;

    /** 现场检测时间(以此定位在用规则版本) */
    @TableField("detect_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "现场检测时间(以此定位在用规则版本)")
    private Date detectTime;

    /** 检测点代号 */
    @TableField("site_no")
    @ApiModelProperty(value = "检测点代号")
    private String siteNo;

    /** 检测点名称 */
    @TableField("site_name")
    @ApiModelProperty(value = "检测点名称")
    private String siteName;

    /** 原始浓度读数(ppm) */
    @TableField("conc_value")
    @ApiModelProperty(value = "原始浓度读数(ppm)")
    private BigDecimal concValue;

    /** 评定等级 1轻微 2中度 3严重 4极严重 0无法判定 */
    @TableField("grade_level")
    @ApiModelProperty(value = "评定等级 1轻微 2中度 3严重 4极严重 0无法判定")
    private Integer gradeLevel;

    /** 评定时适用规则版本主键(快照) */
    @TableField("rule_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "评定时适用规则版本主键(快照)")
    private Long ruleId;

    /** 适用规则代号(快照) */
    @TableField("rule_code")
    @ApiModelProperty(value = "适用规则代号(快照)")
    private String ruleCode;

    /** 适用规则版本号(快照) */
    @TableField("rule_version")
    @ApiModelProperty(value = "适用规则版本号(快照)")
    private Integer ruleVersion;

    /** 复核情形 0未复核 1已重算 2重算后等级变更 */
    @TableField("recheck_status")
    @ApiModelProperty(value = "复核情形 0未复核 1已重算 2重算后等级变更")
    private Integer recheckStatus;

    /** 最近复核时间 */
    @TableField("recheck_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "最近复核时间")
    private Date recheckTime;

    /** 复核人 */
    @TableField("recheck_by")
    @ApiModelProperty(value = "复核人")
    private String recheckBy;

    /** 复核后等级 */
    @TableField("recheck_grade_level")
    @ApiModelProperty(value = "复核后等级")
    private Integer recheckGradeLevel;

    /** 复核时适用规则版本主键 */
    @TableField("recheck_rule_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "复核时适用规则版本主键")
    private Long recheckRuleId;

    /** 复核时适用规则版本号 */
    @TableField("recheck_rule_version")
    @ApiModelProperty(value = "复核时适用规则版本号")
    private Integer recheckRuleVersion;

    /** 复核说明 */
    @TableField("recheck_remark")
    @ApiModelProperty(value = "复核说明")
    private String recheckRemark;

    /** 检漏数据导入批次号(手工录入为NULL) */
    @TableField("batch_no")
    @ApiModelProperty(value = "检漏数据导入批次号(手工录入为NULL)")
    private String batchNo;

    /** 检测点经度 */
    @TableField("lng")
    @ApiModelProperty(value = "检测点经度")
    private BigDecimal lng;

    /** 检测点纬度 */
    @TableField("lat")
    @ApiModelProperty(value = "检测点纬度")
    private BigDecimal lat;

    /** 记录情形 0有效 1作废 */
    @TableField("status")
    @ApiModelProperty(value = "记录情形 0有效 1作废")
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

    public String getCheckNo() {
        return checkNo;
    }

    public void setCheckNo(String checkNo) {
        this.checkNo = checkNo;
    }

    public Date getDetectTime() {
        return detectTime;
    }

    public void setDetectTime(Date detectTime) {
        this.detectTime = detectTime;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public BigDecimal getConcValue() {
        return concValue;
    }

    public void setConcValue(BigDecimal concValue) {
        this.concValue = concValue;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public Integer getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(Integer ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public Integer getRecheckStatus() {
        return recheckStatus;
    }

    public void setRecheckStatus(Integer recheckStatus) {
        this.recheckStatus = recheckStatus;
    }

    public Date getRecheckTime() {
        return recheckTime;
    }

    public void setRecheckTime(Date recheckTime) {
        this.recheckTime = recheckTime;
    }

    public String getRecheckBy() {
        return recheckBy;
    }

    public void setRecheckBy(String recheckBy) {
        this.recheckBy = recheckBy;
    }

    public Integer getRecheckGradeLevel() {
        return recheckGradeLevel;
    }

    public void setRecheckGradeLevel(Integer recheckGradeLevel) {
        this.recheckGradeLevel = recheckGradeLevel;
    }

    public Long getRecheckRuleId() {
        return recheckRuleId;
    }

    public void setRecheckRuleId(Long recheckRuleId) {
        this.recheckRuleId = recheckRuleId;
    }

    public Integer getRecheckRuleVersion() {
        return recheckRuleVersion;
    }

    public void setRecheckRuleVersion(Integer recheckRuleVersion) {
        this.recheckRuleVersion = recheckRuleVersion;
    }

    public String getRecheckRemark() {
        return recheckRemark;
    }

    public void setRecheckRemark(String recheckRemark) {
        this.recheckRemark = recheckRemark;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public void setLng(BigDecimal lng) {
        this.lng = lng;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(BigDecimal lat) {
        this.lat = lat;
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
        return this.updateTime;
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
