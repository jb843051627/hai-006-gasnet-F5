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
 * 处置流程严格五段顺序流转，不得跳级：
 * 0待接单 →(接单)→ 1已接单 →(开始抢修)→ 2抢修中 →(提交抢修结果)→ 3待复核
 * →(现场复核)→ 4待封卡 →(完工封卡)→ 5已封存（终态，永久封存不得再改）。
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

    /** 险情记事(泄漏位置/险情描述等) */
    @TableField("content")
    @ApiModelProperty(value = "险情记事")
    private String content;

    /** 当前处置段 0待接单 1已接单 2抢修中 3待复核 4待封卡 5已封存 */
    @TableField("stage")
    @ApiModelProperty(value = "当前处置段 0待接单 1已接单 2抢修中 3待复核 4待封卡 5已封存")
    private Integer stage;

    /** 工单落定 0待接单 1在办 2已封存 */
    @TableField("status")
    @ApiModelProperty(value = "工单落定 0待接单 1在办 2已封存")
    private Integer status;

    /** 负责人 t_sys_user.id(超期预警接收人) */
    @TableField("owner_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "负责人主键")
    private Long ownerId;

    /** 负责人登录名(快照) */
    @TableField("owner")
    @ApiModelProperty(value = "负责人登录名")
    private String owner;

    /** 负责人姓名(快照) */
    @TableField("owner_name")
    @ApiModelProperty(value = "负责人姓名")
    private String ownerName;

    /** 接单巡检员 t_sys_user.id */
    @TableField("assignee_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "接单巡检员主键")
    private Long assigneeId;

    /** 接单人登录名(快照) */
    @TableField("assignee")
    @ApiModelProperty(value = "接单人登录名")
    private String assignee;

    /** 接单人姓名(快照) */
    @TableField("assignee_name")
    @ApiModelProperty(value = "接单人姓名")
    private String assigneeName;

    /** 处置时限(超期未封卡即预警) */
    @TableField("due_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "处置时限")
    private Date dueAt;

    /** 抢修结果(处置措施/换件/恢复供气等) */
    @TableField("repair_result")
    @ApiModelProperty(value = "抢修结果")
    private String repairResult;

    /** 接单时间 */
    @TableField("claim_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "接单时间")
    private Date claimTime;

    /** 开始抢修时间 */
    @TableField("start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "开始抢修时间")
    private Date startTime;

    /** 提交抢修结果时间 */
    @TableField("submit_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "提交抢修结果时间")
    private Date submitTime;

    /** 现场复核时间 */
    @TableField("review_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "现场复核时间")
    private Date reviewTime;

    /** 完工封卡时间 */
    @TableField("seal_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "完工封卡时间")
    private Date sealTime;

    /** 超期预警已发次数 */
    @TableField("warn_count")
    @ApiModelProperty(value = "超期预警已发次数")
    private Integer warnCount;

    /** 最近一次超期预警时间(24小时内不重复提醒) */
    @TableField("last_warn_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "最近一次超期预警时间")
    private Date lastWarnTime;

    /** 最近一次过口动作 */
    @TableField("last_action")
    @ApiModelProperty(value = "最近一次过口动作")
    private String lastAction;

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

    /** 当前阶段名（展示用，非表字段） */
    @TableField(exist = false)
    @ApiModelProperty(value = "当前阶段名")
    private String stageName;

    /** 是否超期未封卡（展示用，非表字段） */
    @TableField(exist = false)
    @ApiModelProperty(value = "是否超期未封卡")
    private Boolean overdue;

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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getAssigneeName() {
        return assigneeName;
    }

    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }

    public Date getDueAt() {
        return dueAt;
    }

    public void setDueAt(Date dueAt) {
        this.dueAt = dueAt;
    }

    public String getRepairResult() {
        return repairResult;
    }

    public void setRepairResult(String repairResult) {
        this.repairResult = repairResult;
    }

    public Date getClaimTime() {
        return claimTime;
    }

    public void setClaimTime(Date claimTime) {
        this.claimTime = claimTime;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setStartTime(Date startTime) {
        this.startTime = startTime;
    }

    public Date getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(Date submitTime) {
        this.submitTime = submitTime;
    }

    public Date getReviewTime() {
        return reviewTime;
    }

    public void setReviewTime(Date reviewTime) {
        this.reviewTime = reviewTime;
    }

    public Date getSealTime() {
        return sealTime;
    }

    public void setSealTime(Date sealTime) {
        this.sealTime = sealTime;
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

    public String getLastAction() {
        return lastAction;
    }

    public void setLastAction(String lastAction) {
        this.lastAction = lastAction;
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

    public Boolean getOverdue() {
        return overdue;
    }

    public void setOverdue(Boolean overdue) {
        this.overdue = overdue;
    }
}
