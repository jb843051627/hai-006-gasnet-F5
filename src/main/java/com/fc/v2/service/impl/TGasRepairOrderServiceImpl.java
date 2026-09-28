package com.fc.v2.service.impl;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fc.v2.common.exception.StateFlowException;
import com.fc.v2.mapper.auto.TGasRepairOrderMapper;
import com.fc.v2.mapper.auto.TGasRepairOrderRecordMapper;
import com.fc.v2.mapper.auto.TSysNoticeMapper;
import com.fc.v2.mapper.auto.TSysNoticeUserMapper;
import com.fc.v2.mapper.auto.TSysUserMapper;
import com.fc.v2.model.auto.TGasRepairOrder;
import com.fc.v2.model.auto.TGasRepairOrderRecord;
import com.fc.v2.model.auto.TSysEmail;
import com.fc.v2.model.auto.TSysNotice;
import com.fc.v2.model.auto.TSysNoticeUser;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.service.ITGasRepairOrderService;
import com.fc.v2.service.ITSysEmailService;
import com.fc.v2.util.SimpleEmailUtil;

/**
 * 抢修工单 Service业务层处理（state-machine 形状：五段处置，单据流转）
 *
 * 处置链：0待接单 → 1已接单 → 2抢修中 → 3待复核 →（复核不通过退回2抢修中）→ 4复核通过 → 5已封卡。
 * 每一处置段落履历（操作人/操作时间/备注/环节快照），只允许逐段推进不得跳级；
 * 所有流转用"当前阶段"作更新条件（affected rows=0 即阶段已变或单据已封存），防并发重复过口。
 * 完工封卡 status=2 后应用层拒绝一切改删，库级触发器再兜一道，确保记录不可篡改。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGasRepairOrderServiceImpl implements ITGasRepairOrderService {

    private static final Logger log = LoggerFactory.getLogger(TGasRepairOrderServiceImpl.class);

    /** 处置段：待接单 */
    private static final int STAGE_CREATED = 0;
    /** 处置段：已接单 */
    private static final int STAGE_ACCEPTED = 1;
    /** 处置段：抢修中 */
    private static final int STAGE_REPAIRING = 2;
    /** 处置段：待复核 */
    private static final int STAGE_REVIEWING = 3;
    /** 处置段：复核通过 */
    private static final int STAGE_REVIEW_PASSED = 4;
    /** 处置段：已封卡（终态） */
    private static final int STAGE_SEALED = 5;
    /** 处置段名快照（与 stage 0..5 对齐） */
    private static final String[] STAGE_NAMES = {"待接单", "已接单", "抢修中", "待复核", "复核通过", "已封卡"};

    /** 工单状态：待接单（未起） */
    private static final int STATUS_OPEN = 0;
    /** 工单状态：在办 */
    private static final int STATUS_ACTIVE = 1;
    /** 工单状态：已封卡（永久封存） */
    private static final int STATUS_SEALED = 2;

    /** 履历动作：创建 */
    private static final int ACTION_CREATE = 0;
    /** 履历动作：接单 */
    private static final int ACTION_ACCEPT = 1;
    /** 履历动作：开始抢修 */
    private static final int ACTION_START = 2;
    /** 履历动作：提交抢修结果 */
    private static final int ACTION_SUBMIT = 3;
    /** 履历动作：现场复核（含通过/不通过，意见里区分） */
    private static final int ACTION_REVIEW = 4;
    /** 履历动作：完工封卡 */
    private static final int ACTION_SEAL = 5;

    /** 同单超期预警最小间隔（毫秒），24小时内不重复打扰 */
    private static final long WARN_INTERVAL_MS = 24L * 3600 * 1000;

    @javax.annotation.Resource
    private TGasRepairOrderMapper gasRepairOrderMapper;

    @javax.annotation.Resource
    private TGasRepairOrderRecordMapper gasRepairOrderRecordMapper;

    @javax.annotation.Resource
    private TSysUserMapper sysUserMapper;

    @javax.annotation.Resource
    private TSysNoticeMapper sysNoticeMapper;

    @javax.annotation.Resource
    private TSysNoticeUserMapper sysNoticeUserMapper;

    @javax.annotation.Resource
    private ITSysEmailService sysEmailService;

    @Override
    public TGasRepairOrder selectTGasRepairOrderById(Long id) {
        TGasRepairOrder order = this.gasRepairOrderMapper.selectById(id);
        if (order != null) {
            fillDisplayFields(order, new Date());
        }
        return order;
    }

    @Override
    public List<TGasRepairOrder> selectTGasRepairOrderList(QueryWrapper<TGasRepairOrder> queryWrapper) {
        List<TGasRepairOrder> list = this.gasRepairOrderMapper.selectList(queryWrapper);
        Date now = new Date();
        for (TGasRepairOrder order : list) {
            fillDisplayFields(order, now);
        }
        return list;
    }

    @Override
    public List<TSysUser> listCandidateOwners() {
        return this.sysUserMapper.selectList(new QueryWrapper<TSysUser>().orderByAsc("id"));
    }

    /**
     * 建单：燃气泄漏/应急接警创建抢修工单，落 0待接单 与创建履历。
     * 险情地点与险情描述必填；办结时限可空（不填则不参与超期预警）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasRepairOrder create(TGasRepairOrder order, String operator) {
        if (order == null || isBlank(order.getContent())
                || (isBlank(order.getSiteName()) && isBlank(order.getSiteNo()))) {
            throw new StateFlowException("建单失败：险情地点与险情描述为必填项");
        }
        if (isBlank(operator)) {
            throw new StateFlowException("建单失败：操作人缺失");
        }
        Date now = new Date();
        order.setId(null);
        order.setBizNo(generateBizNo(now));
        if (order.getSource() == null) {
            order.setSource(0);
        }
        if (order.getLeakLevel() == null) {
            order.setLeakLevel(1);
        }
        if (order.getOwnerId() != null) {
            TSysUser owner = this.sysUserMapper.selectById(order.getOwnerId());
            if (owner == null) {
                throw new StateFlowException("建单失败：指派负责人不存在");
            }
            order.setOwnerName(owner.getNickname() != null ? owner.getNickname() : owner.getUsername());
        }
        order.setStage(STAGE_CREATED);
        order.setStatus(STATUS_OPEN);
        order.setWarnCount(0);
        order.setDelFlag(0);
        order.setCreateBy(operator);
        if (this.gasRepairOrderMapper.insert(order) <= 0) {
            throw new StateFlowException("建单失败：工单未保存");
        }
        writeRecord(order, ACTION_CREATE, operator,
                isBlank(order.getRemark()) ? order.getContent() : order.getRemark(), now);
        return order;
    }

    /** 接单：巡检员领取工单，0待接单 → 1已接单；领取人即工单负责人 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasRepairOrder accept(Long id, String operator, String remark) {
        TGasRepairOrder order = loadForFlow(id, operator, STAGE_CREATED, "接单");
        TSysUser inspector = findUserByLoginName(operator);
        Date now = new Date();
        order.setOwnerId(inspector == null ? order.getOwnerId() : inspector.getId());
        order.setOwnerName(inspector == null ? order.getOwnerName()
                : (inspector.getNickname() != null ? inspector.getNickname() : inspector.getUsername()));
        order.setAcceptBy(operator);
        order.setAcceptTime(now);
        order.setStage(STAGE_ACCEPTED);
        order.setStatus(STATUS_ACTIVE);
        applyGuardedUpdate(order, STAGE_CREATED, "接单");
        writeRecord(order, ACTION_ACCEPT, operator, remark, now);
        return order;
    }

    /** 开始抢修：1已接单 → 2抢修中，未接单不得开工 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasRepairOrder startRepair(Long id, String operator, String remark) {
        TGasRepairOrder order = loadForFlow(id, operator, STAGE_ACCEPTED, "开始抢修");
        Date now = new Date();
        order.setRepairStartBy(operator);
        order.setRepairStartTime(now);
        order.setStage(STAGE_REPAIRING);
        applyGuardedUpdate(order, STAGE_ACCEPTED, "开始抢修");
        writeRecord(order, ACTION_START, operator, remark, now);
        return order;
    }

    /** 提交抢修结果：2抢修中 → 3待复核，抢修结果为必填，不得空手提交 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasRepairOrder submitResult(Long id, String operator, String repairResult, String remark) {
        if (isBlank(repairResult)) {
            throw new StateFlowException("提交失败：必须填写抢修结果（处置措施、更换部件、复测情况）");
        }
        TGasRepairOrder order = loadForFlow(id, operator, STAGE_REPAIRING, "提交抢修结果");
        Date now = new Date();
        order.setRepairResult(repairResult);
        order.setSubmitBy(operator);
        order.setSubmitTime(now);
        order.setStage(STAGE_REVIEWING);
        applyGuardedUpdate(order, STAGE_REPAIRING, "提交抢修结果");
        writeRecord(order, ACTION_SUBMIT, operator,
                isBlank(remark) ? repairResult : remark, now);
        return order;
    }

    /**
     * 现场复核：3待复核 → 通过 4复核通过；不通过退回 2抢修中返工，
     * 返工后须重新"提交抢修结果"再进复核（仍逐段推进，不跳级）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasRepairOrder review(Long id, String operator, boolean passed, String reviewRemark) {
        if (isBlank(reviewRemark)) {
            throw new StateFlowException("复核失败：必须填写现场复核意见");
        }
        TGasRepairOrder order = loadForFlow(id, operator, STAGE_REVIEWING, "现场复核");
        Date now = new Date();
        int fromStage = STAGE_REVIEWING;
        order.setReviewBy(operator);
        order.setReviewTime(now);
        order.setReviewRemark((passed ? "复核通过：" : "复核不通过，退回抢修：") + reviewRemark);
        order.setStage(passed ? STAGE_REVIEW_PASSED : STAGE_REPAIRING);
        applyGuardedUpdate(order, fromStage, "现场复核");
        writeRecord(order, ACTION_REVIEW, operator,
                (passed ? "复核通过：" : "复核不通过，退回抢修：") + reviewRemark, now);
        return order;
    }

    /** 完工封卡：4复核通过 → 5已封卡（status=2）；封卡即永久封存，履历记录最终状态 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TGasRepairOrder seal(Long id, String operator, String remark) {
        TGasRepairOrder order = loadForFlow(id, operator, STAGE_REVIEW_PASSED, "完工封卡");
        Date now = new Date();
        order.setSealBy(operator);
        order.setSealTime(now);
        order.setStage(STAGE_SEALED);
        order.setStatus(STATUS_SEALED);
        applyGuardedUpdate(order, STAGE_REVIEW_PASSED, "完工封卡");
        writeRecord(order, ACTION_SEAL, operator, isBlank(remark) ? "完工封卡，工单永久封存" : remark, now);
        return order;
    }

    /** 修改工单记事：已封卡不得再改 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateContent(Long id, String content, String operator) {
        TGasRepairOrder order = loadWritable(id);
        if (isBlank(content)) {
            throw new StateFlowException("修改失败：记事内容不能为空");
        }
        TGasRepairOrder patch = new TGasRepairOrder();
        patch.setContent(content);
        int rows = this.gasRepairOrderMapper.update(patch, new UpdateWrapper<TGasRepairOrder>()
                .eq("id", id).ne("status", STATUS_SEALED).eq("del_flag", 0));
        if (rows <= 0) {
            throw new StateFlowException("修改失败：工单已封卡或已删除，不得修改");
        }
        log.info("[repairOrder] 工单 {} 记事由 {} 更新", order.getBizNo(), operator);
        return true;
    }

    /** 删除（软删）：已封卡不得删除 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id, String operator) {
        TGasRepairOrder order = loadWritable(id);
        TGasRepairOrder patch = new TGasRepairOrder();
        patch.setDelFlag(1);
        int rows = this.gasRepairOrderMapper.update(patch, new UpdateWrapper<TGasRepairOrder>()
                .eq("id", id).ne("status", STATUS_SEALED).eq("del_flag", 0));
        if (rows <= 0) {
            throw new StateFlowException("删除失败：工单已封卡，永久封存不得删除");
        }
        log.info("[repairOrder] 工单 {} 由 {} 软删", order.getBizNo(), operator);
        return true;
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
     * 超期预警扫描：已过办结时限仍未封卡、距上次提醒满24小时的工单，
     * 向负责人（无负责人则建单人）发站内通知并抄送邮件，返回本轮预警单数。
     * 单人通知/邮件失败不影响其余工单预警。
     */
    @Override
    public int scanOverdue(Date at) {
        Date now = at == null ? new Date() : at;
        List<TGasRepairOrder> overdue = this.gasRepairOrderMapper.selectList(new QueryWrapper<TGasRepairOrder>()
                .eq("del_flag", 0).ne("status", STATUS_SEALED).lt("due_at", now));
        int warned = 0;
        for (TGasRepairOrder order : overdue) {
            if (order.getLastWarnTime() != null
                    && now.getTime() - order.getLastWarnTime().getTime() < WARN_INTERVAL_MS) {
                continue; // 24小时内已提醒，不重复打扰
            }
            TSysUser recipient = resolveRecipient(order);
            if (recipient == null) {
                log.warn("[repairOrderWarn] 超期工单 {} 无负责人也无法定位建单人，本轮跳过", order.getBizNo());
                continue;
            }
            int days = overdueDays(order.getDueAt(), now);
            try {
                sendOverdueNotice(order, recipient, days, now);
                TGasRepairOrder patch = new TGasRepairOrder();
                patch.setWarnCount((order.getWarnCount() == null ? 0 : order.getWarnCount()) + 1);
                patch.setLastWarnTime(now);
                this.gasRepairOrderMapper.update(patch, new UpdateWrapper<TGasRepairOrder>()
                        .eq("id", order.getId()).ne("status", STATUS_SEALED));
                warned++;
            } catch (Exception e) {
                log.error("[repairOrderWarn] 工单 {} 超期预警发送失败，跳过继续", order.getBizNo(), e);
            }
        }
        if (warned > 0) {
            log.info("[repairOrderWarn] 本轮超期预警 {} 张抢修工单", warned);
        }
        return warned;
    }

    // ------------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------------

    /** 取单并校验：存在、未删、未封卡、当前阶段与期望一致；否则抛非法流转异常 */
    private TGasRepairOrder loadForFlow(Long id, String operator, int expectStage, String actionName) {
        if (id == null || isBlank(operator)) {
            throw new StateFlowException(actionName + "失败：工单或操作人缺失");
        }
        TGasRepairOrder order = this.gasRepairOrderMapper.selectById(id);
        if (order == null || (order.getDelFlag() != null && order.getDelFlag() == 1)) {
            throw new StateFlowException(actionName + "失败：工单不存在或已删除");
        }
        if ((order.getStatus() != null && order.getStatus() == STATUS_SEALED)
                || (order.getStage() != null && order.getStage() == STAGE_SEALED)) {
            throw new StateFlowException(actionName + "失败：工单已完工封卡，永久封存不得再操作");
        }
        int stage = order.getStage() == null ? STAGE_CREATED : order.getStage();
        if (stage != expectStage) {
            throw new StateFlowException(actionName + "失败：工单当前为「" + stageName(stage)
                    + "」，须先完成当前环节，不允许跳级操作");
        }
        return order;
    }

    /** 取未封存单据（改记事/删除前校验） */
    private TGasRepairOrder loadWritable(Long id) {
        if (id == null) {
            throw new StateFlowException("工单不存在");
        }
        TGasRepairOrder order = this.gasRepairOrderMapper.selectById(id);
        if (order == null || (order.getDelFlag() != null && order.getDelFlag() == 1)) {
            throw new StateFlowException("工单不存在或已删除");
        }
        if ((order.getStatus() != null && order.getStatus() == STATUS_SEALED)
                || (order.getStage() != null && order.getStage() == STAGE_SEALED)) {
            throw new StateFlowException("工单已完工封卡，永久封存不得修改或删除");
        }
        return order;
    }

    /**
     * 条件更新：以"进入前阶段+未封卡"为更新条件，affected rows=0 说明阶段已被他人推进
     * 或工单已封卡，抛异常回滚（履历随事务一起回滚），防并发重复过口。
     */
    private void applyGuardedUpdate(TGasRepairOrder order, int expectStage, String actionName) {
        int rows = this.gasRepairOrderMapper.update(order, new UpdateWrapper<TGasRepairOrder>()
                .eq("id", order.getId()).eq("stage", expectStage).ne("status", STATUS_SEALED));
        if (rows <= 0) {
            throw new StateFlowException(actionName + "失败：工单状态已变化或已封卡，请刷新后重试");
        }
    }

    /** 落一条处置履历（操作人/操作时间/备注/动作后环节快照），履历只增不改不删 */
    private void writeRecord(TGasRepairOrder order, int action, String operator, String remark, Date at) {
        TGasRepairOrderRecord record = new TGasRepairOrderRecord();
        record.setOrderId(order.getId());
        record.setBizNo(order.getBizNo());
        int stage = order.getStage() == null ? STAGE_CREATED : order.getStage();
        record.setStage(stage);
        record.setStageName(stageName(stage));
        record.setAction(action);
        record.setOperator(operator);
        record.setRemark(remark);
        record.setCreateTime(at);
        this.gasRepairOrderRecordMapper.insert(record);
    }

    /** 发站内通知 + 抄送邮件（邮件失败不影响站内通知落库） */
    private void sendOverdueNotice(TGasRepairOrder order, TSysUser recipient, int overdueDays, Date now) {
        String stageName = stageName(order.getStage() == null ? STAGE_CREATED : order.getStage());
        String title = "【抢修超期预警】工单" + order.getBizNo() + "已超期" + overdueDays + "天未办结";
        String content = "工单" + order.getBizNo() + "（" + nullToEmpty(order.getSiteName()) + "）当前环节「"
                + stageName + "」，办结时限 " + fmt(order.getDueAt()) + "，已超期 " + overdueDays
                + " 天。请立即登录系统督促责任巡检员尽快完成处置。";

        TSysNotice notice = new TSysNotice();
        notice.setTitle(title);
        notice.setContent(content);
        notice.setType(2);
        notice.setCreateBy("system");
        notice.setCreateTime(now);
        this.sysNoticeMapper.insert(notice);

        TSysNoticeUser link = new TSysNoticeUser();
        link.setNoticeId(notice.getId());
        link.setUserId(recipient.getId());
        link.setState(0);
        link.setCreateBy("system");
        link.setCreateTime(now);
        this.sysNoticeUserMapper.insert(link);

        if (!isBlank(recipient.getEmail())) {
            try {
                TSysEmail mail = new TSysEmail();
                mail.setReceiversEmail(recipient.getEmail());
                mail.setTitle(title);
                mail.setContent(content);
                mail.setCreateBy("system");
                mail.setCreateTime(now);
                this.sysEmailService.insertTSysEmail(mail);
                SimpleEmailUtil.sendEmail(mail);
            } catch (Exception e) {
                log.warn("[repairOrderWarn] 工单 {} 预警邮件发送失败（站内通知已发）：{}",
                        order.getBizNo(), e.getMessage());
            }
        }
    }

    /** 预警接收人：优先负责人；无负责人时按建单人登录名查用户 */
    private TSysUser resolveRecipient(TGasRepairOrder order) {
        if (order.getOwnerId() != null) {
            TSysUser owner = this.sysUserMapper.selectById(order.getOwnerId());
            if (owner != null) {
                return owner;
            }
        }
        if (!isBlank(order.getCreateBy())) {
            return findUserByLoginName(order.getCreateBy());
        }
        return null;
    }

    private TSysUser findUserByLoginName(String loginName) {
        if (isBlank(loginName)) {
            return null;
        }
        return this.sysUserMapper.selectOne(new QueryWrapper<TSysUser>().eq("username", loginName).last("limit 1"));
    }

    /** 回填展示字段：处置段名 + 超期自然日数（已封卡为0） */
    private void fillDisplayFields(TGasRepairOrder order, Date now) {
        int stage = order.getStage() == null ? STAGE_CREATED : order.getStage();
        order.setStageName(stageName(stage));
        int days = (order.getStatus() != null && order.getStatus() == STATUS_SEALED)
                || order.getDueAt() == null ? 0 : overdueDays(order.getDueAt(), now);
        order.setOverdueDays(days);
    }

    /** 超期自然日数（按日历日相减，未超期为0） */
    private int overdueDays(Date dueAt, Date at) {
        try {
            SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd");
            long diff = dayFmt.parse(dayFmt.format(at)).getTime()
                    - dayFmt.parse(dayFmt.format(dueAt)).getTime();
            return (int) Math.max(0, diff / (24L * 3600 * 1000));
        } catch (Exception e) {
            return 0;
        }
    }

    private String stageName(int stage) {
        return stage >= 0 && stage < STAGE_NAMES.length ? STAGE_NAMES[stage] : String.valueOf(stage);
    }

    private String generateBizNo(Date now) {
        long suffix = (IdWorker.getId() % 10000L + 10000L) % 10000L;
        return "WX" + new SimpleDateFormat("yyyyMMddHHmmss").format(now)
                + String.format("%04d", suffix);
    }

    private String fmt(Date date) {
        return date == null ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
