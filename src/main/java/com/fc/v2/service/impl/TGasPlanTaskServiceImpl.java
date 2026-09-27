package com.fc.v2.service.impl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TGasPipeMapper;
import com.fc.v2.mapper.auto.TGasPlanTaskMapper;
import com.fc.v2.model.auto.TGasPipe;
import com.fc.v2.model.auto.TGasPlanTask;
import com.fc.v2.model.auto.TGasTaskDispatch;
import com.fc.v2.model.auto.TSysDepartment;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.service.ITGasPlanTaskService;
import com.fc.v2.service.ITGasTaskDispatchService;
import com.fc.v2.service.ITSysDepartmentService;
import com.fc.v2.service.ITSysUserService;

/**
 * 巡检计划到期任务派单 Service业务层处理（scheduling-job 形状：周期执行）
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TGasPlanTaskServiceImpl implements ITGasPlanTaskService {

    private static final Logger log = LoggerFactory.getLogger(TGasPlanTaskServiceImpl.class);

    /** 条目情形：待派 */
    private static final int STATUS_WAIT = 0;
    /** 条目情形：已派 */
    private static final int STATUS_DONE = 1;
    /** 条目情形：已取消（管段停用） */
    private static final int STATUS_CANCELLED = 3;
    /** 底档情形：已停用（退役） */
    private static final int PIPE_STOPPED = 1;
    /** 超期达到该自然日数即视为紧急，优先派发 */
    private static final int URGENT_OVERDUE_DAYS = 3;
    /** 派发方式：自动扫描 */
    private static final int MODE_AUTO = 0;
    /** 派发方式：手动指派 */
    private static final int MODE_MANUAL = 1;
    /** 接收对象：班组 */
    private static final int TARGET_TEAM = 0;
    /** 接收对象：巡检员 */
    private static final int TARGET_INSPECTOR = 1;
    /** 部门状态：启用 */
    private static final int DEPT_ENABLED = 1;

    @javax.annotation.Resource
    private TGasPlanTaskMapper gasPlanTaskMapper;

    @javax.annotation.Resource
    private TGasPipeMapper gasPipeMapper;

    @Autowired
    private ITGasTaskDispatchService gasTaskDispatchService;

    @Autowired
    private ITSysDepartmentService sysDepartmentService;

    @Autowired
    private ITSysUserService sysUserService;

    @Override
    public TGasPlanTask selectTGasPlanTaskById(Long id) {
        return this.gasPlanTaskMapper.selectById(id);
    }

    @Override
    public List<TGasPlanTask> selectTGasPlanTaskList(QueryWrapper<TGasPlanTask> queryWrapper) {
        return this.gasPlanTaskMapper.selectList(queryWrapper);
    }

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）：JDBC 的 serverTimezone 与本机
     * 时区不对称，直接把 java.util.Date 作参数会整体偏移，使"恰好到期"这类边界用例错判。
     */
    private static String ts(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
    }

    /** 墙钟日期（yyyy-MM-dd），用于按自然日算超期 */
    private static String day(Date d) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd").format(d);
    }

    @Override
    public int overdueDays(TGasPlanTask task, Date at) {
        if (task == null || task.getDueAt() == null || at == null) {
            return 0;
        }
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd");
            long diff = fmt.parse(day(at)).getTime() - fmt.parse(day(task.getDueAt())).getTime();
            return (int) Math.max(0, diff / (24L * 3600 * 1000));
        } catch (java.text.ParseException e) {
            return 0;
        }
    }

    /**
     * 该时刻可处理的条目：未删除 + 待派 + 派单窗口已开
     * （窗口开启时刻 = due_at 前推 amount 个自然日，amount 为空按 0 计）。
     * 状态与删除标记走 SQL，窗口边界沿用墙钟字符串比较。
     * 已停用管段的条目不再生成派单（未关联管段的条目不受影响）。
     */
    @Override
    public List<TGasPlanTask> listDue(Date at) {
        QueryWrapper<TGasPlanTask> qw = new QueryWrapper<TGasPlanTask>();
        qw.eq("del_flag", 0).eq("status", STATUS_WAIT).isNotNull("due_at");
        List<TGasPlanTask> waiting = this.gasPlanTaskMapper.selectList(qw);
        Set<Long> stoppedSites = stoppedSiteIds();
        List<TGasPlanTask> due = new ArrayList<TGasPlanTask>();
        for (TGasPlanTask r : waiting) {
            if (r.getSiteId() != null && stoppedSites.contains(r.getSiteId())) {
                continue;
            }
            int ahead = r.getAmount() == null ? 0 : r.getAmount().intValue();
            Calendar open = Calendar.getInstance();
            open.setTime(r.getDueAt());
            open.add(Calendar.DAY_OF_YEAR, -ahead);
            if (ts(open.getTime()).compareTo(ts(at)) <= 0) {
                due.add(r);
            }
        }
        return due;
    }

    /**
     * 管段停用后取消其在派任务：该管段全部"待派"条目置为已取消，
     * 后续周期扫描与手动派单均不再生成该管段的巡检派单。
     */
    @Override
    public int cancelPendingBySite(Long siteId, String reason) {
        if (siteId == null) {
            return 0;
        }
        TGasPlanTask upd = new TGasPlanTask();
        upd.setStatus(STATUS_CANCELLED);
        upd.setRemark(reason);
        return this.gasPlanTaskMapper.update(upd, new UpdateWrapper<TGasPlanTask>()
                .eq("site_id", siteId).eq("status", STATUS_WAIT).eq("del_flag", 0));
    }

    /** 已停用管段底档主键集合（无停用管段时为空集） */
    private Set<Long> stoppedSiteIds() {
        QueryWrapper<TGasPipe> qw = new QueryWrapper<TGasPipe>();
        qw.eq("del_flag", 0).eq("status", PIPE_STOPPED);
        Set<Long> ids = new HashSet<Long>();
        for (TGasPipe pipe : this.gasPipeMapper.selectList(qw)) {
            ids.add(pipe.getId());
        }
        return ids;
    }

    /**
     * 周期扫描入口：取出该时刻可派条目，紧急（超期≥3天）优先，
     * 按班组当前负载均衡自动派单。返回成功条数；单条失败跳过继续。
     */
    @Override
    public int runOnce(Date at) {
        List<TGasPlanTask> due = listDue(at);
        if (due.isEmpty()) {
            return 0;
        }
        List<TSysDepartment> teams = listEnabledTeams();
        if (teams.isEmpty()) {
            log.warn("[planTaskScan] 存在 {} 条可派任务，但无启用中的巡检班组，本轮跳过", due.size());
            return 0;
        }
        sortUrgentFirst(due, at);
        Map<String, Integer> load = gasTaskDispatchService.countOpenLoadByTarget();
        int ok = 0;
        for (TGasPlanTask task : due) {
            TSysDepartment team = pickLeastLoaded(teams, load);
            try {
                if (dispatchOne(task, TARGET_TEAM, team.getId(), team.getDeptName(),
                        MODE_AUTO, "system", at)) {
                    // 负载就地累加，保证同批次后续任务继续往低负载班组倾斜
                    load.merge(TARGET_TEAM + ":" + team.getId(), 1, Integer::sum);
                    ok++;
                }
            } catch (Exception e) {
                log.error("[planTaskScan] 任务 {} 自动派单失败，跳过继续", task.getId(), e);
            }
        }
        return ok;
    }

    /**
     * 手动批量派单：指定接收对象时全部派给该对象；未指定时按班组负载自动均衡。
     * 仅处理仍为"待派"的条目，已派/已撤销的跳过，保证重复提交不重复派单。
     * 已停用管段的条目一律跳过，不再生成巡检派单。
     */
    @Override
    public int dispatchBatch(List<Long> ids, Integer targetType, Long targetId, String dispatchBy) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Date at = new Date();
        int ok = 0;
        Set<Long> stoppedSites = stoppedSiteIds();
        if (targetId != null) {
            String[] target = resolveTarget(targetType, targetId);
            if (target == null) {
                return 0;
            }
            for (Long id : ids) {
                TGasPlanTask task = this.gasPlanTaskMapper.selectById(id);
                if (task == null || task.getStatus() == null || task.getStatus() != STATUS_WAIT) {
                    continue;
                }
                if (task.getSiteId() != null && stoppedSites.contains(task.getSiteId())) {
                    log.info("[planTaskDispatch] 任务 {} 所属管段已停用，跳过派单", id);
                    continue;
                }
                try {
                    if (dispatchOne(task, targetType, targetId, target[1], MODE_MANUAL, dispatchBy, at)) {
                        ok++;
                    }
                } catch (Exception e) {
                    log.error("[planTaskDispatch] 任务 {} 手动派单失败，跳过继续", id, e);
                }
            }
            return ok;
        }
        // 未指定接收对象：按班组负载自动均衡，紧急优先
        List<TGasPlanTask> tasks = new ArrayList<TGasPlanTask>();
        for (Long id : ids) {
            TGasPlanTask task = this.gasPlanTaskMapper.selectById(id);
            if (task != null && task.getStatus() != null && task.getStatus() == STATUS_WAIT
                    && (task.getSiteId() == null || !stoppedSites.contains(task.getSiteId()))) {
                tasks.add(task);
            }
        }
        List<TSysDepartment> teams = listEnabledTeams();
        if (tasks.isEmpty() || teams.isEmpty()) {
            return 0;
        }
        sortUrgentFirst(tasks, at);
        Map<String, Integer> load = gasTaskDispatchService.countOpenLoadByTarget();
        for (TGasPlanTask task : tasks) {
            TSysDepartment team = pickLeastLoaded(teams, load);
            try {
                if (dispatchOne(task, TARGET_TEAM, team.getId(), team.getDeptName(),
                        MODE_MANUAL, dispatchBy, at)) {
                    load.merge(TARGET_TEAM + ":" + team.getId(), 1, Integer::sum);
                    ok++;
                }
            } catch (Exception e) {
                log.error("[planTaskDispatch] 任务 {} 自动均衡派单失败，跳过继续", task.getId(), e);
            }
        }
        return ok;
    }

    /** 启用中的巡检班组（按主键升序，保证负载并列时选取结果确定） */
    private List<TSysDepartment> listEnabledTeams() {
        QueryWrapper<TSysDepartment> qw = new QueryWrapper<TSysDepartment>();
        qw.eq("status", DEPT_ENABLED).orderByAsc("id");
        return sysDepartmentService.selectTSysDepartmentList(qw);
    }

    /** 紧急优先：超期天数降序，并列时按截止时刻升序 */
    private void sortUrgentFirst(List<TGasPlanTask> tasks, Date at) {
        final Date now = at;
        tasks.sort((a, b) -> {
            int byOverdue = overdueDays(b, now) - overdueDays(a, now);
            if (byOverdue != 0) {
                return byOverdue;
            }
            return ts(a.getDueAt()).compareTo(ts(b.getDueAt()));
        });
    }

    /** 当前负载最低的班组；并列时取主键最小者（teams 已按主键升序） */
    private TSysDepartment pickLeastLoaded(List<TSysDepartment> teams, Map<String, Integer> load) {
        TSysDepartment best = teams.get(0);
        int bestLoad = load.getOrDefault(TARGET_TEAM + ":" + best.getId(), 0);
        for (TSysDepartment team : teams) {
            int cur = load.getOrDefault(TARGET_TEAM + ":" + team.getId(), 0);
            if (cur < bestLoad) {
                best = team;
                bestLoad = cur;
            }
        }
        return best;
    }

    /** 解析指定接收对象，返回 [targetId, targetName]；对象不存在返回 null */
    private String[] resolveTarget(Integer targetType, Long targetId) {
        if (targetType != null && targetType == TARGET_INSPECTOR) {
            TSysUser user = sysUserService.selectTSysUserById(targetId);
            if (user == null) {
                return null;
            }
            String name = user.getNickname() != null ? user.getNickname() : user.getUsername();
            return new String[]{String.valueOf(user.getId()), name};
        }
        TSysDepartment dept = sysDepartmentService.selectTSysDepartmentById(targetId);
        if (dept == null) {
            return null;
        }
        return new String[]{String.valueOf(dept.getId()), dept.getDeptName()};
    }

    /**
     * 落一条派单：先插派发记录再置任务为已派；任务更新失败时补偿删除记录，
     * 避免"任务仍待派却已留痕"导致下轮重复派单。
     */
    private boolean dispatchOne(TGasPlanTask task, Integer targetType, Long targetId, String targetName,
                                Integer mode, String dispatchBy, Date at) {
        int overdue = overdueDays(task, at);
        TGasTaskDispatch record = new TGasTaskDispatch();
        record.setDispatchNo("DP" + new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(at) + "-" + task.getId());
        record.setTaskId(task.getId());
        record.setItemNo(task.getItemNo());
        record.setTargetType(targetType);
        record.setTargetId(String.valueOf(targetId));
        record.setTargetName(targetName);
        record.setDispatchMode(mode);
        record.setUrgent(overdue >= URGENT_OVERDUE_DAYS ? 1 : 0);
        record.setOverdueDays(overdue);
        record.setDispatchTime(at);
        record.setDispatchBy(dispatchBy);
        record.setExecStatus(0);
        record.setStatus(0);
        record.setDelFlag(0);
        record.setRemark(task.getContent());
        if (gasTaskDispatchService.insertTGasTaskDispatch(record) <= 0) {
            return false;
        }
        TGasPlanTask upd = new TGasPlanTask();
        upd.setId(task.getId());
        upd.setStatus(STATUS_DONE);
        if (this.gasPlanTaskMapper.updateById(upd) <= 0) {
            // 补偿：撤掉刚落的派发记录（insert 后主键已回填），任务保持待派，留待下轮
            gasTaskDispatchService.deleteTGasTaskDispatchById(record.getId());
            return false;
        }
        return true;
    }
}
