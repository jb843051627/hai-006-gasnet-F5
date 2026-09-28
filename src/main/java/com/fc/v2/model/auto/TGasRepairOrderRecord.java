package com.fc.v2.model.auto;

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
 * 抢修工单处置履历对象 t_gas_repair_order_record
 *
 * 每一处置段落一条（操作人/操作时间/备注/所处环节快照），只增不改不删；
 * 库级触发器兜底，保障抢修记录真实可溯。
 *
 * @author fuce
 * @date 2026-09-27
 */
@TableName("t_gas_repair_order_record")
@ApiModel(value = "TGasRepairOrderRecord", description = "抢修工单处置履历")
public class TGasRepairOrderRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 抢修工单 t_gas_repair_order.id */
    @TableField("order_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "抢修工单主键")
    private Long orderId;

    /** 抢修工单号(冗余) */
    @TableField("biz_no")
    @ApiModelProperty(value = "抢修工单号")
    private String bizNo;

    /** 动作完成后所处处置段 0待接单 1已接单 2抢修中 3待复核 4复核通过 5已封卡 */
    @TableField("stage")
    @ApiModelProperty(value = "动作完成后所处处置段")
    private Integer stage;

    /** 处置段名快照(待接单/已接单/抢修中/待复核/复核通过/已封卡) */
    @TableField("stage_name")
    @ApiModelProperty(value = "处置段名快照")
    private String stageName;

    /** 过口动作 0创建 1接单 2开始抢修 3提交抢修结果 4现场复核 5完工封卡 */
    @TableField("action")
    @ApiModelProperty(value = "过口动作 0创建 1接单 2开始抢修 3提交抢修结果 4现场复核 5完工封卡")
    private Integer action;

    /** 操作人(登录名) */
    @TableField("operator")
    @ApiModelProperty(value = "操作人")
    private String operator;

    /** 操作备注/处置说明 */
    @TableField("remark")
    @ApiModelProperty(value = "操作备注/处置说明")
    private String remark;

    /** 操作时间 */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "操作时间")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public Integer getStage() {
        return stage;
    }

    public void setStage(Integer stage) {
        this.stage = stage;
    }

    public String getStageName() {
        return stageName;
    }

    public void setStageName(String stageName) {
        this.stageName = stageName;
    }

    public Integer getAction() {
        return action;
    }

    public void setAction(Integer action) {
        this.action = action;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
