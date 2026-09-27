package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fc.v2.mapper.auto.TGasPipeMapper;
import com.fc.v2.mapper.auto.TGasStopBillMapper;
import com.fc.v2.mapper.auto.TGasStopBillRecordMapper;
import com.fc.v2.model.auto.TGasPipe;
import com.fc.v2.model.auto.TGasStopBill;
import com.fc.v2.model.auto.TGasStopBillRecord;
import com.fc.v2.service.ITGasPlanTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 管段停用申请审批链单元测试（不依赖 Spring 容器与数据库，mapper 全部打桩）。
 * 覆盖：发起申请 → 安全核 → 工程评 → 管理层批 → 生效停用；
 * 退回补充 → 重新提交；否决终态；非法流转拦截。
 */
@ExtendWith(MockitoExtension.class)
public class TGasStopBillServiceImplTest {

    @Mock
    private TGasStopBillMapper gasStopBillMapper;
    @Mock
    private TGasStopBillRecordMapper gasStopBillRecordMapper;
    @Mock
    private TGasPipeMapper gasPipeMapper;
    @Mock
    private ITGasPlanTaskService gasPlanTaskService;

    private TGasStopBillServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TGasStopBillServiceImpl();
        ReflectionTestUtils.setField(service, "gasStopBillMapper", gasStopBillMapper);
        ReflectionTestUtils.setField(service, "gasStopBillRecordMapper", gasStopBillRecordMapper);
        ReflectionTestUtils.setField(service, "gasPipeMapper", gasPipeMapper);
        ReflectionTestUtils.setField(service, "gasPlanTaskService", gasPlanTaskService);
    }

    /** 在用管段 */
    private TGasPipe activePipe() {
        TGasPipe pipe = new TGasPipe();
        pipe.setId(0L);
        pipe.setSiteNo("GP00");
        pipe.setStatus(0);
        return pipe;
    }

    /** 在核单据（node 0运维提/1安全核/2工程评/3管理层批） */
    private TGasStopBill runningBill(int node) {
        TGasStopBill bill = new TGasStopBill();
        bill.setId(7001L);
        bill.setBillNo("SB001");
        bill.setSiteId(0L);
        bill.setSiteNo("GP00");
        bill.setNodeNo(node);
        bill.setSignMode(0);
        bill.setNeedCount(1);
        bill.setSignCount(0);
        bill.setStatus(0);
        return bill;
    }

    @Test
    void applyCreatesBillAtSafetyNodeWithSubmitRecord() {
        when(gasPipeMapper.selectOne(any())).thenReturn(activePipe());
        when(gasStopBillMapper.selectCount(any())).thenReturn(0);
        when(gasStopBillMapper.insert(any())).thenReturn(1);

        int rows = service.apply(0L, "已达设计寿命且存在重大隐患", 0, "ops_user");

        assertEquals(1, rows);
        ArgumentCaptor<TGasStopBill> billCap = ArgumentCaptor.forClass(TGasStopBill.class);
        verify(gasStopBillMapper).insert(billCap.capture());
        TGasStopBill bill = billCap.getValue();
        assertEquals(1, bill.getNodeNo());      // 提交后进入 安全核
        assertEquals(0, bill.getStatus());      // 在核
        assertEquals("GP00", bill.getSiteNo());
        assertEquals(1, bill.getNeedCount());   // 任一人签批

        ArgumentCaptor<TGasStopBillRecord> recCap = ArgumentCaptor.forClass(TGasStopBillRecord.class);
        verify(gasStopBillRecordMapper).insert(recCap.capture());
        TGasStopBillRecord record = recCap.getValue();
        assertEquals(0, record.getAction());    // 提交
        assertEquals(0, record.getNodeNo());    // 运维申请
        assertEquals("运维申请", record.getNodeName());
        assertEquals("ops_user", record.getApprover());
        assertEquals("已达设计寿命且存在重大隐患", record.getOpinion());
        assertNotNull(record.getCreateTime());  // 审批时间留痕
    }

    @Test
    void applyFailsWhenPipeStoppedOrInflight() {
        // 管段已停用：不允许再发起停用申请
        TGasPipe stopped = activePipe();
        stopped.setStatus(1);
        when(gasPipeMapper.selectOne(any())).thenReturn(stopped);
        assertEquals(0, service.apply(1L, "理由", 0, "ops"));

        // 同一管段已有在核申请：不允许重复发起
        when(gasPipeMapper.selectOne(any())).thenReturn(activePipe());
        when(gasStopBillMapper.selectCount(any())).thenReturn(1);
        assertEquals(0, service.apply(0L, "理由", 0, "ops"));
        verify(gasStopBillMapper, never()).insert(any());
    }

    @Test
    void approveWalksChainAndFinalApprovalStopsPipeAndCancelsTasks() {
        // 安全核 → 工程评
        when(gasStopBillMapper.selectById(7001L)).thenReturn(runningBill(1));
        TGasStopBill afterSafety = service.approve(7001L, "safe_user", "现场核实属实");
        assertEquals(2, afterSafety.getNodeNo());
        assertEquals(0, afterSafety.getStatus());

        // 工程评 → 管理层批
        when(gasStopBillMapper.selectById(7001L)).thenReturn(runningBill(2));
        TGasStopBill afterEng = service.approve(7001L, "eng_user", "替代方案可行");
        assertEquals(3, afterEng.getNodeNo());
        assertEquals(0, afterEng.getStatus());

        // 管理层批 → 核讫：管段停用 + 取消后续巡检任务
        when(gasStopBillMapper.selectById(7001L)).thenReturn(runningBill(3));
        TGasStopBill finished = service.approve(7001L, "boss", "同意停用");
        assertEquals(1, finished.getStatus());
        verify(gasPipeMapper).update(any(TGasPipe.class), any());
        verify(gasPlanTaskService).cancelPendingBySite(eq(0L), contains("SB001"));

        // 三道各落一条"通过"履历，审批人/环节留痕
        ArgumentCaptor<TGasStopBillRecord> recCap = ArgumentCaptor.forClass(TGasStopBillRecord.class);
        verify(gasStopBillRecordMapper, times(3)).insert(recCap.capture());
        assertEquals("safe_user", recCap.getAllValues().get(0).getApprover());
        assertEquals("安全审核", recCap.getAllValues().get(0).getNodeName());
        assertEquals("eng_user", recCap.getAllValues().get(1).getApprover());
        assertEquals("工程评估", recCap.getAllValues().get(1).getNodeName());
        assertEquals("boss", recCap.getAllValues().get(2).getApprover());
        assertEquals("管理层批准", recCap.getAllValues().get(2).getNodeName());
    }

    @Test
    void approveCountersignNeedsTwoSignatures() {
        // 两名点齐：第一票不落道，第二票才推进
        TGasStopBill bill = runningBill(1);
        bill.setSignMode(1);
        bill.setNeedCount(2);
        when(gasStopBillMapper.selectById(7001L)).thenReturn(bill);

        TGasStopBill afterFirst = service.approve(7001L, "safe_a", "同意");
        assertEquals(1, afterFirst.getNodeNo());  // 仍在安全核
        assertEquals(1, afterFirst.getSignCount());

        TGasStopBill afterSecond = service.approve(7001L, "safe_b", "同意");
        assertEquals(2, afterSecond.getNodeNo()); // 点齐后推进工程评
        assertEquals(0, afterSecond.getSignCount());
    }

    @Test
    void approveRejectsNonRunningBill() {
        TGasStopBill done = runningBill(3);
        done.setStatus(1); // 已核讫
        when(gasStopBillMapper.selectById(7001L)).thenReturn(done);
        assertNull(service.approve(7001L, "boss", "重复签批"));
        verify(gasStopBillRecordMapper, never()).insert(any());
    }

    @Test
    void rejectIsTerminal() {
        when(gasStopBillMapper.selectById(7001L)).thenReturn(runningBill(2));
        TGasStopBill bill = service.reject(7001L, "eng_user", "替代方案不可行");
        assertEquals(3, bill.getStatus()); // 已否决

        ArgumentCaptor<TGasStopBillRecord> recCap = ArgumentCaptor.forClass(TGasStopBillRecord.class);
        verify(gasStopBillRecordMapper).insert(recCap.capture());
        assertEquals(3, recCap.getValue().getAction()); // 否决
        assertEquals("eng_user", recCap.getValue().getApprover());
        assertEquals("替代方案不可行", recCap.getValue().getOpinion());

        // 否决后不再接受任何签批
        when(gasStopBillMapper.selectById(7001L)).thenReturn(bill);
        assertNull(service.approve(7001L, "boss", "补签"));
    }

    @Test
    void rollbackThenResubmit() {
        when(gasStopBillMapper.selectById(7001L)).thenReturn(runningBill(1));
        TGasStopBill bill = service.rollback(7001L, "safe_user", "请补充现场照片");
        assertEquals(2, bill.getStatus());  // 已打回
        assertEquals(0, bill.getNodeNo());  // 打回运维

        when(gasStopBillMapper.selectById(7001L)).thenReturn(bill);
        TGasStopBill resubmitted = service.resubmit(7001L, "已补充现场照片", "ops_user");
        assertEquals(0, resubmitted.getStatus()); // 回到在核
        assertEquals(1, resubmitted.getNodeNo()); // 重进安全核
        assertEquals("已补充现场照片", resubmitted.getApplyReason());

        // 打回与重提均留痕
        ArgumentCaptor<TGasStopBillRecord> recCap = ArgumentCaptor.forClass(TGasStopBillRecord.class);
        verify(gasStopBillRecordMapper, times(2)).insert(recCap.capture());
        assertEquals(2, recCap.getAllValues().get(0).getAction()); // 退回补充
        assertEquals(0, recCap.getAllValues().get(1).getAction()); // 重新提交
    }

    @Test
    void resubmitOnlyWhenReturned() {
        when(gasStopBillMapper.selectById(7001L)).thenReturn(runningBill(1));
        assertNull(service.resubmit(7001L, "理由", "ops"));
    }
}
