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
 * 燃气管段停用申请审批履历对象 t_gas_stop_bill_record
 *
 * @author fuce
 * @date 2026-09-26
 */
@TableName("t_gas_stop_bill_record")
@ApiModel(value = "TGasStopBillRecord", description = "燃气管段停用申请审批履历")
public class TGasStopBillRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 签核单 t_gas_stop_bill.id */
    @TableField("bill_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "签核单主键")
    private Long billId;

    /** 签核单号(冗余) */
    @TableField("bill_no")
    @ApiModelProperty(value = "签核单号")
    private String billNo;

    /** 动作发生时所在道 0..3（0运维提/1安全核/2工程评/3管理层批） */
    @TableField("node_no")
    @ApiModelProperty(value = "动作发生时所在道")
    private Integer nodeNo;

    /** 环节名快照(运维申请/安全审核/工程评估/管理层批准) */
    @TableField("node_name")
    @ApiModelProperty(value = "环节名快照")
    private String nodeName;

    /** 过口动作 0提交 1通过 2退回补充 3否决 */
    @TableField("action")
    @ApiModelProperty(value = "过口动作 0提交 1通过 2退回补充 3否决")
    private Integer action;

    /** 审批人(操作人登录名) */
    @TableField("approver")
    @ApiModelProperty(value = "审批人")
    private String approver;

    /** 审批意见 */
    @TableField("opinion")
    @ApiModelProperty(value = "审批意见")
    private String opinion;

    /** 审批时间 */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "审批时间")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public String getBillNo() {
        return billNo;
    }

    public void setBillNo(String billNo) {
        this.billNo = billNo;
    }

    public Integer getNodeNo() {
        return nodeNo;
    }

    public void setNodeNo(Integer nodeNo) {
        this.nodeNo = nodeNo;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public Integer getAction() {
        return action;
    }

    public void setAction(Integer action) {
        this.action = action;
    }

    public String getApprover() {
        return approver;
    }

    public void setApprover(String approver) {
        this.approver = approver;
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
