package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TGasPipeMapper;
import com.fc.v2.mapper.auto.TGasStopBillMapper;
import com.fc.v2.mapper.auto.TGasStopBillRecordMapper;
import com.fc.v2.model.auto.TGasPipe;
import com.fc.v2.model.auto.TGasStopBill;
import com.fc.v2.model.auto.TGasStopBillRecord;
import com.fc.v2.service.ITGasPlanTaskService;
import com.fc.v2.service.ITGasStopBillService;

/**
 * 燃气管段停用申请签核单 Service业务层处理（approval-chain 形状：多阶段签批）
 *
 * 审批链：0运维提 → 1安全核 → 2工程评 → 3管理层批。
 * 每个过口动作落 t_gas_stop_bill_record 履历（审批人/审批意见/审批时间）；
 * 终审通过同事务内将管段底档置停用，并取消该管段后续待派巡检任务。
 *
 * @author fuce
 * @date 2026-09-26
 */
@Service
public class TGasStopBillServiceImpl implements ITGasStopBillService {

    /** 审批链环节：运维提 */
    private static final int NODE_APPLY = 0;
    /** 审批链环节：安全核（申请提交后首道待审环节） */
    private static final int NODE_SAFETY = 1;
    /** 终审道：管理层批 */
    private static final int NODE_MANAGEMENT = 3;
    /** 环节名快照（与 node_no 0..3 对齐） */
    private static final String[] NODE_NAMES = {"运维申请", "安全审核", "工程评估", "管理层批准"};

    /** 报批情形：在核 */
    private static final int STATUS_RUNNING = 0;
    /** 报批情形：已核讫 */
    private static final int STATUS_PASS = 1;
    /** 报批情形：已打回（待运维补充材料） */
    private static final int STATUS_RETURNED = 2;
    /** 报批情形：已否决（终态） */
    private static final int STATUS_VETO = 3;

    /** 履历动作：提交 */
    private static final int ACTION_SUBMIT = 0;
    /** 履历动作：通过 */
    private static final int ACTION_PASS = 1;
    /** 履历动作：退回补充 */
    private static final int ACTION_RETURN = 2;
    /** 履历动作：否决 */
    private static final int ACTION_VETO = 3;

    /** 同口并印办法：任一人 */
    private static final int MODE_OR = 0;
    /** 同口并印办法：两名点齐 */
    private static final int MODE_AND = 1;

    /** 底档情形：在用 */
    private static final int PIPE_IN_USE = 0;
    /** 底档情形：已停用（退役） */
    private static final int PIPE_STOPPED = 1;

    @javax.annotation.Resource
    private TGasStopBillMapper gasStopBillMapper;

    @javax.annotation.Resource
    private TGasStopBillRecordMapper gasStopBillRecordMapper;

    @javax.annotation.Resource
    private TGasPipeMapper gasPipeMapper;

    @Autowired
    private ITGasPlanTaskService gasPlanTaskService;

    @Override
    public TGasStopBill selectTGasStopBillById(Long id) {
        return this.gasStopBillMapper.selectById(id);
    }

    @Override
    public List<TGasStopBill> selectTGasStopBillList(QueryWrapper<TGasStopBill> queryWrapper) {
        List<TGasStopBill> list = this.gasStopBillMapper.selectList(queryWrapper);
        fillDisplayFields(list);
        return list;
    }

    @Override
    public List<TGasPipe> listActivePipes() {
        QueryWrapper<TGasPipe> qw = new QueryWrapper<TGasPipe>();
        qw.eq("del_flag", 0).eq("status", PIPE_IN_USE).orderByAsc("id");
        return this.gasPipeMapper.selectList(qw);
    }

    /**
     * 运维部门发起停用申请。同一管段只允许一张在核申请；
     * 已停用管段不允许再申请。提交后单据进入 1安全核。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int apply(Long siteId, String applyReason, Integer signMode, String applicant) {
        if (siteId == null || applyReason == null || applyReason.trim().isEmpty()
                || applicant == null || applicant.trim().isEmpty()) {
            return 0;
        }
        TGasPipe pipe = this.gasPipeMapper.selectOne(new QueryWrapper<TGasPipe>()
                .eq("id", siteId).eq("del_flag", 0));
        if (pipe == null || (pipe.getStatus() != null && pipe.getStatus() == PIPE_STOPPED)) {
            return 0;
        }
        Integer inflight = this.gasStopBillMapper.selectCount(new QueryWrapper<TGasStopBill>()
                .eq("site_id", siteId).eq("status", STATUS_RUNNING).eq("del_flag", 0));
        if (inflight != null && inflight > 0) {
            return 0;
        }
        int mode = (signMode != null && signMode == MODE_AND) ? MODE_AND : MODE_OR;
        Date now = new Date();
        TGasStopBill bill = new TGasStopBill();
        bill.setBillNo("SB" + new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(now) + "-" + siteId);
        bill.setSiteId(pipe.getId());
        bill.setSiteNo(pipe.getSiteNo());
        bill.setApplyReason(applyReason);
        bill.setNodeNo(NODE_SAFETY);
        bill.setSignMode(mode);
        bill.setNeedCount(mode == MODE_AND ? 2 : 1);
        bill.setSignCount(0);
        bill.setStatus(STATUS_RUNNING);
        bill.setDelFlag(0);
        if (this.gasStopBillMapper.insert(bill) <= 0) {
            return 0;
        }
        writeRecord(bill, NODE_APPLY, ACTION_SUBMIT, applicant, applyReason, now);
        return 1;
    }

    /**
     * 签批一票（通过）。本口落印数凑齐才推进下一道；
     * 终审道（管理层批）通过后单据核讫，同事务停用管段并取消后续巡检任务。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasStopBill approve(Long id, String approver, String opinion) {
        TGasStopBill bill = runningBill(id, approver);
        if (bill == null) {
            return null;
        }
        Date now = new Date();
        writeRecord(bill, bill.getNodeNo(), ACTION_PASS, approver, opinion, now);
        int signed = (bill.getSignCount() == null ? 0 : bill.getSignCount()) + 1;
        int need = bill.getNeedCount() == null ? 1 : bill.getNeedCount();
        if (signed < need) {
            // 并印未点齐：本口继续等人落印
            bill.setSignCount(signed);
        } else {
            bill.setSignCount(0);
            int node = bill.getNodeNo() == null ? NODE_SAFETY : bill.getNodeNo();
            if (node >= NODE_MANAGEMENT) {
                bill.setStatus(STATUS_PASS);
                executeStop(bill);
            } else {
                bill.setNodeNo(node + 1);
            }
        }
        this.gasStopBillMapper.updateById(bill);
        return bill;
    }

    /**
     * 否决申请（终态）：任一在核环节均可否决，否决后单据封档不再流转。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasStopBill reject(Long id, String approver, String opinion) {
        TGasStopBill bill = runningBill(id, approver);
        if (bill == null) {
            return null;
        }
        writeRecord(bill, bill.getNodeNo(), ACTION_VETO, approver, opinion, new Date());
        bill.setStatus(STATUS_VETO);
        this.gasStopBillMapper.updateById(bill);
        return bill;
    }

    /**
     * 退回补充材料：打回 0运维提，待申请人补充后重新提交。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasStopBill rollback(Long id, String approver, String opinion) {
        TGasStopBill bill = runningBill(id, approver);
        if (bill == null) {
            return null;
        }
        writeRecord(bill, bill.getNodeNo(), ACTION_RETURN, approver, opinion, new Date());
        bill.setNodeNo(NODE_APPLY);
        bill.setSignCount(0);
        bill.setStatus(STATUS_RETURNED);
        this.gasStopBillMapper.updateById(bill);
        return bill;
    }

    /**
     * 打回后重新提交：可顺带更新申请理由，单据回到 1安全核 重走审批链。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasStopBill resubmit(Long id, String applyReason, String applicant) {
        if (id == null || applicant == null || applicant.trim().isEmpty()) {
            return null;
        }
        TGasStopBill bill = this.gasStopBillMapper.selectById(id);
        if (bill == null || bill.getStatus() == null || bill.getStatus() != STATUS_RETURNED) {
            return null;
        }
        if (applyReason != null && !applyReason.trim().isEmpty()) {
            bill.setApplyReason(applyReason);
        }
        bill.setNodeNo(NODE_SAFETY);
        bill.setSignCount(0);
        bill.setStatus(STATUS_RUNNING);
        writeRecord(bill, NODE_APPLY, ACTION_SUBMIT, applicant,
                applyReason != null && !applyReason.trim().isEmpty() ? applyReason : "补充材料后重新提交", new Date());
        this.gasStopBillMapper.updateById(bill);
        return bill;
    }

    @Override
    public List<TGasStopBillRecord> listRecords(Long billId) {
        if (billId == null) {
            return new ArrayList<TGasStopBillRecord>();
        }
        QueryWrapper<TGasStopBillRecord> qw = new QueryWrapper<TGasStopBillRecord>();
        qw.eq("bill_id", billId).orderByAsc("create_time").orderByAsc("id");
        return this.gasStopBillRecordMapper.selectList(qw);
    }

    /** 回查在核单据；单不存在、非在核或审批人为空时返回 null */
    private TGasStopBill runningBill(Long id, String approver) {
        if (id == null || approver == null || approver.trim().isEmpty()) {
            return null;
        }
        TGasStopBill bill = this.gasStopBillMapper.selectById(id);
        if (bill == null || bill.getStatus() == null || bill.getStatus() != STATUS_RUNNING) {
            return null;
        }
        return bill;
    }

    /** 落一条审批履历（审批人/审批意见/审批时间/所在环节快照） */
    private void writeRecord(TGasStopBill bill, Integer nodeNo, Integer action,
                             String approver, String opinion, Date at) {
        TGasStopBillRecord record = new TGasStopBillRecord();
        record.setBillId(bill.getId());
        record.setBillNo(bill.getBillNo());
        int node = nodeNo == null ? NODE_APPLY : nodeNo;
        record.setNodeNo(node);
        record.setNodeName(node >= 0 && node < NODE_NAMES.length ? NODE_NAMES[node] : String.valueOf(node));
        record.setAction(action);
        record.setApprover(approver);
        record.setOpinion(opinion);
        record.setCreateTime(at);
        this.gasStopBillRecordMapper.insert(record);
    }

    /**
     * 终审通过生效：管段底档置停用（仅当仍在用，保证重复提交幂等），
     * 并取消该管段后续待派巡检任务，禁止再生成巡检派单。
     */
    private void executeStop(TGasStopBill bill) {
        if (bill.getSiteId() == null) {
            return;
        }
        TGasPipe upd = new TGasPipe();
        upd.setStatus(PIPE_STOPPED);
        this.gasPipeMapper.update(upd, new UpdateWrapper<TGasPipe>()
                .eq("id", bill.getSiteId()).eq("status", PIPE_IN_USE).eq("del_flag", 0));
        gasPlanTaskService.cancelPendingBySite(bill.getSiteId(), "管段停用审批通过（" + bill.getBillNo() + "），取消后续巡检任务");
    }

    /** 回填展示字段：管段档案名称 + 当前环节名 */
    private void fillDisplayFields(List<TGasStopBill> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> siteIds = new ArrayList<Long>();
        for (TGasStopBill bill : list) {
            if (bill.getSiteId() != null && !siteIds.contains(bill.getSiteId())) {
                siteIds.add(bill.getSiteId());
            }
        }
        Map<Long, String> siteNames = new HashMap<Long, String>();
        if (!siteIds.isEmpty()) {
            QueryWrapper<TGasPipe> qw = new QueryWrapper<TGasPipe>();
            qw.in("id", siteIds);
            for (TGasPipe pipe : this.gasPipeMapper.selectList(qw)) {
                siteNames.put(pipe.getId(), pipe.getSiteName());
            }
        }
        for (TGasStopBill bill : list) {
            bill.setSiteName(siteNames.get(bill.getSiteId()));
            int node = bill.getNodeNo() == null ? 0 : bill.getNodeNo();
            bill.setNodeName(node >= 0 && node < NODE_NAMES.length ? NODE_NAMES[node] : String.valueOf(node));
        }
    }
}
