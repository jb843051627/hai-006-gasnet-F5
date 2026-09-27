package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * 到期任务扫描与派单核心逻辑单元测试
 */
@ExtendWith(MockitoExtension.class)
class TGasPlanTaskServiceImplTest {

    @Mock
    private TGasPlanTaskMapper gasPlanTaskMapper;
    @Mock
    private TGasPipeMapper gasPipeMapper;
    @Mock
    private ITGasTaskDispatchService gasTaskDispatchService;
    @Mock
    private ITSysDepartmentService sysDepartmentService;
    @Mock
    private ITSysUserService sysUserService;

    @InjectMocks
    private TGasPlanTaskServiceImpl service;

    private static Date at(String ts) throws Exception {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(ts);
    }

    private static TGasPlanTask task(long id, String dueAt, Integer amount) throws Exception {
        TGasPlanTask t = new TGasPlanTask();
        t.setId(id);
        t.setItemNo("PT-" + id);
        t.setDueAt(at(dueAt));
        t.setAmount(amount == null ? null : new BigDecimal(amount));
        t.setStatus(0);
        t.setDelFlag(0);
        t.setContent("事由-" + id);
        return t;
    }

    private static TSysDepartment team(long id) {
        TSysDepartment d = new TSysDepartment();
        d.setId(id);
        d.setDeptName("巡检" + id + "班");
        d.setStatus(1);
        return d;
    }

    private static TGasPipe stoppedPipe(long id) {
        TGasPipe p = new TGasPipe();
        p.setId(id);
        p.setStatus(1); // 已停用（退役）
        return p;
    }

    // ---------- listDue ----------

    @Test
    void listDue_onlyWindowOpenedTasks() throws Exception {
        Date now = at("2026-09-26 12:00:00");
        List<TGasPlanTask> rows = Arrays.asList(
                task(1, "2026-09-21 12:00:00", 3),   // 超期5天 → 在窗口
                task(2, "2026-09-28 12:00:00", 3),   // 2天后到期，提前3天 → 窗口已开
                task(3, "2026-10-06 12:00:00", 3),   // 10天后到期，提前3天 → 窗口未开
                task(4, "2026-09-26 11:00:00", null), // 今日到期，amount为空按0 → 在窗口
                task(5, "2026-09-27 12:00:00", null)  // 明天到期，amount为空 → 窗口未开
        );
        when(gasPlanTaskMapper.selectList(any())).thenReturn(rows);

        List<TGasPlanTask> due = service.listDue(now);
        List<Long> ids = due.stream().map(TGasPlanTask::getId).collect(Collectors.toList());
        assertEquals(Arrays.asList(1L, 2L, 4L), ids);
    }

    // ---------- runOnce ----------

    @Test
    void runOnce_emptyDueList_returnsZero() {
        // 回归：旧实现 due.get(0) 在空列表时抛 IndexOutOfBoundsException
        when(gasPlanTaskMapper.selectList(any())).thenReturn(Collections.emptyList());
        int ok = service.runOnce(new Date());
        assertEquals(0, ok);
        verify(gasTaskDispatchService, never()).insertTGasTaskDispatch(any());
    }

    @Test
    void runOnce_noEnabledTeam_returnsZeroAndKeepsPending() throws Exception {
        Date now = at("2026-09-26 12:00:00");
        when(gasPlanTaskMapper.selectList(any()))
                .thenReturn(Collections.singletonList(task(1, "2026-09-25 12:00:00", 3)));
        when(sysDepartmentService.selectTSysDepartmentList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(Collections.emptyList());

        int ok = service.runOnce(now);
        assertEquals(0, ok);
        verify(gasTaskDispatchService, never()).insertTGasTaskDispatch(any());
        verify(gasPlanTaskMapper, never()).updateById(any());
    }

    @Test
    void runOnce_urgentFirstAndLoadBalanced() throws Exception {
        Date now = at("2026-09-26 12:00:00");
        List<TGasPlanTask> rows = new ArrayList<TGasPlanTask>(Arrays.asList(
                task(1, "2026-09-26 13:00:00", 3),  // 今日到期，超期0
                task(2, "2026-09-21 12:00:00", 3),  // 超期5天 → 紧急，应最先派
                task(3, "2026-09-25 12:00:00", 3)   // 超期1天
        ));
        when(gasPlanTaskMapper.selectList(any())).thenReturn(rows);
        when(sysDepartmentService.selectTSysDepartmentList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(Arrays.asList(team(101), team(102)));
        // 已有负载：101班2条，102班0条
        Map<String, Integer> load = new HashMap<String, Integer>();
        load.put("0:101", 2);
        load.put("0:102", 0);
        when(gasTaskDispatchService.countOpenLoadByTarget()).thenReturn(load);
        when(gasTaskDispatchService.insertTGasTaskDispatch(any())).thenReturn(1);
        when(gasPlanTaskMapper.updateById(any())).thenReturn(1);

        int ok = service.runOnce(now);
        assertEquals(3, ok);

        // 派发顺序：超期5天 → 超期1天 → 今日到期
        InOrder inOrder = inOrder(gasTaskDispatchService);
        ArgumentCaptor<TGasTaskDispatch> captor = ArgumentCaptor.forClass(TGasTaskDispatch.class);
        inOrder.verify(gasTaskDispatchService, times(3)).insertTGasTaskDispatch(captor.capture());
        List<TGasTaskDispatch> inserted = captor.getAllValues();
        assertEquals("PT-2", inserted.get(0).getItemNo());
        assertEquals("PT-3", inserted.get(1).getItemNo());
        assertEquals("PT-1", inserted.get(2).getItemNo());

        // 负载均衡：102(0) → 102(1) → 101(并列取id小者)
        assertEquals("102", inserted.get(0).getTargetId());
        assertEquals("102", inserted.get(1).getTargetId());
        assertEquals("101", inserted.get(2).getTargetId());

        // 紧急标记与超期天数
        assertEquals(1, inserted.get(0).getUrgent());
        assertEquals(5, inserted.get(0).getOverdueDays());
        assertEquals(0, inserted.get(1).getUrgent());
        assertEquals(1, inserted.get(1).getOverdueDays());
        assertEquals(0, inserted.get(2).getOverdueDays());

        // 自动派发、派发人 system、任务置为已派
        assertEquals(0, inserted.get(0).getDispatchMode());
        assertEquals("system", inserted.get(0).getDispatchBy());
        ArgumentCaptor<TGasPlanTask> taskCaptor = ArgumentCaptor.forClass(TGasPlanTask.class);
        verify(gasPlanTaskMapper, times(3)).updateById(taskCaptor.capture());
        assertTrue(taskCaptor.getAllValues().stream().allMatch(t -> t.getStatus() == 1));
    }

    @Test
    void runOnce_singleFailure_skipsAndContinues() throws Exception {
        Date now = at("2026-09-26 12:00:00");
        when(gasPlanTaskMapper.selectList(any())).thenReturn(new ArrayList<TGasPlanTask>(Arrays.asList(
                task(1, "2026-09-26 13:00:00", 3),
                task(2, "2026-09-25 12:00:00", 3),
                task(3, "2026-09-24 12:00:00", 3)
        )));
        when(sysDepartmentService.selectTSysDepartmentList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(Collections.singletonList(team(101)));
        when(gasTaskDispatchService.countOpenLoadByTarget()).thenReturn(new HashMap<String, Integer>());
        // 第2条插入抛异常，其余成功
        when(gasTaskDispatchService.insertTGasTaskDispatch(any()))
                .thenReturn(1)
                .thenThrow(new RuntimeException("db error"))
                .thenReturn(1);
        when(gasPlanTaskMapper.updateById(any())).thenReturn(1);

        int ok = service.runOnce(now);
        assertEquals(2, ok);
    }

    // ---------- dispatchBatch ----------

    @Test
    void dispatchBatch_explicitTeam_skipsNonPending() throws Exception {
        when(sysDepartmentService.selectTSysDepartmentById(101L)).thenReturn(team(101));
        when(gasPlanTaskMapper.selectById(1L)).thenReturn(task(1, "2026-09-25 12:00:00", 3));
        TGasPlanTask done = task(2, "2026-09-25 12:00:00", 3);
        done.setStatus(1); // 已派 → 跳过
        when(gasPlanTaskMapper.selectById(2L)).thenReturn(done);
        when(gasTaskDispatchService.insertTGasTaskDispatch(any())).thenReturn(1);
        when(gasPlanTaskMapper.updateById(any())).thenReturn(1);

        int ok = service.dispatchBatch(Arrays.asList(1L, 2L), 0, 101L, "admin");
        assertEquals(1, ok);

        ArgumentCaptor<TGasTaskDispatch> captor = ArgumentCaptor.forClass(TGasTaskDispatch.class);
        verify(gasTaskDispatchService, times(1)).insertTGasTaskDispatch(captor.capture());
        TGasTaskDispatch record = captor.getValue();
        assertEquals("PT-1", record.getItemNo());
        assertEquals("101", record.getTargetId());
        assertEquals("巡检101班", record.getTargetName());
        assertEquals(1, record.getDispatchMode()); // 手动指派
        assertEquals("admin", record.getDispatchBy());
    }

    @Test
    void dispatchBatch_explicitInspector_usesNicknameFallback() throws Exception {
        TSysUser user = new TSysUser();
        user.setId(55L);
        user.setUsername("xunjian01");
        user.setNickname(null);
        when(sysUserService.selectTSysUserById(55L)).thenReturn(user);
        when(gasPlanTaskMapper.selectById(1L)).thenReturn(task(1, "2026-09-25 12:00:00", 3));
        when(gasTaskDispatchService.insertTGasTaskDispatch(any())).thenReturn(1);
        when(gasPlanTaskMapper.updateById(any())).thenReturn(1);

        int ok = service.dispatchBatch(Collections.singletonList(1L), 1, 55L, "admin");
        assertEquals(1, ok);

        ArgumentCaptor<TGasTaskDispatch> captor = ArgumentCaptor.forClass(TGasTaskDispatch.class);
        verify(gasTaskDispatchService).insertTGasTaskDispatch(captor.capture());
        assertEquals(1, captor.getValue().getTargetType());
        assertEquals("55", captor.getValue().getTargetId());
        assertEquals("xunjian01", captor.getValue().getTargetName());
    }

    @Test
    void dispatchBatch_autoBalance_dispatchesToLeastLoadedTeam() throws Exception {
        when(gasPlanTaskMapper.selectById(1L)).thenReturn(task(1, "2026-09-25 12:00:00", 3));
        when(gasPlanTaskMapper.selectById(2L)).thenReturn(task(2, "2026-09-24 12:00:00", 3));
        when(sysDepartmentService.selectTSysDepartmentList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(Arrays.asList(team(101), team(102)));
        Map<String, Integer> load = new HashMap<String, Integer>();
        load.put("0:101", 3);
        load.put("0:102", 1);
        when(gasTaskDispatchService.countOpenLoadByTarget()).thenReturn(load);
        when(gasTaskDispatchService.insertTGasTaskDispatch(any())).thenReturn(1);
        when(gasPlanTaskMapper.updateById(any())).thenReturn(1);

        int ok = service.dispatchBatch(Arrays.asList(1L, 2L), null, null, "admin");
        assertEquals(2, ok);

        ArgumentCaptor<TGasTaskDispatch> captor = ArgumentCaptor.forClass(TGasTaskDispatch.class);
        verify(gasTaskDispatchService, times(2)).insertTGasTaskDispatch(captor.capture());
        // 两条都派给负载更低的102班（就地累加后 1→2 仍低于 3）
        assertEquals("102", captor.getAllValues().get(0).getTargetId());
        assertEquals("102", captor.getAllValues().get(1).getTargetId());
    }

    // ---------- 停用管段阻断 ----------

    @Test
    void listDue_skipsStoppedSiteTasks() throws Exception {
        Date now = at("2026-09-26 12:00:00");
        TGasPlanTask stopped = task(1, "2026-09-25 12:00:00", 3);
        stopped.setSiteId(1L);                          // 已停用管段
        TGasPlanTask active = task(2, "2026-09-25 12:00:00", 3);
        active.setSiteId(0L);                           // 在用管段
        TGasPlanTask unlinked = task(3, "2026-09-25 12:00:00", 3); // 未关联管段
        when(gasPlanTaskMapper.selectList(any())).thenReturn(Arrays.asList(stopped, active, unlinked));
        when(gasPipeMapper.selectList(any())).thenReturn(Collections.singletonList(stoppedPipe(1L)));

        List<TGasPlanTask> due = service.listDue(now);
        List<Long> ids = due.stream().map(TGasPlanTask::getId).collect(Collectors.toList());
        assertEquals(Arrays.asList(2L, 3L), ids);
    }

    @Test
    void dispatchBatch_skipsStoppedSiteTasks() throws Exception {
        TGasPlanTask stopped = task(1, "2026-09-25 12:00:00", 3);
        stopped.setSiteId(1L);
        when(gasPlanTaskMapper.selectById(1L)).thenReturn(stopped);
        when(gasPlanTaskMapper.selectById(2L)).thenReturn(task(2, "2026-09-25 12:00:00", 3));
        when(gasPipeMapper.selectList(any())).thenReturn(Collections.singletonList(stoppedPipe(1L)));
        when(sysDepartmentService.selectTSysDepartmentById(101L)).thenReturn(team(101));
        when(gasTaskDispatchService.insertTGasTaskDispatch(any())).thenReturn(1);
        when(gasPlanTaskMapper.updateById(any())).thenReturn(1);

        int ok = service.dispatchBatch(Arrays.asList(1L, 2L), 0, 101L, "admin");
        assertEquals(1, ok); // 停用管段任务被跳过，仅 PT-2 派出
        ArgumentCaptor<TGasTaskDispatch> captor = ArgumentCaptor.forClass(TGasTaskDispatch.class);
        verify(gasTaskDispatchService, times(1)).insertTGasTaskDispatch(captor.capture());
        assertEquals("PT-2", captor.getValue().getItemNo());
    }

    @Test
    void cancelPendingBySite_marksCancelled() {
        when(gasPlanTaskMapper.update(any(), any())).thenReturn(2);
        assertEquals(2, service.cancelPendingBySite(0L, "管段停用"));
        ArgumentCaptor<TGasPlanTask> captor = ArgumentCaptor.forClass(TGasPlanTask.class);
        verify(gasPlanTaskMapper).update(captor.capture(), any());
        assertEquals(3, captor.getValue().getStatus()); // 3已取消(管段停用)
        // 空管段直接返回，不触库
        assertEquals(0, service.cancelPendingBySite(null, "管段停用"));
    }

    // ---------- overdueDays ----------

    @Test
    void overdueDays_calendarDayDiff() throws Exception {
        ITGasPlanTaskService svc = service;
        // 昨天23点到期，今天凌晨1点扫描 → 按自然日算超期1天
        TGasPlanTask t1 = task(1, "2026-09-25 23:00:00", 3);
        assertEquals(1, svc.overdueDays(t1, at("2026-09-26 01:00:00")));
        // 恰好超期3天 → 紧急边界
        TGasPlanTask t2 = task(2, "2026-09-23 10:00:00", 3);
        assertEquals(3, svc.overdueDays(t2, at("2026-09-26 10:00:00")));
        // 未到期 → 0
        TGasPlanTask t3 = task(3, "2026-09-28 00:00:00", 3);
        assertEquals(0, svc.overdueDays(t3, at("2026-09-26 10:00:00")));
        // 空值容错
        TGasPlanTask t4 = new TGasPlanTask();
        assertEquals(0, svc.overdueDays(t4, new Date()));
    }
}
