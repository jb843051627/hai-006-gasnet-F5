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
 * 每个过口动作（接单/开始抢修/提交抢修结果/现场复核/完工封卡）落一条，
 * 只增不改，作为抢修记录真实性与不可篡改性的留痕凭证。
 *
 * @author fuce
 * @date 2026-09-28
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

    /** 动作发生时所处段 0接单 1开始抢修 2提交抢修结果 3现场复核 4完工封卡 */
    @TableField("stage")
    @ApiModelProperty(value = "动作发生时所处段")
    private Integer stage;

    /** 环节名快照 */
    @TableField("stage_name")
    @ApiModelProperty(value = "环节名快照")
    private String stageName;

    /** 过口动作 0接单 1开始抢修 2提交抢修结果 3现场复核 4完工封卡 */
    @TableField("action")
    @ApiModelProperty(value = "过口动作 0接单 1开始抢修 2提交抢修结果 3现场复核 4完工封卡")
    private Integer action;

    /** 操作人登录名 */
    @TableField("operator")
    @ApiModelProperty(value = "操作人登录名")
    private String operator;

    /** 操作人姓名(快照) */
    @TableField("operator_name")
    @ApiModelProperty(value = "操作人姓名")
    private String operatorName;

    /** 操作备注 */
    @TableField("opinion")
    @ApiModelProperty(value = "操作备注")
    private String opinion;

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

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
