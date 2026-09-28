package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TGasRepairOrder;
import com.fc.v2.model.auto.TGasRepairOrderRecord;
import com.fc.v2.model.auto.TSysUser;

/**
 * 抢修工单 Service接口（state-machine 形状：单据五段顺序流转 + 履历留痕 + 封卡封存）
 *
 * 处置链：接单(0→1) → 开始抢修(1→2) → 提交抢修结果(2→3) → 现场复核(3→4) → 完工封卡(4→5)。
 * 只允许相邻阶段逐档推进，不允许跳级；封卡后单据永久封存不得再修改。
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITGasRepairOrderService {

    /** 按主键查询单据 */
    TGasRepairOrder selectTGasRepairOrderById(Long id);

    /** 列表查询（流转台账，回填阶段名/超期标记） */
    List<TGasRepairOrder> selectTGasRepairOrderList(QueryWrapper<TGasRepairOrder> queryWrapper);

    /** 可选负责人（建单下拉） */
    List<TSysUser> listOwners();

    /**
     * 发生燃气泄漏等险情时创建抢修工单（待接单）。
     * @return 新单主键；入参非法或负责人不存在返回 null
     */
    Long createOrder(String content, Long ownerId, Date dueAt, String remark);

    /** 接单：巡检员领取工单，0待接单 → 1已接单 */
    TGasRepairOrder claim(Long id, TSysUser operator, String remark);

    /** 开始抢修：1已接单 → 2抢修中 */
    TGasRepairOrder start(Long id, TSysUser operator, String remark);

    /** 提交抢修结果：2抢修中 → 3待复核，必须填写抢修结果 */
    TGasRepairOrder submitResult(Long id, TSysUser operator, String repairResult, String remark);

    /** 现场复核：3待复核 → 4待封卡 */
    TGasRepairOrder review(Long id, TSysUser operator, String remark);

    /** 完工封卡：4待封卡 → 5已封存，终态永久封存 */
    TGasRepairOrder seal(Long id, TSysUser operator, String remark);

    /** 处置履历：按操作时间升序返回该单全部过口留痕 */
    List<TGasRepairOrderRecord> listRecords(Long orderId);

    /**
     * 扫描处置时限已过仍未封卡的工单，向负责人/接单人发预警站内信。
     * 同一工单距上次预警不足 24 小时不重复提醒。
     * @return 本轮发出预警的工单数
     */
    int scanOverdue(Date now);
}
