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
import java.util.Date;

/**
 * 抢修工单对象 t_gas_repair_order
 *
 * 五段处置状态机：0待接单 → 1已接单 → 2抢修中 → 3待复核 → 4复核通过 → 5已封卡，
 * 仅允许逐段推进不得跳级；封卡(status=2)后永久封存不得修改（库级触发器兜底）。
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_gas_repair_order")
@ApiModel(value = "TGasRepairOrder", description = "抢修工单")
public class TGasRepairOrder implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 抢修工单号 */
    @TableField("biz_no")
    @ApiModelProperty(value = "抢修工单号")
    private String bizNo;

    /** 工单来源 0巡检上报 1群众报修 2应急接警 */
    @TableField("source")
    @ApiModelProperty(value = "工单来源 0巡检上报 1群众报修 2应急接警")
    private Integer source;

    /** 涉气管段/设施编号(可空) */
    @TableField("site_no")
    @ApiModelProperty(value = "涉气管段/设施编号")
    private String siteNo;

    /** 泄漏/险情发生地点 */
    @TableField("site_name")
    @ApiModelProperty(value = "泄漏/险情发生地点")
    private String siteName;

    /** 紧急程度 0一般 1紧急 2特急 */
    @TableField("leak_level")
    @ApiModelProperty(value = "紧急程度 0一般 1紧急 2特急")
    private Integer leakLevel;

    /** 险情描述/工单记事 */
    @TableField("content")
    @ApiModelProperty(value = "险情描述/工单记事")
    private String content;

    /** 报告/现场联系人 */
    @TableField("contact_person")
    @ApiModelProperty(value = "报告/现场联系人")
    private String contactPerson;

    /** 联系电话 */
    @TableField("contact_phone")
    @ApiModelProperty(value = "联系电话")
    private String contactPhone;

    /** 负责人用户id(接单前为指派负责人,接单后为接单巡检员) */
    @TableField("owner_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "负责人用户id")
    private Long ownerId;

    /** 负责人姓名(冗余) */
    @TableField("owner_name")
    @ApiModelProperty(value = "负责人姓名")
    private String ownerName;

    /** 当前处置段 0待接单 1已接单 2抢修中 3待复核 4复核通过 5已封卡 */
    @TableField("stage")
    @ApiModelProperty(value = "当前处置段 0待接单 1已接单 2抢修中 3待复核 4复核通过 5已封卡")
    private Integer stage;

    /** 工单状态 0待接单(未起) 1在办 2已封卡(永久封存) */
    @TableField("status")
    @ApiModelProperty(value = "工单状态 0待接单 1在办 2已封卡")
    private Integer status;

    /** 接单巡检员(登录名) */
    @TableField("accept_by")
    @ApiModelProperty(value = "接单巡检员")
    private String acceptBy;

    /** 接单时间 */
    @TableField("accept_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "接单时间")
    private Date acceptTime;

    /** 开始抢修操作人 */
    @TableField("repair_start_by")
    @ApiModelProperty(value = "开始抢修操作人")
    private String repairStartBy;

    /** 开始抢修时间 */
    @TableField("repair_start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "开始抢修时间")
    private Date repairStartTime;

    /** 抢修结果(处置措施/更换部件/复测情况) */
    @TableField("repair_result")
    @ApiModelProperty(value = "抢修结果")
    private String repairResult;

    /** 提交抢修结果操作人 */
    @TableField("submit_by")
    @ApiModelProperty(value = "提交抢修结果操作人")
    private String submitBy;

    /** 提交抢修结果时间 */
    @TableField("submit_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "提交抢修结果时间")
    private Date submitTime;

    /** 现场复核操作人 */
    @TableField("review_by")
    @ApiModelProperty(value = "现场复核操作人")
    private String reviewBy;

    /** 现场复核时间 */
    @TableField("review_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "现场复核时间")
    private Date reviewTime;

    /** 现场复核意见 */
    @TableField("review_remark")
    @ApiModelProperty(value = "现场复核意见")
    private String reviewRemark;

    /** 完工封卡操作人 */
    @TableField("seal_by")
    @ApiModelProperty(value = "完工封卡操作人")
    private String sealBy;

    /** 完工封卡时间 */
    @TableField("seal_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "完工封卡时间")
    private Date sealTime;

    /** 办结时限(超过即超期预警) */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "办结时限")
    private Date dueAt;

    /** 已发超期预警次数 */
    @TableField("warn_count")
    @ApiModelProperty(value = "已发超期预警次数")
    private Integer warnCount;

    /** 最近一次超期预警时间(同单24小时内不重复提醒) */
    @TableField("last_warn_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "最近一次超期预警时间")
    private Date lastWarnTime;

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

    /** 当前处置段名（展示用，非表字段） */
    @TableField(exist = false)
    @ApiModelProperty(value = "当前处置段名")
    private String stageName;

    /** 超期自然日数（展示用，非表字段；未超期为0） */
    @TableField(exist = false)
    @ApiModelProperty(value = "超期自然日数")
    private Integer overdueDays;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public Integer getSource() {
        return source;
    }

    public void setSource(Integer source) {
        this.source = source;
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

    public Integer getLeakLevel() {
        return leakLevel;
    }

    public void setLeakLevel(Integer leakLevel) {
        this.leakLevel = leakLevel;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContactPerson() {
        return contactPerson;
    }

    public void setContactPerson(String contactPerson) {
        this.contactPerson = contactPerson;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getAcceptBy() {
        return acceptBy;
    }

    public void setAcceptBy(String acceptBy) {
        this.acceptBy = acceptBy;
    }

    public Date getAcceptTime() {
        return acceptTime;
    }

    public void setAcceptTime(Date acceptTime) {
        this.acceptTime = acceptTime;
    }

    public String getRepairStartBy() {
        return repairStartBy;
    }

    public void setRepairStartBy(String repairStartBy) {
        this.repairStartBy = repairStartBy;
    }

    public Date getRepairStartTime() {
        return repairStartTime;
    }

    public void setRepairStartTime(Date repairStartTime) {
        this.repairStartTime = repairStartTime;
    }

    public String getRepairResult() {
        return repairResult;
    }

    public void setRepairResult(String repairResult) {
        this.repairResult = repairResult;
    }

    public String getSubmitBy() {
        return submitBy;
    }

    public void setSubmitBy(String submitBy) {
        this.submitBy = submitBy;
    }

    public Date getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(Date submitTime) {
        this.submitTime = submitTime;
    }

    public String getReviewBy() {
        return reviewBy;
    }

    public void setReviewBy(String reviewBy) {
        this.reviewBy = reviewBy;
    }

    public Date getReviewTime() {
        return reviewTime;
    }

    public void setReviewTime(Date reviewTime) {
        this.reviewTime = reviewTime;
    }

    public String getReviewRemark() {
        return reviewRemark;
    }

    public void setReviewRemark(String reviewRemark) {
        this.reviewRemark = reviewRemark;
    }

    public String getSealBy() {
        return sealBy;
    }

    public void setSealBy(String sealBy) {
        this.sealBy = sealBy;
    }

    public Date getSealTime() {
        return sealTime;
    }

    public void setSealTime(Date sealTime) {
        this.sealTime = sealTime;
    }

    public Date getDueAt() {
        return dueAt;
    }

    public void setDueAt(Date dueAt) {
        this.dueAt = dueAt;
    }

    public Integer getWarnCount() {
        return warnCount;
    }

    public void setWarnCount(Integer warnCount) {
        this.warnCount = warnCount;
    }

    public Date getLastWarnTime() {
        return lastWarnTime;
    }

    public void setLastWarnTime(Date lastWarnTime) {
        this.lastWarnTime = lastWarnTime;
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

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public Integer getOverdueDays() {
        return overdueDays;
    }

    public void setOverdueDays(Integer overdueDays) {
        this.overdueDays = overdueDays;
    }
}
