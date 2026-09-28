package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.common.exception.BusinessException;
import com.fc.v2.mapper.auto.TGasRepairOrderMapper;
import com.fc.v2.mapper.auto.TGasRepairOrderRecordMapper;
import com.fc.v2.mapper.auto.TSysUserMapper;
import com.fc.v2.model.auto.TGasRepairOrder;
import com.fc.v2.model.auto.TGasRepairOrderRecord;
import com.fc.v2.model.auto.TSysNotice;
import com.fc.v2.model.auto.TSysNoticeUser;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.service.ITGasRepairOrderService;
import com.fc.v2.service.ITSysNoticeService;
import com.fc.v2.service.ITSysNoticeUserService;

/**
 * 抢修工单 Service业务层处理（state-machine 形状：五段顺序流转 + 履历留痕 + 封卡封存）
 *
 * 处置链严格逐档推进，条件 UPDATE 原子防跳级/防并发重入：
 * 接单(0→1) → 开始抢修(1→2) → 提交抢修结果(2→3) → 现场复核(3→4) → 完工封卡(4→5)。
 * 每个过口动作同事务落一条处置履历（操作人/操作时间/环节快照/备注），履历只增不改；
 * 完工封卡后 stage=5/status=2，任何后续流转条件都不再命中，单据永久封存。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGasRepairOrderServiceImpl implements ITGasRepairOrderService {

    /** 处置段：待接单（初始） */
    private static final int STAGE_WAIT_CLAIM = 0;
    /** 处置段：已接单 */
    private static final int STAGE_CLAIMED = 1;
    /** 处置段：抢修中 */
    private static final int STAGE_REPAIRING = 2;
    /** 处置段：待复核 */
    private static final int STAGE_WAIT_REVIEW = 3;
    /** 处置段：待封卡 */
    private static final int STAGE_WAIT_SEAL = 4;
    /** 处置段：已封存（终态） */
    private static final int STAGE_SEALED = 5;

    /** 工单落定：待接单 */
    private static final int STATUS_WAIT_CLAIM = 0;
    /** 工单落定：在办 */
    private static final int STATUS_ACTIVE = 1;
    /** 工单落定：已封存 */
    private static final int STATUS_TERMINAL = 2;

    /** 过口动作 0接单 1开始抢修 2提交抢修结果 3现场复核 4完工封卡（下标与所处段对齐） */
    private static final String[] STAGE_NAMES = {"接单", "开始抢修", "提交抢修结果", "现场复核", "完工封卡"};

    /** 阶段展示名（含终态，下标与 stage 0..5 对齐） */
    private static final String[] STAGE_DISPLAY_NAMES = {"待接单", "已接单", "抢修中", "待复核", "待封卡", "已封存"};

    /** 同一工单两次超期预警的最小间隔（毫秒，24小时） */
    private static final long WARN_INTERVAL_MS = 24L * 60 * 60 * 1000;

    /** 站内信类型：预警通知 */
    private static final int NOTICE_TYPE_WARN = 2;

    @javax.annotation.Resource
    private TGasRepairOrderMapper gasRepairOrderMapper;

    @javax.annotation.Resource
    private TGasRepairOrderRecordMapper gasRepairOrderRecordMapper;

    @javax.annotation.Resource
    private TSysUserMapper sysUserMapper;

    @javax.annotation.Resource
    private ITSysNoticeService sysNoticeService;

    @javax.annotation.Resource
    private ITSysNoticeUserService sysNoticeUserService;

    @Override
    public TGasRepairOrder selectTGasRepairOrderById(Long id) {
        TGasRepairOrder order = this.gasRepairOrderMapper.selectById(id);
        if (order != null) {
            fillDisplayFields(java.util.Collections.singletonList(order));
        }
        return order;
    }

    @Override
    public List<TGasRepairOrder> selectTGasRepairOrderList(QueryWrapper<TGasRepairOrder> queryWrapper) {
        List<TGasRepairOrder> list = this.gasRepairOrderMapper.selectList(queryWrapper);
        fillDisplayFields(list);
        return list;
    }

    @Override
    public List<TSysUser> listOwners() {
        return this.sysUserMapper.selectList(new QueryWrapper<TSysUser>()
                .eq("user_type", "00").orderByAsc("id"));
    }

    /**
     * 发生燃气泄漏等险情时创建抢修工单。落定为待接单/在办入口，
     * 处置时限用于超期预警；负责人必须为在册系统用户。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(String content, Long ownerId, Date dueAt, String remark) {
        if (content == null || content.trim().isEmpty() || ownerId == null) {
            return null;
        }
        TSysUser owner = this.sysUserMapper.selectById(ownerId);
        if (owner == null) {
            return null;
        }
        Date now = new Date();
        TGasRepairOrder order = new TGasRepairOrder();
        order.setBizNo("RO" + new SimpleDateFormat("yyyyMMddHHmmss").format(now));
        order.setContent(content.trim());
        order.setStage(STAGE_WAIT_CLAIM);
        order.setStatus(STATUS_WAIT_CLAIM);
        order.setOwnerId(owner.getId());
        order.setOwner(owner.getUsername());
        order.setOwnerName(owner.getNickname());
        order.setDueAt(dueAt);
        order.setWarnCount(0);
        order.setDelFlag(0);
        order.setRemark(remark);
        if (this.gasRepairOrderMapper.insert(order) <= 0) {
            return null;
        }
        return order.getId();
    }

    /** 接单：巡检员领取工单，待接单 → 已接单，登记接单人与接单时间 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasRepairOrder claim(Long id, TSysUser operator, String remark) {
        TGasRepairOrder order = loadOrder(id);
        requireOperator(operator);
        Date now = new Date();
        int rows = this.gasRepairOrderMapper.update(null, new UpdateWrapper<TGasRepairOrder>()
                .eq("id", id)
                .eq("stage", STAGE_WAIT_CLAIM)
                .eq("status", STATUS_WAIT_CLAIM)
                .eq("del_flag", 0)
                .set("stage", STAGE_CLAIMED)
                .set("status", STATUS_ACTIVE)
                .set("assignee_id", operator.getId())
                .set("assignee", operator.getUsername())
                .set("assignee_name", operator.getNickname())
                .set("claim_time", now)
                .set("update_time", now)
                .set("last_action", STAGE_NAMES[STAGE_WAIT_CLAIM]));
        if (rows <= 0) {
            throw new BusinessException("接单失败：工单不存在或已被领取，请勿重复接单");
        }
        writeRecord(order, STAGE_WAIT_CLAIM, operator, remark, now);
        return reloadOrThrow(id);
    }

    /** 开始抢修：已接单 → 抢修中 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasRepairOrder start(Long id, TSysUser operator, String remark) {
        return advance(id, STAGE_CLAIMED, "start_time", operator, remark, "该工单尚未接单，不能开始抢修");
    }

    /** 提交抢修结果：抢修中 → 待复核，抢修结果必填 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasRepairOrder submitResult(Long id, TSysUser operator, String repairResult, String remark) {
        if (repairResult == null || repairResult.trim().isEmpty()) {
            throw new BusinessException("提交失败：必须填写抢修结果");
        }
        TGasRepairOrder order = loadOrder(id);
        requireOperator(operator);
        Date now = new Date();
        int rows = this.gasRepairOrderMapper.update(null, new UpdateWrapper<TGasRepairOrder>()
                .eq("id", id)
                .eq("stage", STAGE_REPAIRING)
                .eq("status", STATUS_ACTIVE)
                .eq("del_flag", 0)
                .set("stage", STAGE_WAIT_REVIEW)
                .set("submit_time", now)
                .set("update_time", now)
                .set("repair_result", repairResult.trim())
                .set("last_action", STAGE_NAMES[STAGE_REPAIRING]));
        if (rows <= 0) {
            throw new BusinessException("提交失败：当前不是抢修中阶段，不能提交抢修结果");
        }
        String opinion = joinOpinion(repairResult, remark);
        writeRecord(order, STAGE_REPAIRING, operator, opinion, now);
        return reloadOrThrow(id);
    }

    /** 现场复核：待复核 → 待封卡 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasRepairOrder review(Long id, TSysUser operator, String remark) {
        return advance(id, STAGE_WAIT_REVIEW, "review_time", operator, remark, "该工单尚未提交抢修结果，不能现场复核");
    }

    /** 完工封卡：待封卡 → 已封存。封卡即终态，主单与履历此后永久封存不得再改 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasRepairOrder seal(Long id, TSysUser operator, String remark) {
        TGasRepairOrder order = loadOrder(id);
        requireOperator(operator);
        Date now = new Date();
        int rows = this.gasRepairOrderMapper.update(null, new UpdateWrapper<TGasRepairOrder>()
                .eq("id", id)
                .eq("stage", STAGE_WAIT_SEAL)
                .eq("status", STATUS_ACTIVE)
                .eq("del_flag", 0)
                .set("stage", STAGE_SEALED)
                .set("status", STATUS_TERMINAL)
                .set("seal_time", now)
                .set("update_time", now)
                .set("last_action", STAGE_NAMES[STAGE_WAIT_SEAL]));
        if (rows <= 0) {
            throw new BusinessException("封卡失败：仅待封卡阶段的工单可完工封卡");
        }
        writeRecord(order, STAGE_WAIT_SEAL, operator, remark, now);
        return reloadOrThrow(id);
    }

    @Override
    public List<TGasRepairOrderRecord> listRecords(Long orderId) {
        if (orderId == null) {
            return new ArrayList<TGasRepairOrderRecord>();
        }
        return this.gasRepairOrderRecordMapper.selectList(new QueryWrapper<TGasRepairOrderRecord>()
                .eq("order_id", orderId).orderByAsc("create_time").orderByAsc("id"));
    }

    /**
     * 超期预警扫描：处置时限已过仍未封卡、且距上次预警满 24 小时的工单，
     * 向负责人与接单巡检员发站内信督促处置，并累加预警次数。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanOverdue(Date now) {
        Date scanAt = now == null ? new Date() : now;
        QueryWrapper<TGasRepairOrder> qw = new QueryWrapper<TGasRepairOrder>()
                .eq("del_flag", 0)
                .ne("status", STATUS_TERMINAL)
                .lt("due_at", scanAt)
                .and(w -> w.isNull("last_warn_time").or().lt("last_warn_time", new Date(scanAt.getTime() - WARN_INTERVAL_MS)));
        List<TGasRepairOrder> overdueList = this.gasRepairOrderMapper.selectList(qw);
        int warned = 0;
        for (TGasRepairOrder order : overdueList) {
            warnOne(order, scanAt);
            warned++;
        }
        return warned;
    }

    /** 相邻阶段逐档推进（开始抢修/现场复核共用） */
    private TGasRepairOrder advance(Long id, int fromStage, String timeColumn, TSysUser operator,
                                   String remark, String rejectMessage) {
        TGasRepairOrder order = loadOrder(id);
        requireOperator(operator);
        Date now = new Date();
        Integer expectStatus = fromStage == STAGE_WAIT_CLAIM ? STATUS_WAIT_CLAIM : STATUS_ACTIVE;
        int rows = this.gasRepairOrderMapper.update(null, new UpdateWrapper<TGasRepairOrder>()
                .eq("id", id)
                .eq("stage", fromStage)
                .eq("status", expectStatus)
                .eq("del_flag", 0)
                .set("stage", fromStage + 1)
                .set(timeColumn, now)
                .set("update_time", now)
                .set("last_action", STAGE_NAMES[fromStage]));
        if (rows <= 0) {
            throw new BusinessException(rejectMessage);
        }
        String opinion = remark == null || remark.trim().isEmpty() ? null : remark.trim();
        writeRecord(order, fromStage, operator, opinion, now);
        return reloadOrThrow(id);
    }

    /** 回查可流转单据：不存在、已删除或已封存一律拒绝 */
    private TGasRepairOrder loadOrder(Long id) {
        if (id == null) {
            throw new BusinessException("工单不存在");
        }
        TGasRepairOrder order = this.gasRepairOrderMapper.selectById(id);
        if (order == null || (order.getDelFlag() != null && order.getDelFlag() == 1)) {
            throw new BusinessException("工单不存在");
        }
        if (order.getStatus() != null && order.getStatus() == STATUS_TERMINAL) {
            throw new BusinessException("工单已完工封卡并永久封存，不得再进行任何操作");
        }
        return order;
    }

    /** 落一条处置履历（操作人/操作时间/所处环节快照/备注），履历只增不改 */
    private void writeRecord(TGasRepairOrder order, int action, TSysUser operator, String opinion, Date at) {
        TGasRepairOrderRecord record = new TGasRepairOrderRecord();
        record.setOrderId(order.getId());
        record.setBizNo(order.getBizNo());
        record.setStage(action);
        record.setStageName(STAGE_NAMES[action]);
        record.setAction(action);
        record.setOperator(operator.getUsername());
        record.setOperatorName(operator.getNickname() != null ? operator.getNickname() : operator.getUsername());
        record.setOpinion(opinion);
        record.setCreateTime(at);
        this.gasRepairOrderRecordMapper.insert(record);
    }

    /** 单条超期工单预警：站内信通知负责人与接单人（去重），回写预警痕迹 */
    private void warnOne(TGasRepairOrder order, Date now) {
        List<Long> userIds = new ArrayList<Long>();
        if (order.getOwnerId() != null) {
            userIds.add(order.getOwnerId());
        }
        if (order.getAssigneeId() != null && !userIds.contains(order.getAssigneeId())) {
            userIds.add(order.getAssigneeId());
        }
        String stageName = stageName(order.getStage());
        StringBuilder content = new StringBuilder();
        content.append("抢修工单 ").append(order.getBizNo()).append(" 已超过处置时限仍未完工封卡。");
        if (order.getContent() != null) {
            content.append("险情：").append(order.getContent()).append("。");
        }
        content.append("当前阶段：").append(stageName).append("。");
        content.append("负责人：").append(order.getOwnerName() != null ? order.getOwnerName() : order.getOwner()).append("。");
        if (order.getAssigneeName() != null) {
            content.append("接单人：").append(order.getAssigneeName()).append("。");
        }
        content.append("请立即督促现场处置并尽快完成后续流转。");

        TSysNotice notice = new TSysNotice();
        notice.setTitle("【抢修工单超期预警】" + order.getBizNo() + "（" + stageName + "）");
        notice.setContent(content.toString());
        notice.setType(NOTICE_TYPE_WARN);
        this.sysNoticeService.insertTSysNotice(notice);
        for (Long userId : userIds) {
            TSysNoticeUser link = new TSysNoticeUser();
            link.setNoticeId(notice.getId());
            link.setUserId(userId);
            link.setState(0);
            this.sysNoticeUserService.insertTSysNoticeUser(link);
        }

        this.gasRepairOrderMapper.update(null, new UpdateWrapper<TGasRepairOrder>()
                .eq("id", order.getId())
                .ne("status", STATUS_TERMINAL)
                .setSql("warn_count = IFNULL(warn_count, 0) + 1")
                .set("last_warn_time", now)
                .set("update_time", now));
    }

    /** 回填展示字段：当前阶段名 + 超期未封卡标记 */
    private void fillDisplayFields(List<TGasRepairOrder> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (TGasRepairOrder order : list) {
            if (order == null) {
                continue;
            }
            order.setStageName(stageName(order.getStage()));
            boolean sealed = order.getStatus() != null && order.getStatus() == STATUS_TERMINAL;
            order.setOverdue(!sealed && order.getDueAt() != null && order.getDueAt().before(now));
        }
    }

    private String stageName(Integer stage) {
        if (stage == null) {
            return STAGE_DISPLAY_NAMES[STAGE_WAIT_CLAIM];
        }
        if (stage >= 0 && stage < STAGE_DISPLAY_NAMES.length) {
            return STAGE_DISPLAY_NAMES[stage];
        }
        return String.valueOf(stage);
    }

    private String joinOpinion(String repairResult, String remark) {
        String result = repairResult == null ? "" : repairResult.trim();
        if (remark != null && !remark.trim().isEmpty()) {
            result = result.isEmpty() ? remark.trim() : result + "；备注：" + remark.trim();
        }
        return result.isEmpty() ? null : result;
    }

    private TGasRepairOrder reloadOrThrow(Long id) {
        TGasRepairOrder order = this.gasRepairOrderMapper.selectById(id);
        fillDisplayFields(java.util.Collections.singletonList(order));
        return order;
    }

    /** 校验操作人：每个过口动作必须记录操作人 */
    private void requireOperator(TSysUser operator) {
        if (operator == null || operator.getId() == null || operator.getUsername() == null) {
            throw new BusinessException("未获取到登录操作人，请重新登录");
        }
    }
}
