package com.fc.v2.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Date;

import com.fc.v2.common.exception.BusinessException;
import com.fc.v2.mapper.auto.TGasRepairOrderMapper;
import com.fc.v2.mapper.auto.TGasRepairOrderRecordMapper;
import com.fc.v2.mapper.auto.TSysUserMapper;
import com.fc.v2.model.auto.TGasRepairOrder;
import com.fc.v2.model.auto.TGasRepairOrderRecord;
import com.fc.v2.model.auto.TSysNotice;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.service.ITSysNoticeService;
import com.fc.v2.service.ITSysNoticeUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 抢修工单五段顺序流转单元测试（不依赖 Spring 容器与数据库，mapper 全部打桩）。
 * 覆盖：建单 → 接单 → 开始抢修 → 提交抢修结果 → 现场复核 → 完工封卡；
 * 跳级拦截；封卡后永久封存；履历留痕；超期预警。
 */
@ExtendWith(MockitoExtension.class)
public class TGasRepairOrderServiceImplTest {

    private static final Long ORDER_ID = 6001L;

    @Mock
    private TGasRepairOrderMapper gasRepairOrderMapper;
    @Mock
    private TGasRepairOrderRecordMapper gasRepairOrderRecordMapper;
    @Mock
    private TSysUserMapper sysUserMapper;
    @Mock
    private ITSysNoticeService sysNoticeService;
    @Mock
    private ITSysNoticeUserService sysNoticeUserService;

    private TGasRepairOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TGasRepairOrderServiceImpl();
        ReflectionTestUtils.setField(service, "gasRepairOrderMapper", gasRepairOrderMapper);
        ReflectionTestUtils.setField(service, "gasRepairOrderRecordMapper", gasRepairOrderRecordMapper);
        ReflectionTestUtils.setField(service, "sysUserMapper", sysUserMapper);
        ReflectionTestUtils.setField(service, "sysNoticeService", sysNoticeService);
        ReflectionTestUtils.setField(service, "sysNoticeUserService", sysNoticeUserService);
    }

    /** 巡检员（操作人） */
    private TSysUser inspector() {
        TSysUser user = new TSysUser();
        user.setId(1L);
        user.setUsername("inspector01");
        user.setNickname("张巡检");
        return user;
    }

    /** 某阶段的在办工单 */
    private TGasRepairOrder order(int stage, int status) {
        TGasRepairOrder o = new TGasRepairOrder();
        o.setId(ORDER_ID);
        o.setBizNo("RO20260928090001");
        o.setStage(stage);
        o.setStatus(status);
        o.setDelFlag(0);
        o.setOwnerId(2L);
        o.setOwner("boss01");
        o.setOwnerName("李负责");
        return o;
    }

    /** 条件更新成功，并在回查时返回推进后的工单 */
    private void stubAdvance(TGasRepairOrder before, TGasRepairOrder after) {
        when(gasRepairOrderMapper.update(any(), any())).thenReturn(1);
        when(gasRepairOrderMapper.selectById(ORDER_ID)).thenReturn(before, after);
    }

    @Test
    void createOrderEntersWaitClaim() {
        TSysUser owner = new TSysUser();
        owner.setId(2L);
        owner.setUsername("boss01");
        owner.setNickname("李负责");
        when(sysUserMapper.selectById(2L)).thenReturn(owner);
        when(gasRepairOrderMapper.insert(any())).thenReturn(1);

        Date due = new Date(System.currentTimeMillis() + 3600_000L);
        service.createOrder("调压站法兰泄漏", 2L, due, "已警戒");

        ArgumentCaptor<TGasRepairOrder> cap = ArgumentCaptor.forClass(TGasRepairOrder.class);
        verify(gasRepairOrderMapper).insert(cap.capture());
        TGasRepairOrder saved = cap.getValue();
        assertEquals(0, saved.getStage());   // 待接单
        assertEquals(0, saved.getStatus());  // 待接单
        assertEquals("boss01", saved.getOwner());
        assertEquals(0, saved.getWarnCount());
        assertEquals(due, saved.getDueAt());
        assertNotNull(saved.getBizNo());
        assertTrue(saved.getBizNo().startsWith("RO"));
    }

    @Test
    void createOrderRejectsUnknownOwner() {
        when(sysUserMapper.selectById(99L)).thenReturn(null);
        assertNull(service.createOrder("险情", 99L, null, null));
        verify(gasRepairOrderMapper, never()).insert(any());
    }

    @Test
    void fullChainWalksFiveStagesAndWritesRecordEachTime() {
        // 0待接单 → 1已接单
        TGasRepairOrder claimed = order(1, 1);
        claimed.setAssigneeId(1L);
        claimed.setAssignee("inspector01");
        claimed.setAssigneeName("张巡检");
        stubAdvance(order(0, 0), claimed);
        TGasRepairOrder afterClaim = service.claim(ORDER_ID, inspector(), "已电话核实");
        assertEquals(1, afterClaim.getStage());
        assertEquals(1, afterClaim.getStatus());
        assertEquals("inspector01", afterClaim.getAssignee());
        assertNotNull(afterClaim.getStageName());

        // 1已接单 → 2抢修中
        TGasRepairOrder repairing = order(2, 1);
        stubAdvance(order(1, 1), repairing);
        assertEquals(2, service.start(ORDER_ID, inspector(), "关阀降压").getStage());

        // 2抢修中 → 3待复核（抢修结果必填）
        TGasRepairOrder waitReview = order(3, 1);
        waitReview.setRepairResult("更换密封圈，气密试验合格");
        stubAdvance(order(2, 1), waitReview);
        TGasRepairOrder afterSubmit =
                service.submitResult(ORDER_ID, inspector(), "更换密封圈，气密试验合格", null);
        assertEquals(3, afterSubmit.getStage());

        // 3待复核 → 4待封卡
        TGasRepairOrder waitSeal = order(4, 1);
        stubAdvance(order(3, 1), waitSeal);
        assertEquals(4, service.review(ORDER_ID, inspector(), "复核通过").getStage());

        // 4待封卡 → 5已封存（终态）
        TGasRepairOrder sealed = order(5, 2);
        sealed.setSealTime(new Date());
        stubAdvance(order(4, 1), sealed);
        TGasRepairOrder afterSeal = service.seal(ORDER_ID, inspector(), "资料齐全准予封卡");
        assertEquals(5, afterSeal.getStage());
        assertEquals(2, afterSeal.getStatus());
        assertEquals("已封存", afterSeal.getStageName());

        // 五段各落一条履历：操作人/操作时间/环节快照/备注齐备
        ArgumentCaptor<TGasRepairOrderRecord> cap = ArgumentCaptor.forClass(TGasRepairOrderRecord.class);
        verify(gasRepairOrderRecordMapper, times(5)).insert(cap.capture());
        assertEquals(Arrays.asList("接单", "开始抢修", "提交抢修结果", "现场复核", "完工封卡"),
                Arrays.asList(
                        cap.getAllValues().get(0).getStageName(),
                        cap.getAllValues().get(1).getStageName(),
                        cap.getAllValues().get(2).getStageName(),
                        cap.getAllValues().get(3).getStageName(),
                        cap.getAllValues().get(4).getStageName()));
        for (TGasRepairOrderRecord r : cap.getAllValues()) {
            assertEquals(ORDER_ID, r.getOrderId());
            assertEquals("inspector01", r.getOperator());
            assertEquals("张巡检", r.getOperatorName());
            assertNotNull(r.getCreateTime());
        }
        assertNotNull(cap.getAllValues().get(0).getOpinion()); // 接单备注
    }

    @Test
    void claimRejectsWhenAlreadyClaimed() {
        // 条件 UPDATE 未命中（已被他人领取/并发重入）：抛业务异常且不写履历
        when(gasRepairOrderMapper.selectById(ORDER_ID)).thenReturn(order(0, 0));
        when(gasRepairOrderMapper.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.claim(ORDER_ID, inspector(), null));
        verify(gasRepairOrderRecordMapper, never()).insert(any());
    }

    @Test
    void cannotSkipStages() {
        // 待接单直接"开始抢修"：条件不命中，拦截跳级
        when(gasRepairOrderMapper.selectById(ORDER_ID)).thenReturn(order(0, 0));
        when(gasRepairOrderMapper.update(any(), any())).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.start(ORDER_ID, inspector(), null));

        // 抢修中直接"现场复核"：拦截
        when(gasRepairOrderMapper.selectById(ORDER_ID)).thenReturn(order(2, 1));
        assertThrows(BusinessException.class, () -> service.review(ORDER_ID, inspector(), null));

        // 待复核直接"完工封卡"：拦截
        when(gasRepairOrderMapper.selectById(ORDER_ID)).thenReturn(order(3, 1));
        assertThrows(BusinessException.class, () -> service.seal(ORDER_ID, inspector(), null));
        verify(gasRepairOrderRecordMapper, never()).insert(any());
    }

    @Test
    void submitResultRequiresResultText() {
        assertThrows(BusinessException.class, () -> service.submitResult(ORDER_ID, inspector(), "  ", null));
        verify(gasRepairOrderMapper, never()).update(any(), any());
    }

    @Test
    void sealedOrderRejectsAllFurtherActions() {
        when(gasRepairOrderMapper.selectById(ORDER_ID)).thenReturn(order(5, 2));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.seal(ORDER_ID, inspector(), "重复封卡"));
        assertTrue(ex.getMessage().contains("永久封存"));
        verify(gasRepairOrderMapper, never()).update(any(), any());
        verify(gasRepairOrderRecordMapper, never()).insert(any());
    }

    @Test
    void missingOperatorIsRejected() {
        when(gasRepairOrderMapper.selectById(ORDER_ID)).thenReturn(order(0, 0));
        assertThrows(BusinessException.class, () -> service.claim(ORDER_ID, null, null));
    }

    @Test
    void scanOverdueNotifiesOwnerAndAssigneeOncePerDay() {
        TGasRepairOrder overdue = order(2, 1);
        overdue.setAssigneeId(1L);
        overdue.setAssigneeName("张巡检");
        overdue.setDueAt(new Date(System.currentTimeMillis() - 60_000L));
        overdue.setWarnCount(0);
        when(gasRepairOrderMapper.selectList(any())).thenReturn(Arrays.asList(overdue));
        when(gasRepairOrderMapper.update(any(), any())).thenReturn(1);

        int warned = service.scanOverdue(new Date());
        assertEquals(1, warned);

        // 一条预警站内信
        ArgumentCaptor<TSysNotice> noticeCap = ArgumentCaptor.forClass(TSysNotice.class);
        verify(sysNoticeService).insertTSysNotice(noticeCap.capture());
        TSysNotice notice = noticeCap.getValue();
        assertTrue(notice.getTitle().contains("超期预警"));
        assertTrue(notice.getContent().contains("RO20260928090001"));

        // 负责人与接单人各一条接收记录（督促到具体人）
        verify(sysNoticeUserService, times(2)).insertTSysNoticeUser(any());

        // 预警痕迹：次数累加、最近预警时间回写
        verify(gasRepairOrderMapper).update(any(), any());
    }
}
