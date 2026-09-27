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
 * 巡检到期任务派发记录对象 t_gas_task_dispatch
 *
 * @author fuce
 * @date 2026-09-26
 */
@TableName("t_gas_task_dispatch")
@ApiModel(value = "TGasTaskDispatch", description = "巡检到期任务派发记录")
public class TGasTaskDispatch implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 派发单号 */
    @TableField("dispatch_no")
    @ApiModelProperty(value = "派发单号")
    private String dispatchNo;

    /** 关联到期任务 t_gas_plan_task.id */
    @TableField("task_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "关联到期任务")
    private Long taskId;

    /** 巡检派单事项号(冗余) */
    @TableField("item_no")
    @ApiModelProperty(value = "巡检派单事项号")
    private String itemNo;

    /** 接收对象 0班组 1巡检员 */
    @TableField("target_type")
    @ApiModelProperty(value = "接收对象 0班组 1巡检员")
    private Integer targetType;

    /** 接收对象主键(部门/用户) */
    @TableField("target_id")
    @ApiModelProperty(value = "接收对象主键")
    private String targetId;

    /** 接收对象名称(冗余) */
    @TableField("target_name")
    @ApiModelProperty(value = "接收对象名称")
    private String targetName;

    /** 派发方式 0自动扫描 1手动指派 */
    @TableField("dispatch_mode")
    @ApiModelProperty(value = "派发方式 0自动扫描 1手动指派")
    private Integer dispatchMode;

    /** 紧急程度 0普通 1紧急(超期3天及以上) */
    @TableField("urgent")
    @ApiModelProperty(value = "紧急程度 0普通 1紧急")
    private Integer urgent;

    /** 派发时已超期自然日数(未超期为0) */
    @TableField("overdue_days")
    @ApiModelProperty(value = "派发时已超期自然日数")
    private Integer overdueDays;

    /** 派发时间 */
    @TableField("dispatch_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "派发时间")
    private Date dispatchTime;

    /** 派发人(自动派发为system) */
    @TableField("dispatch_by")
    @ApiModelProperty(value = "派发人")
    private String dispatchBy;

    /** 执行情况 0待执行 1执行中 2已完成 */
    @TableField("exec_status")
    @ApiModelProperty(value = "执行情况 0待执行 1执行中 2已完成")
    private Integer execStatus;

    /** 执行完成时间 */
    @TableField("finish_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "执行完成时间")
    private Date finishTime;

    /** 记录情形 0有效 1已撤销 */
    @TableField("status")
    @ApiModelProperty(value = "记录情形 0有效 1已撤销")
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

    public String getDispatchNo() {
        return dispatchNo;
    }

    public void setDispatchNo(String dispatchNo) {
        this.dispatchNo = dispatchNo;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public Integer getTargetType() {
        return targetType;
    }

    public void setTargetType(Integer targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public Integer getDispatchMode() {
        return dispatchMode;
    }

    public void setDispatchMode(Integer dispatchMode) {
        this.dispatchMode = dispatchMode;
    }

    public Integer getUrgent() {
        return urgent;
    }

    public void setUrgent(Integer urgent) {
        this.urgent = urgent;
    }

    public Integer getOverdueDays() {
        return overdueDays;
    }

    public void setOverdueDays(Integer overdueDays) {
        this.overdueDays = overdueDays;
    }

    public Date getDispatchTime() {
        return dispatchTime;
    }

    public void setDispatchTime(Date dispatchTime) {
        this.dispatchTime = dispatchTime;
    }

    public String getDispatchBy() {
        return dispatchBy;
    }

    public void setDispatchBy(String dispatchBy) {
        this.dispatchBy = dispatchBy;
    }

    public Integer getExecStatus() {
        return execStatus;
    }

    public void setExecStatus(Integer execStatus) {
        this.execStatus = execStatus;
    }

    public Date getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(Date finishTime) {
        this.finishTime = finishTime;
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
