package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TGasRepairOrder;
import com.fc.v2.model.auto.TGasRepairOrderRecord;
import com.fc.v2.model.auto.TSysUser;

/**
 * 抢修工单 Service接口（state-machine 形状：五段处置，单据流转）
 *
 * 处置链：0待接单 → 1已接单 → 2抢修中 → 3待复核 →（复核不通过退回2抢修中）→ 4复核通过 → 5已封卡。
 * 每一段落处置履历（操作人/操作时间/备注）；完工封卡后永久封存，拒绝一切改删。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITGasRepairOrderService {

    /** 按主键查询单据（封存后仍可查） */
    TGasRepairOrder selectTGasRepairOrderById(Long id);

    /** 列表查询（流转台账，回填处置段名与超期天数展示字段） */
    List<TGasRepairOrder> selectTGasRepairOrderList(QueryWrapper<TGasRepairOrder> queryWrapper);

    /** 可指派为负责人的巡检员候选（建单选人用） */
    List<TSysUser> listCandidateOwners();

    /** 建单：燃气泄漏/应急接警创建抢修工单，落 0待接单 与创建履历；返回带工单号的单据 */
    TGasRepairOrder create(TGasRepairOrder order, String operator);

    /** 接单：巡检员领取工单，0待接单 → 1已接单 */
    TGasRepairOrder accept(Long id, String operator, String remark);

    /** 开始抢修：1已接单 → 2抢修中 */
    TGasRepairOrder startRepair(Long id, String operator, String remark);

    /** 提交抢修结果：2抢修中 → 3待复核，必须填写抢修结果 */
    TGasRepairOrder submitResult(Long id, String operator, String repairResult, String remark);

    /**
     * 现场复核：3待复核 → 通过 4复核通过；不通过退回 2抢修中（重修后重新提交）。
     * @param passed true复核通过 false复核不通过退回抢修
     */
    TGasRepairOrder review(Long id, String operator, boolean passed, String reviewRemark);

    /** 完工封卡：4复核通过 → 5已封卡（status=2），此后永久封存不得改删 */
    TGasRepairOrder seal(Long id, String operator, String remark);

    /** 修改工单记事：已封卡不得再改 */
    boolean updateContent(Long id, String content, String operator);

    /** 删除（软删）：已封卡不得删除 */
    boolean remove(Long id, String operator);

    /** 处置履历：按操作时间升序返回该单全部留痕 */
    List<TGasRepairOrderRecord> listRecords(Long orderId);

    /**
     * 超期预警扫描：对已过办结时限仍未封卡、且距上次提醒满24小时的工单，
     * 向负责人（无负责人时向建单人）发站内通知（有邮箱时并发邮件），返回本轮预警单数。
     */
    int scanOverdue(Date at);
}
