package com.fc.v2.service;

import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TGasPipe;
import com.fc.v2.model.auto.TGasStopBill;
import com.fc.v2.model.auto.TGasStopBillRecord;

/**
 * 燃气管段停用申请签核单 Service接口（approval-chain 形状：多阶段签批）
 *
 * 审批链：0运维提 → 1安全核 → 2工程评 → 3管理层批；
 * 每个过口动作（提交/通过/退回补充/否决）均落 t_gas_stop_bill_record 履历；
 * 终审通过后将管段底档置为停用，并取消该管段后续巡检任务。
 *
 * @author fuce
 * @date 2026-09-26
 */
public interface ITGasStopBillService {

    /** 按主键回查单据 */
    TGasStopBill selectTGasStopBillById(Long id);

    /** 台账查询（含管段名称、当前环节名等展示字段回填） */
    List<TGasStopBill> selectTGasStopBillList(QueryWrapper<TGasStopBill> queryWrapper);

    /** 可申请停用的在用管段（申请下拉选项） */
    List<TGasPipe> listActivePipes();

    /**
     * 运维部门发起停用申请：单据落 1安全核 待审，并写"提交"履历。
     *
     * @return 1 成功；0 失败（管段不存在/已停用/已有在核申请/参数缺失）
     */
    int apply(Long siteId, String applyReason, Integer signMode, String applicant);

    /** 签批一票（通过）：返回更新后的单据；单不存在或不在在核状态返回 null */
    TGasStopBill approve(Long id, String approver, String opinion);

    /** 否决申请（终态）：返回更新后的单据；单不存在或不在在核状态返回 null */
    TGasStopBill reject(Long id, String approver, String opinion);

    /** 退回补充材料（打回运维）：返回更新后的单据；单不存在或不在在核状态返回 null */
    TGasStopBill rollback(Long id, String approver, String opinion);

    /** 打回后运维补充材料重新提交：返回更新后的单据；单不存在或未被打回返回 null */
    TGasStopBill resubmit(Long id, String applyReason, String applicant);

    /** 某单审批履历（按审批时间升序） */
    List<TGasStopBillRecord> listRecords(Long billId);
}
